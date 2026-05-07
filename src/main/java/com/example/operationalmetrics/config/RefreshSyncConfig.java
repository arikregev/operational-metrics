package com.example.operationalmetrics.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Refresh-only sync mode: iterate the existing {@code package} table (no DT
 * walk), optionally filtered by staleness, and call the orchestrator for each.
 *
 * <p>Decoupled from the DT-walk ({@link SyncConfig}) so operations teams can
 * refresh metrics on a different cadence than they discover new packages.
 */
@ConfigMapping(prefix = "metrics.refresh")
public interface RefreshSyncConfig {

    @WithDefault("false")
    boolean enabled();

    @WithDefault("0 0 4 * * ?")
    String cron();

    @WithDefault("4")
    int concurrency();

    /**
     * Skip packages whose {@code operational_metrics.fetched_at} is fresher
     * than this many days. Use {@code 0} to ignore staleness and refresh
     * every package.
     */
    @WithDefault("90")
    int stalenessDays();
}
