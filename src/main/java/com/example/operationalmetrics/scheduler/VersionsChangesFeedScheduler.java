package com.example.operationalmetrics.scheduler;

import com.example.operationalmetrics.service.VersionsChangesFeedService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Fires the changes-feed poller on a fast cadence (default every 15 min).
 *
 * <p>Skip overlapping firings — if a poll is still in flight when the next
 * cron tick arrives, drop the new tick rather than running two pollers in
 * parallel competing for the same rate budget. The next tick will pick up
 * naturally once the in-flight one finishes; the changes-feed watermark
 * makes this safe — we won't double-process anything.
 */
@ApplicationScoped
public class VersionsChangesFeedScheduler {

    private final VersionsChangesFeedService feedService;

    @Inject
    public VersionsChangesFeedScheduler(VersionsChangesFeedService feedService) {
        this.feedService = feedService;
    }

    @Scheduled(cron = "${metrics.versions-feed.cron}",
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledFeedPoll() {
        feedService.pollAllRegistries();
    }
}
