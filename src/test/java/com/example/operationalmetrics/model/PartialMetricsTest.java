package com.example.operationalmetrics.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PartialMetricsTest {

    @Test
    void mergeFromFillsNullFieldsOnly() {
        var first = new PartialMetrics();
        first.setStarsCount(100);
        first.setScorecardOverallScore(7.5f);

        var second = new PartialMetrics();
        second.setStarsCount(999);  // should be ignored — first already has it
        second.setForksCount(50);   // should win — first didn't set it
        second.setScorecardOverallScore(2.0f); // ignored

        first.mergeFrom(second);

        assertThat(first.getStarsCount()).isEqualTo(100);
        assertThat(first.getForksCount()).isEqualTo(50);
        assertThat(first.getScorecardOverallScore()).isEqualTo(7.5f);
    }

    @Test
    void mergeFromHandlesNull() {
        var partial = new PartialMetrics();
        partial.setStarsCount(10);
        partial.mergeFrom(null);
        assertThat(partial.getStarsCount()).isEqualTo(10);
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
        source.setStarsCount(100);
        source.setForksCount(20);
        source.setDependentReposCount(5L);
        source.setDependentPackagesCount(15L);
        source.setDownloadCount(1000L);
        source.setDownloadPeriod("last-month");
        source.setRankingPercentile(0.5f);
        source.setLastCommitAt(now);
        source.setLastReleaseAt(now);
        source.setCommitFrequency52w("[1,2,3]");
        source.setContributorCount(7);
        source.setIsArchived(false);
        source.setIsDeprecated(false);
        source.setCommunityHealthPct(80f);
        source.setAvgIssueCloseTimeDays(2.5f);
        source.setAvgPrCloseTimeDays(1.5f);
        source.setPrAuthorsCount(3);
        source.setMergedPrCount(40);
        source.setOpenIssuesCount(5);
        source.setOpenPrCount(2);
        source.setAdvisoryCount(0);
        source.setHasSlsaProvenance(true);
        source.setHasOssFuzz(true);
        source.setMaintainerCount(4);
        source.setLicense("Apache-2.0");

        target.mergeFrom(source);

        assertThat(target.getRepoUrl()).isEqualTo(repoUrl);
        assertThat(target.getScorecardOverallScore()).isEqualTo(8.0f);
        assertThat(target.getScorecardChecks()).isEqualTo("[]");
        assertThat(target.getScorecardDate()).isEqualTo(now);
        assertThat(target.getScorecardSource()).isEqualTo("SCORECARD");
        assertThat(target.getStarsCount()).isEqualTo(100);
        assertThat(target.getForksCount()).isEqualTo(20);
        assertThat(target.getDependentReposCount()).isEqualTo(5L);
        assertThat(target.getDependentPackagesCount()).isEqualTo(15L);
        assertThat(target.getDownloadCount()).isEqualTo(1000L);
        assertThat(target.getDownloadPeriod()).isEqualTo("last-month");
        assertThat(target.getRankingPercentile()).isEqualTo(0.5f);
        assertThat(target.getLastCommitAt()).isEqualTo(now);
        assertThat(target.getLastReleaseAt()).isEqualTo(now);
        assertThat(target.getCommitFrequency52w()).isEqualTo("[1,2,3]");
        assertThat(target.getContributorCount()).isEqualTo(7);
        assertThat(target.getIsArchived()).isFalse();
        assertThat(target.getIsDeprecated()).isFalse();
        assertThat(target.getCommunityHealthPct()).isEqualTo(80f);
        assertThat(target.getAvgIssueCloseTimeDays()).isEqualTo(2.5f);
        assertThat(target.getAvgPrCloseTimeDays()).isEqualTo(1.5f);
        assertThat(target.getPrAuthorsCount()).isEqualTo(3);
        assertThat(target.getMergedPrCount()).isEqualTo(40);
        assertThat(target.getOpenIssuesCount()).isEqualTo(5);
        assertThat(target.getOpenPrCount()).isEqualTo(2);
        assertThat(target.getAdvisoryCount()).isEqualTo(0);
        assertThat(target.getHasSlsaProvenance()).isTrue();
        assertThat(target.getHasOssFuzz()).isTrue();
        assertThat(target.getMaintainerCount()).isEqualTo(4);
        assertThat(target.getLicense()).isEqualTo("Apache-2.0");
    }

    @Test
    void priorityWinsForAllFields() {
        var hi = populated(1);
        var lo = populated(2);

        hi.mergeFrom(lo);

        // Every field should retain hi's values
        assertThat(hi.getStarsCount()).isEqualTo(1);
        assertThat(hi.getForksCount()).isEqualTo(1);
        assertThat(hi.getDependentReposCount()).isEqualTo(1L);
        assertThat(hi.getDownloadCount()).isEqualTo(1L);
        assertThat(hi.getMaintainerCount()).isEqualTo(1);
        assertThat(hi.getLicense()).isEqualTo("L1");
    }

    private PartialMetrics populated(int v) {
        var p = new PartialMetrics();
        p.setRepoUrl(RepoUrl.fromComponents("github.com", "o" + v, "r" + v));
        p.setScorecardOverallScore((float) v);
        p.setScorecardChecks("[" + v + "]");
        p.setScorecardDate(Instant.now());
        p.setScorecardSource("S" + v);
        p.setStarsCount(v);
        p.setForksCount(v);
        p.setDependentReposCount((long) v);
        p.setDependentPackagesCount((long) v);
        p.setDownloadCount((long) v);
        p.setDownloadPeriod("p" + v);
        p.setRankingPercentile((float) v);
        p.setLastCommitAt(Instant.now());
        p.setLastReleaseAt(Instant.now());
        p.setCommitFrequency52w("[" + v + "]");
        p.setContributorCount(v);
        p.setIsArchived(v == 1);
        p.setIsDeprecated(v == 1);
        p.setCommunityHealthPct((float) v);
        p.setAvgIssueCloseTimeDays((float) v);
        p.setAvgPrCloseTimeDays((float) v);
        p.setPrAuthorsCount(v);
        p.setMergedPrCount(v);
        p.setOpenIssuesCount(v);
        p.setOpenPrCount(v);
        p.setAdvisoryCount(v);
        p.setHasSlsaProvenance(v == 1);
        p.setHasOssFuzz(v == 1);
        p.setMaintainerCount(v);
        p.setLicense("L" + v);
        return p;
    }
}
