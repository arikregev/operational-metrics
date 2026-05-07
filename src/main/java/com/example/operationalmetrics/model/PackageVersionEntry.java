package com.example.operationalmetrics.model;

import java.time.Instant;

public record PackageVersionEntry(
        Long packageId,
        String version,
        Instant releasedAt,
        String resolvedVia,
        Instant observedAt,
        Instant updatedAt
) {}
