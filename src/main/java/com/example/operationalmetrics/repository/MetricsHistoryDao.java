package com.example.operationalmetrics.repository;

import com.example.operationalmetrics.model.OperationalMetricsEntity;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.UUID;

public interface MetricsHistoryDao {

    @SqlUpdate("""
            INSERT INTO metrics_history (
                package_id, sync_run_id,
                repo_url, repo_platform, repo_owner, repo_name,
                scorecard_overall_score, scorecard_checks, scorecard_date, scorecard_source,
                stars_count, forks_count, dependent_repos_count, dependent_packages_count,
                download_count, download_period, ranking_percentile,
                last_commit_at, last_release_at, last_release_version, last_release_version_source,
                first_release_at, commit_frequency_52w, contributor_count,
                is_archived, is_deprecated, snyk_rating,
                community_health_pct, avg_issue_close_time_days, avg_pr_close_time_days,
                pr_authors_count, merged_pr_count, open_issues_count, open_pr_count,
                advisory_count, has_slsa_provenance, has_oss_fuzz,
                maintainer_count, license,
                raw_source_data, sources_used, fetched_at
            ) VALUES (
                :entity.packageId, :syncRunId,
                :entity.repoUrl, :entity.repoPlatform, :entity.repoOwner, :entity.repoName,
                :entity.scorecardOverallScore, CAST(:entity.scorecardChecks AS JSONB),
                :entity.scorecardDate, :entity.scorecardSource,
                :entity.starsCount, :entity.forksCount,
                :entity.dependentReposCount, :entity.dependentPackagesCount,
                :entity.downloadCount, :entity.downloadPeriod, :entity.rankingPercentile,
                :entity.lastCommitAt, :entity.lastReleaseAt,
                :entity.lastReleaseVersion, :entity.lastReleaseVersionSource,
                :entity.firstReleaseAt,
                CAST(:entity.commitFrequency52w AS JSONB), :entity.contributorCount,
                :entity.isArchived, :entity.isDeprecated, :entity.snykRating,
                :entity.communityHealthPct, :entity.avgIssueCloseTimeDays,
                :entity.avgPrCloseTimeDays,
                :entity.prAuthorsCount, :entity.mergedPrCount,
                :entity.openIssuesCount, :entity.openPrCount,
                :entity.advisoryCount, :entity.hasSlsaProvenance, :entity.hasOssFuzz,
                :entity.maintainerCount, :entity.license,
                CAST(:entity.rawSourceData AS JSONB), CAST(:entity.sourcesUsed AS TEXT[]),
                :entity.fetchedAt
            )
            """)
    void insert(@BindBean("entity") OperationalMetricsEntity entity,
                @Bind("syncRunId") UUID syncRunId);

    @SqlUpdate("DELETE FROM metrics_history WHERE fetched_at < now() - make_interval(days => :days)")
    int deleteOlderThan(@Bind("days") int retentionDays);
}
