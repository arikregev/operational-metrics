package com.example.operationalmetrics.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Discovery-only sync mode: walk Dependency Track and upsert {@code package}
 * rows for every PURL seen. Does NOT call collectors — operational_metrics
 * is left untouched. Cheap (only DT calls, no per-package work) and lets new
 * PURLs surface in the DB ahead of the next full sync.
 */
@ConfigMapping(prefix = "metrics.discovery")
public interface DiscoverySyncConfig {

    @WithDefault("false")
    boolean enabled();

    @WithDefault("0 0 1 * * ?")
    String cron();
}
