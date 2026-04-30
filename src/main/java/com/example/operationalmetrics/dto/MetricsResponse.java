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
    Instant fetchedAt
) {
    public record ScorecardInfo(Float score, String checks, Instant date) {}
    public record PopularityInfo(Integer stars, Integer forks, Long downloads, Float rankingPercentile) {}
    public record ActivityInfo(Instant lastCommit, Instant lastRelease, Integer contributorCount, Boolean archived, Boolean deprecated) {}
    public record CommunityInfo(Float healthPct, Float avgIssueCloseTimeDays, Float avgPrCloseTimeDays, Integer prAuthorsCount, Integer mergedPrCount) {}
    public record SecurityInfo(Integer advisoryCount, Boolean slsaProvenance, Boolean ossFuzz) {}
    public record DependentsInfo(Long repos, Long packages) {}
}
