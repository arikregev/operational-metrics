package com.example.operationalmetrics.model;

import java.time.Instant;

public class PartialMetrics {

    private RepoUrl repoUrl;

    // OpenSSF Scorecard
    private Float scorecardOverallScore;
    private String scorecardChecks; // JSON string
    private Instant scorecardDate;
    private String scorecardSource;

    // Popularity
    private Float rankingPercentile;

    // Activity
    private Instant lastCommitAt;
    private Instant lastReleaseAt;
    private String lastReleaseVersion;
    private String lastReleaseVersionSource;
    private Instant firstReleaseAt;
    private String commitFrequency52w; // JSON string
    private Integer contributorCount;
    private Boolean isArchived;
    private Boolean isDeprecated;
    private String snykRating; // Snyk's package_health.maintenance.rating

    // Community
    private Float communityHealthPct;
    private Float avgIssueCloseTimeDays;
    private Float avgPrCloseTimeDays;

    // Security
    private Integer advisoryCount;

    public void mergeFrom(PartialMetrics other) {
        if (other == null) return;

        if (this.repoUrl == null) this.repoUrl = other.repoUrl;
        if (this.scorecardOverallScore == null) this.scorecardOverallScore = other.scorecardOverallScore;
        if (this.scorecardChecks == null) this.scorecardChecks = other.scorecardChecks;
        if (this.scorecardDate == null) this.scorecardDate = other.scorecardDate;
        if (this.scorecardSource == null) this.scorecardSource = other.scorecardSource;
        if (this.rankingPercentile == null) this.rankingPercentile = other.rankingPercentile;
        if (this.lastCommitAt == null) this.lastCommitAt = other.lastCommitAt;
        if (this.lastReleaseAt == null) this.lastReleaseAt = other.lastReleaseAt;
        if (this.lastReleaseVersion == null) this.lastReleaseVersion = other.lastReleaseVersion;
        if (this.lastReleaseVersionSource == null) this.lastReleaseVersionSource = other.lastReleaseVersionSource;
        if (this.firstReleaseAt == null) this.firstReleaseAt = other.firstReleaseAt;
        if (this.commitFrequency52w == null) this.commitFrequency52w = other.commitFrequency52w;
        if (this.contributorCount == null) this.contributorCount = other.contributorCount;
        if (this.isArchived == null) this.isArchived = other.isArchived;
        if (this.isDeprecated == null) this.isDeprecated = other.isDeprecated;
        if (this.snykRating == null) this.snykRating = other.snykRating;
        if (this.communityHealthPct == null) this.communityHealthPct = other.communityHealthPct;
        if (this.avgIssueCloseTimeDays == null) this.avgIssueCloseTimeDays = other.avgIssueCloseTimeDays;
        if (this.avgPrCloseTimeDays == null) this.avgPrCloseTimeDays = other.avgPrCloseTimeDays;
        if (this.advisoryCount == null) this.advisoryCount = other.advisoryCount;
    }

    // Getters and setters

    public RepoUrl getRepoUrl() { return repoUrl; }
    public void setRepoUrl(RepoUrl repoUrl) { this.repoUrl = repoUrl; }

    public Float getScorecardOverallScore() { return scorecardOverallScore; }
    public void setScorecardOverallScore(Float scorecardOverallScore) { this.scorecardOverallScore = scorecardOverallScore; }

    public String getScorecardChecks() { return scorecardChecks; }
    public void setScorecardChecks(String scorecardChecks) { this.scorecardChecks = scorecardChecks; }

    public Instant getScorecardDate() { return scorecardDate; }
    public void setScorecardDate(Instant scorecardDate) { this.scorecardDate = scorecardDate; }

    public String getScorecardSource() { return scorecardSource; }
    public void setScorecardSource(String scorecardSource) { this.scorecardSource = scorecardSource; }

    public Float getRankingPercentile() { return rankingPercentile; }
    public void setRankingPercentile(Float rankingPercentile) { this.rankingPercentile = rankingPercentile; }

    public Instant getLastCommitAt() { return lastCommitAt; }
    public void setLastCommitAt(Instant lastCommitAt) { this.lastCommitAt = lastCommitAt; }

    public Instant getLastReleaseAt() { return lastReleaseAt; }
    public void setLastReleaseAt(Instant lastReleaseAt) { this.lastReleaseAt = lastReleaseAt; }

    public String getLastReleaseVersion() { return lastReleaseVersion; }
    public void setLastReleaseVersion(String lastReleaseVersion) { this.lastReleaseVersion = lastReleaseVersion; }

    public String getLastReleaseVersionSource() { return lastReleaseVersionSource; }
    public void setLastReleaseVersionSource(String lastReleaseVersionSource) { this.lastReleaseVersionSource = lastReleaseVersionSource; }

    public Instant getFirstReleaseAt() { return firstReleaseAt; }
    public void setFirstReleaseAt(Instant firstReleaseAt) { this.firstReleaseAt = firstReleaseAt; }

    public String getCommitFrequency52w() { return commitFrequency52w; }
    public void setCommitFrequency52w(String commitFrequency52w) { this.commitFrequency52w = commitFrequency52w; }

    public Integer getContributorCount() { return contributorCount; }
    public void setContributorCount(Integer contributorCount) { this.contributorCount = contributorCount; }

    public Boolean getIsArchived() { return isArchived; }
    public void setIsArchived(Boolean isArchived) { this.isArchived = isArchived; }

    public Boolean getIsDeprecated() { return isDeprecated; }
    public void setIsDeprecated(Boolean isDeprecated) { this.isDeprecated = isDeprecated; }

    public String getSnykRating() { return snykRating; }
    public void setSnykRating(String snykRating) { this.snykRating = snykRating; }

    public Float getCommunityHealthPct() { return communityHealthPct; }
    public void setCommunityHealthPct(Float communityHealthPct) { this.communityHealthPct = communityHealthPct; }

    public Float getAvgIssueCloseTimeDays() { return avgIssueCloseTimeDays; }
    public void setAvgIssueCloseTimeDays(Float avgIssueCloseTimeDays) { this.avgIssueCloseTimeDays = avgIssueCloseTimeDays; }

    public Float getAvgPrCloseTimeDays() { return avgPrCloseTimeDays; }
    public void setAvgPrCloseTimeDays(Float avgPrCloseTimeDays) { this.avgPrCloseTimeDays = avgPrCloseTimeDays; }

    public Integer getAdvisoryCount() { return advisoryCount; }
    public void setAdvisoryCount(Integer advisoryCount) { this.advisoryCount = advisoryCount; }
}
