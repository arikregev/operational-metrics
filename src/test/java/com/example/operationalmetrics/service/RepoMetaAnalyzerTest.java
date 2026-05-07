package com.example.operationalmetrics.service;

import com.example.operationalmetrics.client.depsdev.DepsDevClient;
import com.example.operationalmetrics.client.depsdev.dto.DepsDevPackage;
import com.example.operationalmetrics.client.depsdev.dto.DepsDevPurlResponse;
import com.example.operationalmetrics.client.depsdev.dto.DepsDevVersionInfo;
import com.example.operationalmetrics.client.ecosystems.EcosystemsClient;
import com.example.operationalmetrics.client.ecosystems.dto.EcosystemsVersion;
import com.example.operationalmetrics.client.snyk.SnykClient;
import com.example.operationalmetrics.client.snyk.dto.SnykPackageVersionResponse;
import com.example.operationalmetrics.config.RepoMetaAnalyzerConfig;
import com.example.operationalmetrics.config.SnykConfig;
import com.example.operationalmetrics.model.PackageId;
import com.example.operationalmetrics.model.PackageVersionEntry;
import com.example.operationalmetrics.repository.PackageDao;
import com.example.operationalmetrics.repository.PackageVersionDao;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.extension.ExtensionCallback;
import org.jdbi.v3.core.extension.ExtensionConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RepoMetaAnalyzer}. Pure Mockito — no Quarkus context;
 * the JDBI extension callbacks are simulated with the same {@code doAnswer}
 * pattern used by {@link RepoUrlResolverTest}.
 */
@ExtendWith(MockitoExtension.class)
class RepoMetaAnalyzerTest {

    @Mock
    private Jdbi jdbi;

    @Mock
    private RepoMetaAnalyzerConfig config;

    @Mock
    private SnykConfig snykConfig;

    @Mock
    private EcosystemsClient ecosystemsClient;

    @Mock
    private DepsDevClient depsDevClient;

    @Mock
    private SnykClient snykClient;

    @Mock
    private PackageVersionDao dao;

    @Mock
    private PackageDao packageDao;

    private RepoMetaAnalyzer analyzer;
    private PackageId packageId;
    private final Long packageDbId = 42L;

    private RepoMetaAnalyzerConfig.Bulk bulk;
    private RepoMetaAnalyzerConfig.PerVersion perVersion;

    @BeforeEach
    void setUp() {
        analyzer = new RepoMetaAnalyzer(jdbi, config, snykConfig,
                ecosystemsClient, depsDevClient, snykClient);
        packageId = new PackageId("npm", null, "express");

        // Stub jdbi.withExtension(PackageVersionDao.class, callback) — lenient
        // because not every test reads from JDBI.
        lenient().when(jdbi.withExtension(eq(PackageVersionDao.class), any(ExtensionCallback.class)))
                .thenAnswer(inv -> {
                    ExtensionCallback<Object, PackageVersionDao, Exception> callback = inv.getArgument(1);
                    return callback.withExtension(dao);
                });

        // Stub jdbi.useExtension(PackageDao.class, ...) — analyze() uses this
        // to mark a package as polled after a successful run.
        lenient().doAnswer(inv -> {
            ExtensionConsumer<PackageDao, Exception> consumer = inv.getArgument(1);
            consumer.useExtension(packageDao);
            return null;
        }).when(jdbi).useExtension(eq(PackageDao.class), any(ExtensionConsumer.class));

        // Default config: enabled, with both flow priority lists. Tests override
        // the enabled/priority defaults as needed.
        bulk = mock(RepoMetaAnalyzerConfig.Bulk.class);
        perVersion = mock(RepoMetaAnalyzerConfig.PerVersion.class);
        lenient().when(config.enabled()).thenReturn(true);
        lenient().when(config.bulk()).thenReturn(bulk);
        lenient().when(config.perVersion()).thenReturn(perVersion);
        lenient().when(bulk.refreshAfterDays()).thenReturn(7);
        lenient().when(bulk.enabledSourcesByPriority())
                .thenReturn(List.of("ecosystems", "depsdev"));
        lenient().when(perVersion.enabledSourcesByPriority())
                .thenReturn(List.of("snyk", "ecosystems", "depsdev"));

        // Snyk default: enabled with org id present. Tests override as needed.
        lenient().when(snykConfig.enabled()).thenReturn(true);
        lenient().when(snykConfig.orgId()).thenReturn(Optional.of("org-123"));
        lenient().when(snykConfig.apiVersion()).thenReturn("2024-10-15");
    }

