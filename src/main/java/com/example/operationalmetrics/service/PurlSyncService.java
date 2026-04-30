package com.example.operationalmetrics.service;

import com.example.operationalmetrics.client.dependencytrack.DependencyTrackClient;
import com.example.operationalmetrics.client.dependencytrack.dto.DtComponent;
import com.example.operationalmetrics.client.dependencytrack.dto.DtProject;
import com.example.operationalmetrics.config.DependencyTrackConfig;
import com.example.operationalmetrics.config.SyncConfig;
import com.example.operationalmetrics.model.PackageId;
import com.example.operationalmetrics.repository.PackageDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jdbi.v3.core.Jdbi;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class PurlSyncService {

    private static final Logger LOG = Logger.getLogger(PurlSyncService.class);

    private final DependencyTrackClient dtClient;
    private final DependencyTrackConfig dtConfig;
    private final SyncConfig syncConfig;
    private final MetricsOrchestrator orchestrator;
    private final Jdbi jdbi;
    private final AtomicReference<SyncStatus> currentStatus = new AtomicReference<>(SyncStatus.idle());

    @Inject
    public PurlSyncService(@RestClient DependencyTrackClient dtClient,
                           DependencyTrackConfig dtConfig,
                           SyncConfig syncConfig,
                           MetricsOrchestrator orchestrator,
                           Jdbi jdbi) {
        this.dtClient = dtClient;
        this.dtConfig = dtConfig;
        this.syncConfig = syncConfig;
        this.orchestrator = orchestrator;
        this.jdbi = jdbi;
    }

    public void triggerAsync() {
        if (!currentStatus.compareAndSet(currentStatus.get(),
                currentStatus.get().isRunning() ? currentStatus.get() : SyncStatus.running())) {
            if (currentStatus.get().isRunning()) {
                LOG.info("Sync already in progress, skipping");
                return;
            }
        }
        Thread.startVirtualThread(this::runFullSync);
    }

    public void runFullSync() {
        UUID syncRunId = UUID.randomUUID();
        Instant startTime = Instant.now();
        currentStatus.set(SyncStatus.running());
        LOG.infov("Starting full sync, run ID: {0}", syncRunId);

        try {
            Set<PackageId> allPackages = collectAllPurls();
            LOG.infov("Collected {0} unique packages from Dependency Track", allPackages.size());

            jdbi.useExtension(PackageDao.class, dao -> {
                for (PackageId pkg : allPackages) {
                    dao.upsert(pkg.toEntity());
                }
            });

            AtomicInteger processed = new AtomicInteger(0);
            AtomicInteger failed = new AtomicInteger(0);
            List<List<PackageId>> batches = partition(new ArrayList<>(allPackages), syncConfig.batchSize());
            ExecutorService executor = Executors.newFixedThreadPool(syncConfig.concurrency());

            try {
                for (List<PackageId> batch : batches) {
                    List<Future<?>> futures = new ArrayList<>();
                    for (PackageId pkg : batch) {
                        futures.add(executor.submit(() -> {
                            try {
                                orchestrator.collectAndStore(pkg, syncRunId);
                                processed.incrementAndGet();
                            } catch (Exception e) {
                                failed.incrementAndGet();
                                LOG.warnv("Sync failed for {0}: {1}", pkg.canonical(), e.getMessage());
                            }
                        }));
                    }

                    for (Future<?> future : futures) {
                        try {
                            future.get(120, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            failed.incrementAndGet();
                        }
                    }

                    Thread.sleep(syncConfig.rateLimitDelayMs());
                }
            } finally {
                executor.shutdown();
            }

            currentStatus.set(SyncStatus.completed(allPackages.size(), processed.get(), failed.get(),
                    startTime, Instant.now()));
            LOG.infov("Sync completed: {0} total, {1} processed, {2} failed",
                    allPackages.size(), processed.get(), failed.get());

        } catch (Exception e) {
            currentStatus.set(SyncStatus.failed(e.getMessage(), startTime));
            LOG.error("Sync failed", e);
        }
    }

    public SyncStatus getStatus() {
        return currentStatus.get();
    }

    private Set<PackageId> collectAllPurls() {
        Set<PackageId> packages = new LinkedHashSet<>();
        int page = 1;

        while (true) {
            List<DtProject> projects = dtClient.listProjects(page, dtConfig.pageSize());
            if (projects.isEmpty()) break;

            for (DtProject project : projects) {
                int compPage = 1;
                while (true) {
                    List<DtComponent> components = dtClient.listComponents(
                            project.uuid(), compPage, dtConfig.pageSize());
                    if (components.isEmpty()) break;

                    for (DtComponent comp : components) {
                        if (comp.purl() != null) {
                            try {
                                packages.add(PackageId.fromPurl(comp.purl()));
                            } catch (Exception e) {
                                LOG.debugv("Invalid PURL from DT: {0}", comp.purl());
                            }
                        }
                    }
                    compPage++;
                }
            }
            page++;
        }

        return packages;
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    public record SyncStatus(
            String state,
            Integer totalPackages,
            Integer processedPackages,
            Integer failedPackages,
            Instant startedAt,
            Instant completedAt,
            String errorMessage
    ) {
        public boolean isRunning() { return "RUNNING".equals(state); }

        public static SyncStatus idle() {
            return new SyncStatus("IDLE", null, null, null, null, null, null);
        }

        public static SyncStatus running() {
            return new SyncStatus("RUNNING", null, null, null, Instant.now(), null, null);
        }

        public static SyncStatus completed(int total, int processed, int failed, Instant start, Instant end) {
            return new SyncStatus("COMPLETED", total, processed, failed, start, end, null);
        }

        public static SyncStatus failed(String error, Instant start) {
            return new SyncStatus("FAILED", null, null, null, start, Instant.now(), error);
        }
    }
}
