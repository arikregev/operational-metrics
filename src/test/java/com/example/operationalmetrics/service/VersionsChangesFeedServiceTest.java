package com.example.operationalmetrics.service;

import com.example.operationalmetrics.client.ecosystems.EcosystemsClient;
import com.example.operationalmetrics.client.ecosystems.dto.EcosystemsPackageRef;
import com.example.operationalmetrics.config.VersionsFeedConfig;
import com.example.operationalmetrics.model.FeedCursor;
import com.example.operationalmetrics.model.PackageEntity;
import com.example.operationalmetrics.repository.PackageDao;
import com.example.operationalmetrics.repository.VersionsFeedCursorDao;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.extension.ExtensionCallback;
import org.jdbi.v3.core.extension.ExtensionConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VersionsChangesFeedServiceTest {

    @Mock VersionsFeedConfig config;
    @Mock EcosystemsClient ecosystemsClient;
    @Mock RepoMetaAnalyzer repoMetaAnalyzer;
    @Mock Jdbi jdbi;
    @Mock PackageDao packageDao;
    @Mock VersionsFeedCursorDao cursorDao;

    private VersionsChangesFeedService service;

    @BeforeEach
    void setUp() {
        // Sensible defaults — individual tests override what they care about.
        lenient().when(config.enabled()).thenReturn(true);
        lenient().when(config.registriesList()).thenReturn(List.of("npmjs.org"));
        lenient().when(config.perPage()).thenReturn(100);
        lenient().when(config.maxPagesPerPoll()).thenReturn(20);
        lenient().when(config.initialLookbackHours()).thenReturn(2);
        lenient().when(config.concurrency()).thenReturn(4);
        lenient().when(config.rateBudgetPerSecond()).thenReturn(100);   // high so test isn't slow

        // Wire jdbi.withExtension(...) → callback against our mocked DAOs.
        lenient().when(jdbi.withExtension(eq(PackageDao.class), any())).thenAnswer(inv -> {
            ExtensionCallback<?, PackageDao, ?> cb = inv.getArgument(1);
            return cb.withExtension(packageDao);
        });
        lenient().when(jdbi.withExtension(eq(VersionsFeedCursorDao.class), any())).thenAnswer(inv -> {
            ExtensionCallback<?, VersionsFeedCursorDao, ?> cb = inv.getArgument(1);
            return cb.withExtension(cursorDao);
        });
        lenient().doAnswer(inv -> {
            ExtensionConsumer<VersionsFeedCursorDao, ?> cb = inv.getArgument(1);
            cb.useExtension(cursorDao);
            return null;
        }).when(jdbi).useExtension(eq(VersionsFeedCursorDao.class), any());

        // First poll for a registry has no cursor row → service falls back to
        // initialLookbackHours. Tests that want a specific watermark override.
        lenient().when(cursorDao.find(anyString())).thenReturn(Optional.empty());

        service = new VersionsChangesFeedService(config, ecosystemsClient, repoMetaAnalyzer, jdbi);
    }

    // -------------------------------------------------------------------------
    // Status / lifecycle
    // -------------------------------------------------------------------------

    @Test
    void getStatus_initiallyIdle() {
        VersionsChangesFeedService.FeedStatus status = service.getStatus();
        assertThat(status.state()).isEqualTo("IDLE");
        assertThat(status.isRunning()).isFalse();
    }

    @Test
    void pollAllRegistries_disabled_setsIdleAndDoesNothing() {
        when(config.enabled()).thenReturn(false);

        service.pollAllRegistries();

        assertThat(service.getStatus().state()).isEqualTo("IDLE");
        verify(ecosystemsClient, never()).recentPackages(anyString(), anyString(), anyString(), anyInt(), anyInt());
        verify(repoMetaAnalyzer, never()).analyze(any(), anyLong());
    }

    @Test
    void pollAllRegistries_emptyRegistries_setsIdleAndDoesNothing() {
        when(config.registriesList()).thenReturn(Collections.emptyList());

        service.pollAllRegistries();

        assertThat(service.getStatus().state()).isEqualTo("IDLE");
        verify(ecosystemsClient, never()).recentPackages(anyString(), anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void pollAllRegistries_blankRegistryEntry_setsIdleAndDoesNothing() {
        // Default "" → Quarkus parses the empty string as a single blank list element.
        when(config.registriesList()).thenReturn(List.of(""));

        service.pollAllRegistries();

        assertThat(service.getStatus().state()).isEqualTo("IDLE");
        verify(ecosystemsClient, never()).recentPackages(anyString(), anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void pollAllRegistries_unsupportedRegistry_skipsButCompletes() {
        when(config.registriesList()).thenReturn(List.of("not-a-registry.example"));

        service.pollAllRegistries();

        // Status still settles to COMPLETED — the unsupported registry is logged-and-skipped, not failed.
        assertThat(service.getStatus().state()).isEqualTo("COMPLETED");
        verify(ecosystemsClient, never()).recentPackages(anyString(), anyString(), anyString(), anyInt(), anyInt());
    }

    // -------------------------------------------------------------------------
    // Watermark / pagination behaviour
    // -------------------------------------------------------------------------

    @Test
    void pollRegistry_paginatesUntilWatermarkCrossed() {
        // Stored cursor at T-30min. Page 1: 2 fresh + 1 too-old. Should stop after page 1
        // because the watermark is crossed before reaching the per-page limit.
        Instant watermark = Instant.parse("2026-05-07T10:00:00Z");
        when(cursorDao.find("npmjs.org"))
                .thenReturn(Optional.of(new FeedCursor("npmjs.org", watermark, watermark)));

        List<EcosystemsPackageRef> page1 = List.of(
                ref("express",  Instant.parse("2026-05-07T10:30:00Z")),  // > watermark → candidate
                ref("react",    Instant.parse("2026-05-07T10:15:00Z")),  // > watermark → candidate
                ref("oldlib",   Instant.parse("2026-05-07T09:50:00Z"))   // <= watermark → STOP
        );
        when(ecosystemsClient.recentPackages("npmjs.org", "latest_release_published_at", "desc", 1, 100))
                .thenReturn(page1);

        // None of these are in our local table.
        when(packageDao.findByCanonicals(any())).thenReturn(Collections.emptyList());

        service.pollAllRegistries();

        // Verify pagination stopped at page 1 — page 2 must NOT be fetched.
        verify(ecosystemsClient, times(1))
                .recentPackages(anyString(), anyString(), anyString(), eq(1), anyInt());
        verify(ecosystemsClient, never())
                .recentPackages(anyString(), anyString(), anyString(), eq(2), anyInt());

        // Status: scanned 3 (entire page was inspected), matched 0.
        VersionsChangesFeedService.FeedStatus status = service.getStatus();
        assertThat(status.state()).isEqualTo("COMPLETED");
        assertThat(status.scannedPackages()).isEqualTo(3);
        assertThat(status.matchedPackages()).isZero();
        assertThat(status.analyzedPackages()).isZero();
    }

    @Test
    void pollRegistry_emptyFirstPage_stops() {
        when(cursorDao.find(anyString())).thenReturn(Optional.empty());
        when(ecosystemsClient.recentPackages(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        service.pollAllRegistries();

        verify(ecosystemsClient, times(1))
                .recentPackages(anyString(), anyString(), anyString(), eq(1), anyInt());
        verify(ecosystemsClient, never())
                .recentPackages(anyString(), anyString(), anyString(), eq(2), anyInt());
        // Cursor still advances to runStart so we don't get stuck.
        verify(cursorDao, times(1)).upsert(eq("npmjs.org"), any(), eq(null));
    }

    @Test
    void pollRegistry_pageFetchFails_persistsCursorAndContinues() {
        // Even if page-fetch throws, the runStart cursor must still be persisted
        // — failures must not cause the next poll to redo the same window.
        when(ecosystemsClient.recentPackages(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("ecosyste.ms 503"));

        service.pollAllRegistries();

        verify(cursorDao, atLeastOnce()).upsert(eq("npmjs.org"), any(), any());
        // Service still completes (single registry failure is logged-and-handled).
        assertThat(service.getStatus().state()).isEqualTo("COMPLETED");
    }

    @Test
    void pollRegistry_safetyCapHaltsAfterMaxPages() {
        when(config.maxPagesPerPoll()).thenReturn(2);
        // Cursor far in the past so nothing crosses the watermark — only the cap stops us.
        when(cursorDao.find(anyString()))
                .thenReturn(Optional.of(new FeedCursor("npmjs.org",
                        Instant.parse("2020-01-01T00:00:00Z"),
                        Instant.parse("2020-01-01T00:00:00Z"))));

        // Every page returns 1 fresh ref so paginating never auto-stops on watermark.
        when(ecosystemsClient.recentPackages(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenAnswer(inv -> List.of(ref("p" + inv.getArgument(3), Instant.now())));
        when(packageDao.findByCanonicals(any())).thenReturn(Collections.emptyList());

        service.pollAllRegistries();

        verify(ecosystemsClient, times(1)).recentPackages(anyString(), anyString(), anyString(), eq(1), anyInt());
        verify(ecosystemsClient, times(1)).recentPackages(anyString(), anyString(), anyString(), eq(2), anyInt());
        verify(ecosystemsClient, never()).recentPackages(anyString(), anyString(), anyString(), eq(3), anyInt());
    }

    // -------------------------------------------------------------------------
    // Intersection with local package table → analyze
    // -------------------------------------------------------------------------

    @Test
    void pollRegistry_matchedPackages_areAnalyzed() {
        when(cursorDao.find(anyString())).thenReturn(Optional.empty());
        // 3 fresh refs in the page; only 2 match locally.
        Instant t = Instant.now();
        when(ecosystemsClient.recentPackages(anyString(), anyString(), anyString(), eq(1), anyInt()))
                .thenReturn(List.of(
                        ref("express", t.minusSeconds(60)),
                        ref("react",   t.minusSeconds(120)),
                        ref("missing", t.minusSeconds(180))
                ));
        // After page 1, every page is empty → poll stops naturally.
        when(ecosystemsClient.recentPackages(anyString(), anyString(), anyString(), eq(2), anyInt()))
                .thenReturn(Collections.emptyList());

        when(packageDao.findByCanonicals(any())).thenReturn(List.of(
                packageEntity(101L, "npm", null, "express"),
                packageEntity(102L, "npm", null, "react")
        ));

        service.pollAllRegistries();

        verify(repoMetaAnalyzer, times(2)).analyze(any(), anyLong());
        VersionsChangesFeedService.FeedStatus status = service.getStatus();
        assertThat(status.matchedPackages()).isEqualTo(2);
        assertThat(status.analyzedPackages()).isEqualTo(2);
        assertThat(status.failedPackages()).isZero();
    }

    @Test
    void pollRegistry_analyzeThrows_countsFailedButContinues() {
        when(cursorDao.find(anyString())).thenReturn(Optional.empty());
        Instant t = Instant.now();
        when(ecosystemsClient.recentPackages(anyString(), anyString(), anyString(), eq(1), anyInt()))
                .thenReturn(List.of(
                        ref("good",     t.minusSeconds(60)),
                        ref("bad",      t.minusSeconds(120)),
                        ref("alsoGood", t.minusSeconds(180))
                ));
        when(ecosystemsClient.recentPackages(anyString(), anyString(), anyString(), eq(2), anyInt()))
                .thenReturn(Collections.emptyList());

        when(packageDao.findByCanonicals(any())).thenReturn(List.of(
                packageEntity(1L, "npm", null, "good"),
                packageEntity(2L, "npm", null, "bad"),
                packageEntity(3L, "npm", null, "alsoGood")
        ));

        AtomicInteger callCount = new AtomicInteger();
        when(repoMetaAnalyzer.analyze(any(), anyLong())).thenAnswer(inv -> {
            if (callCount.incrementAndGet() == 2) {
                throw new RuntimeException("upstream 500");
            }
            return new ArrayList<>();
        });

        service.pollAllRegistries();

        VersionsChangesFeedService.FeedStatus status = service.getStatus();
        assertThat(status.state()).isEqualTo("COMPLETED");
        assertThat(status.analyzedPackages()).isEqualTo(2);
        assertThat(status.failedPackages()).isEqualTo(1);
    }

    @Test
    void pollRegistry_packageWithNullId_isSkipped() {
        when(cursorDao.find(anyString())).thenReturn(Optional.empty());
        Instant t = Instant.now();
        when(ecosystemsClient.recentPackages(anyString(), anyString(), anyString(), eq(1), anyInt()))
                .thenReturn(List.of(ref("ok", t)));
        when(ecosystemsClient.recentPackages(anyString(), anyString(), anyString(), eq(2), anyInt()))
                .thenReturn(Collections.emptyList());

        // Two entities returned; one with null id should be skipped silently
        // (defensive — entities from DB normally always have id).
        PackageEntity bogus = new PackageEntity();
        bogus.setPurlType("npm");
        bogus.setPurlName("ok");
        bogus.setPurlCanonical("pkg:npm/ok");
        // id deliberately null
        when(packageDao.findByCanonicals(any()))
                .thenReturn(List.of(bogus, packageEntity(7L, "npm", null, "ok")));

        service.pollAllRegistries();

        verify(repoMetaAnalyzer, times(1)).analyze(any(), eq(7L));
    }

    // -------------------------------------------------------------------------
    // Cursor persistence
    // -------------------------------------------------------------------------

    @Test
    void pollRegistry_advancesCursorToNewestSeen() {
        Instant newest = Instant.parse("2026-05-07T11:00:00Z");
        when(cursorDao.find("npmjs.org"))
                .thenReturn(Optional.of(new FeedCursor(
                        "npmjs.org",
                        Instant.parse("2026-05-07T09:00:00Z"),
                        Instant.parse("2026-05-07T09:00:00Z"))));

        when(ecosystemsClient.recentPackages(anyString(), anyString(), anyString(), eq(1), anyInt()))
                .thenReturn(List.of(
                        ref("express", newest),  // newest in this batch
                        ref("react",   Instant.parse("2026-05-07T10:30:00Z"))
                ));
        when(ecosystemsClient.recentPackages(anyString(), anyString(), anyString(), eq(2), anyInt()))
                .thenReturn(Collections.emptyList());
        when(packageDao.findByCanonicals(any())).thenReturn(Collections.emptyList());

        service.pollAllRegistries();

        ArgumentCaptor<Instant> seenAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(cursorDao).upsert(eq("npmjs.org"), any(), seenAtCaptor.capture());
        assertThat(seenAtCaptor.getValue()).isEqualTo(newest);
    }

    @Test
    void pollRegistry_noResults_passesNullSeenAtToCursor_preservingExistingWatermark() {
        // When the page is empty, we pass null for seenAt — the DAO's COALESCE
        // logic keeps the previously-stored watermark unchanged.
        when(ecosystemsClient.recentPackages(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        service.pollAllRegistries();

        verify(cursorDao).upsert(eq("npmjs.org"), any(), eq(null));
    }

    // -------------------------------------------------------------------------
    // Multi-registry behaviour
    // -------------------------------------------------------------------------

    @Test
    void pollAllRegistries_isolatesPerRegistryFailures() {
        when(config.registriesList()).thenReturn(List.of("npmjs.org", "pypi.org"));
        // npmjs throws on every page; pypi succeeds.
        when(ecosystemsClient.recentPackages(eq("npmjs.org"), anyString(), anyString(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("npm down"));
        when(ecosystemsClient.recentPackages(eq("pypi.org"), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        service.pollAllRegistries();

        // pypi was still attempted despite npm's failure.
        verify(ecosystemsClient, atLeastOnce())
                .recentPackages(eq("pypi.org"), anyString(), anyString(), anyInt(), anyInt());
        // Both registries got their cursor refreshed (npm's row records that we tried).
        verify(cursorDao).upsert(eq("npmjs.org"), any(), any());
        verify(cursorDao).upsert(eq("pypi.org"),  any(), any());
        assertThat(service.getStatus().state()).isEqualTo("COMPLETED");
    }

    // -------------------------------------------------------------------------
    // FeedStatus factory shapes
    // -------------------------------------------------------------------------

    @Test
    void feedStatus_factoriesProduceExpectedShapes() {
        assertThat(VersionsChangesFeedService.FeedStatus.idle().state()).isEqualTo("IDLE");
        assertThat(VersionsChangesFeedService.FeedStatus.idle().isRunning()).isFalse();
        assertThat(VersionsChangesFeedService.FeedStatus.running().state()).isEqualTo("RUNNING");
        assertThat(VersionsChangesFeedService.FeedStatus.running().isRunning()).isTrue();

        Instant start = Instant.parse("2026-05-07T10:00:00Z");
        Instant end = Instant.parse("2026-05-07T10:00:30Z");
        VersionsChangesFeedService.FeedStatus completed =
                VersionsChangesFeedService.FeedStatus.completed(50, 7, 6, 1, start, end);
        assertThat(completed.state()).isEqualTo("COMPLETED");
        assertThat(completed.scannedPackages()).isEqualTo(50);
        assertThat(completed.matchedPackages()).isEqualTo(7);
        assertThat(completed.analyzedPackages()).isEqualTo(6);
        assertThat(completed.failedPackages()).isEqualTo(1);

        VersionsChangesFeedService.FeedStatus failed =
                VersionsChangesFeedService.FeedStatus.failed("boom", start);
        assertThat(failed.state()).isEqualTo("FAILED");
        assertThat(failed.errorMessage()).isEqualTo("boom");
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static EcosystemsPackageRef ref(String name, Instant releasedAt) {
        return new EcosystemsPackageRef(name, "npm", "1.0.0", releasedAt);
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