    private void stubUseExtensionVersionDao() throws Exception {
        doAnswer(inv -> {
            ExtensionConsumer<PackageVersionDao, Exception> consumer = inv.getArgument(1);
            consumer.useExtension(dao);
            return null;
        }).when(jdbi).useExtension(eq(PackageVersionDao.class), any(ExtensionConsumer.class));
    }

    private static EcosystemsVersion ecoVersion(String number, Instant publishedAt) {
        EcosystemsVersion v = new EcosystemsVersion();
        v.setNumber(number);
        v.setPublishedAt(publishedAt);
        return v;
    }

    // -----------------------------------------------------------------------
    // analyze (Flow A)
    // -----------------------------------------------------------------------

    @Test
    void analyze_disabled_returnsEmpty() {
        when(config.enabled()).thenReturn(false);

        List<PackageVersionEntry> result = analyzer.analyze(packageId, packageDbId);

        assertThat(result).isEmpty();
        verifyNoInteractions(ecosystemsClient, depsDevClient, snykClient);
        // Disabled → no DB writes either.
        verify(packageDao, never()).markVersionsPolled(anyLong());
    }

    @Test
    void analyze_ecosystemsPriority1_returnsList() throws Exception {
        stubUseExtensionVersionDao();

        Instant now = Instant.now();
        when(ecosystemsClient.versionsList(eq("npmjs.org"), eq("express")))
                .thenReturn(List.of(
                        ecoVersion("4.18.0", now),
                        ecoVersion("4.18.1", now.plusSeconds(60)),
                        ecoVersion("4.19.0", now.plusSeconds(120))
                ));
        when(dao.latestN(eq(packageDbId), anyInt())).thenReturn(List.of());

        analyzer.analyze(packageId, packageDbId);

        // Bulk discovery uses a single batch insert, not per-row upserts.
        verify(dao, times(1)).insertBatch(any(), any(), any(), any());
        verifyNoInteractions(depsDevClient);
    }

    @Test
    void analyze_ecosystemsFails_fallsBackToDepsdev() throws Exception {
        stubUseExtensionVersionDao();

        when(ecosystemsClient.versionsList(anyString(), anyString()))
                .thenThrow(new RuntimeException("ecosystems down"));

        DepsDevPurlResponse.DepsDevVersionKey key = new DepsDevPurlResponse.DepsDevVersionKey(
                "NPM", "express", "4.18.0");
        DepsDevPackage pkg = new DepsDevPackage(
                new DepsDevPackage.PackageKey("NPM", "express"),
                List.of(new DepsDevPackage.VersionRef(key, Instant.now(), null))
        );
        when(depsDevClient.getPackage(eq("NPM"), eq("express"))).thenReturn(pkg);
        when(dao.latestN(eq(packageDbId), anyInt())).thenReturn(List.of());

        analyzer.analyze(packageId, packageDbId);

        verify(dao, atLeastOnce()).insertBatch(any(), any(), any(), any());
    }

    @Test
    void analyze_allSourcesEmpty_returnsEmpty() {
        when(ecosystemsClient.versionsList(anyString(), anyString())).thenReturn(List.of());
        when(depsDevClient.getPackage(anyString(), anyString())).thenReturn(null);

        List<PackageVersionEntry> result = analyzer.analyze(packageId, packageDbId);

        assertThat(result).isEmpty();
        verify(dao, never()).insertBatch(any(), any(), any(), any());
        // "Empty poll" still counts as a successful poll — stamp the timestamp.
        verify(packageDao).markVersionsPolled(packageDbId);
    }

