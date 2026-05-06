package com.example.operationalmetrics.service.collector;

import com.example.operationalmetrics.client.snyk.SnykClient;
import com.example.operationalmetrics.client.snyk.dto.SnykPackageDetails;
import com.example.operationalmetrics.client.snyk.dto.SnykPackageHealth;
import com.example.operationalmetrics.client.snyk.dto.SnykPackageResponse;
import com.example.operationalmetrics.config.SnykConfig;
import com.example.operationalmetrics.model.MetricsSource;
import com.example.operationalmetrics.model.PackageId;
import com.example.operationalmetrics.model.PartialMetrics;
import com.example.operationalmetrics.model.RepoUrl;
import com.example.operationalmetrics.service.MetricsCollector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.Optional;

@ApplicationScoped
public class SnykCollector implements MetricsCollector {

    private static final Logger LOG = Logger.getLogger(SnykCollector.class);

    private final SnykClient snykClient;
    private final SnykConfig snykConfig;

    @Inject
    public SnykCollector(@RestClient SnykClient snykClient, SnykConfig snykConfig) {
        this.snykClient = snykClient;
        this.snykConfig = snykConfig;
    }

    @Override
    public MetricsSource source() {
        return MetricsSource.SNYK;
    }

    @Override
    public boolean requiresRepoUrl() {
        return false;
    }

    @Override
    public boolean supports(PackageId packageId) {
        // Snyk's documented ecosystems exclude github and gem.
        String type = packageId.purlType();
        if (type == null) {
            return false;
        }
        String t = type.toLowerCase();
        return !"github".equals(t) && !"gem".equals(t);
    }

    @Override
    public PartialMetrics collect(PackageId packageId, Optional<RepoUrl> repoUrl) {
        var partial = new PartialMetrics();

        if (!snykConfig.enabled() || snykConfig.orgId().isEmpty()) {
            return partial;
        }

        String ecosystem = mapPurlTypeToEcosystem(packageId.purlType());
        String packageName = formatPackageName(packageId);

        SnykPackageResponse response;
        try {
            response = snykClient.getPackage(
                    snykConfig.orgId().get(),
                    ecosystem,
                    packageName,
                    snykConfig.apiVersion());
        } catch (Exception e) {
            LOG.warnv("Snyk lookup failed for {0}: {1}", packageId.canonical(), e.getMessage());
            return partial;
        }

        if (response == null || response.data() == null || response.data().attributes() == null) {
            return partial;
        }

        mapAttributesToPartial(response.data().attributes(), partial);
        return partial;
    }

    private void mapAttributesToPartial(SnykPackageResponse.Attributes attrs, PartialMetrics partial) {
        SnykPackageHealth health = attrs.packageHealth();
        if (health != null) {
            mapHealthToPartial(health, partial);
        }

        // latest_version → lastReleaseVersion (fall back to maintenance.latest_release_number)
        String latestVersion = attrs.latestVersion();
        if (latestVersion == null && health != null && health.maintenance() != null) {
            latestVersion = health.maintenance().latestReleaseNumber();
        }
        if (latestVersion != null && !latestVersion.isBlank()) {
            partial.setLastReleaseVersion(latestVersion);
            partial.setLastReleaseVersionSource("SNYK");
        }

        SnykPackageDetails details = attrs.packageDetails();
        if (details != null && details.repositoryUrl() != null && !details.repositoryUrl().isBlank()) {
            try {
                partial.setRepoUrl(RepoUrl.parse(details.repositoryUrl()));
            } catch (Exception e) {
                LOG.debugv("Could not parse Snyk repository URL: {0}", details.repositoryUrl());
            }
        }
    }

    private void mapHealthToPartial(SnykPackageHealth health, PartialMetrics partial) {
        SnykPackageHealth.Community community = health.community();
        if (community != null) {
            partial.setStarsCount(community.stargazersCount());
        }

        SnykPackageHealth.Maintenance maintenance = health.maintenance();
        if (maintenance != null) {
            partial.setForksCount(maintenance.forksCount());
            partial.setLastReleaseAt(maintenance.latestReleasePublishedAt());
            partial.setFirstReleaseAt(maintenance.firstReleasePublishedAt());
            partial.setIsArchived(maintenance.isArchived());
            partial.setSnykRating(maintenance.rating());
        }

        SnykPackageHealth.Popularity popularity = health.popularity();
        if (popularity != null) {
            partial.setDependentReposCount(popularity.dependentReposCount());
            partial.setDependentPackagesCount(popularity.dependentPackagesCount());
            partial.setDownloadCount(popularity.downloads());
        }

        SnykPackageHealth.Security security = health.security();
        if (security != null) {
            partial.setAdvisoryCount(security.directVulnerabilitiesTotal());
        }
    }

    private String mapPurlTypeToEcosystem(String purlType) {
        if (purlType == null) {
            return "";
        }
        String t = purlType.toLowerCase();
        return switch (t) {
            case "maven" -> "maven";
            case "npm" -> "npm";
            case "pypi" -> "pypi";
            case "golang" -> "golang";
            case "cargo" -> "cargo";
            case "nuget" -> "nuget";
            case "composer" -> "composer";
            case "cocoapods" -> "cocoapods";
            case "conan" -> "conan";
            case "deb" -> "deb";
            case "hex" -> "hex";
            case "pub" -> "pub";
            case "rpm" -> "rpm";
            case "swift" -> "swift";
            case "apk" -> "apk";
            default -> t;
        };
    }

    private String formatPackageName(PackageId packageId) {
        String type = packageId.purlType() == null ? "" : packageId.purlType().toLowerCase();
        String namespace = packageId.namespace();
        String name = packageId.name();

        // Pass raw values — JAX-RS @PathParam encodes once. Pre-encoding here
        // would produce double-encoding (mirrors deps.dev fix).
        if (namespace == null || namespace.isBlank()) {
            return name;
        }

        return switch (type) {
            case "maven" -> namespace + ":" + name;
            case "golang", "composer", "npm" -> namespace + "/" + name;
            default -> name;
        };
    }
}
