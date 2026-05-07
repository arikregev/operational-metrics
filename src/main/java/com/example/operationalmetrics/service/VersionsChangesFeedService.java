package com.example.operationalmetrics.service;

import com.example.operationalmetrics.client.ecosystems.EcosystemsClient;
import com.example.operationalmetrics.client.ecosystems.dto.EcosystemsPackageRef;
import com.example.operationalmetrics.config.VersionsFeedConfig;
import com.example.operationalmetrics.model.FeedCursor;
import com.example.operationalmetrics.model.PackageEntity;
import com.example.operationalmetrics.repository.PackageDao;
import com.example.operationalmetrics.repository.VersionsFeedCursorDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import org.jdbi.v3.core.Jdbi;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Changes-feed-driven version discovery — the primary path for sub-15-min
 * freshness. Polls ecosyste.ms's
 * {@code GET /api/v1/registries/{r}/packages?sort=latest_release_published_at&order=desc}
 * per registry, paginates descending until results dip below the watermark,
 * intersects names with our local {@code package} table, and queues
 * {@link RepoMetaAnalyzer#analyze} for matched packages.
 *
 * <p>Why not query the registry directly: ecosyste.ms aggregates ~100 registries
 * (npm, PyPI, Maven Central, NuGet, RubyGems, ...) behind one consistent
 * endpoint. Single integration covers them all. The endpoint has no
 * {@code since=} filter so we paginate descending and stop on watermark
 * crossing — works fine because results are sorted newest-first.
 *
 * <p>The companion safety-net path is {@link VersionsSyncService}; if a feed
 * miss occurs (registry outage, our outage during a window, etc.) the sweep
 * eventually catches up via its 30-day staleness pass.
 */
@ApplicationScoped
public class VersionsChangesFeedService {

    private static final Logger LOG = Logger.getLogger(VersionsChangesFeedService.class);

    /**
     * Map ecosyste.ms registry hostname → PURL type. Determines how we build
     * {@code purl_canonical} from a registry's {@code name} field for the
     * intersection-against-local-package-table step.
     */
    private static final Map<String, String> REGISTRY_TO_PURL_TYPE;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("npmjs.org", "npm");
        m.put("pypi.org", "pypi");
        m.put("repo1.maven.org", "maven");
        m.put("rubygems.org", "gem");
        m.put("nuget.org", "nuget");
        m.put("crates.io", "cargo");
        m.put("packagist.org", "composer");
        m.put("hex.pm", "hex");
        m.put("proxy.golang.org", "golang");
        REGISTRY_TO_PURL_TYPE = Map.copyOf(m);
    }

    private final VersionsFeedConfig config;
    private final EcosystemsClient ecosystemsClient;
    private final RepoMetaAnalyzer repoMetaAnalyzer;
    private final Jdbi jdbi;
    private final AtomicReference<FeedStatus> currentStatus = new AtomicReference<>(FeedStatus.idle());

    @Inject
    public VersionsChangesFeedService(VersionsFeedConfig config,
                                      @RestClient EcosystemsClient ecosystemsClient,
                                      RepoMetaAnalyzer repoMetaAnalyzer,
                                      Jdbi jdbi) {
        this.config = config;
        this.ecosystemsClient = ecosystemsClient;
        this.repoMetaAnalyzer = repoMetaAnalyzer;
        this.jdbi = jdbi;
    }

    public void triggerAsync() {
        FeedStatus snap = currentStatus.get();
        if (snap.isRunning()) {
            LOG.info("Versions feed poll already in progress, skipping");
            return;
        }
        if (!currentStatus.compareAndSet(snap, FeedStatus.running())) {
            return;
        }
        Thread.startVirtualThread(this::pollAllRegistries);
    }

    public FeedStatus getStatus() {
        return currentStatus.get();
    }

    /**
     * Iterate every configured registry, polling each in turn. Per-registry
     * failures are logged but don't abort the loop — one bad registry
     * shouldn't starve the others.
     */
    public void pollAllRegistries() {
        if (!config.enabled()) {
            LOG.info("Versions feed is disabled; skipping run");
            currentStatus.set(FeedStatus.idle());
            return;
        }
        List<String> registries = config.registriesList();
        if (registries.isEmpty() || (registries.size() == 1 && registries.getFirst().isBlank())) {
            LOG.info("No registries configured for versions feed; skipping run");
            currentStatus.set(FeedStatus.idle());
            return;
        }

        Instant startTime = Instant.now();
        currentStatus.set(FeedStatus.running());

        AtomicInteger totalScanned = new AtomicInteger();
        AtomicInteger totalMatched = new AtomicInteger();
        AtomicInteger totalAnalyzed = new AtomicInteger();
        AtomicInteger totalFailed = new AtomicInteger();

        Semaphore concurrencyLimit = new Semaphore(config.concurrency());

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
             TokenBucket rateLimit = new TokenBucket(config.rateBudgetPerSecond())) {

            for (String registry : registries) {
                if (registry == null || registry.isBlank()) continue;
                try {
                    pollRegistry(registry, executor, concurrencyLimit, rateLimit,
                            totalScanned, totalMatched, totalAnalyzed, totalFailed);
                } catch (Exception e) {
                    LOG.warnv("Versions feed poll for {0} failed: {1}", registry, e.getMessage());
                }
            }
            // try-with-resources close() blocks until every analyze task completes.
        }

        currentStatus.set(FeedStatus.completed(
                totalScanned.get(), totalMatched.get(),
                totalAnalyzed.get(), totalFailed.get(),
                startTime, Instant.now()));
        LOG.infov("Versions feed completed: {0} scanned, {1} matched, {2} analyzed, {3} failed",
                totalScanned.get(), totalMatched.get(), totalAnalyzed.get(), totalFailed.get());
    }

    private void pollRegistry(String registry,
                              ExecutorService executor,
                              Semaphore concurrencyLimit,
                              TokenBucket rateLimit,
                              AtomicInteger totalScanned,
                              AtomicInteger totalMatched,
                              AtomicInteger totalAnalyzed,
                              AtomicInteger totalFailed) {
        String purlType = REGISTRY_TO_PURL_TYPE.get(registry);
        if (purlType == null) {
            LOG.warnv("Unsupported registry {0} (no PURL type mapping); skipping", registry);
            return;
        }

        Instant runStart = Instant.now();
        Instant watermark = readWatermark(registry, runStart);
        Instant newestSeen = null;

        // Collect candidate refs across pages, descending, until we cross the watermark
        // or hit the safety cap on pagination depth.
        List<EcosystemsPackageRef> candidates = new ArrayList<>();
        boolean reachedWatermark = false;

        for (int page = 1; page <= config.maxPagesPerPoll() && !reachedWatermark; page++) {
            List<EcosystemsPackageRef> pageRefs;
            try {
                pageRefs = ecosystemsClient.recentPackages(
                        registry, "latest_release_published_at", "desc", page, config.perPage());
            } catch (Exception e) {
                LOG.warnv("Page {0} of {1} feed failed: {2}", page, registry, e.getMessage());
                break;  // give up paginating but persist whatever we collected so far
            }
            if (pageRefs == null || pageRefs.isEmpty()) break;
            totalScanned.addAndGet(pageRefs.size());

            for (EcosystemsPackageRef ref : pageRefs) {
                Instant releasedAt = ref.latestReleasePublishedAt();
                if (newestSeen == null && releasedAt != null) {
                    newestSeen = releasedAt;
                }
                if (releasedAt == null) {
                    // Defensive: skip rows with missing timestamp; can't compare to watermark.
                    continue;
                }
                if (!releasedAt.isAfter(watermark)) {
                    reachedWatermark = true;
                    break;
                }
                candidates.add(ref);
            }
        }

        // Intersect candidate names against our local package table in one query.
        List<String> canonicals = candidates.stream()
                .map(ref -> buildCanonical(purlType, ref.name()))
                .filter(s -> s != null)
                .toList();
        if (canonicals.isEmpty()) {
            cursor(registry, runStart, newestSeen);
            return;
        }

        List<PackageEntity> matched = jdbi.withExtension(PackageDao.class,
                dao -> dao.findByCanonicals(canonicals));
        totalMatched.addAndGet(matched.size());

        // Submit analyze() for each matched package, bounded by concurrency + rate.
        List<Future<?>> outstanding = new ArrayList<>();
        for (PackageEntity entity : matched) {
            if (entity.getId() == null) continue;
            try {
                concurrencyLimit.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cursor(registry, runStart, newestSeen);
                throw new RuntimeException("Feed poll interrupted while waiting for capacity", e);
            }
            outstanding.add(executor.submit(() -> {
                try {
                    rateLimit.acquireOrBlock();
                    repoMetaAnalyzer.analyze(entity.toPackageId(), entity.getId());
                    totalAnalyzed.incrementAndGet();
                } catch (Exception e) {
                    totalFailed.incrementAndGet();
                    LOG.warnv("Feed-driven analyze failed for {0}: {1}",
                            entity.getPurlCanonical(), e.getMessage());
                } finally {
                    concurrencyLimit.release();
                }
            }));
        }

        cursor(registry, runStart, newestSeen);
    }

    /**
     * Builds the canonical PURL string the way {@link com.example.operationalmetrics.model.PackageId#canonical()} does, given
     * a PURL type and an ecosyste.ms-format name. Maven names come back as
     * {@code groupId:artifactId} from ecosyste.ms; PURL canonical form uses
     * a slash, so we split-and-rejoin.
     */
    private String buildCanonical(String purlType, String name) {
        if (name == null || name.isBlank()) return null;
        String namespace = null;
        String packageName = name;

        switch (purlType) {
            case "maven" -> {
                int idx = name.indexOf(':');
                if (idx > 0) {
                    namespace = name.substring(0, idx);
                    packageName = name.substring(idx + 1);
                }
            }
            case "composer", "golang" -> {
                int idx = name.indexOf('/');
                if (idx > 0) {
                    namespace = name.substring(0, idx);
                    packageName = name.substring(idx + 1);
                }
            }
            case "npm" -> {
                if (name.startsWith("@")) {
                    int idx = name.indexOf('/');
                    if (idx > 0) {
                        namespace = name.substring(0, idx);  // includes the leading "@"
                        packageName = name.substring(idx + 1);
                    }
                }
            }
            default -> { /* most ecosystems have no namespace */ }
        }

        return namespace == null
                ? "pkg:" + purlType + "/" + packageName
                : "pkg:" + purlType + "/" + namespace + "/" + packageName;
    }

    private Instant readWatermark(String registry, Instant runStart) {
        return jdbi.withExtension(VersionsFeedCursorDao.class, dao -> {
            FeedCursor existing = dao.find(registry).orElse(null);
            if (existing == null || existing.lastSeenReleaseAt() == null) {
                return runStart.minus(Duration.ofHours(config.initialLookbackHours()));
            }
            return existing.lastSeenReleaseAt();
        });
    }

    private void cursor(String registry, Instant runStart, Instant newestSeen) {
        jdbi.useExtension(VersionsFeedCursorDao.class,
                dao -> dao.upsert(registry, runStart, newestSeen));
    }

    /** Same simple in-process token bucket pattern as VersionsSyncService. */
    private static final class TokenBucket implements AutoCloseable {
        private final int capacity;
        private final Semaphore permits;
        private final Thread refiller;
        private volatile boolean stop = false;

        TokenBucket(int permitsPerSecond) {
            this.capacity = Math.max(1, permitsPerSecond);
            this.permits = new Semaphore(this.capacity, true);
            long intervalNanos = 1_000_000_000L / this.capacity;
            this.refiller = Thread.ofVirtual().name("versions-feed-rate-refiller").start(() -> {
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

    public record FeedStatus(
            String state,
            Integer scannedPackages,
            Integer matchedPackages,
            Integer analyzedPackages,
            Integer failedPackages,
            Instant startedAt,
            Instant completedAt,
            String errorMessage
    ) {
        public boolean isRunning() { return "RUNNING".equals(state); }

        public static FeedStatus idle() {
            return new FeedStatus("IDLE", null, null, null, null, null, null, null);
        }

        public static FeedStatus running() {
            return new FeedStatus("RUNNING", null, null, null, null, Instant.now(), null, null);
        }

        public static FeedStatus completed(int scanned, int matched, int analyzed, int failed,
                                           Instant start, Instant end) {
            return new FeedStatus("COMPLETED", scanned, matched, analyzed, failed, start, end, null);
        }

        public static FeedStatus failed(String error, Instant start) {
            return new FeedStatus("FAILED", null, null, null, null, start, Instant.now(), error);
        }
    }
}