    @Test
    void analyze_marksVersionsPolledOnSuccess() throws Exception {
        stubUseExtensionVersionDao();

        Instant now = Instant.now();
        when(ecosystemsClient.versionsList(eq("npmjs.org"), eq("express")))
                .thenReturn(List.of(ecoVersion("4.18.0", now)));
        when(dao.latestN(eq(packageDbId), anyInt())).thenReturn(List.of());

        analyzer.analyze(packageId, packageDbId);

        verify(packageDao).markVersionsPolled(packageDbId);
    }

    @Test
    void analyze_disabled_doesNotMarkPolled() {
        when(config.enabled()).thenReturn(false);

        analyzer.analyze(packageId, packageDbId);

        verify(packageDao, never()).markVersionsPolled(anyLong());
    }

    // -----------------------------------------------------------------------
    // findOrFetchByVersion (Flow B)
    // -----------------------------------------------------------------------

    @Test
    void findOrFetchByVersion_cacheHit_skipsUpstream() {
        Instant releasedAt = Instant.parse("2024-01-15T00:00:00Z");
        PackageVersionEntry cached = new PackageVersionEntry(
                packageDbId, "4.18.0", releasedAt, "ECOSYSTEMS",
                Instant.now(), Instant.now());
        when(dao.findByPackageAndVersion(packageDbId, "4.18.0"))
                .thenReturn(Optional.of(cached));

        Optional<PackageVersionEntry> result =
                analyzer.findOrFetchByVersion(packageId, packageDbId, "4.18.0");

        assertThat(result).isPresent();
        assertThat(result.get().version()).isEqualTo("4.18.0");
        assertThat(result.get().releasedAt()).isEqualTo(releasedAt);
        verifyNoInteractions(snykClient, ecosystemsClient, depsDevClient);
    }

    @Test
    void findOrFetchByVersion_snykPriority1_succeeds() throws Exception {
        when(dao.findByPackageAndVersion(packageDbId, "4.18.0"))
                .thenReturn(Optional.empty())  // initial cache check
                .thenReturn(Optional.of(new PackageVersionEntry(
                        packageDbId, "4.18.0", Instant.parse("2024-01-15T00:00:00Z"),
                        "SNYK", Instant.now(), Instant.now())));  // post-insert read
        stubUseExtensionVersionDao();

        SnykPackageVersionResponse.Attributes attrs = new SnykPackageVersionResponse.Attributes(
                "npm", null, null, null, null, null, null, null, "express", "4.18.0",
                null, Instant.parse("2024-01-15T00:00:00Z"));
        SnykPackageVersionResponse.Data data = new SnykPackageVersionResponse.Data(
                "id", "package_version", attrs, "4.18.0");
        SnykPackageVersionResponse response = new SnykPackageVersionResponse(data);
        when(snykClient.getPackageVersion(eq("org-123"), eq("npm"), eq("express"),
                eq("4.18.0"), eq("2024-10-15"))).thenReturn(response);

        Optional<PackageVersionEntry> result =
                analyzer.findOrFetchByVersion(packageId, packageDbId, "4.18.0");

        assertThat(result).isPresent();
        assertThat(result.get().resolvedVia()).isEqualTo("SNYK");
        verify(dao).insertIfAbsent(eq(packageDbId), eq("4.18.0"), any(), eq("SNYK"));
        verifyNoInteractions(ecosystemsClient, depsDevClient);
    }

    @Test
    void findOrFetchByVersion_snykDisabled_skipsToEcosystems() throws Exception {
        when(snykConfig.enabled()).thenReturn(false);

        when(dao.findByPackageAndVersion(packageDbId, "4.18.0"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new PackageVersionEntry(
                        packageDbId, "4.18.0", Instant.parse("2024-01-15T00:00:00Z"),
                        "ECOSYSTEMS", Instant.now(), Instant.now())));
        stubUseExtensionVersionDao();

        when(ecosystemsClient.versionInfo(eq("npmjs.org"), eq("express"), eq("4.18.0")))
                .thenReturn(ecoVersion("4.18.0", Instant.parse("2024-01-15T00:00:00Z")));

        Optional<PackageVersionEntry> result =
                analyzer.findOrFetchByVersion(packageId, packageDbId, "4.18.0");

        assertThat(result).isPresent();
        assertThat(result.get().resolvedVia()).isEqualTo("ECOSYSTEMS");
        verify(dao).insertIfAbsent(eq(packageDbId), eq("4.18.0"), any(), eq("ECOSYSTEMS"));
        verifyNoInteractions(snykClient);
    }

