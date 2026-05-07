package com.example.operationalmetrics.repository;

import com.example.operationalmetrics.model.OperationalMetricsEntity;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(OperationalMetricsEntity.class)
public interface OperationalMetricsDao {

    @SqlUpdate("""
            INSERT INTO operational_metrics (
                package_id, repo_url, repo_platform, repo_owner, repo_name,
                scorecard_overall_score, scorecard_checks, scorecard_date, scorecard_source,
                ranking_percentile,
                last_commit_at, last_release_at, last_release_version, last_release_version_source,
                first_release_at, commit_frequency_52w, contributor_count,
                is_archived, is_deprecated, snyk_rating,
                community_health_pct, avg_issue_close_time_days, avg_pr_close_time_days,
                advisory_count,
                sources_used, fetched_at,
                created_at, updated_at
            ) VALUES (
                :packageId, :repoUrl, :repoPlatform, :repoOwner, :repoName,
                :scorecardOverallScore, CAST(:scorecardChecks AS JSONB), :scorecardDate, :scorecardSource,
                :rankingPercentile,
                :lastCommitAt, :lastReleaseAt, :lastReleaseVersion, :lastReleaseVersionSource,
                :firstReleaseAt, CAST(:commitFrequency52w AS JSONB), :contributorCount,
                :isArchived, :isDeprecated, :snykRating,
                :communityHealthPct, :avgIssueCloseTimeDays, :avgPrCloseTimeDays,
                :advisoryCount,
                CAST(:sourcesUsed AS TEXT[]), :fetchedAt,
                now(), now()
            )
            ON CONFLICT (package_id) DO UPDATE SET
                repo_url = EXCLUDED.repo_url,
                repo_platform = EXCLUDED.repo_platform,
                repo_owner = EXCLUDED.repo_owner,
                repo_name = EXCLUDED.repo_name,
                scorecard_overall_score = EXCLUDED.scorecard_overall_score,
                scorecard_checks = EXCLUDED.scorecard_checks,
                scorecard_date = EXCLUDED.scorecard_date,
                scorecard_source = EXCLUDED.scorecard_source,
                ranking_percentile = EXCLUDED.ranking_percentile,
                last_commit_at = EXCLUDED.last_commit_at,
                last_release_at = EXCLUDED.last_release_at,
                last_release_version = EXCLUDED.last_release_version,
                last_release_version_source = EXCLUDED.last_release_version_source,
                first_release_at = EXCLUDED.first_release_at,
                commit_frequency_52w = EXCLUDED.commit_frequency_52w,
                contributor_count = EXCLUDED.contributor_count,
                is_archived = EXCLUDED.is_archived,
                is_deprecated = EXCLUDED.is_deprecated,
                snyk_rating = EXCLUDED.snyk_rating,
                community_health_pct = EXCLUDED.community_health_pct,
                avg_issue_close_time_days = EXCLUDED.avg_issue_close_time_days,
                avg_pr_close_time_days = EXCLUDED.avg_pr_close_time_days,
                advisory_count = EXCLUDED.advisory_count,
                sources_used = EXCLUDED.sources_used,
                fetched_at = EXCLUDED.fetched_at,
                updated_at = now()
            """)
    void upsert(@BindBean OperationalMetricsEntity entity);

    @SqlQuery("""
            SELECT m.*, p.purl_type, p.purl_namespace, p.purl_name, p.purl_canonical
            FROM operational_metrics m
            JOIN package p ON p.id = m.package_id
            WHERE m.package_id = :packageId
            """)
    Optional<OperationalMetricsEntity> findByPackageId(@Bind("packageId") Long packageId);

    @SqlQuery("""
            SELECT m.*, p.purl_type, p.purl_namespace, p.purl_name, p.purl_canonical
            FROM operational_metrics m
            JOIN package p ON p.id = m.package_id
            WHERE p.purl_canonical = :canonical
            """)
    Optional<OperationalMetricsEntity> findByCanonical(@Bind("canonical") String canonical);

    @SqlQuery("""
            SELECT m.*, p.purl_type, p.purl_namespace, p.purl_name, p.purl_canonical
            FROM operational_metrics m
            JOIN package p ON p.id = m.package_id
            WHERE p.purl_canonical = ANY(:canonicals::text[])
            """)
    List<OperationalMetricsEntity> findByCanonicals(@Bind("canonicals") List<String> canonicals);
}
