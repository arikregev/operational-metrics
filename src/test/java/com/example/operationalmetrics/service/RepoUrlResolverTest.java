package com.example.operationalmetrics.service;

import com.example.operationalmetrics.client.depsdev.DepsDevClient;
import com.example.operationalmetrics.client.depsdev.dto.DepsDevPurlResponse;
import com.example.operationalmetrics.client.depsdev.dto.DepsDevPurlResponse.DepsDevProjectKey;
import com.example.operationalmetrics.client.depsdev.dto.DepsDevPurlResponse.DepsDevRelatedProject;
import com.example.operationalmetrics.client.ecosystems.EcosystemsClient;
import com.example.operationalmetrics.client.ecosystems.dto.EcosystemsPackage;
import com.example.operationalmetrics.model.MetricsSource;
import com.example.operationalmetrics.model.PackageId;
import com.example.operationalmetrics.model.RepoUrl;
import com.example.operationalmetrics.repository.RepoUrlCacheDao;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.extension.ExtensionCallback;
import org.jdbi.v3.core.extension.ExtensionConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepoUrlResolverTest {

    @Mock
    private Jdbi jdbi;

    @Mock
    private DepsDevClient depsDevClient;

    @Mock
    private EcosystemsClient ecosystemsClient;

    @Mock
    private RepoUrlCacheDao repoUrlCacheDao;

    private RepoUrlResolver resolver;
    private PackageId packageId;

    @BeforeEach
    void setUp() {
        resolver = new RepoUrlResolver(jdbi, depsDevClient, ecosystemsClient);
        packageId = new PackageId("maven", "org.apache.logging.log4j", "log4j-core");

        // Stub jdbi.withExtension to invoke callback with the mocked dao — lenient because cache-only tests do not call resolve().
        lenient().when(jdbi.withExtension(eq(RepoUrlCacheDao.class), any(ExtensionCallback.class)))
                .thenAnswer(inv -> {
                    ExtensionCallback<Object, RepoUrlCacheDao, Exception> callback = inv.getArgument(1);
                    return callback.withExtension(repoUrlCacheDao);
                });
    }

    private void stubUseExtension() throws Exception {
        doAnswer(inv -> {
            ExtensionConsumer<RepoUrlCacheDao, Exception> consumer = inv.getArgument(1);
            consumer.useExtension(repoUrlCacheDao);
            return null;
        }).when(jdbi).useExtension(eq(RepoUrlCacheDao.class), any(ExtensionConsumer.class));
    }

    @Test
    void resolve_cacheHit_returnsCachedRepoUrl() {
        var entry = new RepoUrlCacheDao.RepoUrlCacheEntry(
                42L, "https://github.com/apache/log4j2", "github.com",
                "apache", "log4j2", "DEPS_DEV");
        when(repoUrlCacheDao.findByPackageId(42L)).thenReturn(Optional.of(entry));

        Optional<RepoUrl> result = resolver.resolve(packageId, 42L);

        assertThat(result).isPresent();
        assertThat(result.get().url()).isEqualTo("https://github.com/apache/log4j2");
        assertThat(result.get().platform()).isEqualTo("github.com");
        assertThat(result.get().owner()).isEqualTo("apache");
        assertThat(result.get().name()).isEqualTo("log4j2");
        verify(depsDevClient, never()).lookupPurl(anyString());
        verify(ecosystemsClient, never()).lookupByPurl(anyString());
    }

    @Test
    void resolve_cacheMiss_depsdevSucceeds_returnsRepoUrl() throws Exception {
        when(repoUrlCacheDao.findByPackageId(42L)).thenReturn(Optional.empty());
        stubUseExtension();

        DepsDevPurlResponse response = new DepsDevPurlResponse(
                null, null,
                List.of(new DepsDevRelatedProject(
                        new DepsDevProjectKey("github.com/apache/log4j2"), null, null)),
                null);
        when(depsDevClient.lookupPurl(anyString())).thenReturn(response);

        Optional<RepoUrl> result = resolver.resolve(packageId, 42L);

        assertThat(result).isPresent();
        assertThat(result.get().platform()).isEqualTo("github.com");
        assertThat(result.get().owner()).isEqualTo("apache");
        assertThat(result.get().name()).isEqualTo("log4j2");
        verify(ecosystemsClient, never()).lookupByPurl(anyString());
        // Should cache the result via DEPS_DEV
        verify(repoUrlCacheDao).upsert(eq(42L), eq("https://github.com/apache/log4j2"),
                eq("github.com"), eq("apache"), eq("log4j2"), eq("DEPS_DEV"));
    }

    @Test
    void resolve_cacheMissBothFail_returnsEmpty() {
        when(repoUrlCacheDao.findByPackageId(42L)).thenReturn(Optional.empty());

        // deps.dev throws
        when(depsDevClient.lookupPurl(anyString())).thenThrow(new RuntimeException("404"));

        // ecosyste.ms returns empty list
        when(ecosystemsClient.lookupByPurl(anyString())).thenReturn(List.of());

        Optional<RepoUrl> result = resolver.resolve(packageId, 42L);

        assertThat(result).isEmpty();
        // Cache should NOT be invoked when nothing was resolved
        verify(repoUrlCacheDao, never()).upsert(anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void resolve_depsdevFailsEcosystemsSucceeds_returnsFromEcosystems() throws Exception {
        when(repoUrlCacheDao.findByPackageId(42L)).thenReturn(Optional.empty());
        stubUseExtension();

        // deps.dev returns empty relatedProjects (so empty Optional)
        DepsDevPurlResponse response = new DepsDevPurlResponse(null, null, List.of(), null);
        when(depsDevClient.lookupPurl(anyString())).thenReturn(response);

        // ecosyste.ms succeeds
        EcosystemsPackage pkg = new EcosystemsPackage();
        pkg.setRepositoryUrl("https://github.com/apache/log4j2");
        when(ecosystemsClient.lookupByPurl(anyString())).thenReturn(List.of(pkg));

        Optional<RepoUrl> result = resolver.resolve(packageId, 42L);

        assertThat(result).isPresent();
        assertThat(result.get().url()).isEqualTo("https://github.com/apache/log4j2");
        // Should cache the result via ECOSYSTEMS
        verify(repoUrlCacheDao).upsert(eq(42L), eq("https://github.com/apache/log4j2"),
                eq("github.com"), eq("apache"), eq("log4j2"), eq("ECOSYSTEMS"));
    }

    @Test
    void resolve_depsdevReturnsNonGithubProject_skipsAndFallsBack() throws Exception {
        when(repoUrlCacheDao.findByPackageId(42L)).thenReturn(Optional.empty());
        stubUseExtension();

        // deps.dev returns gitlab project (non-github)
        DepsDevPurlResponse response = new DepsDevPurlResponse(
                null, null,
                List.of(new DepsDevRelatedProject(
                        new DepsDevProjectKey("gitlab.com/foo/bar"), null, null)),
                null);
        when(depsDevClient.lookupPurl(anyString())).thenReturn(response);

        EcosystemsPackage pkg = new EcosystemsPackage();
        pkg.setRepositoryUrl("https://gitlab.com/foo/bar");
        when(ecosystemsClient.lookupByPurl(anyString())).thenReturn(List.of(pkg));

        Optional<RepoUrl> result = resolver.resolve(packageId, 42L);

        assertThat(result).isPresent();
        assertThat(result.get().platform()).isEqualTo("gitlab.com");
    }

    @Test
    void resolve_ecosystemsBlankRepoUrl_returnsEmpty() {
        when(repoUrlCacheDao.findByPackageId(42L)).thenReturn(Optional.empty());

        when(depsDevClient.lookupPurl(anyString())).thenReturn(
                new DepsDevPurlResponse(null, null, null, null));

        EcosystemsPackage pkg = new EcosystemsPackage();
        pkg.setRepositoryUrl("");
        when(ecosystemsClient.lookupByPurl(anyString())).thenReturn(List.of(pkg));

        Optional<RepoUrl> result = resolver.resolve(packageId, 42L);

        assertThat(result).isEmpty();
    }

    @Test
    void cache_callsUpsert() throws Exception {
        stubUseExtension();
        RepoUrl repoUrl = RepoUrl.fromComponents("github.com", "apache", "log4j2");

        resolver.cache(99L, repoUrl, MetricsSource.SCORECARD);

        verify(repoUrlCacheDao).upsert(eq(99L), eq("https://github.com/apache/log4j2"),
                eq("github.com"), eq("apache"), eq("log4j2"), eq("SCORECARD"));
    }

    @Test
    void cache_swallowsExceptionFromDao() throws Exception {
        // useExtension throws — should be silently caught
        doAnswer(inv -> { throw new RuntimeException("db error"); })
                .when(jdbi).useExtension(eq(RepoUrlCacheDao.class), any(ExtensionConsumer.class));

        RepoUrl repoUrl = RepoUrl.fromComponents("github.com", "apache", "log4j2");

        // Should not throw
        resolver.cache(99L, repoUrl, MetricsSource.SCORECARD);
    }
}
