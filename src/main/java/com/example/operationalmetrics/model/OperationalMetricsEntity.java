package com.example.operationalmetrics.model;

import java.time.Instant;
import java.util.List;

public class OperationalMetricsEntity {

    private Long id;
    private Long packageId;

    // Package info (joined from package table)
    private String purlType;
    private String purlNamespace;
    private String purlName;
    private String purlCanonical;

    // Repository
    private String repoUrl;
    private String repoPlatform;
    private String repoOwner;
    private String repoName;

    // Scorecard
    private Float scorecardOverallScore;
    private String scorecardChecks;
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
    private String commitFrequency52w;
    private Integer contributorCount;
    private Boolean isArchived;
    private Boolean isDeprecated;
    private String snykRating;

    // Community
    private Float communityHealthPct;
    private Float avgIssueCloseTimeDays;
    private Float avgPrCloseTimeDays;

    // Security
    private Integer advisoryCount;

    // Meta
    private List<String> sourcesUsed;
    private Instant fetchedAt;
    private Instant createdAt;
    private Instant updatedAt;

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }

    public String getPurlType() { return purlType; }
    public void setPurlType(String purlType) { this.purlType = purlType; }

    public String getPurlNamespace() { return purlNamespace; }
    public void setPurlNamespace(String purlNamespace) { this.purlNamespace = purlNamespace; }

    public String getPurlName() { return purlName; }
    public void setPurlName(String purlName) { this.purlName = purlName; }

    public String getPurlCanonical() { return purlCanonical; }
    public void setPurlCanonical(String purlCanonical) { this.purlCanonical = purlCanonical; }

    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }

    public String getRepoPlatform() { return repoPlatform; }
    public void setRepoPlatform(String repoPlatform) { this.repoPlatform = repoPlatform; }

    public String getRepoOwner() { return repoOwner; }
    public void setRepoOwner(String repoOwner) { this.repoOwner = repoOwner; }

    public String getRepoName() { return repoName; }
    public void setRepoName(String repoName) { this.repoName = repoName; }

    public Float getScorecardOverallScore() { return scorecardOverallScore; }
    public void setScorecardOverallScore(Float v) { this.scorecardOverallScore = v; }

    public String getScorecardChecks() { return scorecardChecks; }
    public void setScorecardChecks(String v) { this.scorecardChecks = v; }

    public Instant getScorecardDate() { return scorecardDate; }
    public void setScorecardDate(Instant v) { this.scorecardDate = v; }

    public String getScorecardSource() { return scorecardSource; }
    public void setScorecardSource(String v) { this.scorecardSource = v; }

    public Float getRankingPercentile() { return rankingPercentile; }
    public void setRankingPercentile(Float v) { this.rankingPercentile = v; }

    public Instant getLastCommitAt() { return lastCommitAt; }
    public void setLastCommitAt(Instant v) { this.lastCommitAt = v; }

    public Instant getLastReleaseAt() { return lastReleaseAt; }
    public void setLastReleaseAt(Instant v) { this.lastReleaseAt = v; }

    public String getLastReleaseVersion() { return lastReleaseVersion; }
    public void setLastReleaseVersion(String v) { this.lastReleaseVersion = v; }

    public String getLastReleaseVersionSource() { return lastReleaseVersionSource; }
    public void setLastReleaseVersionSource(String v) { this.lastReleaseVersionSource = v; }

    public Instant getFirstReleaseAt() { return firstReleaseAt; }
    public void setFirstReleaseAt(Instant v) { this.firstReleaseAt = v; }

    public String getCommitFrequency52w() { return commitFrequency52w; }
    public void setCommitFrequency52w(String v) { this.commitFrequency52w = v; }

    public Integer getContributorCount() { return contributorCount; }
    public void setContributorCount(Integer v) { this.contributorCount = v; }

    public Boolean getIsArchived() { return isArchived; }
    public void setIsArchived(Boolean v) { this.isArchived = v; }

    public Boolean getIsDeprecated() { return isDeprecated; }
    public void setIsDeprecated(Boolean v) { this.isDeprecated = v; }

    public String getSnykRating() { return snykRating; }
    public void setSnykRating(String v) { this.snykRating = v; }

    public Float getCommunityHealthPct() { return communityHealthPct; }
    public void setCommunityHealthPct(Float v) { this.communityHealthPct = v; }

    public Float getAvgIssueCloseTimeDays() { return avgIssueCloseTimeDays; }
    public void setAvgIssueCloseTimeDays(Float v) { this.avgIssueCloseTimeDays = v; }

    public Float getAvgPrCloseTimeDays() { return avgPrCloseTimeDays; }
    public void setAvgPrCloseTimeDays(Float v) { this.avgPrCloseTimeDays = v; }

    public Integer getAdvisoryCount() { return advisoryCount; }
    public void setAdvisoryCount(Integer v) { this.advisoryCount = v; }

    public List<String> getSourcesUsed() { return sourcesUsed; }
    public void setSourcesUsed(List<String> v) { this.sourcesUsed = v; }

    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant v) { this.fetchedAt = v; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
