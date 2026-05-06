package com.example.operationalmetrics.service;

import com.example.operationalmetrics.client.dependencytrack.DependencyTrackClient;
import com.example.operationalmetrics.client.dependencytrack.dto.DtComponent;
import com.example.operationalmetrics.client.dependencytrack.dto.DtProject;
import com.example.operationalmetrics.config.DependencyTrackConfig;
import com.example.operationalmetrics.config.SyncConfig;
import com.example.operationalmetrics.model.PackageId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@ApplicationScoped
public class PurlSyncService {

    private static final Logger LOG = Logger.getLogger(PurlSyncService.class);

    private final DependencyTrackClient dtClient;
    private final DependencyTrackConfig dtConfig;
    private final SyncConfig syncConfig;
    private final MetricsOrchestrator orchestrator;
    private final AtomicReference<SyncStatus> currentStatus = new AtomicReference<>(SyncStatus.idle());

    @Inject
    public PurlSyncService(@RestClient DependencyTrackClient dtClient,
                           DependencyTrackConfig dtConfig,
                           SyncConfig syncConfig,
                           MetricsOrchestrator orchestrator) {
        this.dtClient = dtClient;
        this.dtConfig = dtConfig;
        this.syncConfig = syncConfig;
        this.orchestrator = orchestrator;
    }

    public void triggerAsync() {
        SyncStatus snapshot = currentStatus.get();
        if (snapshot.isRunning()) {
            LOG.info("Sync already in progress, skipping");
            return;
        }
        if (!currentStatus.compareAndSet(snapshot, SyncStatus.running())) {
            LOG.info("Sync already in progress, skipping");
            return;
        }
        Thread.startVirtualThread(this::runFullSync);
    }

    /**
     * Streaming sync: walks Dependency Track pages and submits per-PURL work
     * as soon as it's seen, rather than buffering the whole catalogue first.
     *
     * <p>A {@link Semaphore} sized to {@code syncConfig.concurrency()} caps the
     * number of in-flight orchestrator tasks. When all permits are held, the
     * DT-walking thread blocks at {@code acquire()} — DT pagination naturally
     * throttles to match downstream throughput. No bounded queue needed.
     *
     * <p>Cross-project deduplication is done via a thin {@code Set<PackageId>}
     * (only the identity tuple, ~3MB at 19.6K PURLs).
     */
    public void runFullSync() {
        UUID syncRunId = UUID.randomUUID();
        Instant startTime = Instant.now();
        currentStatus.set(SyncStatus.running());
        LOG.infov("Starting streaming full sync, run ID: {0}", syncRunId);

        Set<PackageId> seen = ConcurrentHashMap.newKeySet();
        AtomicInteger discovered = new AtomicInteger();
        AtomicInteger processed = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger duplicatesSkipped = new AtomicInteger();
        Semaphore concurrencyLimit = new Semaphore(syncConfig.concurrency());
        List<Future<?>> outstanding = new ArrayList<>();

        Exception walkError = null;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                walkDependencyTrack(purl -> {
                    PackageId pkg;
                    try {
                        pkg = PackageId.fromPurl(purl);
                    } catch (Exception e) {
                        LOG.debugv("Invalid PURL from DT: {0}", purl);
                        return;
                    }
                    if (!seen.add(pkg)) {
                        duplicatesSkipped.incrementAndGet();
                        return;
                    }
                    discovered.incrementAndGet();

                    try {
                        concurrencyLimit.acquire();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Sync interrupted while waiting for capacity", e);
                    }

                    outstanding.add(executor.submit(() -> {
                        try {
                            orchestrator.collectAndStore(pkg, syncRunId);
                            processed.incrementAndGet();
                        } catch (Exception e) {
                            failed.incrementAndGet();
                            LOG.warnv("Sync failed for {0}: {1}", pkg.canonical(), e.getMessage());
                        } finally {
                            concurrencyLimit.release();
                        }
                    }));
                });
            } catch (Exception e) {
                walkError = e;
                LOG.error("DT pagination failed; awaiting in-flight tasks before reporting status", e);
            }
            // The try-with-resources close() on the virtual-thread executor blocks
            // until every submitted task completes. No manual future.get() loop.
        }

        if (walkError != null) {
            currentStatus.set(SyncStatus.failed(walkError.getMessage(), startTime));
            return;
        }

        currentStatus.set(SyncStatus.completed(
                discovered.get(), processed.get(), failed.get(), startTime, Instant.now()));
        LOG.infov("Sync completed: {0} discovered ({1} duplicates skipped), {2} processed, {3} failed",
                discovered.get(), duplicatesSkipped.get(), processed.get(), failed.get());
    }

    public SyncStatus getStatus() {
        return currentStatus.get();
    }

    /**
     * Streaming walk of Dependency Track. Emits each component's PURL string
     * to the consumer as soon as it's seen, then discards the underlying
     * {@link DtComponent} so it's eligible for GC immediately.
     */
    private void walkDependencyTrack(Consumer<String> onPurl) {
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
                            onPurl.accept(comp.purl());
                        }
                    }
                    compPage++;
                }
            }
            page++;
        }
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
        public boolean isRunning() {
            return "RUNNING".equals(state);
        }

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
