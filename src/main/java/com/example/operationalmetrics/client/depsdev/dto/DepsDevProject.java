package com.example.operationalmetrics.client.depsdev.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DepsDevProject(
        DepsDevPurlResponse.DepsDevProjectKey projectKey,
        Integer openIssuesCount,
        Integer starsCount,
        Integer forksCount,
        String license,
        String description,
        String homepage,
        DepsDevScorecard scorecard,
        DepsDevOssFuzz ossFuzz
) {
    public record DepsDevScorecard(Float overallScore, List<DepsDevScorecardCheck> checks, String date) {
    }

    public record DepsDevScorecardCheck(String name, Integer score, String reason) {
    }

    public record DepsDevOssFuzz(Integer lineCount, Integer lineCoverCount) {
    }
}
