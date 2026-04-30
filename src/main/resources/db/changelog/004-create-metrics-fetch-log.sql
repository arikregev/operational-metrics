--liquibase formatted sql

--changeset operational-metrics:4
CREATE TABLE metrics_fetch_log (
    id            BIGSERIAL PRIMARY KEY,
    package_id    BIGINT NOT NULL REFERENCES package(id) ON DELETE CASCADE,
    source        VARCHAR(32)  NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    http_status   INTEGER,
    error_message TEXT,
    duration_ms   INTEGER,
    fetched_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_fetchlog_pkg ON metrics_fetch_log (package_id, fetched_at DESC);
