package com.example.operationalmetrics.service;

import com.example.operationalmetrics.config.SourceConfig;
import com.example.operationalmetrics.model.*;
import com.example.operationalmetrics.repository.MetricsFetchLogDao;
import com.example.operationalmetrics.repository.OperationalMetricsDao;
import com.example.operationalmetrics.repository.MetricsHistoryDao;
import com.example.operationalmetrics.repository.PackageDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jdbi.v3.core.Jdbi;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class MetricsOrchestrator {

    private static final Logger LOG = Logger.getLogger(MetricsOrchestrator.class);

    private final SourceConfig sourceConfig;
    private final Map<MetricsSource, MetricsCollector> collectors;
    private final RepoUrlResolver repoUrlResolver;
    private final Jdbi jdbi;

    @Inject
    public MetricsOrchestrator(SourceConfig sourceConfig,
                               Instance<MetricsCollector> collectorInstances,
                               RepoUrlResolver repoUrlResolver,
                               Jdbi jdbi) {
        this.sourceConfig = sourceConfig;
        this.repoUrlResolver = repoUrlResolver;
        this.jdbi = jdbi;

        this.collectors = new HashMap<>();
        for (MetricsCollector collector : collectorInstances) {
            this.collectors.put(collector.source(), collector);
        }
    }

    public OperationalMetricsEntity collectAndStore(PackageId packageId, UUID syncRunId) {
        Long packageDbId = jdbi.withExtension(PackageDao.class, dao -> {
            var existing = dao.findByCanonical(packageId.canonical());
            if (existing.isPresent()) {
                return existing.get().getId();
            }
            return dao.upsert(packageId.toEntity());
        });

        Optional<RepoUrl> repoUrl = repoUrlResolver.resolve(packageId, packageDbId);

        List<String> orderedSources = sourceConfig.enabledSourcesByPriority();
        PartialMetrics merged = new PartialMetrics();
        List<MetricsFetchLog> logs = new ArrayList<>();
        List<String> sourcesUsed = new ArrayList<>();

        for (String sourceKey : orderedSources) {
            MetricsSource source;
            try {
                source = MetricsSource.fromConfigKey(sourceKey);
            } catch (IllegalArgumentException e) {
                LOG.warnv("Unknown source config key: {0}", sourceKey);
                continue;
            }

            MetricsCollector collector = collectors.get(source);
            if (collector == null) {
                continue;
            }

            if (!collector.supports(packageId)) {
                logs.add(MetricsFetchLog.skipped(packageDbId, source, "Unsupported package type"));
                continue;
            }

            if (collector.requiresRepoUrl() && repoUrl.isEmpty()) {
                logs.add(MetricsFetchLog.skipped(packageDbId, source, "No repo URL available"));
                continue;
            }

            try {
                long start = System.currentTimeMillis();
                PartialMetrics partial = collector.collect(packageId, repoUrl);
                int durationMs = (int) (System.currentTimeMillis() - start);

                if (partial.getRepoUrl() != null && repoUrl.isEmpty()) {
                    repoUrl = Optional.of(partial.getRepoUrl());
                    repoUrlResolver.cache(packageDbId, partial.getRepoUrl(), source);
                }

                merged.mergeFrom(partial);
                sourcesUsed.add(source.name());
                logs.add(MetricsFetchLog.success(packageDbId, source, durationMs));
            } catch (Exception e) {
                Integer httpStatus = extractHttpStatus(e);
                logs.add(MetricsFetchLog.failed(packageDbId, source, httpStatus, e.getMessage()));
                LOG.warnv("Collector {0} failed for {1}: {2}", source, packageId.canonical(), e.getMessage());
            }
        }

        OperationalMetricsEntity entity = buildEntity(packageDbId, packageId, merged, sourcesUsed, repoUrl);

        final UUID runId = syncRunId != null ? syncRunId : UUID.randomUUID();
        jdbi.useTransaction(handle -> {
            handle.attach(OperationalMetricsDao.class).upsert(entity);
            handle.attach(MetricsHistoryDao.class).insert(entity, runId);
            for (MetricsFetchLog log : logs) {
                handle.attach(MetricsFetchLogDao.class).insert(log);
            }
        });

        return entity;
    }

    private OperationalMetricsEntity buildEntity(Long packageDbId, PackageId packageId,
                                                  PartialMetrics merged, List<String> sourcesUsed,
                                                  Optional<RepoUrl> repoUrl) {
        var entity = new OperationalMetricsEntity();
        entity.setPackageId(packageDbId);
        entity.setPurlType(packageId.purlType());
        entity.setPurlNamespace(packageId.namespace());
        entity.setPurlName(packageId.name());
        entity.setPurlCanonical(packageId.canonical());

        repoUrl.ifPresent(r -> {
            entity.setRepoUrl(r.url());
            entity.setRepoPlatform(r.platform());
            entity.setRepoOwner(r.owner());
            entity.setRepoName(r.name());
        });

        entity.setScorecardOverallScore(merged.getScorecardOverallScore());
        entity.setScorecardChecks(merged.getScorecardChecks());
        entity.setScorecardDate(merged.getScorecardDate());
        entity.setScorecardSource(merged.getScorecardSource());

        entity.setStarsCount(merged.getStarsCount());
        entity.setForksCount(merged.getForksCount());
        entity.setDependentReposCount(merged.getDependentReposCount());
        entity.setDependentPackagesCount(merged.getDependentPackagesCount());
        entity.setDownloadCount(merged.getDownloadCount());
        entity.setDownloadPeriod(merged.getDownloadPeriod());
        entity.setRankingPercentile(merged.getRankingPercentile());

        entity.setLastCommitAt(merged.getLastCommitAt());
        entity.setLastReleaseAt(merged.getLastReleaseAt());
        entity.setCommitFrequency52w(merged.getCommitFrequency52w());
        entity.setContributorCount(merged.getContributorCount());
        entity.setIsArchived(merged.getIsArchived());
        entity.setIsDeprecated(merged.getIsDeprecated());

        entity.setCommunityHealthPct(merged.getCommunityHealthPct());
        entity.setAvgIssueCloseTimeDays(merged.getAvgIssueCloseTimeDays());
        entity.setAvgPrCloseTimeDays(merged.getAvgPrCloseTimeDays());
        entity.setPrAuthorsCount(merged.getPrAuthorsCount());
        entity.setMergedPrCount(merged.getMergedPrCount());
        entity.setOpenIssuesCount(merged.getOpenIssuesCount());
        entity.setOpenPrCount(merged.getOpenPrCount());

        entity.setAdvisoryCount(merged.getAdvisoryCount());
        entity.setHasSlsaProvenance(merged.getHasSlsaProvenance());
        entity.setHasOssFuzz(merged.getHasOssFuzz());

        entity.setMaintainerCount(merged.getMaintainerCount());
        entity.setLicense(merged.getLicense());

        entity.setSourcesUsed(sourcesUsed);
        entity.setFetchedAt(Instant.now());

        return entity;
    }

    private Integer extractHttpStatus(Exception e) {
        if (e instanceof jakarta.ws.rs.WebApplicationException wae) {
            return wae.getResponse().getStatus();
        }
        return null;
    }
}
