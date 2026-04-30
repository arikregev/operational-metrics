package com.example.operationalmetrics.client.scorecard.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScorecardResult(
        String date,
        ScorecardRepo repo,
        ScorecardVersion scorecard,
        Float score,
        List<ScorecardCheck> checks
) {
    public record ScorecardRepo(String name, String commit) {
    }

    public record ScorecardVersion(String version, String commit) {
    }
}
