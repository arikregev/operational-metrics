package com.example.operationalmetrics.scheduler;

import com.example.operationalmetrics.service.VersionsSyncService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class VersionsSyncScheduler {

    private final VersionsSyncService versionsService;

    @Inject
    public VersionsSyncScheduler(VersionsSyncService versionsService) {
        this.versionsService = versionsService;
    }

    /**
     * Skip overlapping firings — the previous sweep can take longer than the
     * cron interval if many packages need polling. Concurrent runs would
     * compete for the same rate budget and leave logs interleaved.
     */
    @Scheduled(cron = "${metrics.versions.cron}",
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledVersionsSweep() {
        versionsService.runSweep();
    }
}
