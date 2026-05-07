package com.example.operationalmetrics.model;

import java.time.Instant;

/**
 * Per-registry watermark for the changes-feed poller. Persisted in
 * {@code versions_feed_cursor}. Survives restarts so a process bounce
 * doesn't either re-poll already-seen packages or miss a window.
 *
 * @param registry            ecosyste.ms registry hostname (e.g. "npmjs.org")
 * @param lastPolledAt        when this registry was last polled, regardless
 *                            of whether anything was found. Diagnostic.
 * @param lastSeenReleaseAt   the watermark — newest
 *                            {@code latest_release_published_at} we saw on
 *                            the previous poll. Pagination stops when
 *                            results dip below this. Null on first poll;
 *                            seeded with {@code now() - initialLookbackHours}.
 */
public record FeedCursor(
        String registry,
        Instant lastPolledAt,
        Instant lastSeenReleaseAt
) {}
