package com.example.operationalmetrics.service;

import com.example.operationalmetrics.config.ApiConfig;
import com.example.operationalmetrics.dto.MetricsBulkResponse;
import com.example.operationalmetrics.dto.MetricsResponse;
import com.example.operationalmetrics.model.OperationalMetricsEntity;
import com.example.operationalmetrics.model.PackageId;
import com.example.operationalmetrics.repository.OperationalMetricsDao;
import com.example.operationalmetrics.repository.PackageDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jdbi.v3.core.Jdbi;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.concurrent.*;

@ApplicationScoped
public class MetricsQueryService {

    private static final Logger LOG = Logger.getLogger(MetricsQueryService.class);

    private final Jdbi jdbi;
    private final MetricsOrchestrator orchestrator;
    private final ApiConfig apiConfig;

    @Inject
    public MetricsQueryService(Jdbi jdbi, MetricsOrchestrator orchestrator, ApiConfig apiConfig) {
        this.jdbi = jdbi;
        this.orchestrator = orchestrator;
        this.apiConfig = apiConfig;
    }

    public MetricsResponse findByPurl(String purlString) {
        PackageId id = PackageId.fromPurl(purlString);
        return findOrFetch(id);
    }

    public MetricsResponse findByCoordinates(String type, String namespace, String name) {
        PackageId id = new PackageId(type, namespace, name);
        return findOrFetch(id);
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

    private MetricsResponse findOrFetch(PackageId id) {
        Optional<OperationalMetricsEntity> cached = jdbi.withExtension(OperationalMetricsDao.class,
                dao -> dao.findByCanonical(id.canonical()));

        if (cached.isPresent()) {
            return toResponse(cached.get());
        }

        OperationalMetricsEntity entity = orchestrator.collectAndStore(id, null);
        return toResponse(entity);
    }

    public static MetricsResponse toResponse(OperationalMetricsEntity e) {
        return new MetricsResponse(
                e.getPurlCanonical(),
                e.getPurlType(),
                e.getPurlNamespace(),
                e.getPurlName(),
                e.getRepoUrl(),
                new MetricsResponse.ScorecardInfo(
                        e.getScorecardOverallScore(), e.getScorecardChecks(), e.getScorecardDate()),
                new MetricsResponse.PopularityInfo(
                        e.getStarsCount(), e.getForksCount(), e.getDownloadCount(), e.getRankingPercentile()),
                new MetricsResponse.ActivityInfo(
                        e.getLastCommitAt(), e.getLastReleaseAt(), e.getContributorCount(),
                        e.getIsArchived(), e.getIsDeprecated()),
                new MetricsResponse.CommunityInfo(
                        e.getCommunityHealthPct(), e.getAvgIssueCloseTimeDays(), e.getAvgPrCloseTimeDays(),
                        e.getPrAuthorsCount(), e.getMergedPrCount()),
                new MetricsResponse.SecurityInfo(
                        e.getAdvisoryCount(), e.getHasSlsaProvenance(), e.getHasOssFuzz()),
                new MetricsResponse.DependentsInfo(
                        e.getDependentReposCount(), e.getDependentPackagesCount()),
                e.getMaintainerCount(),
                e.getLicense(),
                e.getSourcesUsed(),
                e.getFetchedAt()
        );
    }
}
