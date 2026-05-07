package com.example.operationalmetrics.client.snyk.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SnykPackageDetails(
        @JsonProperty("homepage_url") String homepageUrl,
        @JsonProperty("registry_url") String registryUrl,
        @JsonProperty("repository_url") String repositoryUrl,
        @JsonProperty("download_url") String downloadUrl,
        @JsonProperty("registry_version_url") String registryVersionUrl
) {
}
