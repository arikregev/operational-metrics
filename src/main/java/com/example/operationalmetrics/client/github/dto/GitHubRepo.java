package com.example.operationalmetrics.client.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubRepo(
        Long id,
        String name,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("stargazers_count") Integer stargazersCount,
        @JsonProperty("forks_count") Integer forksCount,
        @JsonProperty("open_issues_count") Integer openIssuesCount,
        Boolean archived,
        Boolean disabled,
        @JsonProperty("pushed_at") Instant pushedAt,
        GitHubLicense license
) {
    public record GitHubLicense(
            String key,
            @JsonProperty("spdx_id") String spdxId,
            String name
    ) {
    }
}
