package com.example.operationalmetrics.resource;

import com.example.operationalmetrics.service.MetricsOrchestrator;
import com.example.operationalmetrics.service.MetricsQueryService;
import com.example.operationalmetrics.service.PurlSyncService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
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

    // Mocked so the Quarkus context can start without downstream wiring.
    @InjectMock
    MetricsOrchestrator orchestrator;

    @InjectMock
    MetricsQueryService queryService;

    @Test
    void triggerSync_returns202AndInvokesService() {
        given()
        .when()
                .post("/api/v1/sync/trigger")
        .then()
                .statusCode(202)
                .body("status", equalTo("SYNC_TRIGGERED"));

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
                .body("state", equalTo("IDLE"));

        verify(syncService).getStatus();
    }

    @Test
    void getStatus_completed_returnsCountsAndState() {
        Instant start = Instant.parse("2026-04-30T01:00:00Z");
        Instant end = Instant.parse("2026-04-30T01:30:00Z");
        when(syncService.getStatus())
                .thenReturn(PurlSyncService.SyncStatus.completed(10, 8, 2, start, end));

        given()
        .when()
                .get("/api/v1/sync/status")
        .then()
                .statusCode(200)
                .body("state", equalTo("COMPLETED"))
                .body("totalPackages", equalTo(10))
                .body("processedPackages", equalTo(8))
                .body("failedPackages", equalTo(2));
    }

    @Test
    void getStatus_failed_returnsErrorMessage() {
        Instant start = Instant.parse("2026-04-30T01:00:00Z");
        when(syncService.getStatus())
                .thenReturn(PurlSyncService.SyncStatus.failed("connection refused", start));

        given()
        .when()
                .get("/api/v1/sync/status")
        .then()
                .statusCode(200)
                .body("state", equalTo("FAILED"))
                .body("errorMessage", equalTo("connection refused"));
    }
}
