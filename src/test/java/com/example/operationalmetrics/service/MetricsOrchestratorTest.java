package com.example.operationalmetrics.service;

import com.example.operationalmetrics.config.RepoMetaAnalyzerConfig;
import com.example.operationalmetrics.config.SourceConfig;
import com.example.operationalmetrics.model.MetricsFetchLog;
import com.example.operationalmetrics.model.MetricsSource;
import com.example.operationalmetrics.model.OperationalMetricsEntity;
import com.example.operationalmetrics.model.PackageEntity;
import com.example.operationalmetrics.model.PackageId;
import com.example.operationalmetrics.model.PartialMetrics;
import com.example.operationalmetrics.model.RepoUrl;
import com.example.operationalmetrics.repository.MetricsFetchLogDao;
import com.example.operationalmetrics.repository.MetricsHistoryDao;
import com.example.operationalmetrics.repository.OperationalMetricsDao;
import com.example.operationalmetrics.repository.PackageDao;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.HandleConsumer;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.extension.ExtensionCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsOrchestratorTest {

    @Mock
    private SourceConfig sourceConfig;

    @Mock
    private Instance<MetricsCollector> collectorInstances;

    @Mock
    private RepoUrlResolver repoUrlResolver;

    @Mock
    private Jdbi jdbi;

    @Mock
    private PackageDao packageDao;

    @Mock
    private OperationalMetricsDao operationalMetricsDao;

    @Mock
    private MetricsHistoryDao historyDao;

    @Mock
    private MetricsFetchLogDao fetchLogDao;

    @Mock
    private Handle handle;

    @Mock
    private MetricsCollector scorecardCollector;

    @Mock
    private MetricsCollector depsDevCollector;

    @Mock
    private RepoMetaAnalyzer repoMetaAnalyzer;

    @Mock
    private RepoMetaAnalyzerConfig repoMetaAnalyzerConfig;

    private PackageId packageId;

    @BeforeEach
    void setUp() {
        packageId = new PackageId("maven", "org.apache.logging.log4j", "log4j-core");

        // Stub collector identity — lenient because not all tests build with both collectors
        lenient().when(scorecardCollector.source()).thenReturn(MetricsSource.SCORECARD);
        lenient().when(depsDevCollector.source()).thenReturn(MetricsSource.DEPS_DEV);

        // Default: analyzer disabled — preserves the historical (pre-feature) behaviour
        // for the bulk of tests. Lenient because not every path reads it.
        lenient().when(repoMetaAnalyzerConfig.enabled()).thenReturn(false);
    }

    private MetricsOrchestrator buildOrchestrator(List<MetricsCollector> collectors) {
        when(collectorInstances.iterator()).thenReturn(collectors.iterator());
        return new MetricsOrchestrator(sourceConfig, collectorInstances, repoUrlResolver,
                repoMetaAnalyzer, repoMetaAnalyzerConfig, jdbi);
    }

    @SuppressWarnings("unchecked")
    private void stubPackageDaoUpsertReturnsId(long id) {
        when(jdbi.withExtension(eq(PackageDao.class), any(ExtensionCallback.class)))
                .thenAnswer(inv -> {
                    ExtensionCallback<Object, PackageDao, Exception> callback = inv.getArgument(1);
                    return callback.withExtension(packageDao);
                });
        when(packageDao.findByCanonical(any())).thenReturn(Optional.empty());
        when(packageDao.upsert(any(PackageEntity.class))).thenReturn(id);
    }

    @SuppressWarnings("unchecked")
    private void stubUseTransactionToInvoke() throws Exception {
        when(handle.attach(OperationalMetricsDao.class)).thenReturn(operationalMetricsDao);
        when(handle.attach(MetricsHistoryDao.class)).thenReturn(historyDao);
        when(handle.attach(MetricsFetchLogDao.class)).thenReturn(fetchLogDao);
        doAnswer(inv -> {
            HandleConsumer<RuntimeException> consumer = inv.getArgument(0);
            consumer.useHandle(handle);
            return null;
        }).when(jdbi).useTransaction(any(HandleConsumer.class));
    }

    @Test
    void collectAndStore_callsAllEnabledSources_inPriorityOrder() throws Exception {
        var orchestrator = buildOrchestrator(List.of(scorecardCollector, depsDevCollector));

        when(sourceConfig.enabledSourcesByPriority())
                .thenReturn(List.of("scorecard", "depsdev"));

        stubPackageDaoUpsertReturnsId(42L);
        stubUseTransactionToInvoke();

        when(repoUrlResolver.resolve(eq(packageId), anyLong()))
                .thenReturn(Optional.of(RepoUrl.fromComponents("github.com", "apache", "log4j2")));

        when(scorecardCollector.requiresRepoUrl()).thenReturn(true);
        when(scorecardCollector.supports(any())).thenReturn(true);
        PartialMetrics scorecardPartial = new PartialMetrics();
        scorecardPartial.setScorecardOverallScore(7.5f);
        when(scorecardCollector.collect(any(), any())).thenReturn(scorecardPartial);

        when(depsDevCollector.requiresRepoUrl()).thenReturn(false);
        when(depsDevCollector.supports(any())).thenReturn(true);
        PartialMetrics depsDevPartial = new PartialMetrics();
        depsDevPartial.setContributorCount(1000);
        when(depsDevCollector.collect(any(), any())).thenReturn(depsDevPartial);

        OperationalMetricsEntity result = orchestrator.collectAndStore(packageId, null);

        assertThat(result).isNotNull();
        assertThat(result.getPackageId()).isEqualTo(42L);
        assertThat(result.getScorecardOverallScore()).isEqualTo(7.5f);
        assertThat(result.getContributorCount()).isEqualTo(1000);
        assertThat(result.getSourcesUsed()).containsExactly("SCORECARD", "DEPS_DEV");
        assertThat(result.getRepoUrl()).isEqualTo("https://github.com/apache/log4j2");

        verify(scorecardCollector).collect(any(), any());
        verify(depsDevCollector).collect(any(), any());
    }

    @Test
    void collectAndStore_skipsSourceRequiringRepoUrlWhenAbsent() throws Exception {
        var orchestrator = buildOrchestrator(List.of(scorecardCollector, depsDevCollector));

        when(sourceConfig.enabledSourcesByPriority())
                .thenReturn(List.of("scorecard", "depsdev"));

        stubPackageDaoUpsertReturnsId(42L);
        stubUseTransactionToInvoke();

        when(repoUrlResolver.resolve(eq(packageId), anyLong())).thenReturn(Optional.empty());

        when(scorecardCollector.requiresRepoUrl()).thenReturn(true);
        when(scorecardCollector.supports(any())).thenReturn(true);

        when(depsDevCollector.requiresRepoUrl()).thenReturn(false);
        when(depsDevCollector.supports(any())).thenReturn(true);
        PartialMetrics depsDevPartial = new PartialMetrics();
        depsDevPartial.setContributorCount(500);
        when(depsDevCollector.collect(any(), any())).thenReturn(depsDevPartial);

        OperationalMetricsEntity result = orchestrator.collectAndStore(packageId, null);

        assertThat(result).isNotNull();
        assertThat(result.getContributorCount()).isEqualTo(500);
        assertThat(result.getSourcesUsed()).containsExactly("DEPS_DEV");

        // Scorecard collect should never be called
        verify(scorecardCollector, never()).collect(any(), any());
        verify(depsDevCollector).collect(any(), any());
    }

    @Test
    void collectAndStore_collectorThrows_logsFailedAndContinues() throws Exception {
        var orchestrator = buildOrchestrator(List.of(scorecardCollector, depsDevCollector));

        when(sourceConfig.enabledSourcesByPriority())
                .thenReturn(List.of("scorecard", "depsdev"));

        stubPackageDaoUpsertReturnsId(42L);
        stubUseTransactionToInvoke();

        when(repoUrlResolver.resolve(eq(packageId), anyLong()))
                .thenReturn(Optional.of(RepoUrl.fromComponents("github.com", "apache", "log4j2")));

        when(scorecardCollector.requiresRepoUrl()).thenReturn(true);
        when(scorecardCollector.supports(any())).thenReturn(true);
        when(scorecardCollector.collect(any(), any()))
                .thenThrow(new RuntimeException("scorecard failed"));

        when(depsDevCollector.requiresRepoUrl()).thenReturn(false);
        when(depsDevCollector.supports(any())).thenReturn(true);
        PartialMetrics depsDevPartial = new PartialMetrics();
        depsDevPartial.setContributorCount(900);
        when(depsDevCollector.collect(any(), any())).thenReturn(depsDevPartial);

        OperationalMetricsEntity result = orchestrator.collectAndStore(packageId, null);

        assertThat(result).isNotNull();
        // Continued past the failed scorecard call
        assertThat(result.getContributorCount()).isEqualTo(900);
        assertThat(result.getSourcesUsed()).containsExactly("DEPS_DEV");

        // Verify a FAILED log was inserted
        ArgumentCaptor<MetricsFetchLog> captor = ArgumentCaptor.forClass(MetricsFetchLog.class);
        verify(fetchLogDao, atLeastOnce()).insert(captor.capture());
        boolean hasFailed = captor.getAllValues().stream()
                .anyMatch(log -> "FAILED".equals(log.getStatus())
                        && "SCORECARD".equals(log.getSource()));
        assertThat(hasFailed).isTrue();
    }

    @Test
    void collectAndStore_persistsToAllThreeTables() throws Exception {
        var orchestrator = buildOrchestrator(List.of(depsDevCollector));

        when(sourceConfig.enabledSourcesByPriority())
                .thenReturn(List.of("depsdev"));

        stubPackageDaoUpsertReturnsId(42L);
        stubUseTransactionToInvoke();

        when(repoUrlResolver.resolve(eq(packageId), anyLong())).thenReturn(Optional.empty());

        when(depsDevCollector.requiresRepoUrl()).thenReturn(false);
        when(depsDevCollector.supports(any())).thenReturn(true);
        PartialMetrics partial = new PartialMetrics();
        partial.setContributorCount(100);
        when(depsDevCollector.collect(any(), any())).thenReturn(partial);

        UUID syncRunId = UUID.randomUUID();
        OperationalMetricsEntity result = orchestrator.collectAndStore(packageId, syncRunId);

        assertThat(result).isNotNull();
        verify(operationalMetricsDao, times(1)).upsert(any(OperationalMetricsEntity.class));
        verify(historyDao, times(1)).insert(any(OperationalMetricsEntity.class), eq(syncRunId));
        verify(fetchLogDao, atLeastOnce()).insert(any(MetricsFetchLog.class));
    }

    @Test
    void collectAndStore_extractsHttpStatusFromWebApplicationException() throws Exception {
        var orchestrator = buildOrchestrator(List.of(depsDevCollector));

        when(sourceConfig.enabledSourcesByPriority())
                .thenReturn(List.of("depsdev"));

        stubPackageDaoUpsertReturnsId(42L);
        stubUseTransactionToInvoke();

        when(repoUrlResolver.resolve(eq(packageId), anyLong())).thenReturn(Optional.empty());

        when(depsDevCollector.requiresRepoUrl()).thenReturn(false);
        when(depsDevCollector.supports(any())).thenReturn(true);
        when(depsDevCollector.collect(any(), any()))
                .thenThrow(new WebApplicationException("Not Found", Response.Status.NOT_FOUND));

        orchestrator.collectAndStore(packageId, null);

        ArgumentCaptor<MetricsFetchLog> captor = ArgumentCaptor.forClass(MetricsFetchLog.class);
        verify(fetchLogDao).insert(captor.capture());

        MetricsFetchLog log = captor.getValue();
        assertThat(log.getStatus()).isEqualTo("FAILED");
        assertThat(log.getHttpStatus()).isEqualTo(404);
    }

    @Test
    void collectAndStore_unknownSourceConfigKey_skipsGracefully() throws Exception {
        var orchestrator = buildOrchestrator(List.of(depsDevCollector));

        when(sourceConfig.enabledSourcesByPriority())
                .thenReturn(List.of("totally-unknown", "depsdev"));

        stubPackageDaoUpsertReturnsId(42L);
        stubUseTransactionToInvoke();

        when(repoUrlResolver.resolve(eq(packageId), anyLong())).thenReturn(Optional.empty());

        when(depsDevCollector.requiresRepoUrl()).thenReturn(false);
        when(depsDevCollector.supports(any())).thenReturn(true);
        PartialMetrics partial = new PartialMetrics();
        partial.setContributorCount(50);
        when(depsDevCollector.collect(any(), any())).thenReturn(partial);

        OperationalMetricsEntity result = orchestrator.collectAndStore(packageId, null);

        assertThat(result).isNotNull();
        assertThat(result.getContributorCount()).isEqualTo(50);
        assertThat(result.getSourcesUsed()).containsExactly("DEPS_DEV");
    }

    @Test
    void collectAndStore_unsupportedPackage_logsSkipped() throws Exception {
        var orchestrator = buildOrchestrator(List.of(depsDevCollector));

        when(sourceConfig.enabledSourcesByPriority())
                .thenReturn(List.of("depsdev"));

        stubPackageDaoUpsertReturnsId(42L);
        stubUseTransactionToInvoke();

        when(repoUrlResolver.resolve(eq(packageId), anyLong())).thenReturn(Optional.empty());

        when(depsDevCollector.supports(any())).thenReturn(false);

        OperationalMetricsEntity result = orchestrator.collectAndStore(packageId, null);

        assertThat(result).isNotNull();
        assertThat(result.getSourcesUsed()).isEmpty();

        verify(depsDevCollector, never()).collect(any(), any());

        ArgumentCaptor<MetricsFetchLog> captor = ArgumentCaptor.forClass(MetricsFetchLog.class);
        verify(fetchLogDao).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("SKIPPED");
    }

    @Test
    void collectAndStore_collectorReturnsRepoUrl_cachesIt() throws Exception {
        var orchestrator = buildOrchestrator(List.of(depsDevCollector));

        when(sourceConfig.enabledSourcesByPriority())
                .thenReturn(List.of("depsdev"));

        stubPackageDaoUpsertReturnsId(42L);
        stubUseTransactionToInvoke();

        when(repoUrlResolver.resolve(eq(packageId), anyLong())).thenReturn(Optional.empty());

        when(depsDevCollector.requiresRepoUrl()).thenReturn(false);
        when(depsDevCollector.supports(any())).thenReturn(true);

        PartialMetrics partial = new PartialMetrics();
        RepoUrl discovered = RepoUrl.fromComponents("github.com", "owner", "repo");
        partial.setRepoUrl(discovered);
        partial.setContributorCount(10);
        when(depsDevCollector.collect(any(), any())).thenReturn(partial);

        OperationalMetricsEntity result = orchestrator.collectAndStore(packageId, null);

        assertThat(result.getRepoUrl()).isEqualTo("https://github.com/owner/repo");
        verify(repoUrlResolver).cache(eq(42L), eq(discovered), eq(MetricsSource.DEPS_DEV));
    }

    @Test
    void collectAndStore_existingPackage_reusesPackageId() throws Exception {
        var orchestrator = buildOrchestrator(List.of(depsDevCollector));

        when(sourceConfig.enabledSourcesByPriority())
                .thenReturn(List.of("depsdev"));

        // existing package: skip upsert
        when(jdbi.withExtension(eq(PackageDao.class), any(ExtensionCallback.class)))
                .thenAnswer(inv -> {
                    ExtensionCallback<Object, PackageDao, Exception> callback = inv.getArgument(1);
                    return callback.withExtension(packageDao);
                });
        PackageEntity existingEntity = new PackageEntity();
        existingEntity.setId(99L);
        when(packageDao.findByCanonical(any())).thenReturn(Optional.of(existingEntity));

        stubUseTransactionToInvoke();
        when(repoUrlResolver.resolve(eq(packageId), anyLong())).thenReturn(Optional.empty());

        when(depsDevCollector.requiresRepoUrl()).thenReturn(false);
        when(depsDevCollector.supports(any())).thenReturn(true);
        PartialMetrics partial = new PartialMetrics();
        when(depsDevCollector.collect(any(), any())).thenReturn(partial);

        OperationalMetricsEntity result = orchestrator.collectAndStore(packageId, null);

        assertThat(result.getPackageId()).isEqualTo(99L);
        verify(packageDao, never()).upsert(any());
    }

    @Test
    void collectAndStore_repoMetaAnalyzerEnabled_invokesLatestVersionBackfillOnly() throws Exception {
        // Bulk version discovery (analyze) was decoupled into VersionsSyncService;
        // the orchestrator now only piggy-backs the latest-version Flow-B
        // backfill (findOrFetchByVersion) when a release version is known.
        var orchestrator = buildOrchestrator(List.of(depsDevCollector));

        when(sourceConfig.enabledSourcesByPriority())
                .thenReturn(List.of("depsdev"));

        stubPackageDaoUpsertReturnsId(42L);
        stubUseTransactionToInvoke();

        when(repoUrlResolver.resolve(eq(packageId), anyLong())).thenReturn(Optional.empty());

        when(depsDevCollector.requiresRepoUrl()).thenReturn(false);
        when(depsDevCollector.supports(any())).thenReturn(true);
        PartialMetrics partial = new PartialMetrics();
        partial.setLastReleaseVersion("4.18.0");
        when(depsDevCollector.collect(any(), any())).thenReturn(partial);

        // Enable analyzer for this test — overrides the lenient default.
        when(repoMetaAnalyzerConfig.enabled()).thenReturn(true);

        orchestrator.collectAndStore(packageId, null);

        verify(repoMetaAnalyzer, never()).analyze(any(), anyLong());
        verify(repoMetaAnalyzer).findOrFetchByVersion(eq(packageId), anyLong(), eq("4.18.0"));
    }
}
