package com.example.operationalmetrics.service;

import com.example.operationalmetrics.client.dependencytrack.DependencyTrackClient;
import com.example.operationalmetrics.client.dependencytrack.dto.DtComponent;
import com.example.operationalmetrics.client.dependencytrack.dto.DtProject;
import com.example.operationalmetrics.config.DependencyTrackConfig;
import com.example.operationalmetrics.config.SyncConfig;
import com.example.operationalmetrics.model.PackageId;
import com.example.operationalmetrics.repository.PackageDao;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.extension.ExtensionConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurlSyncServiceTest {

    @Mock
    private DependencyTrackClient dtClient;

    @Mock
    private DependencyTrackConfig dtConfig;

    @Mock
    private SyncConfig syncConfig;

    @Mock
    private MetricsOrchestrator orchestrator;

    @Mock
    private Jdbi jdbi;

    @Mock
    private PackageDao packageDao;

    private PurlSyncService service;

    @BeforeEach
    void setUp() {
        service = new PurlSyncService(dtClient, dtConfig, syncConfig, orchestrator, jdbi);
    }

    @SuppressWarnings("unchecked")
    private void stubUseExtension() throws Exception {
        doAnswer(inv -> {
            ExtensionConsumer<PackageDao, Exception> consumer = inv.getArgument(1);
            consumer.useExtension(packageDao);
            return null;
        }).when(jdbi).useExtension(eq(PackageDao.class), any(ExtensionConsumer.class));
    }

    private void stubSyncConfigDefaults() {
        // lenient — empty-DT path skips batch loop and never reads these
        lenient().when(syncConfig.batchSize()).thenReturn(100);
        lenient().when(syncConfig.concurrency()).thenReturn(2);
        lenient().when(syncConfig.rateLimitDelayMs()).thenReturn(10L);
    }

    private void stubDtConfigPageSize() {
        when(dtConfig.pageSize()).thenReturn(100);
    }

    @Test
    void getStatus_initiallyIdle() {
        PurlSyncService.SyncStatus status = service.getStatus();

        assertThat(status.state()).isEqualTo("IDLE");
        assertThat(status.isRunning()).isFalse();
    }

    @Test
    void runFullSync_emptyDt_completesSuccessfully() throws Exception {
        stubDtConfigPageSize();
        stubSyncConfigDefaults();
        stubUseExtension();

        when(dtClient.listProjects(1, 100)).thenReturn(List.of());

        service.runFullSync();

        PurlSyncService.SyncStatus status = service.getStatus();
        assertThat(status.state()).isEqualTo("COMPLETED");
        assertThat(status.totalPackages()).isEqualTo(0);
        assertThat(status.processedPackages()).isEqualTo(0);
        assertThat(status.failedPackages()).isEqualTo(0);
        verify(orchestrator, never()).collectAndStore(any(), any());
    }

    @Test
    void runFullSync_singlePurl_callsOrchestrator() throws Exception {
        stubDtConfigPageSize();
        stubSyncConfigDefaults();
        stubUseExtension();

        DtProject project = new DtProject("uuid1", "p1", "v1", null);
        DtComponent comp = new DtComponent("c1", "g", "n", "1", "pkg:npm/express@4.18.0", null);

        when(dtClient.listProjects(1, 100)).thenReturn(List.of(project));
        when(dtClient.listProjects(2, 100)).thenReturn(List.of());
        when(dtClient.listComponents(eq("uuid1"), eq(1), eq(100))).thenReturn(List.of(comp));
        when(dtClient.listComponents(eq("uuid1"), eq(2), eq(100))).thenReturn(List.of());

        service.runFullSync();

        verify(orchestrator, times(1)).collectAndStore(any(PackageId.class), any(UUID.class));
        verify(packageDao, atLeastOnce()).upsert(any());

        PurlSyncService.SyncStatus status = service.getStatus();
        assertThat(status.state()).isEqualTo("COMPLETED");
        assertThat(status.totalPackages()).isEqualTo(1);
        assertThat(status.processedPackages()).isEqualTo(1);
        assertThat(status.failedPackages()).isEqualTo(0);
    }

    @Test
    void runFullSync_invalidPurl_skipsAndContinues() throws Exception {
        stubDtConfigPageSize();
        stubSyncConfigDefaults();
        stubUseExtension();

        DtProject project = new DtProject("uuid1", "p1", "v1", null);
        DtComponent badComp = new DtComponent("c1", "g", "n", "1", "not-a-purl", null);
        DtComponent goodComp = new DtComponent("c2", "g", "n", "1", "pkg:npm/lodash@4.17.21", null);

        when(dtClient.listProjects(1, 100)).thenReturn(List.of(project));
        when(dtClient.listProjects(2, 100)).thenReturn(List.of());
        when(dtClient.listComponents(eq("uuid1"), eq(1), eq(100)))
                .thenReturn(List.of(badComp, goodComp));
        when(dtClient.listComponents(eq("uuid1"), eq(2), eq(100))).thenReturn(List.of());

        service.runFullSync();

        // Only valid purl should have been processed
        verify(orchestrator, times(1)).collectAndStore(any(PackageId.class), any(UUID.class));
        PurlSyncService.SyncStatus status = service.getStatus();
        assertThat(status.totalPackages()).isEqualTo(1);
    }

    @Test
    void runFullSync_orchestratorFails_marksFailureCounter() throws Exception {
        stubDtConfigPageSize();
        stubSyncConfigDefaults();
        stubUseExtension();

        DtProject project = new DtProject("uuid1", "p1", "v1", null);
        DtComponent comp = new DtComponent("c1", "g", "n", "1", "pkg:npm/express@4.18.0", null);

        when(dtClient.listProjects(1, 100)).thenReturn(List.of(project));
        when(dtClient.listProjects(2, 100)).thenReturn(List.of());
        when(dtClient.listComponents(eq("uuid1"), eq(1), eq(100))).thenReturn(List.of(comp));
        when(dtClient.listComponents(eq("uuid1"), eq(2), eq(100))).thenReturn(List.of());

        when(orchestrator.collectAndStore(any(), any()))
                .thenThrow(new RuntimeException("collect failed"));

        service.runFullSync();

        PurlSyncService.SyncStatus status = service.getStatus();
        assertThat(status.state()).isEqualTo("COMPLETED");
        assertThat(status.processedPackages()).isEqualTo(0);
        assertThat(status.failedPackages()).isEqualTo(1);
    }

    @Test
    void runFullSync_dtClientThrows_marksFailedStatus() throws Exception {
        when(dtConfig.pageSize()).thenReturn(100);
        when(dtClient.listProjects(1, 100)).thenThrow(new RuntimeException("DT down"));

        service.runFullSync();

        PurlSyncService.SyncStatus status = service.getStatus();
        assertThat(status.state()).isEqualTo("FAILED");
        assertThat(status.errorMessage()).contains("DT down");
    }

    @Test
    void syncStatus_factories_idle() {
        PurlSyncService.SyncStatus status = PurlSyncService.SyncStatus.idle();

        assertThat(status.state()).isEqualTo("IDLE");
        assertThat(status.isRunning()).isFalse();
        assertThat(status.totalPackages()).isNull();
        assertThat(status.processedPackages()).isNull();
        assertThat(status.failedPackages()).isNull();
        assertThat(status.startedAt()).isNull();
        assertThat(status.completedAt()).isNull();
        assertThat(status.errorMessage()).isNull();
    }

    @Test
    void syncStatus_factories_running() {
        PurlSyncService.SyncStatus status = PurlSyncService.SyncStatus.running();

        assertThat(status.state()).isEqualTo("RUNNING");
        assertThat(status.isRunning()).isTrue();
        assertThat(status.startedAt()).isNotNull();
        assertThat(status.completedAt()).isNull();
        assertThat(status.errorMessage()).isNull();
    }

    @Test
    void syncStatus_factories_completed() {
        Instant start = Instant.parse("2026-04-30T10:00:00Z");
        Instant end = Instant.parse("2026-04-30T10:30:00Z");

        PurlSyncService.SyncStatus status = PurlSyncService.SyncStatus.completed(
                100, 95, 5, start, end);

        assertThat(status.state()).isEqualTo("COMPLETED");
        assertThat(status.isRunning()).isFalse();
        assertThat(status.totalPackages()).isEqualTo(100);
        assertThat(status.processedPackages()).isEqualTo(95);
        assertThat(status.failedPackages()).isEqualTo(5);
        assertThat(status.startedAt()).isEqualTo(start);
        assertThat(status.completedAt()).isEqualTo(end);
        assertThat(status.errorMessage()).isNull();
    }

    @Test
    void syncStatus_factories_failed() {
        Instant start = Instant.parse("2026-04-30T10:00:00Z");

        PurlSyncService.SyncStatus status = PurlSyncService.SyncStatus.failed(
                "Connection refused", start);

        assertThat(status.state()).isEqualTo("FAILED");
        assertThat(status.isRunning()).isFalse();
        assertThat(status.errorMessage()).isEqualTo("Connection refused");
        assertThat(status.startedAt()).isEqualTo(start);
        assertThat(status.completedAt()).isNotNull();
    }

    @Test
    void syncStatus_isRunning_onlyTrueWhenRunning() {
        assertThat(PurlSyncService.SyncStatus.idle().isRunning()).isFalse();
        assertThat(PurlSyncService.SyncStatus.running().isRunning()).isTrue();
        assertThat(PurlSyncService.SyncStatus.completed(0, 0, 0, Instant.now(), Instant.now())
                .isRunning()).isFalse();
        assertThat(PurlSyncService.SyncStatus.failed("err", Instant.now()).isRunning()).isFalse();
    }

    @Test
    void runFullSync_componentsWithNullPurl_skippedWithoutError() throws Exception {
        stubDtConfigPageSize();
        stubSyncConfigDefaults();
        stubUseExtension();

        DtProject project = new DtProject("uuid1", "p1", "v1", null);
        DtComponent compNoPurl = new DtComponent("c1", "g", "n", "1", null, null);
        DtComponent goodComp = new DtComponent("c2", "g", "n", "1", "pkg:npm/lodash@4.17.21", null);

        when(dtClient.listProjects(1, 100)).thenReturn(List.of(project));
        when(dtClient.listProjects(2, 100)).thenReturn(List.of());
        when(dtClient.listComponents(eq("uuid1"), eq(1), eq(100)))
                .thenReturn(List.of(compNoPurl, goodComp));
        when(dtClient.listComponents(eq("uuid1"), eq(2), eq(100))).thenReturn(List.of());

        service.runFullSync();

        // Only the valid purl should have been processed
        verify(orchestrator, times(1)).collectAndStore(any(), any());
    }

    @Test
    void runFullSync_paginatesProjectsAndComponents() throws Exception {
        stubDtConfigPageSize();
        stubSyncConfigDefaults();
        stubUseExtension();

        DtProject p1 = new DtProject("u1", "p1", "v1", null);
        DtProject p2 = new DtProject("u2", "p2", "v1", null);
        DtComponent c1 = new DtComponent("c1", "g", "n", "1", "pkg:npm/a@1.0.0", null);
        DtComponent c2 = new DtComponent("c2", "g", "n", "1", "pkg:npm/b@1.0.0", null);

        when(dtClient.listProjects(1, 100)).thenReturn(List.of(p1));
        when(dtClient.listProjects(2, 100)).thenReturn(List.of(p2));
        when(dtClient.listProjects(3, 100)).thenReturn(List.of());

        when(dtClient.listComponents(eq("u1"), eq(1), eq(100))).thenReturn(List.of(c1));
        when(dtClient.listComponents(eq("u1"), eq(2), eq(100))).thenReturn(List.of());
        when(dtClient.listComponents(eq("u2"), eq(1), eq(100))).thenReturn(List.of(c2));
        when(dtClient.listComponents(eq("u2"), eq(2), eq(100))).thenReturn(List.of());

        service.runFullSync();

        PurlSyncService.SyncStatus status = service.getStatus();
        assertThat(status.totalPackages()).isEqualTo(2);
        verify(orchestrator, times(2)).collectAndStore(any(), any());
    }
}
