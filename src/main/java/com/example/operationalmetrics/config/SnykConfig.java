package com.example.operationalmetrics.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "snyk")
public interface SnykConfig {

    @WithDefault("false")
    boolean enabled();

    Optional<String> token();

    Optional<String> orgId();

    @WithDefault("https://api.snyk.io")
    String apiUrl();

    @WithDefault("2024-10-15")
    String apiVersion();
}
