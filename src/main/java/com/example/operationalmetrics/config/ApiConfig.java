package com.example.operationalmetrics.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "metrics.api")
public interface ApiConfig {

    @WithDefault("4")
    int onDemandConcurrency();
}
