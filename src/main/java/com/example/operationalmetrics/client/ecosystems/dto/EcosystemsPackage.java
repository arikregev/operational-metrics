package com.example.operationalmetrics.client.ecosystems.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EcosystemsPackage {

    private Integer id;
    private String name;
    private String ecosystem;
    private String namespace;
    private String purl;
    private String description;
    private String homepage;

    @JsonProperty("repository_url")
    private String repositoryUrl;

    private String licenses;

    @JsonProperty("normalized_licenses")
    private List<String> normalizedLicenses;

    @JsonProperty("versions_count")
    private Integer versionsCount;

    @JsonProperty("first_release_published_at")
    private Instant firstReleasePublishedAt;

    @JsonProperty("latest_release_published_at")
    private Instant latestReleasePublishedAt;

    @JsonProperty("latest_release_number")
    private String latestReleaseNumber;

    private Long downloads;

    @JsonProperty("downloads_period")
    private String downloadsPeriod;

    @JsonProperty("dependent_repos_count")
    private Long dependentReposCount;

    @JsonProperty("dependent_packages_count")
    private Long dependentPackagesCount;

    private Boolean critical;

    private EcosystemsRankings rankings;

    @JsonProperty("issue_metadata")
    private EcosystemsIssueMetadata issueMetadata;

    @JsonProperty("repo_metadata")
    private EcosystemsRepoMetadata repoMetadata;

    private String status;

    // Getters and setters

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEcosystem() { return ecosystem; }
    public void setEcosystem(String ecosystem) { this.ecosystem = ecosystem; }

    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }

    public String getPurl() { return purl; }
    public void setPurl(String purl) { this.purl = purl; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getHomepage() { return homepage; }
    public void setHomepage(String homepage) { this.homepage = homepage; }

    public String getRepositoryUrl() { return repositoryUrl; }
    public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }

    public String getLicenses() { return licenses; }
    public void setLicenses(String licenses) { this.licenses = licenses; }

    public List<String> getNormalizedLicenses() { return normalizedLicenses; }
    public void setNormalizedLicenses(List<String> normalizedLicenses) { this.normalizedLicenses = normalizedLicenses; }

    public Integer getVersionsCount() { return versionsCount; }
    public void setVersionsCount(Integer versionsCount) { this.versionsCount = versionsCount; }

    public Instant getFirstReleasePublishedAt() { return firstReleasePublishedAt; }
    public void setFirstReleasePublishedAt(Instant firstReleasePublishedAt) { this.firstReleasePublishedAt = firstReleasePublishedAt; }

    public Instant getLatestReleasePublishedAt() { return latestReleasePublishedAt; }
    public void setLatestReleasePublishedAt(Instant latestReleasePublishedAt) { this.latestReleasePublishedAt = latestReleasePublishedAt; }

    public String getLatestReleaseNumber() { return latestReleaseNumber; }
    public void setLatestReleaseNumber(String latestReleaseNumber) { this.latestReleaseNumber = latestReleaseNumber; }

    public Long getDownloads() { return downloads; }
    public void setDownloads(Long downloads) { this.downloads = downloads; }

    public String getDownloadsPeriod() { return downloadsPeriod; }
    public void setDownloadsPeriod(String downloadsPeriod) { this.downloadsPeriod = downloadsPeriod; }

    public Long getDependentReposCount() { return dependentReposCount; }
    public void setDependentReposCount(Long dependentReposCount) { this.dependentReposCount = dependentReposCount; }

    public Long getDependentPackagesCount() { return dependentPackagesCount; }
    public void setDependentPackagesCount(Long dependentPackagesCount) { this.dependentPackagesCount = dependentPackagesCount; }

    public Boolean getCritical() { return critical; }
    public void setCritical(Boolean critical) { this.critical = critical; }

    public EcosystemsRankings getRankings() { return rankings; }
    public void setRankings(EcosystemsRankings rankings) { this.rankings = rankings; }

    public EcosystemsIssueMetadata getIssueMetadata() { return issueMetadata; }
    public void setIssueMetadata(EcosystemsIssueMetadata issueMetadata) { this.issueMetadata = issueMetadata; }

    public EcosystemsRepoMetadata getRepoMetadata() { return repoMetadata; }
    public void setRepoMetadata(EcosystemsRepoMetadata repoMetadata) { this.repoMetadata = repoMetadata; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    // Inner classes

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EcosystemsRankings {

        private Float downloads;

        @JsonProperty("dependent_repos_count")
        private Float dependentReposCount;

        @JsonProperty("dependent_packages_count")
        private Float dependentPackagesCount;

        @JsonProperty("stargazers_count")
        private Float stargazersCount;

        @JsonProperty("forks_count")
        private Float forksCount;

        private Float average;

        public Float getDownloads() { return downloads; }
        public void setDownloads(Float downloads) { this.downloads = downloads; }

        public Float getDependentReposCount() { return dependentReposCount; }
        public void setDependentReposCount(Float dependentReposCount) { this.dependentReposCount = dependentReposCount; }

        public Float getDependentPackagesCount() { return dependentPackagesCount; }
        public void setDependentPackagesCount(Float dependentPackagesCount) { this.dependentPackagesCount = dependentPackagesCount; }

        public Float getStargazersCount() { return stargazersCount; }
        public void setStargazersCount(Float stargazersCount) { this.stargazersCount = stargazersCount; }

        public Float getForksCount() { return forksCount; }
        public void setForksCount(Float forksCount) { this.forksCount = forksCount; }

        public Float getAverage() { return average; }
        public void setAverage(Float average) { this.average = average; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EcosystemsIssueMetadata {

        @JsonProperty("issues_count")
        private Integer issuesCount;

        @JsonProperty("pull_requests_count")
        private Integer pullRequestsCount;

        @JsonProperty("avg_time_to_close_issue")
        private Float avgTimeToCloseIssue;

        @JsonProperty("avg_time_to_close_pull_request")
        private Float avgTimeToClosePullRequest;

        @JsonProperty("issues_closed_count")
        private Integer issuesClosedCount;

        @JsonProperty("pull_requests_closed_count")
        private Integer pullRequestsClosedCount;

        @JsonProperty("pull_request_authors_count")
        private Integer pullRequestAuthorsCount;

        @JsonProperty("issue_authors_count")
        private Integer issueAuthorsCount;

        @JsonProperty("merged_pull_requests_count")
        private Integer mergedPullRequestsCount;

        public Integer getIssuesCount() { return issuesCount; }
        public void setIssuesCount(Integer issuesCount) { this.issuesCount = issuesCount; }

        public Integer getPullRequestsCount() { return pullRequestsCount; }
        public void setPullRequestsCount(Integer pullRequestsCount) { this.pullRequestsCount = pullRequestsCount; }

        public Float getAvgTimeToCloseIssue() { return avgTimeToCloseIssue; }
        public void setAvgTimeToCloseIssue(Float avgTimeToCloseIssue) { this.avgTimeToCloseIssue = avgTimeToCloseIssue; }

        public Float getAvgTimeToClosePullRequest() { return avgTimeToClosePullRequest; }
        public void setAvgTimeToClosePullRequest(Float avgTimeToClosePullRequest) { this.avgTimeToClosePullRequest = avgTimeToClosePullRequest; }

        public Integer getIssuesClosedCount() { return issuesClosedCount; }
        public void setIssuesClosedCount(Integer issuesClosedCount) { this.issuesClosedCount = issuesClosedCount; }

        public Integer getPullRequestsClosedCount() { return pullRequestsClosedCount; }
        public void setPullRequestsClosedCount(Integer pullRequestsClosedCount) { this.pullRequestsClosedCount = pullRequestsClosedCount; }

        public Integer getPullRequestAuthorsCount() { return pullRequestAuthorsCount; }
        public void setPullRequestAuthorsCount(Integer pullRequestAuthorsCount) { this.pullRequestAuthorsCount = pullRequestAuthorsCount; }

        public Integer getIssueAuthorsCount() { return issueAuthorsCount; }
        public void setIssueAuthorsCount(Integer issueAuthorsCount) { this.issueAuthorsCount = issueAuthorsCount; }

        public Integer getMergedPullRequestsCount() { return mergedPullRequestsCount; }
        public void setMergedPullRequestsCount(Integer mergedPullRequestsCount) { this.mergedPullRequestsCount = mergedPullRequestsCount; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EcosystemsRepoMetadata {

        @JsonProperty("stargazers_count")
        private Integer stargazersCount;

        @JsonProperty("forks_count")
        private Integer forksCount;

        @JsonProperty("open_issues_count")
        private Integer openIssuesCount;

        @JsonProperty("subscribers_count")
        private Integer subscribersCount;

        @JsonProperty("pushed_at")
        private Instant pushedAt;

        private Boolean archived;
        private Boolean fork;
        private String language;

        @JsonProperty("tags_count")
        private Integer tagsCount;

        public Integer getStargazersCount() { return stargazersCount; }
        public void setStargazersCount(Integer stargazersCount) { this.stargazersCount = stargazersCount; }

        public Integer getForksCount() { return forksCount; }
        public void setForksCount(Integer forksCount) { this.forksCount = forksCount; }

        public Integer getOpenIssuesCount() { return openIssuesCount; }
        public void setOpenIssuesCount(Integer openIssuesCount) { this.openIssuesCount = openIssuesCount; }

        public Integer getSubscribersCount() { return subscribersCount; }
        public void setSubscribersCount(Integer subscribersCount) { this.subscribersCount = subscribersCount; }

        public Instant getPushedAt() { return pushedAt; }
        public void setPushedAt(Instant pushedAt) { this.pushedAt = pushedAt; }

        public Boolean getArchived() { return archived; }
        public void setArchived(Boolean archived) { this.archived = archived; }

        public Boolean getFork() { return fork; }
        public void setFork(Boolean fork) { this.fork = fork; }

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }

        public Integer getTagsCount() { return tagsCount; }
        public void setTagsCount(Integer tagsCount) { this.tagsCount = tagsCount; }
    }
}
