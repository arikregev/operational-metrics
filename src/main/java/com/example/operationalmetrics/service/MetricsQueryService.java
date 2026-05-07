package com.example.operationalmetrics.service;

import com.example.operationalmetrics.config.ApiConfig;
import com.example.operationalmetrics.dto.MetricsBulkResponse;
import com.example.operationalmetrics.dto.MetricsResponse;
import com.example.operationalmetrics.model.OperationalMetricsEntity;
import com.example.operationalmetrics.model.PackageId;
import com.example.operationalmetrics.model.PackageVersionEntry;
import com.example.operationalmetrics.repository.OperationalMetricsDao;
import com.example.operationalmetrics.repository.PackageDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jdbi.v3.core.Jdbi;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@ApplicationScoped
public class MetricsQueryService {

    private static final Logger LOG = Logger.getLogger(MetricsQueryService.class);

    private final Jdbi jdbi;
    private final MetricsOrchestrator orchestrator;
    private final RepoMetaAnalyzer repoMetaAnalyzer;
    private final ApiConfig apiConfig;

    @Inject
    public MetricsQueryService(Jdbi jdbi, MetricsOrchestrator orchestrator,
                               RepoMetaAnalyzer repoMetaAnalyzer, ApiConfig apiConfig) {
        this.jdbi = jdbi;
        this.orchestrator = orchestrator;
        this.repoMetaAnalyzer = repoMetaAnalyzer;
        this.apiConfig = apiConfig;
    }

    public MetricsResponse findByPurl(String purlString) {
        PackageId id = PackageId.fromPurl(purlString);
        String version = extractVersion(purlString);
        return findOrFetch(id, version);
    }

    public MetricsResponse findByCoordinates(String type, String namespace, String name) {
        PackageId id = new PackageId(type, namespace, name);
        return findOrFetch(id, null);
    }

    /** Extracts the version segment from a PURL string. Returns null when the PURL has no @version. */
    static String extractVersion(String purlString) {
        if (purlString == null) return null;
        int at = purlString.indexOf('@');
        if (at < 0 || at == purlString.length() - 1) return null;
        // Strip qualifiers / subpath if present
        String rest = purlString.substring(at + 1);
        int end = rest.length();
        int q = rest.indexOf('?');
        int h = rest.indexOf('#');
        if (q >= 0) end = Math.min(end, q);
        if (h >= 0) end = Math.min(end, h);
        String v = rest.substring(0, end).trim();
        return v.isEmpty() ? null : v;
    }

    public MetricsBulkResponse findBulk(List<String> purls) {
        List<MetricsResponse> results = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        List<PackageId> packageIds = new ArrayList<>();
        for (String purl : purls) {
            try {
                packageIds.add(PackageId.fromPurl(purl));
            } catch (Exception e) {
                errors.add(purl + ": " + e.getMessage());
            }
        }

        List<String> canonicals = packageIds.stream().map(PackageId::canonical).toList();
        Map<String, OperationalMetricsEntity> cached = jdbi.withExtension(OperationalMetricsDao.class, dao -> {
            var found = dao.findByCanonicals(canonicals);
            var map = new HashMap<String, OperationalMetricsEntity>();
            for (var entity : found) {
                map.put(entity.getPurlCanonical(), entity);
            }
            return map;
        });

        List<PackageId> missing = new ArrayList<>();
        for (PackageId id : packageIds) {
            OperationalMetricsEntity entity = cached.get(id.canonical());
            if (entity != null) {
                results.add(toResponse(entity));
            } else {
                missing.add(id);
            }
        }

        if (!missing.isEmpty()) {
            ExecutorService executor = Executors.newFixedThreadPool(
                    Math.min(missing.size(), apiConfig.onDemandConcurrency()));
            try {
                List<Future<MetricsResponse>> futures = new ArrayList<>();
                for (PackageId id : missing) {
                    futures.add(executor.submit(() -> {
                        try {
                            OperationalMetricsEntity entity = orchestrator.collectAndStore(id, null);
                            return toResponse(entity);
                        } catch (Exception e) {
                            LOG.warnv("On-demand fetch failed for {0}: {1}", id.canonical(), e.getMessage());
                            return null;
                        }
                    }));
                }

                for (int i = 0; i < futures.size(); i++) {
                    try {
                        MetricsResponse response = futures.get(i).get(60, TimeUnit.SECONDS);
                        if (response != null) {
                            results.add(response);
                        } else {
                            errors.add(missing.get(i).canonical());
                        }
                    } catch (Exception e) {
                        errors.add(missing.get(i).canonical() + ": " + e.getMessage());
                    }
                }
            } finally {
                executor.shutdown();
            }
        }

        return new MetricsBulkResponse(results, errors);
    }

