package com.example.operationalmetrics.scheduler;

import com.example.operationalmetrics.config.DiscoverySyncConfig;
import com.example.operationalmetrics.config.RefreshSyncConfig;
import com.example.operationalmetrics.service.PurlSyncService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MetricsSyncScheduler {

    private final PurlSyncService syncService;
    private final RefreshSyncConfig refreshConfig;
    private final DiscoverySyncConfig discoveryConfig;

    @Inject
    public MetricsSyncScheduler(PurlSyncService syncService,
                                RefreshSyncConfig refreshConfig,
                                DiscoverySyncConfig discoveryConfig) {
        this.syncService = syncService;
        this.refreshConfig = refreshConfig;
        this.discoveryConfig = discoveryConfig;
    }

    /**
     * Full sync — DT walk + collect for every PURL. The original (and
     * default) cadence; behaviour preserved exactly.
     */
    @Scheduled(cron = "${metrics.sync.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledFullSync() {
        syncService.runFullSync();
    }

    /**
     * Refresh-only — iterate the local {@code package} table, refresh
     * metrics for stale entries. Disabled by default (operators opt in via
     * {@code metrics.refresh.enabled=true} when they want a different
     * cadence than the full sync).
     */
    @Scheduled(cron = "${metrics.refresh.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledRefreshSync() {
        if (!refreshConfig.enabled()) return;
        syncService.runRefresh();
    }

    /**
     * Discovery-only — DT walk only, upsert {@code package} rows. Disabled
     * by default; useful when operators want fresh package discovery on a
     * faster cadence than expensive metrics collection.
     */
    @Scheduled(cron = "${metrics.discovery.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledDiscoverySync() {
        if (!discoveryConfig.enabled()) return;
        syncService.runDiscovery();
    }
}
