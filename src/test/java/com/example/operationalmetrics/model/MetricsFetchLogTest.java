package com.example.operationalmetrics.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsFetchLogTest {

    @Test
    void successFactorySetsExpectedFields() {
        var log = MetricsFetchLog.success(42L, MetricsSource.SCORECARD, 1234);
        assertThat(log.getPackageId()).isEqualTo(42L);
        assertThat(log.getSource()).isEqualTo("SCORECARD");
        assertThat(log.getStatus()).isEqualTo("SUCCESS");
        assertThat(log.getDurationMs()).isEqualTo(1234);
        assertThat(log.getFetchedAt()).isNotNull();
        assertThat(log.getHttpStatus()).isNull();
        assertThat(log.getErrorMessage()).isNull();
    }

    @Test
    void failedFactorySetsExpectedFields() {
        var log = MetricsFetchLog.failed(7L, MetricsSource.DEPS_DEV, 503, "boom");
        assertThat(log.getPackageId()).isEqualTo(7L);
        assertThat(log.getSource()).isEqualTo("DEPS_DEV");
        assertThat(log.getStatus()).isEqualTo("FAILED");
        assertThat(log.getHttpStatus()).isEqualTo(503);
        assertThat(log.getErrorMessage()).isEqualTo("boom");
    }

    @Test
    void skippedFactorySetsExpectedFields() {
        var log = MetricsFetchLog.skipped(3L, MetricsSource.GITHUB, "no repo URL");
        assertThat(log.getPackageId()).isEqualTo(3L);
        assertThat(log.getStatus()).isEqualTo("SKIPPED");
        assertThat(log.getErrorMessage()).isEqualTo("no repo URL");
    }

    @Test
    void settersWork() {
        var log = new MetricsFetchLog();
        log.setId(99L);
        log.setHttpStatus(429);
        log.setDurationMs(50);
        log.setSource("X");
        log.setStatus("Y");
        log.setErrorMessage("Z");
        assertThat(log.getId()).isEqualTo(99L);
        assertThat(log.getHttpStatus()).isEqualTo(429);
        assertThat(log.getDurationMs()).isEqualTo(50);
    }
}
