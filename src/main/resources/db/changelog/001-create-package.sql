--liquibase formatted sql

--changeset operational-metrics:1
CREATE TABLE package (
    id              BIGSERIAL PRIMARY KEY,
    purl_type       VARCHAR(32)   NOT NULL,
    purl_namespace  VARCHAR(512),
    purl_name       VARCHAR(512)  NOT NULL,
    purl_canonical  VARCHAR(1024) NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_package_coords UNIQUE (purl_type, purl_namespace, purl_name)
);

CREATE INDEX idx_package_canonical ON package (purl_canonical);
CREATE INDEX idx_package_type_ns ON package (purl_type, purl_namespace);
