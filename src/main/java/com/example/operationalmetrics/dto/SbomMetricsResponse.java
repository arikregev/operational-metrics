package com.example.operationalmetrics.dto;

import java.util.List;

public record SbomMetricsResponse(
    int packagesTotal,
    int packagesFetchedOnDemand,
    List<MetricsResponse> results,
    List<ErrorEntry> errors
) {
    public record ErrorEntry(String purl, String reason) {}
}
