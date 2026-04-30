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

    @SqlQuery("SELECT * FROM package WHERE purl_type = :type AND purl_namespace = :namespace AND purl_name = :name")
    Optional<PackageEntity> findByCoordinates(@Bind("type") String type,
                                              @Bind("namespace") String namespace,
                                              @Bind("name") String name);

    @SqlQuery("SELECT * FROM package WHERE purl_type = :type AND purl_namespace = :namespace")
    List<PackageEntity> findByTypeAndNamespace(@Bind("type") String type,
                                               @Bind("namespace") String namespace);
}
