package com.example.operationalmetrics.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PartialMetricsTest {

    @Test
    void mergeFromFillsNullFieldsOnly() {
        var first = new PartialMetrics();
        first.setRankingPercentile(0.1f);
        first.setScorecardOverallScore(7.5f);

        var second = new PartialMetrics();
        second.setRankingPercentile(0.9f);  // ignored — first already has it
        second.setContributorCount(50);      // wins — first didn't set it
        second.setScorecardOverallScore(2.0f); // ignored

        first.mergeFrom(second);

        assertThat(first.getRankingPercentile()).isEqualTo(0.1f);
        assertThat(first.getContributorCount()).isEqualTo(50);
        assertThat(first.getScorecardOverallScore()).isEqualTo(7.5f);
    }

    @Test
    void mergeFromHandlesNull() {
        var partial = new PartialMetrics();
        partial.setRankingPercentile(0.5f);
        partial.mergeFrom(null);
        assertThat(partial.getRankingPercentile()).isEqualTo(0.5f);
    }

    @Test
    void mergeFromCoversAllFields() {
        var target = new PartialMetrics();
        var source = new PartialMetrics();

        var repoUrl = RepoUrl.fromComponents("github.com", "o", "r");
        var now = Instant.now();

        source.setRepoUrl(repoUrl);
        source.setScorecardOverallScore(8.0f);
        source.setScorecardChecks("[]");
        source.setScorecardDate(now);
        source.setScorecardSource("SCORECARD");
        source.setRankingPercentile(0.5f);
        source.setLastCommitAt(now);
        source.setLastReleaseAt(now);
        source.setLastReleaseVersion("1.2.3");
        source.setLastReleaseVersionSource("ECOSYSTEMS");
        source.setFirstReleaseAt(now);
        source.setCommitFrequency52w("[1,2,3]");
        source.setContributorCount(7);
        source.setIsArchived(false);
        source.setIsDeprecated(false);
        source.setSnykRating("Healthy");
        source.setCommunityHealthPct(80f);
        source.setAvgIssueCloseTimeDays(2.5f);
        source.setAvgPrCloseTimeDays(1.5f);
        source.setAdvisoryCount(0);

        target.mergeFrom(source);

        assertThat(target.getRepoUrl()).isEqualTo(repoUrl);
        assertThat(target.getScorecardOverallScore()).isEqualTo(8.0f);
        assertThat(target.getScorecardChecks()).isEqualTo("[]");
        assertThat(target.getScorecardDate()).isEqualTo(now);
        assertThat(target.getScorecardSource()).isEqualTo("SCORECARD");
        assertThat(target.getRankingPercentile()).isEqualTo(0.5f);
        assertThat(target.getLastCommitAt()).isEqualTo(now);
        assertThat(target.getLastReleaseAt()).isEqualTo(now);
        assertThat(target.getLastReleaseVersion()).isEqualTo("1.2.3");
        assertThat(target.getLastReleaseVersionSource()).isEqualTo("ECOSYSTEMS");
        assertThat(target.getFirstReleaseAt()).isEqualTo(now);
        assertThat(target.getCommitFrequency52w()).isEqualTo("[1,2,3]");
        assertThat(target.getContributorCount()).isEqualTo(7);
        assertThat(target.getIsArchived()).isFalse();
        assertThat(target.getIsDeprecated()).isFalse();
        assertThat(target.getSnykRating()).isEqualTo("Healthy");
        assertThat(target.getCommunityHealthPct()).isEqualTo(80f);
        assertThat(target.getAvgIssueCloseTimeDays()).isEqualTo(2.5f);
        assertThat(target.getAvgPrCloseTimeDays()).isEqualTo(1.5f);
        assertThat(target.getAdvisoryCount()).isEqualTo(0);
    }

    @Test
    void priorityWinsForAllFields() {
        var hi = populated(1);
        var lo = populated(2);

        hi.mergeFrom(lo);

        // Every field should retain hi's values
        assertThat(hi.getRankingPercentile()).isEqualTo(1.0f);
        assertThat(hi.getContributorCount()).isEqualTo(1);
        assertThat(hi.getCommunityHealthPct()).isEqualTo(1.0f);
        assertThat(hi.getAdvisoryCount()).isEqualTo(1);
        assertThat(hi.getLastReleaseVersion()).isEqualTo("v1");
        assertThat(hi.getSnykRating()).isEqualTo("R1");
    }

    private PartialMetrics populated(int v) {
        var p = new PartialMetrics();
        p.setRepoUrl(RepoUrl.fromComponents("github.com", "o" + v, "r" + v));
        p.setScorecardOverallScore((float) v);
        p.setScorecardChecks("[" + v + "]");
        p.setScorecardDate(Instant.now());
        p.setScorecardSource("S" + v);
        p.setRankingPercentile((float) v);
        p.setLastCommitAt(Instant.now());
        p.setLastReleaseAt(Instant.now());
        p.setLastReleaseVersion("v" + v);
        p.setLastReleaseVersionSource("VSRC" + v);
        p.setFirstReleaseAt(Instant.now());
        p.setCommitFrequency52w("[" + v + "]");
        p.setContributorCount(v);
        p.setIsArchived(v == 1);
        p.setIsDeprecated(v == 1);
        p.setSnykRating("R" + v);
        p.setCommunityHealthPct((float) v);
        p.setAvgIssueCloseTimeDays((float) v);
        p.setAvgPrCloseTimeDays((float) v);
        p.setAdvisoryCount(v);
        return p;
    }
}
