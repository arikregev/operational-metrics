package com.example.operationalmetrics.repository;

import com.example.operationalmetrics.model.PackageVersionEntry;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RegisterConstructorMapper(PackageVersionEntry.class)
public interface PackageVersionDao {

    /**
     * Inserts a single (package, version) row if it doesn't exist. Existing
     * rows are left untouched — version metadata is immutable: a registry's
     * {@code released_at} for a given version is fixed at publish time and
     * never changes. Using {@code DO NOTHING} avoids unnecessary DB write
     * work when re-polling already-known versions.
     *
     * <p>For per-call use from {@code findOrFetchByVersion}; bulk discovery
     * paths should prefer {@link #insertBatch} for ~5–10x throughput.
     */
    @SqlUpdate("""
            INSERT INTO package_version (package_id, version, released_at, resolved_via)
            VALUES (:packageId, :version, :releasedAt, :resolvedVia)
            ON CONFLICT (package_id, version) DO NOTHING
            """)
    void insertIfAbsent(@Bind("packageId") Long packageId,
                         @Bind("version") String version,
                         @Bind("releasedAt") Instant releasedAt,
                         @Bind("resolvedVia") String resolvedVia);

    /**
     * Batch insert. Uses JDBI's {@code @SqlBatch} which compiles to a single
     * JDBC {@code addBatch}/{@code executeBatch} round trip — significant
     * speedup for packages with many versions (npm, maven). Same
     * {@code ON CONFLICT DO NOTHING} semantics.
     */
    @SqlBatch("""
            INSERT INTO package_version (package_id, version, released_at, resolved_via)
            VALUES (:packageId, :version, :releasedAt, :resolvedVia)
            ON CONFLICT (package_id, version) DO NOTHING
            """)
    void insertBatch(@Bind("packageId") List<Long> packageIds,
                     @Bind("version") List<String> versions,
                     @Bind("releasedAt") List<Instant> releasedAts,
                     @Bind("resolvedVia") List<String> resolvedVias);

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
}
