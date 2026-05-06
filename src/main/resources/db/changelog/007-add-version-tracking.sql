--liquibase formatted sql

--changeset operational-metrics:7
-- Latest-version + first-release tracking on operational_metrics and metrics_history.
-- Plus a snyk_rating column carrying Snyk's package_health.maintenance.rating
-- string (e.g. "Healthy", "Active") for the operational-metrics dashboard.
ALTER TABLE operational_metrics
    ADD COLUMN last_release_version          VARCHAR(128),
    ADD COLUMN last_release_version_source   VARCHAR(32),
    ADD COLUMN first_release_at              TIMESTAMPTZ,
    ADD COLUMN snyk_rating                   VARCHAR(64);

ALTER TABLE metrics_history
    ADD COLUMN last_release_version          VARCHAR(128),
    ADD COLUMN last_release_version_source   VARCHAR(32),
    ADD COLUMN first_release_at              TIMESTAMPTZ,
    ADD COLUMN snyk_rating                   VARCHAR(64);

-- Per-version metadata, populated by RepoMetaAnalyzer. Lets the API answer
-- "this dependency is N versions and M days behind latest" without a separate
-- upstream call on every query.
CREATE TABLE package_version (
    id            BIGSERIAL PRIMARY KEY,
    package_id    BIGINT NOT NULL REFERENCES package(id) ON DELETE CASCADE,
    version       VARCHAR(128) NOT NULL,
    released_at   TIMESTAMPTZ,
    resolved_via  VARCHAR(32) NOT NULL,
    observed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_package_version UNIQUE (package_id, version)
);
CREATE INDEX idx_pkg_version_pkg_released ON package_version (package_id, released_at DESC);
