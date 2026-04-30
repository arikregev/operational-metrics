package com.example.operationalmetrics.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "metrics.sync")
public interface SyncConfig {

    @WithDefault("0 0 2 * * ?")
    String cron();

    @WithDefault("4")
    int concurrency();

    @WithDefault("500")
    int batchSize();

    @WithDefault("1000")
    long rateLimitDelayMs();

    @WithDefault("3")
    int maxRetries();
}
