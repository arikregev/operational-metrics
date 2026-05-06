--liquibase formatted sql

--changeset operational-metrics:6
-- Replace the (purl_type, purl_namespace, purl_name) UNIQUE with one on
-- purl_canonical.
--
-- The DAO's INSERT uses ON CONFLICT (purl_canonical), which requires a
-- matching unique/exclusion constraint. Beyond fixing that ERROR, the new
-- key also closes a duplicate-row hole: purl_namespace is nullable, and
-- PostgreSQL's default UNIQUE semantics treat NULLs as distinct, so two
-- concurrent upserts of e.g. pkg:pypi/requests (where namespace = NULL)
-- would both succeed under the old constraint. purl_canonical is NOT NULL
-- and deterministically encodes identity per the PURL spec, so a UNIQUE
-- on it correctly rejects duplicates for every package type.
ALTER TABLE package DROP CONSTRAINT uq_package_coords;
DROP INDEX idx_package_canonical;
ALTER TABLE package ADD CONSTRAINT uq_package_canonical UNIQUE (purl_canonical);
