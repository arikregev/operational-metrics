package com.example.operationalmetrics.model;

import java.time.Instant;

public class MetricsFetchLog {

    private Long id;
    private Long packageId;
    private String source;
    private String status;
    private Integer httpStatus;
    private String errorMessage;
    private Integer durationMs;
    private Instant fetchedAt;

    public static MetricsFetchLog success(Long packageId, MetricsSource source, int durationMs) {
        var log = new MetricsFetchLog();
        log.packageId = packageId;
        log.source = source.name();
        log.status = "SUCCESS";
        log.durationMs = durationMs;
        log.fetchedAt = Instant.now();
        return log;
    }

    public static MetricsFetchLog failed(Long packageId, MetricsSource source, Integer httpStatus, String error) {
        var log = new MetricsFetchLog();
        log.packageId = packageId;
        log.source = source.name();
        log.status = "FAILED";
        log.httpStatus = httpStatus;
        log.errorMessage = error;
        log.fetchedAt = Instant.now();
        return log;
    }

    public static MetricsFetchLog skipped(Long packageId, MetricsSource source, String reason) {
        var log = new MetricsFetchLog();
        log.packageId = packageId;
        log.source = source.name();
        log.status = "SKIPPED";
        log.errorMessage = reason;
        log.fetchedAt = Instant.now();
        return log;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }

    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }
}
