package com.example.operationalmetrics.service;

import com.example.operationalmetrics.client.depsdev.DepsDevClient;
import com.example.operationalmetrics.client.depsdev.dto.DepsDevPackage;
import com.example.operationalmetrics.client.depsdev.dto.DepsDevVersionInfo;
import com.example.operationalmetrics.client.ecosystems.EcosystemsClient;
import com.example.operationalmetrics.client.ecosystems.dto.EcosystemsVersion;
import com.example.operationalmetrics.client.snyk.SnykClient;
import com.example.operationalmetrics.client.snyk.dto.SnykPackageVersionResponse;
import com.example.operationalmetrics.config.RepoMetaAnalyzerConfig;
import com.example.operationalmetrics.config.SnykConfig;
import com.example.operationalmetrics.model.MetricsSource;
import com.example.operationalmetrics.model.PackageId;
import com.example.operationalmetrics.model.PackageVersionEntry;
import com.example.operationalmetrics.repository.PackageVersionDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import org.jdbi.v3.core.Jdbi;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tracks per-version release metadata in the {@code package_version} table.
 *
 * <p>Two flows:
 * <ul>
 *   <li><b>Flow A — bulk version discovery.</b> Inline during sync. Calls
 *       list-capable sources (ecosyste.ms, deps.dev) in configured priority
 *       order; the first that returns a non-empty list wins. Skipped if any
 *       row for the package has been updated within
 *       {@code refreshAfterDays}.</li>
 *   <li><b>Flow B — single-version on-demand.</b> Cache-first. On miss,
 *       falls through Snyk (priority 1) → ecosyste.ms (2) → deps.dev (3)
 *       in configured priority order, returning the first that succeeds.
 *       Result upserted to {@code package_version}.</li>
 * </ul>
 */
@ApplicationScoped
public class RepoMetaAnalyzer {

    private static final Logger LOG = Logger.getLogger(RepoMetaAnalyzer.class);

    private final Jdbi jdbi;
    private final RepoMetaAnalyzerConfig config;
    private final SnykConfig snykConfig;
    private final EcosystemsClient ecosystemsClient;
    private final DepsDevClient depsDevClient;
    private final SnykClient snykClient;

    @Inject
    public RepoMetaAnalyzer(Jdbi jdbi,
                            RepoMetaAnalyzerConfig config,
                            SnykConfig snykConfig,
                            @RestClient EcosystemsClient ecosystemsClient,
                            @RestClient DepsDevClient depsDevClient,
                            @RestClient SnykClient snykClient) {
        this.jdbi = jdbi;
        this.config = config;
        this.snykConfig = snykConfig;
        this.ecosystemsClient = ecosystemsClient;
        this.depsDevClient = depsDevClient;
        this.snykClient = snykClient;
    }

    // -----------------------------------------------------------------------
    // Flow A — bulk version discovery
    // -----------------------------------------------------------------------

    /**
     * Fetches all known versions for the given package and upserts them into
     * {@code package_version}. Skips the upstream call when any existing row
     * for the package has been updated within {@code refreshAfterDays}.
     *
     * @return the rows after the upsert; empty list if no source returned
     *         versions or if recency-skip applied.
     */
    public List<PackageVersionEntry> analyze(PackageId packageId, Long packageDbId) {
        if (!config.enabled()) {
            return List.of();
        }
        if (recencySkip(packageDbId)) {
            LOG.debugv("Skipping bulk analyze for {0} — recent rows exist", packageId.canonical());
            return latestN(packageId, packageDbId, Integer.MAX_VALUE);
        }

        for (String sourceKey : config.bulk().enabledSourcesByPriority()) {
            List<UpstreamVersion> versions;
            try {
                versions = switch (sourceKey) {
                    case "ecosystems" -> fetchVersionsFromEcosystems(packageId);
                    case "depsdev" -> fetchVersionsFromDepsDev(packageId);
                    default -> List.of();
                };
            } catch (Exception e) {
                LOG.debugv("Bulk version fetch from {0} failed for {1}: {2}",
                        sourceKey, packageId.canonical(), e.getMessage());
                continue;
            }

            if (!versions.isEmpty()) {
                String resolvedVia = sourceKeyToMetricsSource(sourceKey).name();
                jdbi.useExtension(PackageVersionDao.class, dao -> {
                    for (UpstreamVersion v : versions) {
                        dao.upsert(packageDbId, v.version(), v.releasedAt(), resolvedVia);
                    }
                });
                LOG.infov("Bulk analyze for {0}: {1} versions from {2}",
                        packageId.canonical(), versions.size(), resolvedVia);
                return latestN(packageId, packageDbId, Integer.MAX_VALUE);
            }
        }

        return List.of();
    }

