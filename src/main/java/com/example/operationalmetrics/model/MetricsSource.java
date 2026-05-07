package com.example.operationalmetrics.model;

public enum MetricsSource {
    SNYK("snyk"),
    SCORECARD("scorecard"),
    DEPS_DEV("depsdev"),
    ECOSYSTEMS("ecosystems"),
    GITHUB("github");

    private final String configKey;

    MetricsSource(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public static MetricsSource fromConfigKey(String key) {
        for (MetricsSource source : values()) {
            if (source.configKey.equals(key)) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unknown source config key: " + key);
    }
}
