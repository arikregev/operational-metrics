package com.example.operationalmetrics.resource;

import com.example.operationalmetrics.dto.MetricsBulkResponse;
import com.example.operationalmetrics.dto.MetricsResponse;
import com.example.operationalmetrics.repository.JdbiTestProducer;
import com.example.operationalmetrics.service.MetricsOrchestrator;
import com.example.operationalmetrics.service.MetricsQueryService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusTest
class MetricsResourceTest {

    @InjectMock
    MetricsQueryService queryService;

    // Mocked so the Quarkus context can start without downstream wiring.
    @InjectMock
    MetricsOrchestrator orchestrator;

    @BeforeEach
    void resetSharedMocks() {
        // Jdbi is provided as a CDI @Mock alternative (see JdbiTestProducer);
        // because the producer is shared across tests, reset it explicitly.
        Mockito.reset(JdbiTestProducer.MOCK);
    }

    private static MetricsResponse sampleResponse(String purl) {
        return new MetricsResponse(
                purl,
                "npm",
                null,
                "express",
                "https://github.com/expressjs/express",
                null, null, null, null, null,         // scorecard, popularity, activity, community, security
                List.of("SCORECARD"),                  // sourcesUsed
                null,                                   // versionInfo
                Instant.parse("2026-04-30T12:00:00Z")  // fetchedAt
        );
    }

    @Test
    void getByPurl_validPurl_returns200WithMetrics() {
        String purl = "pkg:npm/express";
        when(queryService.findByPurl(purl)).thenReturn(sampleResponse(purl));

        given()
                .queryParam("purl", purl)
        .when()
                .get("/api/v1/metrics")
        .then()
                .statusCode(200)
                .contentType("application/json")
                .body("purl", equalTo(purl))
                .body("purlType", equalTo("npm"))
                .body("purlName", equalTo("express"))
                // license + maintainerCount removed in migration 008
                .body("sourcesUsed", hasSize(1))
                .body("sourcesUsed[0]", equalTo("SCORECARD"));

        verify(queryService).findByPurl(purl);
    }

    @Test
    void getByPurl_blankPurl_returns400() {
        given()
                .queryParam("purl", "")
        .when()
                .get("/api/v1/metrics")
        .then()
                .statusCode(400)
                .body("error", equalTo("purl query parameter is required"));

        verifyNoInteractions(queryService);
    }

    @Test
    void getByPurl_missingPurl_returns400() {
        given()
        .when()
                .get("/api/v1/metrics")
        .then()
                .statusCode(400)
                .body("error", equalTo("purl query parameter is required"));

        verifyNoInteractions(queryService);
    }

    @Test
    void getByPurl_invalidPurl_returns400() {
        String purl = "not-a-purl";
        when(queryService.findByPurl(purl))
                .thenThrow(new IllegalArgumentException("Invalid PURL: " + purl));

        given()
                .queryParam("purl", purl)
        .when()
                .get("/api/v1/metrics")
        .then()
                .statusCode(400)
                .body("error", equalTo("Invalid PURL: " + purl));
    }

    @Test
    void getByCoordinates_validParams_returns200() {
        when(queryService.findByCoordinates("maven", "org.example", "lib"))
                .thenReturn(sampleResponse("pkg:maven/org.example/lib"));

        given()
                .queryParam("type", "maven")
                .queryParam("namespace", "org.example")
                .queryParam("name", "lib")
        .when()
                .get("/api/v1/metrics/coordinates")
        .then()
                .statusCode(200)
                .body("purl", equalTo("pkg:maven/org.example/lib"));

        verify(queryService).findByCoordinates("maven", "org.example", "lib");
    }

    @Test
    void getByCoordinates_validParams_namespaceOptional_returns200() {
        when(queryService.findByCoordinates(eq("npm"), any(), eq("express")))
                .thenReturn(sampleResponse("pkg:npm/express"));

        given()
                .queryParam("type", "npm")
                .queryParam("name", "express")
        .when()
                .get("/api/v1/metrics/coordinates")
        .then()
                .statusCode(200)
                .body("purl", equalTo("pkg:npm/express"));
    }

    @Test
    void getByCoordinates_missingType_returns400() {
        given()
                .queryParam("name", "express")
        .when()
                .get("/api/v1/metrics/coordinates")
        .then()
                .statusCode(400)
                .body("error", equalTo("type and name query parameters are required"));

        verifyNoInteractions(queryService);
    }

    @Test
    void getByCoordinates_missingName_returns400() {
        given()
                .queryParam("type", "npm")
        .when()
                .get("/api/v1/metrics/coordinates")
        .then()
                .statusCode(400)
                .body("error", equalTo("type and name query parameters are required"));

        verifyNoInteractions(queryService);
    }

    @Test
    void getByCoordinates_invalidPackage_returns400() {
        when(queryService.findByCoordinates(anyString(), any(), anyString()))
                .thenThrow(new IllegalArgumentException("bad coords"));

        given()
                .queryParam("type", "npm")
                .queryParam("name", "express")
        .when()
                .get("/api/v1/metrics/coordinates")
        .then()
                .statusCode(400)
                .body("error", equalTo("bad coords"));
    }

    @Test
    void bulkLookup_validRequest_returns200() {
        List<String> purls = List.of("pkg:npm/express", "pkg:npm/react");
        MetricsBulkResponse mockResponse = new MetricsBulkResponse(
                List.of(sampleResponse("pkg:npm/express"), sampleResponse("pkg:npm/react")),
                List.of()
        );
        when(queryService.findBulk(purls)).thenReturn(mockResponse);

        given()
                .contentType("application/json")
                .body("{\"purls\":[\"pkg:npm/express\",\"pkg:npm/react\"]}")
        .when()
                .post("/api/v1/metrics/bulk")
        .then()
                .statusCode(200)
                .body("results", hasSize(2))
                .body("errors", hasSize(0));

        verify(queryService).findBulk(purls);
    }

    @Test
    void bulkLookup_emptyPurls_returns400() {
        given()
                .contentType("application/json")
                .body("{\"purls\":[]}")
        .when()
                .post("/api/v1/metrics/bulk")
        .then()
                .statusCode(400)
                .body("error", equalTo("purls list is required and must not be empty"));

        verifyNoInteractions(queryService);
    }

    @Test
    void bulkLookup_nullBody_returns400() {
        given()
                .contentType("application/json")
        .when()
                .post("/api/v1/metrics/bulk")
        .then()
                .statusCode(400)
                .body("error", equalTo("purls list is required and must not be empty"));

        verifyNoInteractions(queryService);
    }

    @Test
    void bulkLookup_nullPurlsField_returns400() {
        given()
                .contentType("application/json")
                .body("{}")
        .when()
                .post("/api/v1/metrics/bulk")
        .then()
                .statusCode(400)
                .body("error", equalTo("purls list is required and must not be empty"));

        verifyNoInteractions(queryService);
    }
}
