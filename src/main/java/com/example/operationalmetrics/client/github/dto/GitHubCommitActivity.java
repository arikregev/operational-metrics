package com.example.operationalmetrics.client.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubCommitActivity(List<Integer> days, Integer total, Long week) {
}
