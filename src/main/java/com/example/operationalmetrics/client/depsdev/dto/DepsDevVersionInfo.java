package com.example.operationalmetrics.client.depsdev.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DepsDevVersionInfo(
        DepsDevPurlResponse.DepsDevVersionKey versionKey,
        Instant publishedAt,
        Boolean isDefault,
        Boolean isDeprecated,
        String purl
) {
}
