# Operational Metrics

A Quarkus microservice that aggregates **operational health metrics** for open-source packages from four data sources, exposed through a single REST API.

It pulls Package URLs (PURLs) from a [Dependency Track](https://dependencytrack.org/) instance, fetches metrics from each enabled source according to a configurable priority, merges them by PURL identity (`type + namespace + name` — version is intentionally ignored), and stores the result in PostgreSQL with a configurable history retention window.

For internal architecture, data flow, and schema details, see [ARCHITECTURE.md](ARCHITECTURE.md).

---

## What it gives you

- **One API for four upstream sources** — OpenSSF Scorecard, deps.dev, ecosyste.ms, GitHub REST. No per-source clients in your code.
- **Source priority + merge** — each source contributes the fields it is best at; higher-priority sources win when they overlap. Sources can be toggled individually.
- **PURL-native** — query by full PURL, by coordinates (`type + namespace + name`), or by uploading a CycloneDX SBOM and getting metrics for every component back.
- **On-demand fetch** — if a PURL is not yet in the cache, the API fetches it synchronously rather than returning 404.
- **Scheduled sync** from Dependency Track with configurable concurrency, batch size, and rate-limit delay.
- **Historical snapshots** retained for a configurable window (default 90 days) with a daily purge job.

---

## Quick start

### With docker-compose (local dev with PostgreSQL)

```bash
GITHUB_TOKEN=ghp_... DT_URL=http://host.docker.internal:8081 DT_API_KEY=... \
  docker compose up --build
```

The app listens on `http://localhost:8080`. Postgres is exposed on `5432`.

### With Quarkus dev mode

```bash
# Start a Postgres locally (or point DB_URL at one you have)
docker run -d --name pg -p 5432:5432 \
  -e POSTGRES_DB=operational_metrics \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  postgres:16-alpine

# Then
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw quarkus:dev
```

---

## API

### Single PURL lookup

```bash
curl 'http://localhost:8080/api/v1/metrics?purl=pkg:maven/org.apache.logging.log4j/log4j-core'
```

If the PURL is unknown, the service fetches it from upstream sources synchronously and caches the result.

### By coordinates

```bash
curl 'http://localhost:8080/api/v1/metrics/coordinates?type=npm&name=express'
curl 'http://localhost:8080/api/v1/metrics/coordinates?type=maven&namespace=org.apache.logging.log4j&name=log4j-core'
```

### Bulk lookup

```bash
curl -X POST http://localhost:8080/api/v1/metrics/bulk \
  -H 'Content-Type: application/json' \
  -d '{"purls":["pkg:maven/com.fasterxml.jackson.core/jackson-databind","pkg:npm/express","pkg:pypi/requests"]}'
```

Missing PURLs are fetched concurrently (capped by `metrics.api.on-demand-concurrency`).

### SBOM upload (CycloneDX JSON / XML)

```bash
curl -X POST http://localhost:8080/api/v1/sbom/upload \
  -F file=@bom.json
```

Returns metrics for every component in the SBOM, fetching any unknown PURLs on demand.

### Trigger / inspect Dependency Track sync

```bash
curl -X POST http://localhost:8080/api/v1/sync/trigger
curl http://localhost:8080/api/v1/sync/status
```

### Health checks

`/q/health/live`, `/q/health/ready`, `/q/health/started` (Quarkus SmallRye Health).

### Response shape

```json
{
  "purl": "pkg:maven/org.apache.logging.log4j/log4j-core",
  "purlType": "maven",
  "purlNamespace": "org.apache.logging.log4j",
  "purlName": "log4j-core",
  "repoUrl": "https://github.com/apache/logging-log4j2",
  "scorecard":  { "score": 7.6, "checks": "...", "date": "..." },
  "popularity": { "stars": 8421, "forks": 3210, "downloads": 894523112, "rankingPercentile": 0.04 },
  "activity":   { "lastCommit": "...", "lastRelease": "...", "contributorCount": 312, "archived": false, "deprecated": false },
  "community":  { "healthPct": 88.0, "avgIssueCloseTimeDays": 4.2, "avgPrCloseTimeDays": 1.8, "prAuthorsCount": 145, "mergedPrCount": 2104 },
  "security":   { "advisoryCount": 3, "slsaProvenance": true, "ossFuzz": true },
  "dependents": { "repos": 156342, "packages": 8214 },
  "maintainerCount": 12,
  "license": "Apache-2.0",
  "sourcesUsed": ["SCORECARD", "DEPS_DEV", "ECOSYSTEMS", "GITHUB"],
  "fetchedAt": "2026-04-30T02:15:00Z"
}
```

Fields populated by lower-priority sources only appear when higher-priority sources don't cover them. Null fields are omitted (`@JsonInclude(NON_NULL)`).

---

## Configuration

All settings are externalised through Quarkus `@ConfigMapping` interfaces and exposed as env vars in containerised deployments.

### Sources

```properties
metrics.sources.source.scorecard.enabled=true
metrics.sources.source.scorecard.priority=1
metrics.sources.source.depsdev.enabled=true
metrics.sources.source.depsdev.priority=2
metrics.sources.source.ecosystems.enabled=true
metrics.sources.source.ecosystems.priority=3
metrics.sources.source.github.enabled=true
metrics.sources.source.github.priority=4
```

Priority is "lower number wins" when the same field is provided by multiple sources. Disabling a source stops the orchestrator from calling it entirely.

### Required external credentials

| Env var | Purpose | Notes |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL | Liquibase migrates schema on startup |
| `DT_URL`, `DT_API_KEY` | Dependency Track | Used by the scheduled sync |
| `GITHUB_TOKEN` | GitHub PAT | 5K req/hr without it you'll get 60. Permissions: `metadata:read`, `contents:read` |
| `ECOSYSTEMS_CONTACT_EMAIL` | ecosyste.ms polite pool | Lifts rate limit from 5K/hr to 15K/hr |

### Sync, history, API

```properties
metrics.sync.cron=0 0 2 * * ?      # daily at 02:00
metrics.sync.concurrency=4
metrics.sync.batch-size=500
metrics.sync.rate-limit-delay-ms=1000
metrics.history.retention-days=90
metrics.history.purge-cron=0 0 3 * * ?   # daily at 03:00
metrics.api.on-demand-concurrency=4
```

---

## Deployment

### Docker

```bash
docker build -t operational-metrics:latest .
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/operational_metrics \
  -e DB_USERNAME=postgres -e DB_PASSWORD=postgres \
  -e DT_URL=... -e DT_API_KEY=... -e GITHUB_TOKEN=... \
  operational-metrics:latest
```

### Helm

A production-ready chart lives in [`charts/operational-metrics`](charts/operational-metrics).

```bash
# With inline credentials (dev / sandbox)
helm install om ./charts/operational-metrics \
  --set image.tag=1.0.0 \
  --set database.password=postgres \
  --set dependencyTrack.apiKey=... \
  --set github.token=...

# With existing secrets (production)
helm install om ./charts/operational-metrics \
  --set image.tag=1.0.0 \
  --set database.existingSecret=om-db --set database.existingSecretKey=password \
  --set dependencyTrack.existingSecret=om-dt --set dependencyTrack.existingSecretKey=api-key \
  --set github.existingSecret=om-gh    --set github.existingSecretKey=token
```

The chart provides: Deployment, Service, ConfigMap, Secret, ServiceAccount, optional HPA, optional Ingress. Liveness/readiness/startup probes are wired to Quarkus health endpoints.

---

## Development

### Project layout

```
src/main/java/com/example/operationalmetrics/
├── config/        @ConfigMapping interfaces (sources, DT, GitHub, sync, history, api)
├── client/        @RegisterRestClient interfaces + per-source DTOs
├── model/         Domain entities (PackageId, OperationalMetricsEntity, PartialMetrics, ...)
├── dto/           API request / response records
├── repository/    JDBI SqlObject DAOs
├── service/       Orchestration + per-source collectors
├── resource/      JAX-RS endpoints
└── scheduler/     @Scheduled jobs (sync, history purge)
```

Project conventions:
- **Constructor CDI injection** everywhere — no field injection.
- **Records** for DTOs and value objects where possible.
- **JDBI SqlObject** with `@RegisterBeanMapper` and explicit `INSERT ... ON CONFLICT` upserts. No ORM.
- **Liquibase** SQL changesets under `src/main/resources/db/changelog/`.

### Build & test

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw clean compile
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw test
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw quarkus:dev
```

---

## License

[MIT](LICENSE)
