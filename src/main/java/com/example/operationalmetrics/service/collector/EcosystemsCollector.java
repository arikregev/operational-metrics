package com.example.operationalmetrics.service.collector;

import com.example.operationalmetrics.client.ecosystems.EcosystemsClient;
import com.example.operationalmetrics.client.ecosystems.dto.EcosystemsPackage;
import com.example.operationalmetrics.model.MetricsSource;
import com.example.operationalmetrics.model.PackageId;
import com.example.operationalmetrics.model.PartialMetrics;
import com.example.operationalmetrics.model.RepoUrl;
import com.example.operationalmetrics.service.MetricsCollector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class EcosystemsCollector implements MetricsCollector {

    private static final Logger LOG = Logger.getLogger(EcosystemsCollector.class);

    private final EcosystemsClient ecosystemsClient;

    @Inject
    public EcosystemsCollector(@RestClient EcosystemsClient ecosystemsClient) {
        this.ecosystemsClient = ecosystemsClient;
    }

    @Override
    public MetricsSource source() {
        return MetricsSource.ECOSYSTEMS;
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

        List<EcosystemsPackage> results;
        try {
            results = ecosystemsClient.lookupByPurl(packageId.canonical());
        } catch (Exception e) {
            LOG.warnv("ecosyste.ms lookup failed for {0}: {1}", packageId.canonical(), e.getMessage());
            return partial;
        }

        if (results == null || results.isEmpty()) {
            return partial;
        }

        EcosystemsPackage pkg = results.getFirst();
        mapPackageToPartial(pkg, partial);

        return partial;
    }

    private void mapPackageToPartial(EcosystemsPackage pkg, PartialMetrics partial) {
        if (pkg.getRepositoryUrl() != null && !pkg.getRepositoryUrl().isBlank()) {
            try {
                partial.setRepoUrl(RepoUrl.parse(pkg.getRepositoryUrl()));
            } catch (Exception e) {
                LOG.debugv("Could not parse ecosyste.ms repository URL: {0}", pkg.getRepositoryUrl());
            }
        }

        partial.setDownloadCount(pkg.getDownloads());
        partial.setDownloadPeriod(pkg.getDownloadsPeriod());
        partial.setDependentReposCount(pkg.getDependentReposCount());
        partial.setDependentPackagesCount(pkg.getDependentPackagesCount());
        partial.setLicense(pkg.getLicenses());
        partial.setMaintainerCount(null);

        if (pkg.getLatestReleasePublishedAt() != null) {
            partial.setLastReleaseAt(pkg.getLatestReleasePublishedAt());
        }

        if (pkg.getRankings() != null) {
            partial.setRankingPercentile(pkg.getRankings().getAverage());
        }

        if (pkg.getIssueMetadata() != null) {
            var im = pkg.getIssueMetadata();
            if (im.getAvgTimeToCloseIssue() != null) {
                partial.setAvgIssueCloseTimeDays(im.getAvgTimeToCloseIssue() / 86400f);
            }
            if (im.getAvgTimeToClosePullRequest() != null) {
                partial.setAvgPrCloseTimeDays(im.getAvgTimeToClosePullRequest() / 86400f);
            }
            partial.setPrAuthorsCount(im.getPullRequestAuthorsCount());
            partial.setMergedPrCount(im.getMergedPullRequestsCount());
            partial.setOpenIssuesCount(im.getIssuesCount());
            partial.setOpenPrCount(im.getPullRequestsCount());
        }

        if (pkg.getRepoMetadata() != null) {
            var rm = pkg.getRepoMetadata();
            partial.setStarsCount(rm.getStargazersCount());
            partial.setForksCount(rm.getForksCount());
            partial.setIsArchived(rm.getArchived());
            if (rm.getPushedAt() != null) {
                partial.setLastCommitAt(rm.getPushedAt());
            }
        }
    }
}
