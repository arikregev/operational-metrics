package com.example.operationalmetrics.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "metrics.history")
public interface HistoryConfig {

    @WithDefault("90")
    int retentionDays();

    @WithDefault("0 0 3 * * ?")
    String purgeCron();
}
