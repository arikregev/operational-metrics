package com.example.operationalmetrics.client.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubCommunityProfile(
        @JsonProperty("health_percentage") Integer healthPercentage,
        String description,
        String documentation
) {
}
