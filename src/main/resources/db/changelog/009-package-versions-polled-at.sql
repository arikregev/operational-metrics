--liquibase formatted sql

--changeset operational-metrics:9
-- Track when each package's version list was last polled. Denormalised onto
-- `package` itself so we can pick due-to-poll packages with an index-only scan
-- against ~9M rows, instead of joining against the ~900M-row package_version
-- table. Updated by RepoMetaAnalyzer.analyze() after each successful poll.
ALTER TABLE package ADD COLUMN latest_versions_polled_at TIMESTAMPTZ;

-- NULLS FIRST ordering: never-polled packages sort to the front of the
-- VersionsSyncService.runSweep() work queue.
CREATE INDEX idx_package_versions_polled
    ON package (latest_versions_polled_at NULLS FIRST);
