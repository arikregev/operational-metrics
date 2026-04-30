package com.example.operationalmetrics.resource;

import com.example.operationalmetrics.model.OperationalMetricsEntity;
import com.example.operationalmetrics.model.PackageId;
import com.example.operationalmetrics.repository.JdbiTestProducer;
import com.example.operationalmetrics.repository.OperationalMetricsDao;
import com.example.operationalmetrics.service.MetricsOrchestrator;
import com.example.operationalmetrics.service.MetricsQueryService;
import com.example.operationalmetrics.service.SbomParserService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.extension.ExtensionCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusTest
class SbomMetricsResourceTest {

    @InjectMock
    SbomParserService sbomParser;

    @InjectMock
    MetricsOrchestrator orchestrator;

    // Even though SbomMetricsResource doesn't use it directly, the bean is
    // still wired into other resources/services in the same context.
    @InjectMock
    MetricsQueryService queryService;

    /** The shared {@link Jdbi} mock from {@link JdbiTestProducer}. */
    private final Jdbi jdbi = JdbiTestProducer.MOCK;

    @BeforeEach
    void resetJdbiMock() {
        Mockito.reset(jdbi);
    }

    private static OperationalMetricsEntity entityFor(PackageId id) {
        var entity = new OperationalMetricsEntity();
        entity.setPurlType(id.purlType());
        entity.setPurlNamespace(id.namespace());
        entity.setPurlName(id.name());
        entity.setPurlCanonical(id.canonical());
        entity.setLicense("Apache-2.0");
        entity.setSourcesUsed(List.of("SCORECARD"));
        entity.setFetchedAt(Instant.parse("2026-04-30T12:00:00Z"));
        return entity;
    }

    @SuppressWarnings("unchecked")
    private void mockJdbiWithExtension(Optional<OperationalMetricsEntity> result) {
        when(jdbi.withExtension(eq(OperationalMetricsDao.class), any(ExtensionCallback.class)))
                .thenReturn(result);
    }

    @Test
    void uploadSbom_validFile_cachedHit_returnsCachedMetrics() {
        PackageId pkg = new PackageId("npm", null, "express");
        when(sbomParser.parse(any(), any())).thenReturn(List.of(pkg));
        mockJdbiWithExtension(Optional.of(entityFor(pkg)));

        given()
                .multiPart("file", "bom.json", "{}".getBytes(), "application/octet-stream")
        .when()
                .post("/api/v1/sbom/upload")
        .then()
                .statusCode(200)
                .body("packagesTotal", equalTo(1))
                .body("packagesFetchedOnDemand", equalTo(0))
                .body("results", hasSize(1))
                .body("results[0].purl", equalTo("pkg:npm/express"))
                .body("results[0].license", equalTo("Apache-2.0"))
                .body("errors", hasSize(0));

        // Cached path: orchestrator must NOT be called.
        verify(orchestrator, never()).collectAndStore(any(), any());
    }

    @Test
    void uploadSbom_validFile_cacheMiss_invokesOrchestrator() {
        PackageId pkg = new PackageId("npm", null, "lodash");
        when(sbomParser.parse(any(), any())).thenReturn(List.of(pkg));
        mockJdbiWithExtension(Optional.empty());
        when(orchestrator.collectAndStore(eq(pkg), any())).thenReturn(entityFor(pkg));

        given()
                .multiPart("file", "bom.json", "{}".getBytes(), "application/octet-stream")
        .when()
                .post("/api/v1/sbom/upload")
        .then()
                .statusCode(200)
                .body("packagesTotal", equalTo(1))
                .body("packagesFetchedOnDemand", equalTo(1))
                .body("results", hasSize(1))
                .body("errors", hasSize(0));

        verify(orchestrator).collectAndStore(eq(pkg), any());
    }

    @Test
    void uploadSbom_orchestratorFails_recordsErrorEntry() {
        PackageId pkg = new PackageId("npm", null, "broken");
        when(sbomParser.parse(any(), any())).thenReturn(List.of(pkg));
        mockJdbiWithExtension(Optional.empty());
        when(orchestrator.collectAndStore(eq(pkg), any()))
                .thenThrow(new RuntimeException("upstream offline"));

        given()
                .multiPart("file", "bom.json", "{}".getBytes(), "application/octet-stream")
        .when()
                .post("/api/v1/sbom/upload")
        .then()
                .statusCode(200)
                .body("packagesTotal", equalTo(1))
                .body("packagesFetchedOnDemand", equalTo(0))
                .body("results", hasSize(0))
                .body("errors", hasSize(1))
                .body("errors[0].purl", equalTo("pkg:npm/broken"))
                .body("errors[0].reason", equalTo("upstream offline"));
    }

    @Test
    void uploadSbom_invalidSbom_returns400() {
        when(sbomParser.parse(any(), any()))
                .thenThrow(new IllegalArgumentException("Failed to parse SBOM as CycloneDX: bad"));

        given()
                .multiPart("file", "bom.json", "garbage".getBytes(), "application/octet-stream")
        .when()
                .post("/api/v1/sbom/upload")
        .then()
                .statusCode(400)
                .body("error", equalTo("Failed to parse SBOM as CycloneDX: bad"));

        verifyNoInteractions(orchestrator);
    }

    @Test
    void uploadSbom_emptyComponents_returnsZeroResults() {
        when(sbomParser.parse(any(), any())).thenReturn(List.of());

        given()
                .multiPart("file", "bom.json", "{}".getBytes(), "application/octet-stream")
        .when()
                .post("/api/v1/sbom/upload")
        .then()
                .statusCode(200)
                .body("packagesTotal", equalTo(0))
                .body("packagesFetchedOnDemand", equalTo(0))
                .body("results", hasSize(0))
                .body("errors", hasSize(0));

        verifyNoInteractions(orchestrator);
    }

    @Test
    void uploadSbom_noFile_returns400() {
        // multipart request with no parts -> SbomUploadForm.file is null.
        given()
                .multiPart("filename", "bom.json")
        .when()
                .post("/api/v1/sbom/upload")
        .then()
                .statusCode(400)
                .body("error", equalTo("SBOM file is required"));

        verifyNoInteractions(sbomParser);
        verifyNoInteractions(orchestrator);
    }

    @Test
    void uploadSbom_passesSyncRunIdNullToOrchestrator() {
        PackageId pkg = new PackageId("npm", null, "axios");
        when(sbomParser.parse(any(), any())).thenReturn(List.of(pkg));
        mockJdbiWithExtension(Optional.empty());
        when(orchestrator.collectAndStore(eq(pkg), any())).thenReturn(entityFor(pkg));

        given()
                .multiPart("file", "bom.json", "{}".getBytes(), "application/octet-stream")
        .when()
                .post("/api/v1/sbom/upload")
        .then()
                .statusCode(200);

        // The resource always passes null as the sync-run ID for on-demand SBOM uploads.
        verify(orchestrator).collectAndStore(eq(pkg), (UUID) eq(null));
    }
}
