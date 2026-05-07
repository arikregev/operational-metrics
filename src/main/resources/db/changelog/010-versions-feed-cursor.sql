--liquibase formatted sql

--changeset operational-metrics:10
-- Cursor table for the changes-feed poller (VersionsChangesFeedService).
-- One row per ecosyste.ms registry we poll. Stores the watermark the poller
-- uses to decide "have I already seen this package's release?" — it stops
-- paginating descending when a result's latest_release_published_at is
-- earlier than last_seen_release_at.
--
-- Survives restarts so we don't either re-do work or miss a window.
CREATE TABLE versions_feed_cursor (
    registry             VARCHAR(64)  PRIMARY KEY,
    last_polled_at       TIMESTAMPTZ  NOT NULL,
    last_seen_release_at TIMESTAMPTZ,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);