    // -----------------------------------------------------------------------
    // Flow B — single-version on-demand
    // -----------------------------------------------------------------------

    /**
     * Returns metadata for {@code (packageId, version)}. Cache-first; on miss
     * falls through the configured per-version priority chain
     * (Snyk → ecosyste.ms → deps.dev by default), upserts the first hit to
     * {@code package_version}, and returns it.
     */
    public Optional<PackageVersionEntry> findOrFetchByVersion(
            PackageId packageId, Long packageDbId, String version) {
        if (version == null || version.isBlank()) {
            return Optional.empty();
        }

        Optional<PackageVersionEntry> cached = jdbi.withExtension(PackageVersionDao.class,
                dao -> dao.findByPackageAndVersion(packageDbId, version));
        if (cached.isPresent() && cached.get().releasedAt() != null) {
            return cached;
        }

        if (!config.enabled()) {
            return cached;  // possibly an entry without releasedAt; caller decides
        }

        for (String sourceKey : config.perVersion().enabledSourcesByPriority()) {
            UpstreamVersion fetched;
            try {
                fetched = switch (sourceKey) {
                    case "snyk" -> fetchVersionFromSnyk(packageId, version);
                    case "ecosystems" -> fetchVersionFromEcosystems(packageId, version);
                    case "depsdev" -> fetchVersionFromDepsDev(packageId, version);
                    default -> null;
                };
            } catch (Exception e) {
                LOG.debugv("Per-version fetch from {0} failed for {1}@{2}: {3}",
                        sourceKey, packageId.canonical(), version, e.getMessage());
                continue;
            }

            if (fetched != null) {
                String resolvedVia = sourceKeyToMetricsSource(sourceKey).name();
                jdbi.useExtension(PackageVersionDao.class,
                        dao -> dao.upsert(packageDbId, version, fetched.releasedAt(), resolvedVia));
                return jdbi.withExtension(PackageVersionDao.class,
                        dao -> dao.findByPackageAndVersion(packageDbId, version));
            }
        }

        return cached;
    }

    // -----------------------------------------------------------------------
    // Cache-only reads
    // -----------------------------------------------------------------------

    public List<PackageVersionEntry> latestN(PackageId packageId, Long packageDbId, int n) {
        return jdbi.withExtension(PackageVersionDao.class, dao -> dao.latestN(packageDbId, n));
    }

