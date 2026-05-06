package com.example.operationalmetrics.client.depsdev.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DepsDevPackage(
        @JsonProperty("packageKey") PackageKey packageKey,
        List<VersionRef> versions
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PackageKey(String system, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VersionRef(
            DepsDevPurlResponse.DepsDevVersionKey versionKey,
            @JsonProperty("publishedAt") Instant publishedAt,
            @JsonProperty("isDefault") Boolean isDefault
    ) {
    }
}
