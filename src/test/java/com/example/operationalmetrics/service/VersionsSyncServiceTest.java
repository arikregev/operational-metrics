package com.example.operationalmetrics.service;

import com.example.operationalmetrics.config.VersionsSyncConfig;
import com.example.operationalmetrics.model.PackageEntity;
import com.example.operationalmetrics.repository.PackageDao;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.extension.ExtensionCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VersionsSyncServiceTest {

    @Mock VersionsSyncConfig config;
    @Mock RepoMetaAnalyzer repoMetaAnalyzer;
    @Mock Jdbi jdbi;
    @Mock PackageDao packageDao;

    private VersionsSyncService service;

    @BeforeEach
    void setUp() {
        // Sensible defaults; individual tests override what they care about.
        lenient().when(config.enabled()).thenReturn(true);
        lenient().when(config.concurrency()).thenReturn(4);
        lenient().when(config.stalenessDays()).thenReturn(30);
        lenient().when(config.batchSize()).thenReturn(1000);
        lenient().when(config.rateBudgetPerSecond()).thenReturn(100);  // high so test isn't slow

        // Wire jdbi.withExtension(PackageDao.class, ...) to call back with our mocked DAO.
        lenient().when(jdbi.withExtension(eq(PackageDao.class), any())).thenAnswer(inv -> {
            ExtensionCallback<?, PackageDao, ?> cb = inv.getArgument(1);
            return cb.withExtension(packageDao);
        });

        service = new VersionsSyncService(config, repoMetaAnalyzer, jdbi);
    }

    @Test
    void getStatus_initiallyIdle() {
        VersionsSyncService.SweepStatus status = service.getStatus();
        assertThat(status.state()).isEqualTo("IDLE");
        assertThat(status.isRunning()).isFalse();
    }

    @Test
    void runSweep_disabled_setsIdleAndDoesNothing() {
        when(config.enabled()).thenReturn(false);

        service.runSweep();

        assertThat(service.getStatus().state()).isEqualTo("IDLE");
        verify(repoMetaAnalyzer, never()).analyze(any(), anyLong());
        verify(packageDao, never()).findPackagesDuePoll(anyInt(), anyInt());
    }

    @Test
    void runSweep_emptyDuePoll_completesWithZeroes() {
        when(packageDao.findPackagesDuePoll(30, 1000)).thenReturn(Collections.emptyList());

        service.runSweep();

        VersionsSyncService.SweepStatus status = service.getStatus();
        assertThat(status.state()).isEqualTo("COMPLETED");
        assertThat(status.discoveredPackages()).isZero();
        assertThat(status.processedPackages()).isZero();
        assertThat(status.failedPackages()).isZero();
        verify(repoMetaAnalyzer, never()).analyze(any(), anyLong());
    }

    @Test
    void runSweep_processesEveryDuePackage() {
        List<PackageEntity> due = List.of(
                packageEntity(1L, "maven", "g.a", "lib1"),
                packageEntity(2L, "npm", null, "express"),
                packageEntity(3L, "pypi", null, "requests")
        );
        when(packageDao.findPackagesDuePoll(30, 1000)).thenReturn(due);

        service.runSweep();

        VersionsSyncService.SweepStatus status = service.getStatus();
        assertThat(status.state()).isEqualTo("COMPLETED");
        assertThat(status.discoveredPackages()).isEqualTo(3);
        assertThat(status.processedPackages()).isEqualTo(3);
        assertThat(status.failedPackages()).isZero();
        verify(repoMetaAnalyzer, times(3)).analyze(any(), anyLong());
    }

    @Test
    void runSweep_packageWithNullId_isSkipped() {
        // Edge case — defensive; entity from DB should always have id, but guard anyway.
        PackageEntity bogus = new PackageEntity();
        bogus.setPurlType("npm");
        bogus.setPurlName("foo");
        bogus.setPurlCanonical("pkg:npm/foo");
        // id stays null
        PackageEntity good = packageEntity(99L, "npm", null, "express");
        when(packageDao.findPackagesDuePoll(anyInt(), anyInt()))
                .thenReturn(List.of(bogus, good));

        service.runSweep();

        VersionsSyncService.SweepStatus status = service.getStatus();
        assertThat(status.discoveredPackages()).isEqualTo(2);
        assertThat(status.skippedPackages()).isEqualTo(1);
        assertThat(status.processedPackages()).isEqualTo(1);
        verify(repoMetaAnalyzer, times(1)).analyze(any(), eq(99L));
    }

    @Test
    void runSweep_analyzeThrows_countsAsFailedButDoesNotAbortSweep() {
        List<PackageEntity> due = List.of(
                packageEntity(1L, "npm", null, "good"),
                packageEntity(2L, "npm", null, "bad"),
                packageEntity(3L, "npm", null, "alsoGood")
        );
        when(packageDao.findPackagesDuePoll(anyInt(), anyInt())).thenReturn(due);

        // Throw on the second one only.
        AtomicInteger callCount = new AtomicInteger();
        when(repoMetaAnalyzer.analyze(any(), anyLong())).thenAnswer(inv -> {
            if (callCount.incrementAndGet() == 2) {
                throw new RuntimeException("upstream blew up");
            }
            return new ArrayList<>();
        });

        service.runSweep();

        VersionsSyncService.SweepStatus status = service.getStatus();
        assertThat(status.state()).isEqualTo("COMPLETED");
        assertThat(status.discoveredPackages()).isEqualTo(3);
        assertThat(status.processedPackages()).isEqualTo(2);
        assertThat(status.failedPackages()).isEqualTo(1);
    }

    @Test
    void runSweep_dbProducerThrows_marksFailedStatus() {
        when(packageDao.findPackagesDuePoll(anyInt(), anyInt()))
                .thenThrow(new RuntimeException("DB unreachable"));

        service.runSweep();

        VersionsSyncService.SweepStatus status = service.getStatus();
        assertThat(status.state()).isEqualTo("FAILED");
        assertThat(status.errorMessage()).contains("DB unreachable");
    }

    @Test
    void sweepStatus_factoriesProduceExpectedShapes() {
        assertThat(VersionsSyncService.SweepStatus.idle().state()).isEqualTo("IDLE");
        assertThat(VersionsSyncService.SweepStatus.idle().isRunning()).isFalse();
        assertThat(VersionsSyncService.SweepStatus.running().state()).isEqualTo("RUNNING");
        assertThat(VersionsSyncService.SweepStatus.running().isRunning()).isTrue();

        Instant start = Instant.parse("2026-05-07T10:00:00Z");
        Instant end = Instant.parse("2026-05-07T10:30:00Z");
        VersionsSyncService.SweepStatus completed =
                VersionsSyncService.SweepStatus.completed(100, 95, 3, 2, start, end);
        assertThat(completed.state()).isEqualTo("COMPLETED");
        assertThat(completed.discoveredPackages()).isEqualTo(100);
        assertThat(completed.processedPackages()).isEqualTo(95);
        assertThat(completed.failedPackages()).isEqualTo(3);
        assertThat(completed.skippedPackages()).isEqualTo(2);

        VersionsSyncService.SweepStatus failed =
                VersionsSyncService.SweepStatus.failed("boom", start);
        assertThat(failed.state()).isEqualTo("FAILED");
        assertThat(failed.errorMessage()).isEqualTo("boom");
    }

    private static PackageEntity packageEntity(Long id, String type, String namespace, String name) {
        PackageEntity e = new PackageEntity();
        e.setId(id);
        e.setPurlType(type);
        e.setPurlNamespace(namespace);
        e.setPurlName(name);
        String canonical = namespace == null
                ? "pkg:" + type + "/" + name
                : "pkg:" + type + "/" + namespace + "/" + name;
        e.setPurlCanonical(canonical);
        return e;
    }
}