    public Optional<PackageVersionEntry> findCached(Long packageDbId, String version) {
        return jdbi.withExtension(PackageVersionDao.class,
                dao -> dao.findByPackageAndVersion(packageDbId, version));
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private boolean recencySkip(Long packageDbId) {
        Optional<Instant> lastUpdated = jdbi.withExtension(PackageVersionDao.class,
                dao -> dao.lastUpdatedAt(packageDbId));
        if (lastUpdated.isEmpty() || lastUpdated.get() == null) {
            return false;
        }
        Duration age = Duration.between(lastUpdated.get(), Instant.now());
        return age.toDays() < config.bulk().refreshAfterDays();
    }

    private MetricsSource sourceKeyToMetricsSource(String key) {
        return switch (key) {
            case "snyk" -> MetricsSource.SNYK;
            case "ecosystems" -> MetricsSource.ECOSYSTEMS;
            case "depsdev" -> MetricsSource.DEPS_DEV;
            default -> throw new IllegalArgumentException("Unknown source: " + key);
        };
    }

    // ---- ecosyste.ms ---------------------------------------------------------

    private List<UpstreamVersion> fetchVersionsFromEcosystems(PackageId packageId) {
        String registry = ecosystemsRegistry(packageId.purlType());
        if (registry == null) return List.of();
        String name = ecosystemsName(packageId);
        List<EcosystemsVersion> raw = ecosystemsClient.versionsList(registry, name);
        if (raw == null) return List.of();
        List<UpstreamVersion> out = new ArrayList<>(raw.size());
        for (EcosystemsVersion v : raw) {
            if (v.getNumber() != null) {
                out.add(new UpstreamVersion(v.getNumber(), v.getPublishedAt()));
            }
        }
        return out;
    }

    private UpstreamVersion fetchVersionFromEcosystems(PackageId packageId, String version) {
        String registry = ecosystemsRegistry(packageId.purlType());
        if (registry == null) return null;
        EcosystemsVersion v = ecosystemsClient.versionInfo(registry, ecosystemsName(packageId), version);
        return v == null ? null : new UpstreamVersion(v.getNumber() != null ? v.getNumber() : version, v.getPublishedAt());
    }

    private String ecosystemsRegistry(String purlType) {
        return switch (purlType) {
            case "maven" -> "repo1.maven.org";
            case "npm" -> "npmjs.org";
            case "pypi" -> "pypi.org";
            case "gem" -> "rubygems.org";
            case "nuget" -> "nuget.org";
            case "cargo" -> "crates.io";
            case "golang", "go" -> "proxy.golang.org";
            case "composer" -> "packagist.org";
            default -> null;
        };
    }

    private String ecosystemsName(PackageId packageId) {
        if ("maven".equals(packageId.purlType()) && packageId.namespace() != null) {
            return packageId.namespace() + ":" + packageId.name();
        }
        if (packageId.namespace() != null && !packageId.namespace().isBlank()) {
            return packageId.namespace() + "/" + packageId.name();
        }
        return packageId.name();
    }

    // ---- deps.dev ------------------------------------------------------------

    private List<UpstreamVersion> fetchVersionsFromDepsDev(PackageId packageId) {
        String system = depsDevSystem(packageId.purlType());
        if (system == null) return List.of();
        DepsDevPackage pkg = depsDevClient.getPackage(system, depsDevName(packageId));
        if (pkg == null || pkg.versions() == null) return List.of();
        List<UpstreamVersion> out = new ArrayList<>(pkg.versions().size());
        for (DepsDevPackage.VersionRef ref : pkg.versions()) {
            if (ref.versionKey() != null && ref.versionKey().version() != null) {
                out.add(new UpstreamVersion(ref.versionKey().version(), ref.publishedAt()));
            }
        }
        return out;
    }

    private UpstreamVersion fetchVersionFromDepsDev(PackageId packageId, String version) {
        String system = depsDevSystem(packageId.purlType());
        if (system == null) return null;
        DepsDevVersionInfo info = depsDevClient.getVersionInfo(system, depsDevName(packageId), version);
        return info == null ? null : new UpstreamVersion(version, info.publishedAt());
    }

    private String depsDevSystem(String purlType) {
        return switch (purlType) {
            case "maven" -> "MAVEN";
            case "npm" -> "NPM";
            case "pypi" -> "PYPI";
            case "golang", "go" -> "GO";
            case "cargo" -> "CARGO";
            case "nuget" -> "NUGET";
            case "gem" -> "RUBYGEMS";
            default -> null;
        };
    }

    private String depsDevName(PackageId packageId) {
        if ("maven".equals(packageId.purlType()) && packageId.namespace() != null) {
            return packageId.namespace() + ":" + packageId.name();
        }
        return packageId.namespace() != null && !packageId.namespace().isBlank()
                ? packageId.namespace() + "/" + packageId.name()
                : packageId.name();
    }

    // ---- Snyk per-version ----------------------------------------------------

    private UpstreamVersion fetchVersionFromSnyk(PackageId packageId, String version) {
        if (!snykConfig.enabled() || snykConfig.orgId().isEmpty()) {
            return null;
        }
        String ecosystem = snykEcosystem(packageId.purlType());
        if (ecosystem == null) return null;

        SnykPackageVersionResponse response = snykClient.getPackageVersion(
                snykConfig.orgId().get(), ecosystem, snykName(packageId),
                version, snykConfig.apiVersion());
        if (response == null || response.data() == null
                || response.data().attributes() == null
                || response.data().attributes().publishedAt() == null) {
            return null;
        }
        return new UpstreamVersion(version, response.data().attributes().publishedAt());
    }

    private String snykEcosystem(String purlType) {
        return switch (purlType) {
            case "github", "gem" -> null;  // Snyk doesn't accept these
            default -> purlType.toLowerCase();
        };
    }

    private String snykName(PackageId packageId) {
        if ("maven".equals(packageId.purlType()) && packageId.namespace() != null) {
            return packageId.namespace() + ":" + packageId.name();
        }
        if (packageId.namespace() != null && !packageId.namespace().isBlank()) {
            return packageId.namespace() + "/" + packageId.name();
        }
        return packageId.name();
    }

    // -----------------------------------------------------------------------
    // Internal record
    // -----------------------------------------------------------------------

    private record UpstreamVersion(String version, Instant releasedAt) {}
}
