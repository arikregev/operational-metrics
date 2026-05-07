package com.example.operationalmetrics.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Versions sweep: discover new {@code package_version} rows on a separate
 * cadence from {@code operational_metrics}. At ~9M packages, polling every
 * package every 2 days is not feasible from a single source's rate limit;
 * the sweep is the safety net that processes a bounded batch per firing.
 *
 * <p>The changes-feed-driven primary path lives in a follow-up PR; this
 * config covers the sweep that ships in PR #1.
 */
@ConfigMapping(prefix = "metrics.versions")
public interface VersionsSyncConfig {

    @WithDefault("true")
    boolean enabled();

    @WithDefault("0 0 3 * * ?")
    String cron();

    /**
     * Max in-flight {@code analyze()} tasks. I/O bound (HTTP + DB), so a
     * value above the local CPU count is fine — virtual threads don't pin
     * OS threads. Default 32 covers the typical Snyk-or-ecosyste.ms latency
     * profile on a 2 CPU machine without saturating either upstream.
     */
    @WithDefault("32")
    int concurrency();

    /**
     * Pick packages whose {@code latest_versions_polled_at} is older than
     * this many days (NULL counts as infinitely old).
     */
    @WithDefault("30")
    int stalenessDays();

    /**
     * Max packages to process in a single sweep firing. At 9M packages over
     * a 30-day cycle, ~300K/day. Caps wall-clock per cron run and keeps the
     * upstream-API blast radius bounded if something goes wrong.
     */
    @WithDefault("300000")
    int batchSize();

    /**
     * Outgoing requests-per-second cap across the sweep, applied via a
     * token bucket. Throttles below the slowest upstream's rate limit so
     * the orchestrator's on-demand fetches and the metrics-refresh sweep
     * still have headroom on the same upstream.
     */
    @WithDefault("4")
    int rateBudgetPerSecond();
}
