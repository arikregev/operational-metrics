package com.example.operationalmetrics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MetricsResponse(
    String purl,
    String purlType,
    String purlNamespace,
    String purlName,
    String repoUrl,
    ScorecardInfo scorecard,
    PopularityInfo popularity,
    ActivityInfo activity,
    CommunityInfo community,
    SecurityInfo security,
    DependentsInfo dependents,
    Integer maintainerCount,
    String license,
    List<String> sourcesUsed,
    VersionInfo versionInfo,
    Instant fetchedAt
) {
    public record ScorecardInfo(Float score, String checks, Instant date) {}
    public record PopularityInfo(Integer stars, Integer forks, Long downloads, Float rankingPercentile) {}
    public record ActivityInfo(
        Instant lastCommit,
        Instant lastRelease,
        String lastReleaseVersion,
        String lastReleaseVersionSource,
        Instant firstRelease,
        Integer contributorCount,
        Boolean archived,
        Boolean deprecated,
        String snykRating
    ) {}
    public record CommunityInfo(Float healthPct, Float avgIssueCloseTimeDays, Float avgPrCloseTimeDays, Integer prAuthorsCount, Integer mergedPrCount) {}
    public record SecurityInfo(Integer advisoryCount, Boolean slsaProvenance, Boolean ossFuzz) {}
    public record DependentsInfo(Long repos, Long packages) {}

    /**
     * Per-version comparison info. Populated when the request PURL contains a
     * version segment (e.g., {@code pkg:maven/log4j-core@2.17.0}). Surfaces the
     * release date of the requested version and how stale it is relative to
     * the latest known version.
     */
    public record VersionInfo(
        String version,
        Instant releasedAt,
        String resolvedVia,
        Long daysSinceRelease,
        Long daysOlderThanLatest
    ) {}
}
