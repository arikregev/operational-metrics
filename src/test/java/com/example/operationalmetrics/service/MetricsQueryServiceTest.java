package com.example.operationalmetrics.service;

import com.example.operationalmetrics.config.ApiConfig;
import com.example.operationalmetrics.dto.MetricsBulkResponse;
import com.example.operationalmetrics.dto.MetricsResponse;
import com.example.operationalmetrics.model.OperationalMetricsEntity;
import com.example.operationalmetrics.model.PackageId;
import com.example.operationalmetrics.model.PackageVersionEntry;
import com.example.operationalmetrics.repository.OperationalMetricsDao;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.extension.ExtensionCallback;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsQueryServiceTest {

    @Mock
    private Jdbi jdbi;

    @Mock
    private MetricsOrchestrator orchestrator;

    @Mock
    private RepoMetaAnalyzer repoMetaAnalyzer;

    @Mock
    private ApiConfig apiConfig;

    @Mock
    private OperationalMetricsDao operationalMetricsDao;

    private MetricsQueryService service;

    @BeforeEach
    void setUp() {
        service = new MetricsQueryService(jdbi, orchestrator, repoMetaAnalyzer, apiConfig);

        // Stub jdbi.withExtension(OperationalMetricsDao.class, callback) — lenient because static toResponse test does not use it
        lenient().when(jdbi.withExtension(eq(OperationalMetricsDao.class), any(ExtensionCallback.class)))
                .thenAnswer(inv -> {
                    ExtensionCallback<Object, OperationalMetricsDao, Exception> callback = inv.getArgument(1);
                    return callback.withExtension(operationalMetricsDao);
                });
    }

    private OperationalMetricsEntity entityFor(String canonical, String type, String namespace, String name) {
        OperationalMetricsEntity e = new OperationalMetricsEntity();
        e.setPackageId(42L);
        e.setPurlCanonical(canonical);
        e.setPurlType(type);
        e.setPurlNamespace(namespace);
        e.setPurlName(name);
        e.setStarsCount(123);
        e.setForksCount(45);
        e.setRepoUrl("https://github.com/" + namespace + "/" + name);
        e.setSourcesUsed(List.of("DEPS_DEV"));
        e.setFetchedAt(Instant.parse("2026-04-30T10:00:00Z"));
        return e;
    }

    @Test
    void findByPurl_cacheHit_returnsResponse() {
        var entity = entityFor("pkg:npm/express", "npm", null, "express");
        when(operationalMetricsDao.findByCanonical("pkg:npm/express"))
                .thenReturn(Optional.of(entity));

        MetricsResponse response = service.findByPurl("pkg:npm/express@4.18.0");

        assertThat(response.purl()).isEqualTo("pkg:npm/express");
        assertThat(response.popularity().stars()).isEqualTo(123);
        assertThat(response.popularity().forks()).isEqualTo(45);
        verify(orchestrator, never()).collectAndStore(any(), any());
    }

    @Test
    void findByPurl_cacheMiss_callsOrchestrator() {
        when(operationalMetricsDao.findByCanonical("pkg:npm/express"))
                .thenReturn(Optional.empty());

        var entity = entityFor("pkg:npm/express", "npm", null, "express");
        when(orchestrator.collectAndStore(any(PackageId.class), isNull()))
                .thenReturn(entity);

        MetricsResponse response = service.findByPurl("pkg:npm/express@4.18.0");

        assertThat(response.purl()).isEqualTo("pkg:npm/express");
        verify(orchestrator).collectAndStore(any(PackageId.class), isNull());
    }

    @Test
    void findByCoordinates_buildsPackageIdAndDispatches() {
        var entity = entityFor("pkg:maven/org.apache.logging.log4j/log4j-core",
                "maven", "org.apache.logging.log4j", "log4j-core");
        when(operationalMetricsDao.findByCanonical("pkg:maven/org.apache.logging.log4j/log4j-core"))
                .thenReturn(Optional.of(entity));

        MetricsResponse response = service.findByCoordinates(
                "maven", "org.apache.logging.log4j", "log4j-core");

        assertThat(response.purlType()).isEqualTo("maven");
        assertThat(response.purlNamespace()).isEqualTo("org.apache.logging.log4j");
        assertThat(response.purlName()).isEqualTo("log4j-core");
        verify(orchestrator, never()).collectAndStore(any(), any());
    }

    @Test
    void findBulk_mixedFoundAndMissing() {
        when(apiConfig.onDemandConcurrency()).thenReturn(4);

        var entityExpress = entityFor("pkg:npm/express", "npm", null, "express");
        var entityLodash = entityFor("pkg:npm/lodash", "npm", null, "lodash");
        var entityMissing = entityFor("pkg:npm/missing-pkg", "npm", null, "missing-pkg");

        // Mock findByCanonicals to return only the cached ones
        when(operationalMetricsDao.findByCanonicals(any()))
                .thenReturn(List.of(entityExpress, entityLodash));

        // Orchestrator handles the missing one
        when(orchestrator.collectAndStore(any(PackageId.class), isNull()))
                .thenReturn(entityMissing);

        MetricsBulkResponse response = service.findBulk(List.of(
                "pkg:npm/express@4.18.0",
                "pkg:npm/lodash@4.17.21",
                "pkg:npm/missing-pkg@1.0.0"
        ));

        assertThat(response.results()).hasSize(3);
        assertThat(response.errors()).isEmpty();
        verify(orchestrator, times(1)).collectAndStore(any(PackageId.class), isNull());
    }

    @Test
    void findBulk_invalidPurl_addedToErrors() {
        when(operationalMetricsDao.findByCanonicals(any())).thenReturn(List.of());

        MetricsBulkResponse response = service.findBulk(List.of("not-a-purl"));

        assertThat(response.results()).isEmpty();
        assertThat(response.errors()).hasSize(1);
        assertThat(response.errors().get(0)).contains("not-a-purl");
        verify(orchestrator, never()).collectAndStore(any(), any());
    }

    @Test
    void findBulk_orchestratorReturnsNull_addedToErrors() {
        when(apiConfig.onDemandConcurrency()).thenReturn(2);
        when(operationalMetricsDao.findByCanonicals(any())).thenReturn(List.of());
        when(orchestrator.collectAndStore(any(PackageId.class), isNull()))
                .thenThrow(new RuntimeException("collector failed"));

        MetricsBulkResponse response = service.findBulk(List.of("pkg:npm/foo@1.0.0"));

        assertThat(response.results()).isEmpty();
        assertThat(response.errors()).hasSize(1);
        assertThat(response.errors().get(0)).contains("pkg:npm/foo");
    }

    @Test
    void findBulk_emptyList_returnsEmptyResponse() {
        when(operationalMetricsDao.findByCanonicals(any())).thenReturn(List.of());

        MetricsBulkResponse response = service.findBulk(List.of());

        assertThat(response.results()).isEmpty();
        assertThat(response.errors()).isEmpty();
    }

    @Test
    void toResponse_mapsAllFieldsCorrectly() {
        OperationalMetricsEntity e = new OperationalMetricsEntity();
        e.setPurlCanonical("pkg:maven/org.example/lib");
        e.setPurlType("maven");
        e.setPurlNamespace("org.example");
        e.setPurlName("lib");
        e.setRepoUrl("https://github.com/org/lib");
        e.setScorecardOverallScore(8.5f);
        e.setScorecardChecks("[]");
        e.setScorecardDate(Instant.parse("2026-01-01T00:00:00Z"));
        e.setStarsCount(1000);
        e.setForksCount(100);
        e.setDownloadCount(5000L);
        e.setRankingPercentile(95.5f);
        e.setLastCommitAt(Instant.parse("2026-04-15T00:00:00Z"));
        e.setLastReleaseAt(Instant.parse("2026-04-10T00:00:00Z"));
        e.setContributorCount(25);
        e.setIsArchived(false);
        e.setIsDeprecated(false);
        e.setCommunityHealthPct(85.0f);
        e.setAvgIssueCloseTimeDays(3.5f);
        e.setAvgPrCloseTimeDays(1.2f);
        e.setPrAuthorsCount(20);
        e.setMergedPrCount(150);
        e.setAdvisoryCount(2);
        e.setHasSlsaProvenance(true);
        e.setHasOssFuzz(false);
        e.setDependentReposCount(500L);
        e.setDependentPackagesCount(80L);
        e.setMaintainerCount(5);
        e.setLicense("MIT");
        e.setSourcesUsed(List.of("SCORECARD", "DEPS_DEV"));
        e.setFetchedAt(Instant.parse("2026-04-30T12:00:00Z"));

        MetricsResponse resp = MetricsQueryService.toResponse(e);

        assertThat(resp.purl()).isEqualTo("pkg:maven/org.example/lib");
        assertThat(resp.purlType()).isEqualTo("maven");
        assertThat(resp.purlNamespace()).isEqualTo("org.example");
        assertThat(resp.purlName()).isEqualTo("lib");
        assertThat(resp.repoUrl()).isEqualTo("https://github.com/org/lib");

        assertThat(resp.scorecard().score()).isEqualTo(8.5f);
        assertThat(resp.scorecard().checks()).isEqualTo("[]");
        assertThat(resp.scorecard().date()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(resp.popularity().stars()).isEqualTo(1000);
        assertThat(resp.popularity().forks()).isEqualTo(100);
        assertThat(resp.popularity().downloads()).isEqualTo(5000L);
        assertThat(resp.popularity().rankingPercentile()).isEqualTo(95.5f);

        assertThat(resp.activity().lastCommit()).isEqualTo(Instant.parse("2026-04-15T00:00:00Z"));
        assertThat(resp.activity().lastRelease()).isEqualTo(Instant.parse("2026-04-10T00:00:00Z"));
        assertThat(resp.activity().contributorCount()).isEqualTo(25);
        assertThat(resp.activity().archived()).isFalse();
        assertThat(resp.activity().deprecated()).isFalse();

        assertThat(resp.community().healthPct()).isEqualTo(85.0f);
        assertThat(resp.community().avgIssueCloseTimeDays()).isEqualTo(3.5f);
        assertThat(resp.community().avgPrCloseTimeDays()).isEqualTo(1.2f);
        assertThat(resp.community().prAuthorsCount()).isEqualTo(20);
        assertThat(resp.community().mergedPrCount()).isEqualTo(150);

        assertThat(resp.security().advisoryCount()).isEqualTo(2);
        assertThat(resp.security().slsaProvenance()).isTrue();
        assertThat(resp.security().ossFuzz()).isFalse();

        assertThat(resp.dependents().repos()).isEqualTo(500L);
        assertThat(resp.dependents().packages()).isEqualTo(80L);

        assertThat(resp.maintainerCount()).isEqualTo(5);
        assertThat(resp.license()).isEqualTo("MIT");
        assertThat(resp.sourcesUsed()).containsExactly("SCORECARD", "DEPS_DEV");
        assertThat(resp.fetchedAt()).isEqualTo(Instant.parse("2026-04-30T12:00:00Z"));
    }

    @Test
    void findByPurl_withVersionSegment_populatesVersionInfo() {
        var entity = entityFor("pkg:npm/express", "npm", null, "express");
        entity.setLastReleaseAt(Instant.parse("2026-04-29T00:00:00Z"));
        when(operationalMetricsDao.findByCanonical("pkg:npm/express"))
                .thenReturn(Optional.of(entity));

        Instant releasedAt = Instant.parse("2024-01-15T00:00:00Z");
        PackageVersionEntry entry = new PackageVersionEntry(
                42L, "4.18.0", releasedAt, "SNYK",
                Instant.parse("2026-04-30T08:00:00Z"),
                Instant.parse("2026-04-30T08:00:00Z"));
        when(repoMetaAnalyzer.findOrFetchByVersion(any(PackageId.class), eq(42L), eq("4.18.0")))
                .thenReturn(Optional.of(entry));

        MetricsResponse response = service.findByPurl("pkg:npm/express@4.18.0");

        assertThat(response.versionInfo()).isNotNull();
        assertThat(response.versionInfo().version()).isEqualTo("4.18.0");
        assertThat(response.versionInfo().releasedAt()).isEqualTo(releasedAt);
        assertThat(response.versionInfo().resolvedVia()).isEqualTo("SNYK");
        assertThat(response.versionInfo().daysSinceRelease()).isNotNull();
        assertThat(response.versionInfo().daysOlderThanLatest()).isNotNull();
    }
}
