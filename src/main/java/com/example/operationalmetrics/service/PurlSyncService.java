package com.example.operationalmetrics.service;

import com.example.operationalmetrics.client.dependencytrack.DependencyTrackClient;
import com.example.operationalmetrics.client.dependencytrack.dto.DtComponent;
import com.example.operationalmetrics.client.dependencytrack.dto.DtProject;
import com.example.operationalmetrics.config.DependencyTrackConfig;
import com.example.operationalmetrics.config.RefreshSyncConfig;
import com.example.operationalmetrics.config.SyncConfig;
import com.example.operationalmetrics.model.PackageEntity;
import com.example.operationalmetrics.model.PackageId;
import com.example.operationalmetrics.repository.PackageDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import org.jdbi.v3.core.Jdbi;

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

/**
 * Three sync modes for {@code operational_metrics}, all sharing the same
 * streaming + Semaphore-bounded virtual-thread executor pattern:
 *
 * <ul>
 *   <li><b>Full</b> ({@link #runFullSync}) — walks Dependency Track, submits
 *       every (deduped) PURL to the orchestrator. Discovery + refresh in one.
 *   <li><b>Refresh</b> ({@link #runRefresh}) — iterates the existing
 *       {@code package} table (no DT walk), optionally filtered by staleness,
 *       and re-runs the orchestrator for each.
 *   <li><b>Discovery</b> ({@link #runDiscovery}) — walks DT and upserts
 *       {@code package} rows but does NOT call the orchestrator. Cheap; new
 *       PURLs sit in the DB until on-demand fetch or the next refresh/full.
 * </ul>
 */
@ApplicationScoped
public class PurlSyncService {

    private static final Logger LOG = Logger.getLogger(PurlSyncService.class);

    private final DependencyTrackClient dtClient;
    private final DependencyTrackConfig dtConfig;
    private final SyncConfig syncConfig;
    private final RefreshSyncConfig refreshConfig;
    private final MetricsOrchestrator orchestrator;
    private final Jdbi jdbi;
    private final AtomicReference<SyncStatus> currentStatus = new AtomicReference<>(SyncStatus.idle());

    @Inject
    public PurlSyncService(@RestClient DependencyTrackClient dtClient,
                           DependencyTrackConfig dtConfig,
                           SyncConfig syncConfig,
                           RefreshSyncConfig refreshConfig,
                           MetricsOrchestrator orchestrator,
                           Jdbi jdbi) {
        this.dtClient = dtClient;
        this.dtConfig = dtConfig;
        this.syncConfig = syncConfig;
        this.refreshConfig = refreshConfig;
        this.orchestrator = orchestrator;
        this.jdbi = jdbi;
    }

    // -----------------------------------------------------------------------
    // Async triggers
    // -----------------------------------------------------------------------

    public void triggerAsync() {
        triggerInVirtualThread(this::runFullSync);
    }

    public void triggerRefreshAsync() {
        triggerInVirtualThread(this::runRefresh);
    }

    public void triggerDiscoveryAsync() {
        triggerInVirtualThread(this::runDiscovery);
    }

    private void triggerInVirtualThread(Runnable target) {
        SyncStatus snapshot = currentStatus.get();
        if (snapshot.isRunning()) {
            LOG.info("Sync already in progress, skipping");
            return;
        }
        if (!currentStatus.compareAndSet(snapshot, SyncStatus.running(snapshot.mode()))) {
            LOG.info("Sync already in progress, skipping");
            return;
        }
        Thread.startVirtualThread(target);
    }

    public SyncStatus getStatus() {
        return currentStatus.get();
    }

    // -----------------------------------------------------------------------
    // Mode implementations
    // -----------------------------------------------------------------------

