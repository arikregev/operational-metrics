package com.example.operationalmetrics.client.snyk.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SnykPackageHealth(
        Community community,
        Maintenance maintenance,
        Popularity popularity,
        Security security,
        @JsonProperty("overall_rating") String overallRating
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Community(
            String description,
            @JsonProperty("has_code_of_conduct_file") Boolean hasCodeOfConductFile,
            @JsonProperty("has_contributing_file") Boolean hasContributingFile,
            @JsonProperty("has_funding_file") Boolean hasFundingFile,
            @JsonProperty("has_readme_file") Boolean hasReadmeFile,
            String rating,
            @JsonProperty("stargazers_count") Integer stargazersCount
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Maintenance(
            String description,
            @JsonProperty("first_release_published_at") Instant firstReleasePublishedAt,
            @JsonProperty("forks_count") Integer forksCount,
            @JsonProperty("is_archived") Boolean isArchived,
            @JsonProperty("is_forked") Boolean isForked,
            @JsonProperty("latest_release_number") String latestReleaseNumber,
            @JsonProperty("latest_release_published_at") Instant latestReleasePublishedAt,
            String lifecycle,
            String rating,
            @JsonProperty("total_versions_count") Integer totalVersionsCount
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Popularity(
            @JsonProperty("dependent_packages_count") Long dependentPackagesCount,
            @JsonProperty("dependent_repos_count") Long dependentReposCount,
            String description,
            Long downloads,
            String rating
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Security(
            String description,
            @JsonProperty("direct_vulnerabilities") Boolean directVulnerabilities,
            @JsonProperty("direct_vulnerabilities_total") Integer directVulnerabilitiesTotal,
            String rating
    ) {
    }
}
