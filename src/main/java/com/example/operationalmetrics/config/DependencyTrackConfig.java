package com.example.operationalmetrics.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "dependency-track")
public interface DependencyTrackConfig {

    String url();

    String apiKey();

    @WithDefault("100")
    int pageSize();
}
