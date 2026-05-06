package com.example.operationalmetrics.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "metrics.sync")
public interface SyncConfig {

    @WithDefault("0 0 2 * * ?")
    String cron();

    /**
     * Maximum number of orchestrator tasks in flight at once during a sync.
     * Used as the permit count for the streaming sync's Semaphore — when
     * saturated, DT pagination naturally throttles to match downstream
     * throughput.
     */
    @WithDefault("4")
    int concurrency();
}
