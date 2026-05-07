package com.example.operationalmetrics.repository;

import com.example.operationalmetrics.model.PackageEntity;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(PackageEntity.class)
public interface PackageDao {

    @SqlUpdate("""
            INSERT INTO package (purl_type, purl_namespace, purl_name, purl_canonical, created_at, updated_at)
            VALUES (:purlType, :purlNamespace, :purlName, :purlCanonical, now(), now())
            ON CONFLICT (purl_canonical) DO UPDATE SET updated_at = now()
            RETURNING id
            """)
    @GetGeneratedKeys("id")
    long upsert(@BindBean PackageEntity entity);

    @SqlQuery("SELECT * FROM package WHERE purl_canonical = :canonical")
    Optional<PackageEntity> findByCanonical(@Bind("canonical") String canonical);

    /**
     * Bulk-find by canonical PURLs. Used by the changes-feed poller to
     * intersect a registry's recently-released list against our local
     * catalogue in a single round-trip. {@code purl_canonical} is indexed,
     * so the query is fast even at ~9M-row scale.
     */
    @SqlQuery("SELECT * FROM package WHERE purl_canonical = ANY(:canonicals)")
    List<PackageEntity> findByCanonicals(@Bind("canonicals") List<String> canonicals);

    @SqlQuery("SELECT * FROM package WHERE purl_type = :type AND purl_namespace = :namespace AND purl_name = :name")
    Optional<PackageEntity> findByCoordinates(@Bind("type") String type,
                                              @Bind("namespace") String namespace,
                                              @Bind("name") String name);

    @SqlQuery("SELECT * FROM package WHERE purl_type = :type AND purl_namespace = :namespace")
    List<PackageEntity> findByTypeAndNamespace(@Bind("type") String type,
                                               @Bind("namespace") String namespace);

    /**
     * Returns packages whose latest {@code operational_metrics.fetched_at} is
     * older than {@code stalenessDays} days, or that have no metrics row at
     * all. When {@code stalenessDays <= 0}, returns every package — staleness
     * filter disabled. Oldest-first ordering so progress is monotonic across
     * runs that hit batch limits.
     */
    @SqlQuery("""
            SELECT p.* FROM package p
            LEFT JOIN operational_metrics m ON m.package_id = p.id
            WHERE :stalenessDays <= 0
               OR m.fetched_at IS NULL
               OR m.fetched_at < now() - make_interval(days => :stalenessDays)
            ORDER BY m.fetched_at ASC NULLS FIRST
            """)
    List<PackageEntity> findStalePackages(@Bind("stalenessDays") int stalenessDays);

    /**
     * Returns packages whose version list is due to be polled — those whose
     * {@code latest_versions_polled_at} is older than {@code stalenessDays}
     * or has never been set. Bounded by {@code batchSize} so a single sweep
     * firing doesn't OOM at 9M-package scale. Oldest-first ordering so
     * forward progress is guaranteed across consecutive runs.
     */
    @SqlQuery("""
            SELECT * FROM package
            WHERE latest_versions_polled_at IS NULL
               OR latest_versions_polled_at < now() - make_interval(days => :stalenessDays)
            ORDER BY latest_versions_polled_at ASC NULLS FIRST
            LIMIT :batchSize
            """)
    List<PackageEntity> findPackagesDuePoll(@Bind("stalenessDays") int stalenessDays,
                                             @Bind("batchSize") int batchSize);

    /**
     * Stamps {@code latest_versions_polled_at = now()} after a successful
     * version-list poll, regardless of whether new versions were inserted.
     * The semantic is "we asked the registry on this date" — failures must
     * NOT call this so the next sweep retries the package.
     */
    @SqlUpdate("UPDATE package SET latest_versions_polled_at = now() WHERE id = :id")
    void markVersionsPolled(@Bind("id") Long id);
}
