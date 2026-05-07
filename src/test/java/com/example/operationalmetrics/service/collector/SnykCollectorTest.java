package com.example.operationalmetrics.service.collector;

import com.example.operationalmetrics.client.snyk.SnykClient;
import com.example.operationalmetrics.client.snyk.dto.SnykPackageDetails;
import com.example.operationalmetrics.client.snyk.dto.SnykPackageHealth;
import com.example.operationalmetrics.client.snyk.dto.SnykPackageResponse;
import com.example.operationalmetrics.config.SnykConfig;
import com.example.operationalmetrics.model.MetricsSource;
import com.example.operationalmetrics.model.PackageId;
import com.example.operationalmetrics.model.PartialMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnykCollectorTest {

    @Mock
    private SnykClient snykClient;

    @Mock
    private SnykConfig snykConfig;

    private SnykCollector collector;
    private PackageId mavenPackage;
    private PackageId npmPackage;

    @BeforeEach
    void setUp() {
        collector = new SnykCollector(snykClient, snykConfig);
        mavenPackage = new PackageId("maven", "org.apache.logging.log4j", "log4j-core");
        npmPackage = new PackageId("npm", null, "express");

        // Default: enabled with orgId/apiVersion. Lenient because some tests verify
        // the disabled/empty-orgId paths and never read these stubs.
        lenient().when(snykConfig.enabled()).thenReturn(true);
        lenient().when(snykConfig.orgId()).thenReturn(Optional.of("org-123"));
        lenient().when(snykConfig.apiVersion()).thenReturn("2024-10-15");
    }

    private SnykPackageResponse buildFullResponse() {
        SnykPackageHealth.Community community = new SnykPackageHealth.Community(
                "community description",
                true, true, false, true,
                "Active",
                75000);
        SnykPackageHealth.Maintenance maintenance = new SnykPackageHealth.Maintenance(
                "maintenance description",
                Instant.parse("2010-01-03T00:00:00Z"),
                66,
                false,
                false,
                "4.19.2",
                Instant.parse("2024-10-08T00:00:00Z"),
                "active",
                "Healthy",
                760);
        SnykPackageHealth.Popularity popularity = new SnykPackageHealth.Popularity(
                280L, 65000L, "popularity description", 12000L, "Influential project");
        SnykPackageHealth.Security security = new SnykPackageHealth.Security(
                "security description", true, 3, "Security issues found");
        SnykPackageHealth health = new SnykPackageHealth(community, maintenance, popularity, security,
                "Review recommended");

        SnykPackageDetails details = new SnykPackageDetails(
                "https://expressjs.com",
                "https://www.npmjs.com/package/express",
                "https://github.com/expressjs/express",
                null,
                null);

        SnykPackageResponse.OwnerDetails owner = new SnykPackageResponse.OwnerDetails(
                123, "Internet", "expressjs", 45, 65000);

        SnykPackageResponse.Attributes attrs = new SnykPackageResponse.Attributes(
                "npm",
                "JavaScript",
                "4.19.2",
                owner,
                details,
                health,
                "pkg:npm/express",
                "express");

        SnykPackageResponse.Data data = new SnykPackageResponse.Data("pkg:npm/express", "package", attrs);
        return new SnykPackageResponse(data);
    }

    @Test
    void collect_disabled_returnsEmpty() {
        when(snykConfig.enabled()).thenReturn(false);

        PartialMetrics partial = collector.collect(npmPackage, Optional.empty());

        assertThat(partial.getRepoUrl()).isNull();
        assertThat(partial.getSnykRating()).isNull();
        verifyNoInteractions(snykClient);
    }

    @Test
    void collect_emptyOrgId_returnsEmpty() {
        when(snykConfig.enabled()).thenReturn(true);
        when(snykConfig.orgId()).thenReturn(Optional.empty());

        PartialMetrics partial = collector.collect(npmPackage, Optional.empty());

        assertThat(partial.getSnykRating()).isNull();
        verifyNoInteractions(snykClient);
    }

    @Test
    void collect_fullResponse_mapsAllFields() {
        when(snykClient.getPackage(eq("org-123"), eq("npm"), eq("express"), eq("2024-10-15")))
                .thenReturn(buildFullResponse());

        PartialMetrics partial = collector.collect(npmPackage, Optional.empty());

        assertThat(partial.getAdvisoryCount()).isEqualTo(3);
        assertThat(partial.getLastReleaseAt()).isEqualTo(Instant.parse("2024-10-08T00:00:00Z"));
        assertThat(partial.getFirstReleaseAt()).isEqualTo(Instant.parse("2010-01-03T00:00:00Z"));
        assertThat(partial.getIsArchived()).isFalse();
        assertThat(partial.getLastReleaseVersion()).isEqualTo("4.19.2");
        assertThat(partial.getLastReleaseVersionSource()).isEqualTo("SNYK");
        assertThat(partial.getSnykRating()).isEqualTo("Healthy");
        assertThat(partial.getRepoUrl()).isNotNull();
        assertThat(partial.getRepoUrl().platform()).isEqualTo("github.com");
        assertThat(partial.getRepoUrl().owner()).isEqualTo("expressjs");
        assertThat(partial.getRepoUrl().name()).isEqualTo("express");
    }

    @Test
    void collect_clientThrows_returnsEmpty() {
        when(snykClient.getPackage(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("snyk down"));

        PartialMetrics partial = collector.collect(npmPackage, Optional.empty());

        assertThat(partial.getRepoUrl()).isNull();
        assertThat(partial.getSnykRating()).isNull();
        assertThat(partial.getLastReleaseVersion()).isNull();
    }

    @Test
    void supports_falseForGithub() {
        PackageId github = new PackageId("github", "expressjs", "express");
        assertThat(collector.supports(github)).isFalse();
    }

    @Test
    void supports_falseForGem() {
        PackageId gem = new PackageId("gem", null, "rails");
        assertThat(collector.supports(gem)).isFalse();
    }

    @Test
    void supports_trueForNpm() {
        assertThat(collector.supports(npmPackage)).isTrue();
    }

    @Test
    void source_returnsSnyk() {
        assertThat(collector.source()).isEqualTo(MetricsSource.SNYK);
    }

    @Test
    void requiresRepoUrl_returnsFalse() {
        assertThat(collector.requiresRepoUrl()).isFalse();
    }

    @Test
    void collect_mavenPurl_combinesGroupAndArtifactWithColon() {
        when(snykClient.getPackage(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(buildFullResponse());

        collector.collect(mavenPackage, Optional.empty());

        ArgumentCaptor<String> ecosystem = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
        verify(snykClient).getPackage(eq("org-123"), ecosystem.capture(), name.capture(), eq("2024-10-15"));

        assertThat(ecosystem.getValue()).isEqualTo("maven");
        // Maven name on Snyk is "groupId:artifactId" — passed RAW; JAX-RS encodes once.
        assertThat(name.getValue()).isEqualTo("org.apache.logging.log4j:log4j-core");
    }

    @Test
    void collect_repositoryUrlAbsent_doesNotSetRepoUrl() {
        SnykPackageHealth.Maintenance maintenance = new SnykPackageHealth.Maintenance(
                null, null, 1, false, false, "1.0.0", null, "active", "Healthy", 1);
        SnykPackageHealth health = new SnykPackageHealth(null, maintenance, null, null, null);
        SnykPackageDetails details = new SnykPackageDetails(null, null, null, null, null);

        SnykPackageResponse.Attributes attrs = new SnykPackageResponse.Attributes(
                "npm", null, "1.0.0", null, details, health, "pkg:npm/express", "express");
        SnykPackageResponse.Data data = new SnykPackageResponse.Data("pkg:npm/express", "package", attrs);
        SnykPackageResponse resp = new SnykPackageResponse(data);

        when(snykClient.getPackage(anyString(), anyString(), anyString(), anyString())).thenReturn(resp);

        PartialMetrics partial = collector.collect(npmPackage, Optional.empty());

        assertThat(partial.getRepoUrl()).isNull();
        assertThat(partial.getLastReleaseVersion()).isEqualTo("1.0.0");
    }

    @Test
    void collect_invalidRepositoryUrl_doesNotThrow() {
        SnykPackageDetails details = new SnykPackageDetails(null, null, "not a valid url", null, null);
        SnykPackageHealth health = new SnykPackageHealth(null, null, null, null, null);
        SnykPackageResponse.Attributes attrs = new SnykPackageResponse.Attributes(
                "npm", null, null, null, details, health, "pkg:npm/express", "express");
        SnykPackageResponse.Data data = new SnykPackageResponse.Data("pkg:npm/express", "package", attrs);
        SnykPackageResponse resp = new SnykPackageResponse(data);

        when(snykClient.getPackage(anyString(), anyString(), anyString(), anyString())).thenReturn(resp);

        PartialMetrics partial = collector.collect(npmPackage, Optional.empty());

        // Invalid URL is swallowed — repoUrl stays null but no exception.
        assertThat(partial.getRepoUrl()).isNull();
    }

    @Test
    void collect_snykRating_setOnlyWhenPresent() {
        // null rating in the maintenance block → null in PartialMetrics
        SnykPackageHealth.Maintenance maintenance = new SnykPackageHealth.Maintenance(
                null, null, null, null, null, null, null, null, null, null);
        SnykPackageHealth health = new SnykPackageHealth(null, maintenance, null, null, null);
        SnykPackageResponse.Attributes attrs = new SnykPackageResponse.Attributes(
                "npm", null, null, null, null, health, "pkg:npm/express", "express");
        SnykPackageResponse.Data data = new SnykPackageResponse.Data("pkg:npm/express", "package", attrs);
        SnykPackageResponse resp = new SnykPackageResponse(data);

        when(snykClient.getPackage(anyString(), anyString(), anyString(), anyString())).thenReturn(resp);

        PartialMetrics partial = collector.collect(npmPackage, Optional.empty());

        assertThat(partial.getSnykRating()).isNull();
    }

    @Test
    void collect_fallsBackToLatestReleaseNumberWhenLatestVersionMissing() {
        SnykPackageHealth.Maintenance maintenance = new SnykPackageHealth.Maintenance(
                null, null, null, false, false, "9.9.9", null, null, null, null);
        SnykPackageHealth health = new SnykPackageHealth(null, maintenance, null, null, null);
        SnykPackageResponse.Attributes attrs = new SnykPackageResponse.Attributes(
                "npm", null, null, null, null, health, "pkg:npm/express", "express");
        SnykPackageResponse.Data data = new SnykPackageResponse.Data("pkg:npm/express", "package", attrs);
        SnykPackageResponse resp = new SnykPackageResponse(data);

        when(snykClient.getPackage(anyString(), anyString(), anyString(), anyString())).thenReturn(resp);

        PartialMetrics partial = collector.collect(npmPackage, Optional.empty());

        assertThat(partial.getLastReleaseVersion()).isEqualTo("9.9.9");
        assertThat(partial.getLastReleaseVersionSource()).isEqualTo("SNYK");
    }
}
