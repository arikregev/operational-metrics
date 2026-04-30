package com.example.operationalmetrics.service.collector;

import com.example.operationalmetrics.client.ecosystems.EcosystemsClient;
import com.example.operationalmetrics.client.ecosystems.dto.EcosystemsPackage;
import com.example.operationalmetrics.model.MetricsSource;
import com.example.operationalmetrics.model.PackageId;
import com.example.operationalmetrics.model.PartialMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EcosystemsCollectorTest {

    @Mock
    private EcosystemsClient ecosystemsClient;

    private EcosystemsCollector collector;
    private PackageId packageId;

    @BeforeEach
    void setUp() {
        collector = new EcosystemsCollector(ecosystemsClient);
        packageId = new PackageId("npm", null, "express");
    }

    @Test
    void collect_fullResponse_mapsEverything() {
        EcosystemsPackage pkg = new EcosystemsPackage();
        pkg.setRepositoryUrl("https://github.com/expressjs/express");
        pkg.setDownloads(1_000_000L);
        pkg.setDownloadsPeriod("last-month");
        pkg.setDependentReposCount(5_000L);
        pkg.setDependentPackagesCount(20_000L);
        pkg.setLicenses("MIT");
        Instant releaseAt = Instant.parse("2026-04-01T00:00:00Z");
        pkg.setLatestReleasePublishedAt(releaseAt);

        EcosystemsPackage.EcosystemsRankings rankings = new EcosystemsPackage.EcosystemsRankings();
        rankings.setAverage(95.5f);
        pkg.setRankings(rankings);

        EcosystemsPackage.EcosystemsIssueMetadata issueMd = new EcosystemsPackage.EcosystemsIssueMetadata();
        issueMd.setAvgTimeToCloseIssue(86400f * 7);   // 7 days in seconds
        issueMd.setAvgTimeToClosePullRequest(86400f * 3); // 3 days in seconds
        issueMd.setPullRequestAuthorsCount(45);
        issueMd.setMergedPullRequestsCount(120);
        issueMd.setIssuesCount(15);
        issueMd.setPullRequestsCount(8);
        pkg.setIssueMetadata(issueMd);

        EcosystemsPackage.EcosystemsRepoMetadata repoMd = new EcosystemsPackage.EcosystemsRepoMetadata();
        repoMd.setStargazersCount(60_000);
        repoMd.setForksCount(10_000);
        repoMd.setArchived(false);
        Instant pushedAt = Instant.parse("2026-04-25T12:00:00Z");
        repoMd.setPushedAt(pushedAt);
        pkg.setRepoMetadata(repoMd);

        when(ecosystemsClient.lookupByPurl(packageId.canonical())).thenReturn(List.of(pkg));

        PartialMetrics partial = collector.collect(packageId, Optional.empty());

        assertThat(partial.getRepoUrl()).isNotNull();
        assertThat(partial.getRepoUrl().platform()).isEqualTo("github.com");
        assertThat(partial.getRepoUrl().owner()).isEqualTo("expressjs");
        assertThat(partial.getRepoUrl().name()).isEqualTo("express");

        assertThat(partial.getDownloadCount()).isEqualTo(1_000_000L);
        assertThat(partial.getDownloadPeriod()).isEqualTo("last-month");
        assertThat(partial.getDependentReposCount()).isEqualTo(5_000L);
        assertThat(partial.getDependentPackagesCount()).isEqualTo(20_000L);
        assertThat(partial.getLicense()).isEqualTo("MIT");
        assertThat(partial.getLastReleaseAt()).isEqualTo(releaseAt);
        assertThat(partial.getRankingPercentile()).isEqualTo(95.5f);

        assertThat(partial.getAvgIssueCloseTimeDays()).isEqualTo(7.0f, within(0.001f));
        assertThat(partial.getAvgPrCloseTimeDays()).isEqualTo(3.0f, within(0.001f));
        assertThat(partial.getPrAuthorsCount()).isEqualTo(45);
        assertThat(partial.getMergedPrCount()).isEqualTo(120);
        assertThat(partial.getOpenIssuesCount()).isEqualTo(15);
        assertThat(partial.getOpenPrCount()).isEqualTo(8);

        assertThat(partial.getStarsCount()).isEqualTo(60_000);
        assertThat(partial.getForksCount()).isEqualTo(10_000);
        assertThat(partial.getIsArchived()).isFalse();
        assertThat(partial.getLastCommitAt()).isEqualTo(pushedAt);
    }

    @Test
    void collect_emptyList_returnsEmpty() {
        when(ecosystemsClient.lookupByPurl(anyString())).thenReturn(List.of());

        PartialMetrics partial = collector.collect(packageId, Optional.empty());

        assertThat(partial.getDownloadCount()).isNull();
        assertThat(partial.getStarsCount()).isNull();
        assertThat(partial.getRepoUrl()).isNull();
    }

    @Test
    void collect_clientThrows_returnsEmpty() {
        when(ecosystemsClient.lookupByPurl(anyString())).thenThrow(new RuntimeException("network error"));

        PartialMetrics partial = collector.collect(packageId, Optional.empty());

        assertThat(partial.getDownloadCount()).isNull();
        assertThat(partial.getStarsCount()).isNull();
        assertThat(partial.getRepoUrl()).isNull();
        assertThat(partial.getLicense()).isNull();
    }

    @Test
    void collect_invalidRepositoryUrl_doesNotThrow() {
        EcosystemsPackage pkg = new EcosystemsPackage();
        pkg.setRepositoryUrl("not a url");
        pkg.setDownloads(50L);
        when(ecosystemsClient.lookupByPurl(anyString())).thenReturn(List.of(pkg));

        PartialMetrics partial = collector.collect(packageId, Optional.empty());

        assertThat(partial.getRepoUrl()).isNull();
        // Other fields still populated even though URL parse failed
        assertThat(partial.getDownloadCount()).isEqualTo(50L);
    }

    @Test
    void collect_avgTimeToCloseIssue_convertsSecondsToDays() {
        EcosystemsPackage pkg = new EcosystemsPackage();
        EcosystemsPackage.EcosystemsIssueMetadata issueMd = new EcosystemsPackage.EcosystemsIssueMetadata();
        issueMd.setAvgTimeToCloseIssue(172800f);          // 2 days in seconds
        issueMd.setAvgTimeToClosePullRequest(43200f);     // 0.5 days in seconds
        pkg.setIssueMetadata(issueMd);
        when(ecosystemsClient.lookupByPurl(anyString())).thenReturn(List.of(pkg));

        PartialMetrics partial = collector.collect(packageId, Optional.empty());

        assertThat(partial.getAvgIssueCloseTimeDays()).isEqualTo(2.0f, within(0.0001f));
        assertThat(partial.getAvgPrCloseTimeDays()).isEqualTo(0.5f, within(0.0001f));
    }

    @Test
    void source_returnsEcosystems() {
        assertThat(collector.source()).isEqualTo(MetricsSource.ECOSYSTEMS);
    }

    @Test
    void requiresRepoUrl_returnsFalse() {
        assertThat(collector.requiresRepoUrl()).isFalse();
    }
}
