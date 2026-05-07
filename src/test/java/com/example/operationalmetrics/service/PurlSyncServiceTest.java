package com.example.operationalmetrics.service;

import com.example.operationalmetrics.client.dependencytrack.DependencyTrackClient;
import com.example.operationalmetrics.client.dependencytrack.dto.DtComponent;
import com.example.operationalmetrics.client.dependencytrack.dto.DtProject;
import com.example.operationalmetrics.config.DependencyTrackConfig;
import com.example.operationalmetrics.config.RefreshSyncConfig;
import com.example.operationalmetrics.config.SyncConfig;
import com.example.operationalmetrics.model.PackageId;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private RefreshSyncConfig refreshConfig;

    @Mock
    private MetricsOrchestrator orchestrator;

    @Mock
    private Jdbi jdbi;

    private PurlSyncService service;

    @BeforeEach
    void setUp() {
        // Default refresh config — lenient because the FULL sync paths don't read it.
        lenient().when(refreshConfig.stalenessDays()).thenReturn(90);
        lenient().when(refreshConfig.concurrency()).thenReturn(2);
        service = new PurlSyncService(dtClient, dtConfig, syncConfig, refreshConfig, orchestrator, jdbi);
    }

    private void stubSyncConfigDefaults() {
        // lenient — empty-DT path never invokes the orchestrator path that reads concurrency
        lenient().when(syncConfig.concurrency()).thenReturn(2);
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
    void runFullSync_emptyDt_completesSuccessfully() {
        stubDtConfigPageSize();
        stubSyncConfigDefaults();

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
    void runFullSync_singlePurl_callsOrchestrator() {
        stubDtConfigPageSize();
        stubSyncConfigDefaults();

        DtProject project = new DtProject("uuid1", "p1", "v1", null);
        DtComponent comp = new DtComponent("c1", "g", "n", "1", "pkg:npm/express@4.18.0", null);

        when(dtClient.listProjects(1, 100)).thenReturn(List.of(project));
        when(dtClient.listProjects(2, 100)).thenReturn(List.of());
        when(dtClient.listComponents(eq("uuid1"), eq(1), eq(100))).thenReturn(List.of(comp));
        when(dtClient.listComponents(eq("uuid1"), eq(2), eq(100))).thenReturn(List.of());

        service.runFullSync();

        verify(orchestrator, times(1)).collectAndStore(any(PackageId.class), any(UUID.class));

        PurlSyncService.SyncStatus status = service.getStatus();
        assertThat(status.state()).isEqualTo("COMPLETED");
        assertThat(status.totalPackages()).isEqualTo(1);
        assertThat(status.processedPackages()).isEqualTo(1);
        assertThat(status.failedPackages()).isEqualTo(0);
    }

    @Test
    void runFullSync_invalidPurl_skipsAndContinues() {
        stubDtConfigPageSize();
        stubSyncConfigDefaults();

        DtProject project = new DtProject("uuid1", "p1", "v1", null);
        DtComponent badComp = new DtComponent("c1", "g", "n", "1", "not-a-purl", null);
        DtComponent goodComp = new DtComponent("c2", "g", "n", "1", "pkg:npm/lodash@4.17.21", null);

        when(dtClient.listProjects(1, 100)).thenReturn(List.of(project));
        when(dtClient.listProjects(2, 100)).thenReturn(List.of());
        when(dtClient.listComponents(eq("uuid1"), eq(1), eq(100)))
                .thenReturn(List.of(badComp, goodComp));
        when(dtClient.listComponents(eq("uuid1"), eq(2), eq(100))).thenReturn(List.of());

        service.runFullSync();

        verify(orchestrator, times(1)).collectAndStore(any(PackageId.class), any(UUID.class));
        PurlSyncService.SyncStatus status = service.getStatus();
        assertThat(status.totalPackages()).isEqualTo(1);
    }

    @Test
    void runFullSync_orchestratorFails_marksFailureCounter() {
        stubDtConfigPageSize();
        stubSyncConfigDefaults();

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
    void runFullSync_dtClientThrows_marksFailedStatus() {
        when(dtConfig.pageSize()).thenReturn(100);
        when(dtClient.listProjects(1, 100)).thenThrow(new RuntimeException("DT down"));

        service.runFullSync();

        PurlSyncService.SyncStatus status = service.getStatus();
        assertThat(status.state()).isEqualTo("FAILED");
        assertThat(status.errorMessage()).contains("DT down");
    }

    @Test
    void runFullSync_componentsWithNullPurl_skippedWithoutError() {
        stubDtConfigPageSize();
        stubSyncConfigDefaults();

        DtProject project = new DtProject("uuid1", "p1", "v1", null);
        DtComponent compNoPurl = new DtComponent("c1", "g", "n", "1", null, null);
        DtComponent goodComp = new DtComponent("c2", "g", "n", "1", "pkg:npm/lodash@4.17.21", null);

        when(dtClient.listProjects(1, 100)).thenReturn(List.of(project));
        when(dtClient.listProjects(2, 100)).thenReturn(List.of());
        when(dtClient.listComponents(eq("uuid1"), eq(1), eq(100)))
                .thenReturn(List.of(compNoPurl, goodComp));
        when(dtClient.listComponents(eq("uuid1"), eq(2), eq(100))).thenReturn(List.of());

        service.runFullSync();

        verify(orchestrator, times(1)).collectAndStore(any(), any());
    }

    @Test
    void runFullSync_paginatesProjectsAndComponents() {
        stubDtConfigPageSize();
        stubSyncConfigDefaults();

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

    @Test
    void runFullSync_duplicatePurlAcrossProjects_orchestratorCalledOnce() {
        stubDtConfigPageSize();
        stubSyncConfigDefaults();

        // Two projects, both depending on the same artifact
        DtProject p1 = new DtProject("u1", "p1", "v1", null);
        DtProject p2 = new DtProject("u2", "p2", "v1", null);
        DtComponent dupA = new DtComponent("c1", "g", "n", "1", "pkg:npm/lodash@4.17.21", null);
        DtComponent dupB = new DtComponent("c2", "g", "n", "1", "pkg:npm/lodash@4.17.21", null);

        when(dtClient.listProjects(1, 100)).thenReturn(List.of(p1, p2));
        when(dtClient.listProjects(2, 100)).thenReturn(List.of());
        when(dtClient.listComponents(eq("u1"), eq(1), eq(100))).thenReturn(List.of(dupA));
        when(dtClient.listComponents(eq("u1"), eq(2), eq(100))).thenReturn(List.of());
        when(dtClient.listComponents(eq("u2"), eq(1), eq(100))).thenReturn(List.of(dupB));
        when(dtClient.listComponents(eq("u2"), eq(2), eq(100))).thenReturn(List.of());

        service.runFullSync();

        // Identity (npm, null, lodash) appears twice in DT but should reach orchestrator once
        verify(orchestrator, times(1)).collectAndStore(any(PackageId.class), any(UUID.class));
        PurlSyncService.SyncStatus status = service.getStatus();
        assertThat(status.totalPackages()).isEqualTo(1);
        assertThat(status.processedPackages()).isEqualTo(1);
    }

    /**
     * Verifies the Semaphore-based concurrency cap. Mocks the orchestrator to
     * block on a latch and counts how many calls were "in flight" when each
     * call arrived. The peak must never exceed {@code syncConfig.concurrency()}.
     */
    @Test
    void runFullSync_concurrencyLimitHonored() throws Exception {
        when(dtConfig.pageSize()).thenReturn(100);
        when(syncConfig.concurrency()).thenReturn(2);

        // 5 distinct PURLs across one project — more than the concurrency cap
        DtProject project = new DtProject("u1", "p1", "v1", null);
        List<DtComponent> components = List.of(
                new DtComponent("c1", "g", "n1", "1", "pkg:npm/a@1.0.0", null),
                new DtComponent("c2", "g", "n2", "1", "pkg:npm/b@1.0.0", null),
                new DtComponent("c3", "g", "n3", "1", "pkg:npm/c@1.0.0", null),
                new DtComponent("c4", "g", "n4", "1", "pkg:npm/d@1.0.0", null),
                new DtComponent("c5", "g", "n5", "1", "pkg:npm/e@1.0.0", null)
        );

        when(dtClient.listProjects(1, 100)).thenReturn(List.of(project));
        when(dtClient.listProjects(2, 100)).thenReturn(List.of());
        when(dtClient.listComponents(eq("u1"), eq(1), eq(100))).thenReturn(components);
        when(dtClient.listComponents(eq("u1"), eq(2), eq(100))).thenReturn(List.of());

        AtomicInteger inFlight = new AtomicInteger(0);
        AtomicInteger peakInFlight = new AtomicInteger(0);
        CountDownLatch release = new CountDownLatch(1);

        when(orchestrator.collectAndStore(any(), any())).thenAnswer(inv -> {
            int now = inFlight.incrementAndGet();
            peakInFlight.updateAndGet(prev -> Math.max(prev, now));
            // hold the task until the test releases — exposes max concurrency
            release.await(2, TimeUnit.SECONDS);
            inFlight.decrementAndGet();
            return null;
        });

        // Release after a short delay so all tasks pile up at the Semaphore first
        Thread releaser = Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            release.countDown();
        });

        service.runFullSync();
        releaser.join();

        assertThat(peakInFlight.get())
                .as("peak in-flight tasks must not exceed Semaphore permit count")
                .isLessThanOrEqualTo(2);
        verify(orchestrator, times(5)).collectAndStore(any(), any());
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
        PurlSyncService.SyncStatus status = PurlSyncService.SyncStatus.running("FULL");

        assertThat(status.state()).isEqualTo("RUNNING");
        assertThat(status.mode()).isEqualTo("FULL");
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
                "FULL", 100, 95, 5, start, end);

        assertThat(status.state()).isEqualTo("COMPLETED");
        assertThat(status.mode()).isEqualTo("FULL");
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
                "FULL", "Connection refused", start);

        assertThat(status.state()).isEqualTo("FAILED");
        assertThat(status.mode()).isEqualTo("FULL");
        assertThat(status.isRunning()).isFalse();
        assertThat(status.errorMessage()).isEqualTo("Connection refused");
        assertThat(status.startedAt()).isEqualTo(start);
        assertThat(status.completedAt()).isNotNull();
    }

    @Test
    void syncStatus_isRunning_onlyTrueWhenRunning() {
        assertThat(PurlSyncService.SyncStatus.idle().isRunning()).isFalse();
        assertThat(PurlSyncService.SyncStatus.running("FULL").isRunning()).isTrue();
        assertThat(PurlSyncService.SyncStatus.completed("FULL", 0, 0, 0, Instant.now(), Instant.now())
                .isRunning()).isFalse();
        assertThat(PurlSyncService.SyncStatus.failed("FULL", "err", Instant.now()).isRunning()).isFalse();
    }
}
