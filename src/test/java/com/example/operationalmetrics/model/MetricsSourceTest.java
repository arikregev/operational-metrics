package com.example.operationalmetrics.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricsSourceTest {

    @Test
    void configKeysMapToExpectedSources() {
        assertThat(MetricsSource.fromConfigKey("scorecard")).isEqualTo(MetricsSource.SCORECARD);
        assertThat(MetricsSource.fromConfigKey("depsdev")).isEqualTo(MetricsSource.DEPS_DEV);
        assertThat(MetricsSource.fromConfigKey("ecosystems")).isEqualTo(MetricsSource.ECOSYSTEMS);
        assertThat(MetricsSource.fromConfigKey("github")).isEqualTo(MetricsSource.GITHUB);
    }

    @Test
    void unknownKeyThrows() {
        assertThatThrownBy(() -> MetricsSource.fromConfigKey("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown source config key");
    }

    @Test
    void configKeyAccessor() {
        for (MetricsSource s : MetricsSource.values()) {
            assertThat(s.configKey()).isNotBlank();
        }
    }
}
