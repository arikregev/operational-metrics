package com.example.operationalmetrics.repository;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.Optional;

public interface RepoUrlCacheDao {

    record RepoUrlCacheEntry(Long packageId, String repoUrl, String repoPlatform,
                             String repoOwner, String repoName, String resolvedVia) {}

    @SqlUpdate("""
            INSERT INTO repo_url_cache (package_id, repo_url, repo_platform, repo_owner, repo_name, resolved_via, created_at, updated_at)
            VALUES (:packageId, :repoUrl, :repoPlatform, :repoOwner, :repoName, :resolvedVia, now(), now())
            ON CONFLICT (package_id) DO UPDATE SET
                repo_url = EXCLUDED.repo_url,
                repo_platform = EXCLUDED.repo_platform,
                repo_owner = EXCLUDED.repo_owner,
                repo_name = EXCLUDED.repo_name,
                resolved_via = EXCLUDED.resolved_via,
                updated_at = now()
            """)
    void upsert(@Bind("packageId") Long packageId,
                @Bind("repoUrl") String repoUrl,
                @Bind("repoPlatform") String repoPlatform,
                @Bind("repoOwner") String repoOwner,
                @Bind("repoName") String repoName,
                @Bind("resolvedVia") String resolvedVia);

    @SqlQuery("""
            SELECT package_id, repo_url, repo_platform, repo_owner, repo_name, resolved_via
            FROM repo_url_cache
            WHERE package_id = :packageId
            """)
    Optional<RepoUrlCacheEntry> findByPackageId(@Bind("packageId") Long packageId);
}
