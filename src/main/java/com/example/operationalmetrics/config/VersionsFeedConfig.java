package com.example.operationalmetrics.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;
import java.util.Optional;

/**
 * Changes-feed-driven version discovery: polls ecosyste.ms's recently-released
 * endpoint per registry on a fast cron and queues {@code analyze()} for any
 * package whose release we haven't seen yet AND that exists in our local
 * {@code package} table.
 *
 * <p>The endpoint:
 * {@code GET /api/v1/registries/{registry}/packages?sort=latest_release_published_at&order=desc}
 * has no {@code since=} filter, so we paginate descending and stop when
 * results dip below the watermark stored per-registry in
 * {@code versions_feed_cursor}.
 *
 * <p>Decoupled from {@link VersionsSyncConfig} (the safety-net sweep). The
 * feed is the primary path for sub-15-min freshness; the sweep catches drift
 * if the feed misses something.
 */
@ConfigMapping(prefix = "metrics.versions-feed")
public interface VersionsFeedConfig {

    @WithDefault("true")
    boolean enabled();

    /** Default: every 15 min. */
    @WithDefault("0 */15 * * * ?")
    String cron();

    /**
     * ecosyste.ms registry hostnames to poll. Listed by ecosystem coverage
     * priority — npm and PyPI are the highest-volume so usually the first to
     * surface drift. Default empty so operators opt in deliberately;
     * polling a registry we don't have any packages for wastes API budget.
     *
     * <p>{@link Optional} (not a defaulted {@link List}) because SmallRye's
     * collection converter rejects {@code @WithDefault("")} for List<T> —
     * empty string converts to null, which fails the implicit non-null
     * config-property check. Use {@link #registriesList()} to get a
     * never-null view.
     */
    Optional<List<String>> registries();

    /** Always-non-null view of {@link #registries()}. */
    default List<String> registriesList() {
        return registries().orElse(List.of());
    }

    /** {@code per_page} query param to ecosyste.ms. Max documented is 100. */
    @WithDefault("100")
    int perPage();

    /**
     * Safety cap on pagination depth per poll. With per-page=100, 20 pages =
     * 2000 packages. If a registry's tail is more than 2000 deep within the
     * cron interval, the next poll catches up. Prevents runaway during
     * burst-release windows or registry mass re-imports.
     */
    @WithDefault("20")
    int maxPagesPerPoll();

    /**
     * On the very first poll for a registry (no row in
     * {@code versions_feed_cursor}), seed the watermark this many hours back.
     * Tradeoff: too small → miss anything older that we haven't caught
     * yet; too large → first poll is heavy.
     */
    @WithDefault("2")
    int initialLookbackHours();

    /**
     * Max in-flight {@code analyze()} tasks for the matched packages. Same
     * I/O-bound calculus as {@link VersionsSyncConfig}; default lower
     * because feed work is bursty and we don't want it to crowd the sweep.
     */
    @WithDefault("8")
    int concurrency();

    /**
     * Outgoing analyze-rate cap, in requests-per-second, applied via a
     * shared token bucket. Set lower than the sweep's budget so the feed
     * doesn't starve the sweep when both are running.
     */
    @WithDefault("2")
    int rateBudgetPerSecond();
}