    private MetricsResponse findOrFetch(PackageId id, String requestedVersion) {
        Optional<OperationalMetricsEntity> cached = jdbi.withExtension(OperationalMetricsDao.class,
                dao -> dao.findByCanonical(id.canonical()));

        OperationalMetricsEntity entity = cached.orElseGet(() -> orchestrator.collectAndStore(id, null));
        MetricsResponse.VersionInfo versionInfo = buildVersionInfo(id, entity, requestedVersion);
        return toResponse(entity, versionInfo);
    }

    /**
     * Resolves the requested version (if any) to a {@link MetricsResponse.VersionInfo}.
     * Calls {@link RepoMetaAnalyzer#findOrFetchByVersion} which is cache-first;
     * remote lookup only on miss. Returns null when no version was requested.
     */
    private MetricsResponse.VersionInfo buildVersionInfo(PackageId id, OperationalMetricsEntity entity, String version) {
        if (version == null || entity.getPackageId() == null) return null;
        try {
            Optional<PackageVersionEntry> entry = repoMetaAnalyzer.findOrFetchByVersion(
                    id, entity.getPackageId(), version);
            if (entry.isEmpty()) return null;
            PackageVersionEntry e = entry.get();
            Long daysSinceRelease = e.releasedAt() == null ? null
                    : Duration.between(e.releasedAt(), Instant.now()).toDays();
            Long daysOlderThanLatest = (e.releasedAt() != null && entity.getLastReleaseAt() != null)
                    ? Duration.between(e.releasedAt(), entity.getLastReleaseAt()).toDays()
                    : null;
            return new MetricsResponse.VersionInfo(e.version(), e.releasedAt(),
                    e.resolvedVia(), daysSinceRelease, daysOlderThanLatest);
        } catch (Exception ex) {
            LOG.debugv("Version-info lookup failed for {0}@{1}: {2}",
                    id.canonical(), version, ex.getMessage());
            return null;
        }
    }

    public static MetricsResponse toResponse(OperationalMetricsEntity e) {
        return toResponse(e, null);
    }

    public static MetricsResponse toResponse(OperationalMetricsEntity e, MetricsResponse.VersionInfo versionInfo) {
        return new MetricsResponse(
                e.getPurlCanonical(),
                e.getPurlType(),
                e.getPurlNamespace(),
                e.getPurlName(),
                e.getRepoUrl(),
                new MetricsResponse.ScorecardInfo(
                        e.getScorecardOverallScore(), e.getScorecardChecks(), e.getScorecardDate()),
                new MetricsResponse.PopularityInfo(e.getRankingPercentile()),
                new MetricsResponse.ActivityInfo(
                        e.getLastCommitAt(), e.getLastReleaseAt(),
                        e.getLastReleaseVersion(), e.getLastReleaseVersionSource(),
                        e.getFirstReleaseAt(),
                        e.getContributorCount(),
                        e.getIsArchived(), e.getIsDeprecated(),
                        e.getSnykRating()),
                new MetricsResponse.CommunityInfo(
                        e.getCommunityHealthPct(), e.getAvgIssueCloseTimeDays(), e.getAvgPrCloseTimeDays()),
                new MetricsResponse.SecurityInfo(e.getAdvisoryCount()),
                e.getSourcesUsed(),
                versionInfo,
                e.getFetchedAt()
        );
    }
}
