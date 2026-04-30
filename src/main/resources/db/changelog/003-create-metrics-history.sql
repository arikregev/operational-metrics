--liquibase formatted sql

--changeset operational-metrics:3
CREATE TABLE metrics_history (
    id                          BIGSERIAL PRIMARY KEY,
    package_id                  BIGINT NOT NULL REFERENCES package(id) ON DELETE CASCADE,
    sync_run_id                 UUID NOT NULL,

    repo_url                    VARCHAR(1024),
    repo_platform               VARCHAR(32),
    repo_owner                  VARCHAR(256),
    repo_name                   VARCHAR(256),

    scorecard_overall_score     REAL,
    scorecard_checks            JSONB,
    scorecard_date              TIMESTAMPTZ,
    scorecard_source            VARCHAR(32),

    stars_count                 INTEGER,
    forks_count                 INTEGER,
    dependent_repos_count       BIGINT,
    dependent_packages_count    BIGINT,
    download_count              BIGINT,
    download_period             VARCHAR(32),
    ranking_percentile          REAL,

    last_commit_at              TIMESTAMPTZ,
    last_release_at             TIMESTAMPTZ,
    commit_frequency_52w        JSONB,
    contributor_count           INTEGER,
    is_archived                 BOOLEAN,
    is_deprecated               BOOLEAN,

    community_health_pct        REAL,
    avg_issue_close_time_days   REAL,
    avg_pr_close_time_days      REAL,
    pr_authors_count            INTEGER,
    merged_pr_count             INTEGER,
    open_issues_count           INTEGER,
    open_pr_count               INTEGER,

    advisory_count              INTEGER,
    has_slsa_provenance         BOOLEAN,
    has_oss_fuzz                BOOLEAN,

    maintainer_count            INTEGER,

    license                     VARCHAR(128),

    raw_source_data             JSONB,
    sources_used                TEXT[],
    fetched_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_history_package_date ON metrics_history (package_id, fetched_at DESC);
CREATE INDEX idx_history_fetched ON metrics_history (fetched_at);
CREATE INDEX idx_history_sync_run ON metrics_history (sync_run_id);
