package com.example.operationalmetrics.client.dependencytrack.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DtComponent(String uuid, String group, String name, String version, String purl, String purlCoordinates) {
}
