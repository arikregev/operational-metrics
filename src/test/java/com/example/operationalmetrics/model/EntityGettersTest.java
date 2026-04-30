package com.example.operationalmetrics.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercise getters/setters of OperationalMetricsEntity and PackageEntity to
 * cover the boilerplate accessor methods.
 */
class EntityGettersTest {

    @Test
    void packageEntityRoundTrip() {
        var e = new PackageEntity();
        var now = Instant.now();
        e.setId(1L);
        e.setPurlType("maven");
        e.setPurlNamespace("com.x");
        e.setPurlName("y");
        e.setPurlCanonical("pkg:maven/com.x/y");
        e.setCreatedAt(now);
        e.setUpdatedAt(now);

        assertThat(e.getId()).isEqualTo(1L);
        assertThat(e.getPurlType()).isEqualTo("maven");
        assertThat(e.getPurlNamespace()).isEqualTo("com.x");
        assertThat(e.getPurlName()).isEqualTo("y");
        assertThat(e.getPurlCanonical()).isEqualTo("pkg:maven/com.x/y");
        assertThat(e.getCreatedAt()).isEqualTo(now);
        assertThat(e.getUpdatedAt()).isEqualTo(now);

        var id = e.toPackageId();
        assertThat(id).isEqualTo(new PackageId("maven", "com.x", "y"));
    }

    @Test
    void operationalMetricsEntityRoundTrip() {
        var e = new OperationalMetricsEntity();
        var now = Instant.now();
        e.setId(1L);
        e.setPackageId(2L);
        e.setPurlType("maven");
        e.setPurlNamespace("ns");
        e.setPurlName("n");
        e.setPurlCanonical("pkg:maven/ns/n");
        e.setRepoUrl("https://github.com/o/r");
        e.setRepoPlatform("github.com");
        e.setRepoOwner("o");
        e.setRepoName("r");
        e.setScorecardOverallScore(7.5f);
        e.setScorecardChecks("[]");
        e.setScorecardDate(now);
        e.setScorecardSource("SCORECARD");
        e.setStarsCount(100);
        e.setForksCount(20);
        e.setDependentReposCount(5L);
        e.setDependentPackagesCount(10L);
        e.setDownloadCount(1000L);
        e.setDownloadPeriod("last-month");
        e.setRankingPercentile(0.1f);
        e.setLastCommitAt(now);
        e.setLastReleaseAt(now);
        e.setCommitFrequency52w("[1]");
        e.setContributorCount(3);
        e.setIsArchived(false);
        e.setIsDeprecated(false);
        e.setCommunityHealthPct(80f);
        e.setAvgIssueCloseTimeDays(2f);
        e.setAvgPrCloseTimeDays(1f);
        e.setPrAuthorsCount(3);
        e.setMergedPrCount(50);
        e.setOpenIssuesCount(7);
        e.setOpenPrCount(2);
        e.setAdvisoryCount(1);
        e.setHasSlsaProvenance(true);
        e.setHasOssFuzz(true);
        e.setMaintainerCount(4);
        e.setLicense("Apache-2.0");
        e.setRawSourceData("{}");
        e.setSourcesUsed(List.of("SCORECARD"));
        e.setFetchedAt(now);
        e.setCreatedAt(now);
        e.setUpdatedAt(now);

        assertThat(e.getId()).isEqualTo(1L);
        assertThat(e.getPackageId()).isEqualTo(2L);
        assertThat(e.getPurlType()).isEqualTo("maven");
        assertThat(e.getPurlNamespace()).isEqualTo("ns");
        assertThat(e.getPurlName()).isEqualTo("n");
        assertThat(e.getPurlCanonical()).isEqualTo("pkg:maven/ns/n");
        assertThat(e.getRepoUrl()).isEqualTo("https://github.com/o/r");
        assertThat(e.getRepoPlatform()).isEqualTo("github.com");
        assertThat(e.getRepoOwner()).isEqualTo("o");
        assertThat(e.getRepoName()).isEqualTo("r");
        assertThat(e.getScorecardOverallScore()).isEqualTo(7.5f);
        assertThat(e.getScorecardChecks()).isEqualTo("[]");
        assertThat(e.getScorecardDate()).isEqualTo(now);
        assertThat(e.getScorecardSource()).isEqualTo("SCORECARD");
        assertThat(e.getStarsCount()).isEqualTo(100);
        assertThat(e.getForksCount()).isEqualTo(20);
        assertThat(e.getDependentReposCount()).isEqualTo(5L);
        assertThat(e.getDependentPackagesCount()).isEqualTo(10L);
        assertThat(e.getDownloadCount()).isEqualTo(1000L);
        assertThat(e.getDownloadPeriod()).isEqualTo("last-month");
        assertThat(e.getRankingPercentile()).isEqualTo(0.1f);
        assertThat(e.getLastCommitAt()).isEqualTo(now);
        assertThat(e.getLastReleaseAt()).isEqualTo(now);
        assertThat(e.getCommitFrequency52w()).isEqualTo("[1]");
        assertThat(e.getContributorCount()).isEqualTo(3);
        assertThat(e.getIsArchived()).isFalse();
        assertThat(e.getIsDeprecated()).isFalse();
        assertThat(e.getCommunityHealthPct()).isEqualTo(80f);
        assertThat(e.getAvgIssueCloseTimeDays()).isEqualTo(2f);
        assertThat(e.getAvgPrCloseTimeDays()).isEqualTo(1f);
        assertThat(e.getPrAuthorsCount()).isEqualTo(3);
        assertThat(e.getMergedPrCount()).isEqualTo(50);
        assertThat(e.getOpenIssuesCount()).isEqualTo(7);
        assertThat(e.getOpenPrCount()).isEqualTo(2);
        assertThat(e.getAdvisoryCount()).isEqualTo(1);
        assertThat(e.getHasSlsaProvenance()).isTrue();
        assertThat(e.getHasOssFuzz()).isTrue();
        assertThat(e.getMaintainerCount()).isEqualTo(4);
        assertThat(e.getLicense()).isEqualTo("Apache-2.0");
        assertThat(e.getRawSourceData()).isEqualTo("{}");
        assertThat(e.getSourcesUsed()).containsExactly("SCORECARD");
        assertThat(e.getFetchedAt()).isEqualTo(now);
        assertThat(e.getCreatedAt()).isEqualTo(now);
        assertThat(e.getUpdatedAt()).isEqualTo(now);
    }
}
