package com.example.operationalmetrics.client.snyk.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SnykPackageResponse(Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String id, String type, Attributes attributes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Attributes(
            String ecosystem,
            String language,
            @JsonProperty("latest_version") String latestVersion,
            @JsonProperty("owner_details") OwnerDetails ownerDetails,
            @JsonProperty("package_details") SnykPackageDetails packageDetails,
            @JsonProperty("package_health") SnykPackageHealth packageHealth,
            @JsonProperty("package_id") String packageId,
            @JsonProperty("package_name") String packageName
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OwnerDetails(
            @JsonProperty("followers_count") Integer followersCount,
            String location,
            String name,
            @JsonProperty("repositories_count") Integer repositoriesCount,
            @JsonProperty("total_stars") Integer totalStars
    ) {
    }
}
