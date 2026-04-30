package com.example.operationalmetrics.client.scorecard.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScorecardCheck(
        String name,
        Integer score,
        String reason,
        List<String> details,
        ScorecardDocumentation documentation
) {
    public record ScorecardDocumentation(
            @JsonProperty("short") String shortDesc,
            String url
    ) {
    }
}
