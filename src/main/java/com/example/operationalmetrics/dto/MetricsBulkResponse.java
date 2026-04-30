package com.example.operationalmetrics.dto;

import java.util.List;

public record MetricsBulkResponse(List<MetricsResponse> results, List<String> errors) {}
