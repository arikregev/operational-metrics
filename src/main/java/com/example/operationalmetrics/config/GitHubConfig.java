package com.example.operationalmetrics.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "github")
public interface GitHubConfig {

    Optional<String> token();

    @WithDefault("https://api.github.com")
    String apiUrl();
}
