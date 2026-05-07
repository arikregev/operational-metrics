package com.example.operationalmetrics.resource;

import com.example.operationalmetrics.service.MetricsOrchestrator;
import com.example.operationalmetrics.service.MetricsQueryService;
import com.example.operationalmetrics.service.PurlSyncService;
import com.example.operationalmetrics.service.VersionsSyncService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class SyncResourceTest {

    @InjectMock
    PurlSyncService syncService;

    @InjectMock
    VersionsSyncService versionsService;

    // Mocked so the Quarkus context can start without downstream wiring.
    @InjectMock
    MetricsOrchestrator orchestrator;

    @InjectMock
    MetricsQueryService queryService;

    @BeforeEach
    void setUp() {
        // GET /status now combines both sync states. Default the versions side
        // to IDLE so each metrics-focused test doesn't have to repeat it.
        when(versionsService.getStatus())
                .thenReturn(VersionsSyncService.SweepStatus.idle());
    }

    @Test
    void triggerSync_returns202AndInvokesService() {
        given()
        .when()
                .post("/api/v1/sync/trigger")
        .then()
                .statusCode(202)
                .body("status", equalTo("FULL_SYNC_TRIGGERED"));

        verify(syncService, times(1)).triggerAsync();
    }

    @Test
    void getStatus_idle_returnsIdleState() {
        when(syncService.getStatus()).thenReturn(PurlSyncService.SyncStatus.idle());

        given()
        .when()
                .get("/api/v1/sync/status")
        .then()
                .statusCode(200)
                .body("metrics.state", equalTo("IDLE"))
                .body("versions.state", equalTo("IDLE"));

        verify(syncService).getStatus();
    }

    @Test
    void getStatus_completed_returnsCountsAndState() {
        Instant start = Instant.parse("2026-04-30T01:00:00Z");
        Instant end = Instant.parse("2026-04-30T01:30:00Z");
        when(syncService.getStatus())
                .thenReturn(PurlSyncService.SyncStatus.completed("FULL", 10, 8, 2, start, end));

        given()
        .when()
                .get("/api/v1/sync/status")
        .then()
                .statusCode(200)
                .body("metrics.state", equalTo("COMPLETED"))
                .body("metrics.totalPackages", equalTo(10))
                .body("metrics.processedPackages", equalTo(8))
                .body("metrics.failedPackages", equalTo(2));
    }

    @Test
    void getStatus_failed_returnsErrorMessage() {
        Instant start = Instant.parse("2026-04-30T01:00:00Z");
        when(syncService.getStatus())
                .thenReturn(PurlSyncService.SyncStatus.failed("FULL", "connection refused", start));

        given()
        .when()
                .get("/api/v1/sync/status")
        .then()
                .statusCode(200)
                .body("metrics.state", equalTo("FAILED"))
                .body("metrics.errorMessage", equalTo("connection refused"));
    }

    @Test
    void triggerRefresh_returns202AndInvokesService() {
        given()
        .when()
                .post("/api/v1/sync/refresh")
        .then()
                .statusCode(202)
                .body("status", equalTo("REFRESH_TRIGGERED"));

        verify(syncService, times(1)).triggerRefreshAsync();
    }

    @Test
    void triggerDiscovery_returns202AndInvokesService() {
        given()
        .when()
                .post("/api/v1/sync/discovery")
        .then()
                .statusCode(202)
                .body("status", equalTo("DISCOVERY_TRIGGERED"));

        verify(syncService, times(1)).triggerDiscoveryAsync();
    }

    @Test
    void triggerVersionsSweep_returns202AndInvokesService() {
        given()
        .when()
                .post("/api/v1/sync/versions")
        .then()
                .statusCode(202)
                .body("status", equalTo("VERSIONS_SWEEP_TRIGGERED"));

        verify(versionsService, times(1)).triggerAsync();
    }
}
