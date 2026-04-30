package com.example.operationalmetrics.scheduler;

import com.example.operationalmetrics.service.PurlSyncService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MetricsSyncScheduler {

    private final PurlSyncService syncService;

    @Inject
    public MetricsSyncScheduler(PurlSyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(cron = "${metrics.sync.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledSync() {
        syncService.runFullSync();
    }
}
