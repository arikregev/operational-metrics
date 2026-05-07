package com.example.operationalmetrics.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class PartialMetrics {

    private RepoUrl repoUrl;

    // OpenSSF Scorecard
    private Float scorecardOverallScore;
    private String scorecardChecks; // JSON string
    private Instant scorecardDate;
    private String scorecardSource;

    // Popularity
    private Integer starsCount;
    private Integer forksCount;
    private Long dependentReposCount;
    private Long dependentPackagesCount;
    private Long downloadCount;
    private String downloadPeriod;
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
    private Integer prAuthorsCount;
    private Integer mergedPrCount;
    private Integer openIssuesCount;
    private Integer openPrCount;

    // Security
    private Integer advisoryCount;
    private Boolean hasSlsaProvenance;
    private Boolean hasOssFuzz;

    // Maintainer
    private Integer maintainerCount;

    // License
    private String license;

    public void mergeFrom(PartialMetrics other) {
        if (other == null) return;

        if (this.repoUrl == null) this.repoUrl = other.repoUrl;
        if (this.scorecardOverallScore == null) this.scorecardOverallScore = other.scorecardOverallScore;
        if (this.scorecardChecks == null) this.scorecardChecks = other.scorecardChecks;
        if (this.scorecardDate == null) this.scorecardDate = other.scorecardDate;
        if (this.scorecardSource == null) this.scorecardSource = other.scorecardSource;
        if (this.starsCount == null) this.starsCount = other.starsCount;
        if (this.forksCount == null) this.forksCount = other.forksCount;
        if (this.dependentReposCount == null) this.dependentReposCount = other.dependentReposCount;
        if (this.dependentPackagesCount == null) this.dependentPackagesCount = other.dependentPackagesCount;
        if (this.downloadCount == null) this.downloadCount = other.downloadCount;
        if (this.downloadPeriod == null) this.downloadPeriod = other.downloadPeriod;
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
        if (this.prAuthorsCount == null) this.prAuthorsCount = other.prAuthorsCount;
        if (this.mergedPrCount == null) this.mergedPrCount = other.mergedPrCount;
        if (this.openIssuesCount == null) this.openIssuesCount = other.openIssuesCount;
        if (this.openPrCount == null) this.openPrCount = other.openPrCount;
        if (this.advisoryCount == null) this.advisoryCount = other.advisoryCount;
        if (this.hasSlsaProvenance == null) this.hasSlsaProvenance = other.hasSlsaProvenance;
        if (this.hasOssFuzz == null) this.hasOssFuzz = other.hasOssFuzz;
        if (this.maintainerCount == null) this.maintainerCount = other.maintainerCount;
        if (this.license == null) this.license = other.license;
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

    public Integer getStarsCount() { return starsCount; }
    public void setStarsCount(Integer starsCount) { this.starsCount = starsCount; }

    public Integer getForksCount() { return forksCount; }
    public void setForksCount(Integer forksCount) { this.forksCount = forksCount; }

    public Long getDependentReposCount() { return dependentReposCount; }
    public void setDependentReposCount(Long dependentReposCount) { this.dependentReposCount = dependentReposCount; }

    public Long getDependentPackagesCount() { return dependentPackagesCount; }
    public void setDependentPackagesCount(Long dependentPackagesCount) { this.dependentPackagesCount = dependentPackagesCount; }

    public Long getDownloadCount() { return downloadCount; }
    public void setDownloadCount(Long downloadCount) { this.downloadCount = downloadCount; }

    public String getDownloadPeriod() { return downloadPeriod; }
    public void setDownloadPeriod(String downloadPeriod) { this.downloadPeriod = downloadPeriod; }

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

    public Integer getPrAuthorsCount() { return prAuthorsCount; }
    public void setPrAuthorsCount(Integer prAuthorsCount) { this.prAuthorsCount = prAuthorsCount; }

    public Integer getMergedPrCount() { return mergedPrCount; }
    public void setMergedPrCount(Integer mergedPrCount) { this.mergedPrCount = mergedPrCount; }

    public Integer getOpenIssuesCount() { return openIssuesCount; }
    public void setOpenIssuesCount(Integer openIssuesCount) { this.openIssuesCount = openIssuesCount; }

    public Integer getOpenPrCount() { return openPrCount; }
    public void setOpenPrCount(Integer openPrCount) { this.openPrCount = openPrCount; }

    public Integer getAdvisoryCount() { return advisoryCount; }
    public void setAdvisoryCount(Integer advisoryCount) { this.advisoryCount = advisoryCount; }

    public Boolean getHasSlsaProvenance() { return hasSlsaProvenance; }
    public void setHasSlsaProvenance(Boolean hasSlsaProvenance) { this.hasSlsaProvenance = hasSlsaProvenance; }

    public Boolean getHasOssFuzz() { return hasOssFuzz; }
    public void setHasOssFuzz(Boolean hasOssFuzz) { this.hasOssFuzz = hasOssFuzz; }

    public Integer getMaintainerCount() { return maintainerCount; }
    public void setMaintainerCount(Integer maintainerCount) { this.maintainerCount = maintainerCount; }

    public String getLicense() { return license; }
    public void setLicense(String license) { this.license = license; }
}
