package com.example.operationalmetrics.service.collector;

import com.example.operationalmetrics.client.depsdev.DepsDevClient;
import com.example.operationalmetrics.client.depsdev.dto.DepsDevProject;
import com.example.operationalmetrics.client.depsdev.dto.DepsDevPurlResponse;
import com.example.operationalmetrics.model.MetricsSource;
import com.example.operationalmetrics.model.PackageId;
import com.example.operationalmetrics.model.PartialMetrics;
import com.example.operationalmetrics.model.RepoUrl;
import com.example.operationalmetrics.service.MetricsCollector;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.Optional;

@ApplicationScoped
public class DepsDevCollector implements MetricsCollector {

    private static final Logger LOG = Logger.getLogger(DepsDevCollector.class);

    private final DepsDevClient depsDevClient;
    private final ObjectMapper objectMapper;

    @Inject
    public DepsDevCollector(@RestClient DepsDevClient depsDevClient, ObjectMapper objectMapper) {
        this.depsDevClient = depsDevClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public MetricsSource source() {
        return MetricsSource.DEPS_DEV;
    }

    @Override
    public boolean requiresRepoUrl() {
        return false;
    }

    @Override
    public boolean supports(PackageId packageId) {
        return true;
    }

    @Override
    public PartialMetrics collect(PackageId packageId, Optional<RepoUrl> repoUrl) {
        var partial = new PartialMetrics();

        // Pass raw values to JAX-RS @PathParam — Quarkus REST Client percent-encodes
        // them exactly once. Manually URL-encoding first produced double-encoding
        // (pkg%253Anpm%252F...) which deps.dev returned HTTP 400 for.
        DepsDevPurlResponse purlResponse;
        try {
            purlResponse = depsDevClient.lookupPurl(packageId.canonical());
        } catch (Exception e) {
            LOG.warnv("deps.dev PURL lookup failed for {0}: {1}", packageId.canonical(), e.getMessage());
            return partial;
        }

        if (purlResponse.advisoryKeys() != null) {
            partial.setAdvisoryCount(purlResponse.advisoryKeys().size());
        }

        String projectId = extractGitHubProjectId(purlResponse);
        if (projectId != null) {
            try {
                RepoUrl resolved = resolveRepoUrl(projectId);
                partial.setRepoUrl(resolved);
            } catch (Exception e) {
                LOG.debugv("Could not parse repo URL from deps.dev project: {0}", projectId);
            }

            try {
                DepsDevProject project = depsDevClient.getProject(projectId);
                mapProjectToPartial(project, partial);
            } catch (Exception e) {
                LOG.debugv("deps.dev project lookup failed for {0}: {1}", projectId, e.getMessage());
            }
        }

        try {
            var dependents = depsDevClient.getDependents(
                    mapPurlTypeToSystem(packageId.purlType()),
                    formatName(packageId),
                    purlResponse.versionKey() != null ? purlResponse.versionKey().version() : "");
            if (dependents != null) {
                partial.setDependentPackagesCount(dependents.dependentCount());
            }
        } catch (Exception e) {
            LOG.debugv("deps.dev dependents lookup failed: {0}", e.getMessage());
        }

        return partial;
    }

    private void mapProjectToPartial(DepsDevProject project, PartialMetrics partial) {
        partial.setStarsCount(project.starsCount());
        partial.setForksCount(project.forksCount());
        partial.setLicense(project.license());

        if (project.openIssuesCount() != null) {
            partial.setOpenIssuesCount(project.openIssuesCount());
        }

        if (project.scorecard() != null) {
            partial.setScorecardOverallScore(project.scorecard().overallScore());
            partial.setScorecardSource(MetricsSource.DEPS_DEV.name());
            if (project.scorecard().checks() != null) {
                try {
                    partial.setScorecardChecks(objectMapper.writeValueAsString(project.scorecard().checks()));
                } catch (JsonProcessingException e) {
                    LOG.warn("Failed to serialize deps.dev scorecard checks", e);
                }
            }
        }

        if (project.ossFuzz() != null) {
            partial.setHasOssFuzz(true);
        }
    }

    private String extractGitHubProjectId(DepsDevPurlResponse response) {
        if (response.relatedProjects() == null) return null;
        return response.relatedProjects().stream()
                .filter(rp -> rp.projectKey() != null && rp.projectKey().id() != null)
                .filter(rp -> rp.projectKey().id().startsWith("github.com/"))
                .map(rp -> rp.projectKey().id())
                .findFirst()
                .orElse(null);
    }

    private RepoUrl resolveRepoUrl(String projectId) {
        String[] parts = projectId.split("/", 3);
        if (parts.length >= 3) {
            return RepoUrl.fromComponents(parts[0], parts[1], parts[2]);
        }
        throw new IllegalArgumentException("Cannot parse project ID: " + projectId);
    }

    private String mapPurlTypeToSystem(String purlType) {
        return switch (purlType.toLowerCase()) {
            case "maven" -> "MAVEN";
            case "npm" -> "NPM";
            case "pypi" -> "PYPI";
            case "golang", "go" -> "GO";
            case "cargo" -> "CARGO";
            case "nuget" -> "NUGET";
            case "gem" -> "RUBYGEMS";
            default -> purlType.toUpperCase();
        };
    }

    private String formatName(PackageId packageId) {
        // Maven on deps.dev uses "groupId:artifactId" as the package name.
        // No URL-encoding here — JAX-RS @PathParam handles that exactly once.
        if ("maven".equals(packageId.purlType()) && packageId.namespace() != null) {
            return packageId.namespace() + ":" + packageId.name();
        }
        return packageId.name();
    }
}
