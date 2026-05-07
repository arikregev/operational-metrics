package com.example.operationalmetrics.repository;

import com.example.operationalmetrics.model.FeedCursor;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.time.Instant;
import java.util.Optional;

@RegisterConstructorMapper(FeedCursor.class)
public interface VersionsFeedCursorDao {

    @SqlQuery("""
            SELECT registry, last_polled_at, last_seen_release_at
            FROM versions_feed_cursor
            WHERE registry = :registry
            """)
    Optional<FeedCursor> find(@Bind("registry") String registry);

    /**
     * Upsert cursor after a poll. {@code lastSeenReleaseAt} is COALESCE'd to
     * preserve the existing value when the poll found no new releases (the
     * caller passes {@code null} in that case rather than wiping the
     * watermark).
     */
    @SqlUpdate("""
            INSERT INTO versions_feed_cursor (registry, last_polled_at, last_seen_release_at, updated_at)
            VALUES (:registry, :polledAt, :seenAt, now())
            ON CONFLICT (registry) DO UPDATE SET
                last_polled_at       = EXCLUDED.last_polled_at,
                last_seen_release_at = COALESCE(EXCLUDED.last_seen_release_at, versions_feed_cursor.last_seen_release_at),
                updated_at           = now()
            """)
    void upsert(@Bind("registry") String registry,
                @Bind("polledAt") Instant polledAt,
                @Bind("seenAt") Instant seenAt);
}
