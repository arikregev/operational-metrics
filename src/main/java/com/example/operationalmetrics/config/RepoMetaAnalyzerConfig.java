package com.example.operationalmetrics.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Configuration for {@code RepoMetaAnalyzer}, which populates the
 * {@code package_version} table with per-version release dates.
 *
 * <p>Two distinct flows:
 * <ul>
 *   <li><b>Bulk</b> — version-list discovery during sync. ecosyste.ms and
 *       deps.dev have list endpoints; Snyk does not, so it's omitted here.</li>
 *   <li><b>Per-version</b> — single-version on-demand lookup. All three
 *       sources (Snyk, ecosyste.ms, deps.dev) support it.</li>
 * </ul>
 */
@ConfigMapping(prefix = "metrics.repo-meta-analyzer")
public interface RepoMetaAnalyzerConfig {

    @WithDefault("true")
    boolean enabled();

    Bulk bulk();

    PerVersion perVersion();

    interface Bulk {
        /**
         * Skip Flow A if any package_version row for the package has been
         * updated within this many days. Avoids hammering upstream APIs on
         * every nightly sync.
         */
        @WithDefault("7")
        int refreshAfterDays();

        Map<String, SourcePriority> source();

        default List<String> enabledSourcesByPriority() {
            return source().entrySet().stream()
                    .sorted(Comparator.comparingInt(e -> e.getValue().priority()))
                    .map(Map.Entry::getKey)
                    .toList();
        }
    }

    interface PerVersion {
        Map<String, SourcePriority> source();

        default List<String> enabledSourcesByPriority() {
            return source().entrySet().stream()
                    .sorted(Comparator.comparingInt(e -> e.getValue().priority()))
                    .map(Map.Entry::getKey)
                    .toList();
        }
    }

    interface SourcePriority {
        int priority();
    }
}
