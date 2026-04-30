package com.example.operationalmetrics.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ConfigMapping(prefix = "metrics.sources")
public interface SourceConfig {

    Map<String, SourceEntry> source();

    interface SourceEntry {
        boolean enabled();
        int priority();
        Optional<String> baseUrl();
    }

    default List<String> enabledSourcesByPriority() {
        return source().entrySet().stream()
                .filter(e -> e.getValue().enabled())
                .sorted(Comparator.comparingInt(e -> e.getValue().priority()))
                .map(Map.Entry::getKey)
                .toList();
    }
}
