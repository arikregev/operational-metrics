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
    private String commitFrequency52w;
    private Integer contributorCount;
    private Boolean isArchived;
    private Boolean isDeprecated;

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

    // Meta
    private String rawSourceData;
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

    public Integer getStarsCount() { return starsCount; }
    public void setStarsCount(Integer v) { this.starsCount = v; }

    public Integer getForksCount() { return forksCount; }
    public void setForksCount(Integer v) { this.forksCount = v; }

    public Long getDependentReposCount() { return dependentReposCount; }
    public void setDependentReposCount(Long v) { this.dependentReposCount = v; }

    public Long getDependentPackagesCount() { return dependentPackagesCount; }
    public void setDependentPackagesCount(Long v) { this.dependentPackagesCount = v; }

    public Long getDownloadCount() { return downloadCount; }
    public void setDownloadCount(Long v) { this.downloadCount = v; }

    public String getDownloadPeriod() { return downloadPeriod; }
    public void setDownloadPeriod(String v) { this.downloadPeriod = v; }

    public Float getRankingPercentile() { return rankingPercentile; }
    public void setRankingPercentile(Float v) { this.rankingPercentile = v; }

    public Instant getLastCommitAt() { return lastCommitAt; }
    public void setLastCommitAt(Instant v) { this.lastCommitAt = v; }

    public Instant getLastReleaseAt() { return lastReleaseAt; }
    public void setLastReleaseAt(Instant v) { this.lastReleaseAt = v; }

    public String getCommitFrequency52w() { return commitFrequency52w; }
    public void setCommitFrequency52w(String v) { this.commitFrequency52w = v; }

    public Integer getContributorCount() { return contributorCount; }
    public void setContributorCount(Integer v) { this.contributorCount = v; }

    public Boolean getIsArchived() { return isArchived; }
    public void setIsArchived(Boolean v) { this.isArchived = v; }

    public Boolean getIsDeprecated() { return isDeprecated; }
    public void setIsDeprecated(Boolean v) { this.isDeprecated = v; }

    public Float getCommunityHealthPct() { return communityHealthPct; }
    public void setCommunityHealthPct(Float v) { this.communityHealthPct = v; }

    public Float getAvgIssueCloseTimeDays() { return avgIssueCloseTimeDays; }
    public void setAvgIssueCloseTimeDays(Float v) { this.avgIssueCloseTimeDays = v; }

    public Float getAvgPrCloseTimeDays() { return avgPrCloseTimeDays; }
    public void setAvgPrCloseTimeDays(Float v) { this.avgPrCloseTimeDays = v; }

    public Integer getPrAuthorsCount() { return prAuthorsCount; }
    public void setPrAuthorsCount(Integer v) { this.prAuthorsCount = v; }

    public Integer getMergedPrCount() { return mergedPrCount; }
    public void setMergedPrCount(Integer v) { this.mergedPrCount = v; }

    public Integer getOpenIssuesCount() { return openIssuesCount; }
    public void setOpenIssuesCount(Integer v) { this.openIssuesCount = v; }

    public Integer getOpenPrCount() { return openPrCount; }
    public void setOpenPrCount(Integer v) { this.openPrCount = v; }

    public Integer getAdvisoryCount() { return advisoryCount; }
    public void setAdvisoryCount(Integer v) { this.advisoryCount = v; }

    public Boolean getHasSlsaProvenance() { return hasSlsaProvenance; }
    public void setHasSlsaProvenance(Boolean v) { this.hasSlsaProvenance = v; }

    public Boolean getHasOssFuzz() { return hasOssFuzz; }
    public void setHasOssFuzz(Boolean v) { this.hasOssFuzz = v; }

    public Integer getMaintainerCount() { return maintainerCount; }
    public void setMaintainerCount(Integer v) { this.maintainerCount = v; }

    public String getLicense() { return license; }
    public void setLicense(String v) { this.license = v; }

    public String getRawSourceData() { return rawSourceData; }
    public void setRawSourceData(String v) { this.rawSourceData = v; }

    public List<String> getSourcesUsed() { return sourcesUsed; }
    public void setSourcesUsed(List<String> v) { this.sourcesUsed = v; }

    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant v) { this.fetchedAt = v; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