    @Test
    void findOrFetchByVersion_snykThrows_skipsToEcosystems() throws Exception {
        when(dao.findByPackageAndVersion(packageDbId, "4.18.0"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new PackageVersionEntry(
                        packageDbId, "4.18.0", Instant.parse("2024-01-15T00:00:00Z"),
                        "ECOSYSTEMS", Instant.now(), Instant.now())));
        stubUseExtensionVersionDao();

        when(snykClient.getPackageVersion(anyString(), anyString(), anyString(),
                anyString(), anyString())).thenThrow(new RuntimeException("snyk down"));

        when(ecosystemsClient.versionInfo(eq("npmjs.org"), eq("express"), eq("4.18.0")))
                .thenReturn(ecoVersion("4.18.0", Instant.parse("2024-01-15T00:00:00Z")));

        Optional<PackageVersionEntry> result =
                analyzer.findOrFetchByVersion(packageId, packageDbId, "4.18.0");

        assertThat(result).isPresent();
        assertThat(result.get().resolvedVia()).isEqualTo("ECOSYSTEMS");
        verify(dao).insertIfAbsent(eq(packageDbId), eq("4.18.0"), any(), eq("ECOSYSTEMS"));
    }

    @Test
    void findOrFetchByVersion_allSourcesFail_returnsCacheValue() {
        // DAO miss-on-released_at (cache exists but releasedAt is null) — should return
        // it after every source fails.
        PackageVersionEntry stale = new PackageVersionEntry(
                packageDbId, "4.18.0", null, "ECOSYSTEMS",
                Instant.now(), Instant.now());
        when(dao.findByPackageAndVersion(packageDbId, "4.18.0"))
                .thenReturn(Optional.of(stale));

        when(snykClient.getPackageVersion(anyString(), anyString(), anyString(),
                anyString(), anyString())).thenThrow(new RuntimeException("snyk down"));
        when(ecosystemsClient.versionInfo(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("ecosystems down"));
        when(depsDevClient.getVersionInfo(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("depsdev down"));

        Optional<PackageVersionEntry> result =
                analyzer.findOrFetchByVersion(packageId, packageDbId, "4.18.0");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(stale);
        verify(dao, never()).insertIfAbsent(anyLong(), anyString(), any(), anyString());
    }

    // -----------------------------------------------------------------------
    // Cache-only reads
    // -----------------------------------------------------------------------

    @Test
    void latestN_returnsCacheOnly() {
        PackageVersionEntry e = new PackageVersionEntry(
                packageDbId, "4.18.0", Instant.now(), "ECOSYSTEMS",
                Instant.now(), Instant.now());
        when(dao.latestN(eq(packageDbId), eq(5))).thenReturn(List.of(e));

        List<PackageVersionEntry> result = analyzer.latestN(packageId, packageDbId, 5);

        assertThat(result).containsExactly(e);
        verify(dao).latestN(packageDbId, 5);
        verifyNoInteractions(snykClient, ecosystemsClient, depsDevClient);
    }

    @Test
    void findCached_returnsCacheOnly() {
        PackageVersionEntry e = new PackageVersionEntry(
                packageDbId, "4.18.0", Instant.now(), "SNYK",
                Instant.now(), Instant.now());
        when(dao.findByPackageAndVersion(packageDbId, "4.18.0"))
                .thenReturn(Optional.of(e));

        Optional<PackageVersionEntry> result = analyzer.findCached(packageDbId, "4.18.0");

        assertThat(result).isPresent().contains(e);
        verify(dao).findByPackageAndVersion(packageDbId, "4.18.0");
        verifyNoInteractions(snykClient, ecosystemsClient, depsDevClient);
    }
}
