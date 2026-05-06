package com.example.operationalmetrics.repository;

import com.example.operationalmetrics.model.PackageVersionEntry;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RegisterConstructorMapper(PackageVersionEntry.class)
public interface PackageVersionDao {

    @SqlUpdate("""
            INSERT INTO package_version (package_id, version, released_at, resolved_via)
            VALUES (:packageId, :version, :releasedAt, :resolvedVia)
            ON CONFLICT (package_id, version) DO UPDATE SET
                released_at  = COALESCE(EXCLUDED.released_at, package_version.released_at),
                resolved_via = EXCLUDED.resolved_via,
                updated_at   = now()
            """)
    void upsert(@Bind("packageId") Long packageId,
                @Bind("version") String version,
                @Bind("releasedAt") Instant releasedAt,
                @Bind("resolvedVia") String resolvedVia);

    @SqlQuery("""
            SELECT package_id, version, released_at, resolved_via, observed_at, updated_at
            FROM package_version
            WHERE package_id = :packageId AND version = :version
            """)
    Optional<PackageVersionEntry> findByPackageAndVersion(@Bind("packageId") Long packageId,
                                                          @Bind("version") String version);

    @SqlQuery("""
            SELECT package_id, version, released_at, resolved_via, observed_at, updated_at
            FROM package_version
            WHERE package_id = :packageId
            ORDER BY released_at DESC NULLS LAST
            LIMIT :n
            """)
    List<PackageVersionEntry> latestN(@Bind("packageId") Long packageId, @Bind("n") int n);

    @SqlQuery("SELECT max(updated_at) FROM package_version WHERE package_id = :packageId")
    Optional<Instant> lastUpdatedAt(@Bind("packageId") Long packageId);
}
