package com.example.operationalmetrics.scheduler;

import com.example.operationalmetrics.service.HistoryPurgeService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class HistoryPurgeScheduler {

    private final HistoryPurgeService purgeService;

    @Inject
    public HistoryPurgeScheduler(HistoryPurgeService purgeService) {
        this.purgeService = purgeService;
    }

    @Scheduled(cron = "${metrics.history.purge-cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledPurge() {
        purgeService.purge();
    }
}
