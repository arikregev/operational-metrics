--liquibase formatted sql

--changeset operational-metrics:8
-- Drop columns we no longer collect or surface — minimises stored data and
-- removes unused upstream fetch paths from the collectors.
--
-- Postgres doesn't allow column-order changes without a full table recreation,
-- so the remaining columns retain their original creation order. New schema
-- inspection (\d operational_metrics) will reflect the trim.
ALTER TABLE operational_metrics
    DROP COLUMN stars_count,
    DROP COLUMN forks_count,
    DROP COLUMN dependent_repos_count,
    DROP COLUMN dependent_packages_count,
    DROP COLUMN download_count,
    DROP COLUMN download_period,
    DROP COLUMN pr_authors_count,
    DROP COLUMN merged_pr_count,
    DROP COLUMN open_issues_count,
    DROP COLUMN open_pr_count,
    DROP COLUMN has_slsa_provenance,
    DROP COLUMN has_oss_fuzz,
    DROP COLUMN maintainer_count,
    DROP COLUMN license,
    DROP COLUMN raw_source_data;

ALTER TABLE metrics_history
    DROP COLUMN stars_count,
    DROP COLUMN forks_count,
    DROP COLUMN dependent_repos_count,
    DROP COLUMN dependent_packages_count,
    DROP COLUMN download_count,
    DROP COLUMN download_period,
    DROP COLUMN pr_authors_count,
    DROP COLUMN merged_pr_count,
    DROP COLUMN open_issues_count,
    DROP COLUMN open_pr_count,
    DROP COLUMN has_slsa_provenance,
    DROP COLUMN has_oss_fuzz,
    DROP COLUMN maintainer_count,
    DROP COLUMN license,
    DROP COLUMN raw_source_data;
