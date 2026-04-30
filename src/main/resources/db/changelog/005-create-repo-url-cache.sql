--liquibase formatted sql

--changeset operational-metrics:5
CREATE TABLE repo_url_cache (
    id            BIGSERIAL PRIMARY KEY,
    package_id    BIGINT NOT NULL REFERENCES package(id) ON DELETE CASCADE,
    repo_url      VARCHAR(1024) NOT NULL,
    repo_platform VARCHAR(32)   NOT NULL,
    repo_owner    VARCHAR(256)  NOT NULL,
    repo_name     VARCHAR(256)  NOT NULL,
    resolved_via  VARCHAR(32)   NOT NULL,
    resolved_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_repocache_pkg UNIQUE (package_id)
);
