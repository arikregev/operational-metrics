package com.example.operationalmetrics.client.dependencytrack.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DtProject(String uuid, String name, String version, String purl) {
}
