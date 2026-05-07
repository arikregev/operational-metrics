package com.example.operationalmetrics.client.snyk.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SnykPackageVersionResponse(Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            String id,
            String type,
            Attributes attributes,
            @JsonProperty("package_version") String packageVersion
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Attributes(
            String ecosystem,
            String language,
            @JsonProperty("latest_version") String latestVersion,
            @JsonProperty("latest_version_indicator") Boolean latestVersionIndicator,
            @JsonProperty("owner_details") SnykPackageResponse.OwnerDetails ownerDetails,
            @JsonProperty("package_details") SnykPackageDetails packageDetails,
            @JsonProperty("package_health") SnykPackageHealth packageHealth,
            @JsonProperty("package_id") String packageId,
            @JsonProperty("package_name") String packageName,
            @JsonProperty("package_version") String packageVersion,
            @JsonProperty("package_version_id") String packageVersionId,
            @JsonProperty("published_at") Instant publishedAt
    ) {
    }
}
