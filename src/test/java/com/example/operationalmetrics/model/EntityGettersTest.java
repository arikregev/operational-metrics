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
        e.setRankingPercentile(0.1f);
        e.setLastCommitAt(now);
        e.setLastReleaseAt(now);
        e.setLastReleaseVersion("2.0.0");
        e.setLastReleaseVersionSource("ECOSYSTEMS");
        e.setFirstReleaseAt(now);
        e.setCommitFrequency52w("[1]");
        e.setContributorCount(3);
        e.setIsArchived(false);
        e.setIsDeprecated(false);
        e.setSnykRating("Healthy");
        e.setCommunityHealthPct(80f);
        e.setAvgIssueCloseTimeDays(2f);
        e.setAvgPrCloseTimeDays(1f);
        e.setAdvisoryCount(1);
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
        assertThat(e.getRankingPercentile()).isEqualTo(0.1f);
        assertThat(e.getLastCommitAt()).isEqualTo(now);
        assertThat(e.getLastReleaseAt()).isEqualTo(now);
        assertThat(e.getLastReleaseVersion()).isEqualTo("2.0.0");
        assertThat(e.getLastReleaseVersionSource()).isEqualTo("ECOSYSTEMS");
        assertThat(e.getFirstReleaseAt()).isEqualTo(now);
        assertThat(e.getCommitFrequency52w()).isEqualTo("[1]");
        assertThat(e.getContributorCount()).isEqualTo(3);
        assertThat(e.getIsArchived()).isFalse();
        assertThat(e.getIsDeprecated()).isFalse();
        assertThat(e.getSnykRating()).isEqualTo("Healthy");
        assertThat(e.getCommunityHealthPct()).isEqualTo(80f);
        assertThat(e.getAvgIssueCloseTimeDays()).isEqualTo(2f);
        assertThat(e.getAvgPrCloseTimeDays()).isEqualTo(1f);
        assertThat(e.getAdvisoryCount()).isEqualTo(1);
        assertThat(e.getSourcesUsed()).containsExactly("SCORECARD");
        assertThat(e.getFetchedAt()).isEqualTo(now);
        assertThat(e.getCreatedAt()).isEqualTo(now);
        assertThat(e.getUpdatedAt()).isEqualTo(now);
    }
}