    /**
     * Full sync: DT walk + orchestrator per PURL. Backwards-compatible with
     * the pre-modes behaviour (existing callers and the
     * {@code metrics.sync.cron} scheduler keep firing this).
     */
    public void runFullSync() {
        UUID syncRunId = UUID.randomUUID();
        Instant startTime = Instant.now();
        currentStatus.set(SyncStatus.running("FULL"));
        LOG.infov("Starting FULL sync, run ID: {0}", syncRunId);

        Set<PackageId> seen = ConcurrentHashMap.newKeySet();
        AtomicInteger duplicatesSkipped = new AtomicInteger();

        StreamingResult result = streamingRun("FULL", syncConfig.concurrency(), (executor, limit, counters) -> {
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
                counters.discovered.incrementAndGet();
                submitTask(executor, limit, counters, () -> orchestrator.collectAndStore(pkg, syncRunId), pkg.canonical());
            });
        }, startTime);

        finalizeStatus("FULL", result, startTime,
                () -> LOG.infov("FULL sync completed: {0} discovered ({1} dupes), {2} processed, {3} failed",
                        result.counters.discovered.get(), duplicatesSkipped.get(),
                        result.counters.processed.get(), result.counters.failed.get()));
    }

    /**
     * Refresh-only: iterate {@code package} table (filtered by staleness),
     * call orchestrator for each. No DT walk.
     */
    public void runRefresh() {
        UUID syncRunId = UUID.randomUUID();
        Instant startTime = Instant.now();
        currentStatus.set(SyncStatus.running("REFRESH"));
        int stalenessDays = refreshConfig.stalenessDays();
        LOG.infov("Starting REFRESH sync, run ID: {0}, staleness >= {1}d", syncRunId, stalenessDays);

        StreamingResult result = streamingRun("REFRESH", refreshConfig.concurrency(),
                (executor, limit, counters) -> {
                    List<PackageEntity> stale = jdbi.withExtension(PackageDao.class,
                            dao -> dao.findStalePackages(stalenessDays));
                    for (PackageEntity entity : stale) {
                        counters.discovered.incrementAndGet();
                        PackageId pkg = entity.toPackageId();
                        submitTask(executor, limit, counters,
                                () -> orchestrator.collectAndStore(pkg, syncRunId), pkg.canonical());
                    }
                }, startTime);

        finalizeStatus("REFRESH", result, startTime,
                () -> LOG.infov("REFRESH sync completed: {0} discovered, {1} processed, {2} failed",
                        result.counters.discovered.get(), result.counters.processed.get(),
                        result.counters.failed.get()));
    }

    /**
     * Discovery-only: walk DT, upsert {@code package} rows. No orchestrator call.
     */
    public void runDiscovery() {
        Instant startTime = Instant.now();
        currentStatus.set(SyncStatus.running("DISCOVERY"));
        LOG.infov("Starting DISCOVERY sync");

        Set<PackageId> seen = ConcurrentHashMap.newKeySet();
        AtomicInteger duplicatesSkipped = new AtomicInteger();

        StreamingResult result = streamingRun("DISCOVERY", syncConfig.concurrency(),
                (executor, limit, counters) -> {
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
                        counters.discovered.incrementAndGet();
                        submitTask(executor, limit, counters,
                                () -> jdbi.useExtension(PackageDao.class, dao -> dao.upsert(pkg.toEntity())),
                                pkg.canonical());
                    });
                }, startTime);

        finalizeStatus("DISCOVERY", result, startTime,
                () -> LOG.infov("DISCOVERY sync completed: {0} discovered ({1} dupes), {2} processed, {3} failed",
                        result.counters.discovered.get(), duplicatesSkipped.get(),
                        result.counters.processed.get(), result.counters.failed.get()));
    }

    // -----------------------------------------------------------------------
    // Streaming helper — Semaphore-bounded virtual-thread fan-out
    // -----------------------------------------------------------------------

    /**
     * Runs a producer that submits work via {@link #submitTask}, blocks on
     * the try-with-resources executor close until every submitted task
     * finishes, and returns aggregate counters + any walk exception.
     */
    private StreamingResult streamingRun(String mode, int concurrency, ProducerFn producer, Instant startTime) {
        Counters counters = new Counters();
        Semaphore limit = new Semaphore(concurrency);
        Exception walkError = null;

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                producer.produce(executor, limit, counters);
            } catch (Exception e) {
                walkError = e;
                LOG.errorv("{0} producer failed; awaiting in-flight tasks before reporting status: {1}",
                        mode, e.getMessage());
            }
            // try-with-resources close() blocks until every submitted virtual thread completes.
        }

        return new StreamingResult(counters, walkError);
    }

    private void submitTask(ExecutorService executor, Semaphore limit, Counters counters,
                            Runnable work, String packageLabel) {
        try {
            limit.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Sync interrupted while waiting for capacity", e);
        }
        Future<?> future = executor.submit(() -> {
            try {
                work.run();
                counters.processed.incrementAndGet();
            } catch (Exception e) {
                counters.failed.incrementAndGet();
                LOG.warnv("Sync failed for {0}: {1}", packageLabel, e.getMessage());
            } finally {
                limit.release();
            }
        });
        counters.outstanding.add(future);
    }

    private void finalizeStatus(String mode, StreamingResult result, Instant startTime, Runnable logSummary) {
        if (result.walkError != null) {
            currentStatus.set(SyncStatus.failed(mode, result.walkError.getMessage(), startTime));
            return;
        }
        currentStatus.set(SyncStatus.completed(
                mode,
                result.counters.discovered.get(),
                result.counters.processed.get(),
                result.counters.failed.get(),
                startTime,
                Instant.now()));
        logSummary.run();
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

    @FunctionalInterface
    private interface ProducerFn {
        void produce(ExecutorService executor, Semaphore limit, Counters counters);
    }

    private static final class Counters {
        final AtomicInteger discovered = new AtomicInteger();
        final AtomicInteger processed = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();
        final List<Future<?>> outstanding = new ArrayList<>();
    }

    private record StreamingResult(Counters counters, Exception walkError) {}

    /**
     * Sync status. The {@code mode} field tells callers which of the three
     * trigger paths produced this status; {@code state} is the lifecycle
     * stage within that mode (IDLE / RUNNING / COMPLETED / FAILED).
     */
    public record SyncStatus(
            String state,
            String mode,
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
            return new SyncStatus("IDLE", null, null, null, null, null, null, null);
        }

        public static SyncStatus running(String mode) {
            return new SyncStatus("RUNNING", mode, null, null, null, Instant.now(), null, null);
        }

        public static SyncStatus completed(String mode, int total, int processed, int failed,
                                           Instant start, Instant end) {
            return new SyncStatus("COMPLETED", mode, total, processed, failed, start, end, null);
        }

        public static SyncStatus failed(String mode, String error, Instant start) {
            return new SyncStatus("FAILED", mode, null, null, null, start, Instant.now(), error);
        }
    }
}
