package com.example.operationalmetrics.service;

import com.example.operationalmetrics.config.VersionsSyncConfig;
import com.example.operationalmetrics.model.PackageEntity;
import com.example.operationalmetrics.repository.PackageDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.jdbi.v3.core.Jdbi;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Discovers new {@code package_version} rows on a cadence decoupled from
 * {@code operational_metrics} refresh. Calls {@link RepoMetaAnalyzer#analyze}
 * for each package whose {@code latest_versions_polled_at} is older than
 * {@link VersionsSyncConfig#stalenessDays()}.
 *
 * <p>At ~9M packages a 2-day full sweep isn't feasible from upstream rate
 * limits alone. This class is the safety-net polling component; the
 * registry-changes-feed primary path (which catches new releases within
 * minutes) ships in a follow-up PR. The two are designed to share this
 * service's analyze-via-token-bucket pipeline.
 *
 * <p><b>Pacing:</b> token-bucket cap of {@code rateBudgetPerSecond} requests.
 * Concurrency cap of {@code concurrency} in-flight tasks (virtual threads).
 * Both apply simultaneously — concurrency caps thread fan-out for memory,
 * token bucket caps outgoing API rate independent of how many threads exist.
 */
@ApplicationScoped
public class VersionsSyncService {

    private static final Logger LOG = Logger.getLogger(VersionsSyncService.class);

    private final VersionsSyncConfig config;
    private final RepoMetaAnalyzer repoMetaAnalyzer;
    private final Jdbi jdbi;
    private final AtomicReference<SweepStatus> currentStatus = new AtomicReference<>(SweepStatus.idle());

    @Inject
    public VersionsSyncService(VersionsSyncConfig config,
                               RepoMetaAnalyzer repoMetaAnalyzer,
                               Jdbi jdbi) {
        this.config = config;
        this.repoMetaAnalyzer = repoMetaAnalyzer;
        this.jdbi = jdbi;
    }

    public void triggerAsync() {
        SweepStatus snapshot = currentStatus.get();
        if (snapshot.isRunning()) {
            LOG.info("Versions sweep already in progress, skipping");
            return;
        }
        if (!currentStatus.compareAndSet(snapshot, SweepStatus.running())) {
            return;
        }
        Thread.startVirtualThread(this::runSweep);
    }

    public SweepStatus getStatus() {
        return currentStatus.get();
    }

    /**
     * Walk the package table for due-to-poll packages, run analyze for each
     * within concurrency + rate limits.
     */
    public void runSweep() {
        if (!config.enabled()) {
            LOG.info("Versions sweep is disabled; skipping run");
            currentStatus.set(SweepStatus.idle());
            return;
        }

        Instant startTime = Instant.now();
        currentStatus.set(SweepStatus.running());
        LOG.infov("Starting versions sweep (staleness>={0}d, batchSize={1}, concurrency={2}, rate={3}/s)",
                config.stalenessDays(), config.batchSize(), config.concurrency(), config.rateBudgetPerSecond());

        AtomicInteger discovered = new AtomicInteger();
        AtomicInteger processed = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        Semaphore concurrencyLimit = new Semaphore(config.concurrency());
        List<Future<?>> outstanding = new ArrayList<>();

        Exception walkError = null;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
             TokenBucket rateLimit = new TokenBucket(config.rateBudgetPerSecond())) {
            try {
                List<PackageEntity> due = jdbi.withExtension(PackageDao.class,
                        dao -> dao.findPackagesDuePoll(config.stalenessDays(), config.batchSize()));
                for (PackageEntity entity : due) {
                    discovered.incrementAndGet();
                    if (entity.getId() == null) {
                        skipped.incrementAndGet();
                        continue;
                    }

                    try {
                        concurrencyLimit.acquire();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Versions sweep interrupted while waiting for capacity", e);
                    }

                    final PackageEntity pkgEntity = entity;
                    outstanding.add(executor.submit(() -> {
                        try {
                            rateLimit.acquireOrBlock();
                            repoMetaAnalyzer.analyze(pkgEntity.toPackageId(), pkgEntity.getId());
                            processed.incrementAndGet();
                        } catch (Exception e) {
                            failed.incrementAndGet();
                            LOG.warnv("Versions analyze failed for {0}: {1}",
                                    pkgEntity.getPurlCanonical(), e.getMessage());
                        } finally {
                            concurrencyLimit.release();
                        }
                    }));
                }
            } catch (Exception e) {
                walkError = e;
                LOG.errorv("Versions sweep producer failed; awaiting in-flight tasks: {0}", e.getMessage());
            }
            // try-with-resources close() blocks until every task completes,
            // then closes the rate limiter (stops its refill thread).
        }

        if (walkError != null) {
            currentStatus.set(SweepStatus.failed(walkError.getMessage(), startTime));
            return;
        }
        currentStatus.set(SweepStatus.completed(
                discovered.get(), processed.get(), failed.get(), skipped.get(),
                startTime, Instant.now()));
        LOG.infov("Versions sweep completed: {0} discovered, {1} processed, {2} failed, {3} skipped",
                discovered.get(), processed.get(), failed.get(), skipped.get());
    }

    /**
     * Simple in-process token bucket. Capacity = {@code permitsPerSecond}
     * (one second of burst allowance). A daemon thread releases one permit
     * every {@code 1000/permitsPerSecond} ms up to capacity. Consumers
     * {@link Semaphore#acquire()} as normal — no shared lock between consumers,
     * so concurrency stays parallel.
     *
     * <p>Single-instance only. If you scale horizontally, replace with a
     * distributed rate limiter (Redis-backed, etc.).
     */
    private static final class TokenBucket implements AutoCloseable {
        private final int capacity;
        private final Semaphore permits;
        private final Thread refiller;
        private volatile boolean stop = false;

        TokenBucket(int permitsPerSecond) {
            this.capacity = Math.max(1, permitsPerSecond);
            this.permits = new Semaphore(this.capacity, true);
            long intervalNanos = 1_000_000_000L / this.capacity;
            this.refiller = Thread.ofVirtual().name("versions-rate-refiller").start(() -> {
                while (!stop) {
                    try {
                        TimeUnit.NANOSECONDS.sleep(intervalNanos);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (permits.availablePermits() < capacity) {
                        permits.release();
                    }
                }
            });
        }

        void acquireOrBlock() throws InterruptedException {
            permits.acquire();
        }

        @Override
        public void close() {
            stop = true;
            refiller.interrupt();
        }
    }

    public record SweepStatus(
            String state,
            Integer discoveredPackages,
            Integer processedPackages,
            Integer failedPackages,
            Integer skippedPackages,
            Instant startedAt,
            Instant completedAt,
            String errorMessage
    ) {
        public boolean isRunning() { return "RUNNING".equals(state); }

        public static SweepStatus idle() {
            return new SweepStatus("IDLE", null, null, null, null, null, null, null);
        }

        public static SweepStatus running() {
            return new SweepStatus("RUNNING", null, null, null, null, Instant.now(), null, null);
        }

        public static SweepStatus completed(int discovered, int processed, int failed, int skipped,
                                             Instant start, Instant end) {
            return new SweepStatus("COMPLETED", discovered, processed, failed, skipped, start, end, null);
        }

        public static SweepStatus failed(String error, Instant start) {
            return new SweepStatus("FAILED", null, null, null, null, start, Instant.now(), error);
        }

    }
}
