package com.example.operationalmetrics.service;

import com.example.operationalmetrics.client.depsdev.DepsDevClient;
import com.example.operationalmetrics.client.depsdev.dto.DepsDevPurlResponse;
import com.example.operationalmetrics.client.ecosystems.EcosystemsClient;
import com.example.operationalmetrics.client.ecosystems.dto.EcosystemsPackage;
import com.example.operationalmetrics.model.MetricsSource;
import com.example.operationalmetrics.model.PackageId;
import com.example.operationalmetrics.model.RepoUrl;
import com.example.operationalmetrics.repository.RepoUrlCacheDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jdbi.v3.core.Jdbi;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class RepoUrlResolver {

    private static final Logger LOG = Logger.getLogger(RepoUrlResolver.class);

    private final Jdbi jdbi;
    private final DepsDevClient depsDevClient;
    private final EcosystemsClient ecosystemsClient;

    @Inject
    public RepoUrlResolver(Jdbi jdbi,
                           @RestClient DepsDevClient depsDevClient,
                           @RestClient EcosystemsClient ecosystemsClient) {
        this.jdbi = jdbi;
        this.depsDevClient = depsDevClient;
        this.ecosystemsClient = ecosystemsClient;
    }

    public Optional<RepoUrl> resolve(PackageId packageId, Long packageDbId) {
        Optional<RepoUrl> cached = fromCache(packageDbId);
        if (cached.isPresent()) {
            return cached;
        }

        Optional<RepoUrl> resolved = resolveViaDepsdev(packageId);
        MetricsSource resolvedVia = MetricsSource.DEPS_DEV;
        if (resolved.isEmpty()) {
            resolved = resolveViaEcosystems(packageId);
            resolvedVia = MetricsSource.ECOSYSTEMS;
        }

        final MetricsSource source = resolvedVia;
        resolved.ifPresent(repoUrl -> cache(packageDbId, repoUrl, source));

        return resolved;
    }

    public void cache(Long packageId, RepoUrl repoUrl, MetricsSource resolvedVia) {
        try {
            jdbi.useExtension(RepoUrlCacheDao.class, dao ->
                    dao.upsert(packageId, repoUrl.url(), repoUrl.platform(),
                            repoUrl.owner(), repoUrl.name(), resolvedVia.name()));
        } catch (Exception e) {
            LOG.debugv("Failed to cache repo URL for package {0}: {1}", packageId, e.getMessage());
        }
    }

    private Optional<RepoUrl> fromCache(Long packageDbId) {
        return jdbi.withExtension(RepoUrlCacheDao.class, dao -> {
            var entry = dao.findByPackageId(packageDbId);
            return entry.map(e -> new RepoUrl(e.repoUrl(), e.repoPlatform(), e.repoOwner(), e.repoName()));
        });
    }

    private Optional<RepoUrl> resolveViaDepsdev(PackageId packageId) {
        try {
            // Pass the raw canonical PURL — JAX-RS @PathParam encodes it once.
            // Pre-encoding here would produce double-encoding (pkg%253A...) which
            // deps.dev rejects with HTTP 400.
            DepsDevPurlResponse response = depsDevClient.lookupPurl(packageId.canonical());
            if (response.relatedProjects() != null) {
                return response.relatedProjects().stream()
                        .filter(rp -> rp.projectKey() != null && rp.projectKey().id() != null)
                        .filter(rp -> rp.projectKey().id().contains("github.com"))
                        .findFirst()
                        .map(rp -> {
                            String id = rp.projectKey().id();
                            String[] parts = id.split("/", 3);
                            if (parts.length >= 3) {
                                return RepoUrl.fromComponents(parts[0], parts[1], parts[2]);
                            }
                            return null;
                        });
            }
        } catch (Exception e) {
            LOG.debugv("deps.dev repo URL resolution failed for {0}: {1}", packageId.canonical(), e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<RepoUrl> resolveViaEcosystems(PackageId packageId) {
        try {
            List<EcosystemsPackage> results = ecosystemsClient.lookupByPurl(packageId.canonical());
            if (results != null && !results.isEmpty()) {
                String repoUrl = results.getFirst().getRepositoryUrl();
                if (repoUrl != null && !repoUrl.isBlank()) {
                    return Optional.of(RepoUrl.parse(repoUrl));
                }
            }
        } catch (Exception e) {
            LOG.debugv("ecosyste.ms repo URL resolution failed for {0}: {1}", packageId.canonical(), e.getMessage());
        }
        return Optional.empty();
    }
}
