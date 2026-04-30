package com.example.operationalmetrics.client.depsdev.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DepsDevPurlResponse(
        DepsDevVersionKey versionKey,
        String purl,
        List<DepsDevRelatedProject> relatedProjects,
        List<DepsDevAdvisoryKey> advisoryKeys
) {
    public record DepsDevVersionKey(String system, String name, String version) {
    }

    public record DepsDevRelatedProject(DepsDevProjectKey projectKey, String relationType, String relationProvenance) {
    }

    public record DepsDevProjectKey(String id) {
    }

    public record DepsDevAdvisoryKey(String id) {
    }
}
