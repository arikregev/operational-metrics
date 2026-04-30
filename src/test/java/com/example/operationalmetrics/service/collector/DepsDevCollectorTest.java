package com.example.operationalmetrics.service.collector;

import com.example.operationalmetrics.client.depsdev.DepsDevClient;
import com.example.operationalmetrics.client.depsdev.dto.DepsDevDependents;
import com.example.operationalmetrics.client.depsdev.dto.DepsDevProject;
import com.example.operationalmetrics.client.depsdev.dto.DepsDevPurlResponse;
import com.example.operationalmetrics.model.MetricsSource;
import com.example.operationalmetrics.model.PackageId;
import com.example.operationalmetrics.model.PartialMetrics;
import com.example.operationalmetrics.model.RepoUrl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepsDevCollectorTest {

    @Mock
    private DepsDevClient depsDevClient;

    private ObjectMapper objectMapper;
    private DepsDevCollector collector;
    private PackageId mavenPackage;
    private PackageId npmPackage;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        collector = new DepsDevCollector(depsDevClient, objectMapper);
        mavenPackage = new PackageId("maven", "org.apache.commons", "commons-lang3");
        npmPackage = new PackageId("npm", null, "express");
    }

    @Test
    void collect_fullResponse_populatesAllFields() throws Exception {
        DepsDevPurlResponse purlResponse = new DepsDevPurlResponse(
                new DepsDevPurlResponse.DepsDevVersionKey("MAVEN", "org.apache.commons:commons-lang3", "3.14.0"),
                "pkg:maven/org.apache.commons/commons-lang3@3.14.0",
                List.of(new DepsDevPurlResponse.DepsDevRelatedProject(
                        new DepsDevPurlResponse.DepsDevProjectKey("github.com/apache/commons-lang"),
                        "SOURCE_REPO_TYPE",
                        "FETCH"
                )),
                List.of(
                        new DepsDevPurlResponse.DepsDevAdvisoryKey("GHSA-xxxx-yyyy-zzzz"),
                        new DepsDevPurlResponse.DepsDevAdvisoryKey("GHSA-aaaa-bbbb-cccc")
                )
        );

        DepsDevProject.DepsDevScorecard scorecard = new DepsDevProject.DepsDevScorecard(
                6.4f,
                List.of(new DepsDevProject.DepsDevScorecardCheck("Maintained", 10, "ok")),
                "2026-04-27"
        );
        DepsDevProject project = new DepsDevProject(
                new DepsDevPurlResponse.DepsDevProjectKey("github.com/apache/commons-lang"),
                42,
                2500,
                510,
                "Apache-2.0",
                "Apache Commons Lang",
                "https://commons.apache.org/proper/commons-lang/",
                scorecard,
                new DepsDevProject.DepsDevOssFuzz(1000, 800)
        );
        DepsDevDependents dependents = new DepsDevDependents(1234L, 200L, 1034L);

        String encodedPurl = URLEncoder.encode(mavenPackage.canonical(), StandardCharsets.UTF_8);
        String encodedProjectId = URLEncoder.encode("github.com/apache/commons-lang", StandardCharsets.UTF_8);
        String encodedName = URLEncoder.encode("org.apache.commons:commons-lang3", StandardCharsets.UTF_8);

        when(depsDevClient.lookupPurl(encodedPurl)).thenReturn(purlResponse);
        when(depsDevClient.getProject(encodedProjectId)).thenReturn(project);
        when(depsDevClient.getDependents("MAVEN", encodedName, "3.14.0")).thenReturn(dependents);

        PartialMetrics partial = collector.collect(mavenPackage, Optional.empty());

        assertThat(partial.getAdvisoryCount()).isEqualTo(2);
        RepoUrl resolved = partial.getRepoUrl();
        assertThat(resolved).isNotNull();
        assertThat(resolved.platform()).isEqualTo("github.com");
        assertThat(resolved.owner()).isEqualTo("apache");
        assertThat(resolved.name()).isEqualTo("commons-lang");

        assertThat(partial.getStarsCount()).isEqualTo(2500);
        assertThat(partial.getForksCount()).isEqualTo(510);
        assertThat(partial.getLicense()).isEqualTo("Apache-2.0");
        assertThat(partial.getOpenIssuesCount()).isEqualTo(42);
        assertThat(partial.getScorecardOverallScore()).isEqualTo(6.4f);
        assertThat(partial.getScorecardSource()).isEqualTo(MetricsSource.DEPS_DEV.name());
        assertThat(partial.getScorecardChecks()).contains("Maintained");
        assertThat(partial.getHasOssFuzz()).isTrue();
        assertThat(partial.getDependentPackagesCount()).isEqualTo(1234L);
    }

    @Test
    void collect_clientThrows_returnsEmpty() {
        when(depsDevClient.lookupPurl(anyString())).thenThrow(new RuntimeException("boom"));

        PartialMetrics partial = collector.collect(mavenPackage, Optional.empty());

        assertThat(partial.getAdvisoryCount()).isNull();
        assertThat(partial.getStarsCount()).isNull();
        assertThat(partial.getRepoUrl()).isNull();
        assertThat(partial.getDependentPackagesCount()).isNull();
        verify(depsDevClient, never()).getProject(anyString());
        verify(depsDevClient, never()).getDependents(anyString(), anyString(), anyString());
    }

    @Test
    void collect_noRelatedProjects_skipsProjectLookup() {
        DepsDevPurlResponse purlResponse = new DepsDevPurlResponse(
                new DepsDevPurlResponse.DepsDevVersionKey("NPM", "express", "4.18.0"),
                "pkg:npm/express@4.18.0",
                List.of(),
                List.of()
        );
        String encodedPurl = URLEncoder.encode(npmPackage.canonical(), StandardCharsets.UTF_8);
        String encodedName = URLEncoder.encode("express", StandardCharsets.UTF_8);
        when(depsDevClient.lookupPurl(encodedPurl)).thenReturn(purlResponse);
        when(depsDevClient.getDependents("NPM", encodedName, "4.18.0"))
                .thenReturn(new DepsDevDependents(0L, 0L, 0L));

        PartialMetrics partial = collector.collect(npmPackage, Optional.empty());

        assertThat(partial.getRepoUrl()).isNull();
        assertThat(partial.getStarsCount()).isNull();
        verify(depsDevClient, never()).getProject(anyString());
    }

    @Test
    void collect_advisoryKeys_setsAdvisoryCount() {
        DepsDevPurlResponse purlResponse = new DepsDevPurlResponse(
                new DepsDevPurlResponse.DepsDevVersionKey("NPM", "express", "4.18.0"),
                "pkg:npm/express@4.18.0",
                null,
                List.of(
                        new DepsDevPurlResponse.DepsDevAdvisoryKey("a"),
                        new DepsDevPurlResponse.DepsDevAdvisoryKey("b"),
                        new DepsDevPurlResponse.DepsDevAdvisoryKey("c")
                )
        );
        String encodedPurl = URLEncoder.encode(npmPackage.canonical(), StandardCharsets.UTF_8);
        when(depsDevClient.lookupPurl(encodedPurl)).thenReturn(purlResponse);

        PartialMetrics partial = collector.collect(npmPackage, Optional.empty());

        assertThat(partial.getAdvisoryCount()).isEqualTo(3);
    }

    @Test
    void collect_ossFuzzPresent_setsHasOssFuzzTrue() {
        DepsDevPurlResponse purlResponse = new DepsDevPurlResponse(
                new DepsDevPurlResponse.DepsDevVersionKey("NPM", "express", "4.18.0"),
                "pkg:npm/express@4.18.0",
                List.of(new DepsDevPurlResponse.DepsDevRelatedProject(
                        new DepsDevPurlResponse.DepsDevProjectKey("github.com/expressjs/express"),
                        "SOURCE_REPO_TYPE",
                        "FETCH"
                )),
                List.of()
        );
        DepsDevProject project = new DepsDevProject(
                new DepsDevPurlResponse.DepsDevProjectKey("github.com/expressjs/express"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new DepsDevProject.DepsDevOssFuzz(100, 80)
        );
        String encodedPurl = URLEncoder.encode(npmPackage.canonical(), StandardCharsets.UTF_8);
        String encodedProjectId = URLEncoder.encode("github.com/expressjs/express", StandardCharsets.UTF_8);
        when(depsDevClient.lookupPurl(encodedPurl)).thenReturn(purlResponse);
        when(depsDevClient.getProject(encodedProjectId)).thenReturn(project);

        PartialMetrics partial = collector.collect(npmPackage, Optional.empty());

        assertThat(partial.getHasOssFuzz()).isTrue();
    }

    @Test
    void collect_dependentsLookupFails_continues() {
        DepsDevPurlResponse purlResponse = new DepsDevPurlResponse(
                new DepsDevPurlResponse.DepsDevVersionKey("NPM", "express", "4.18.0"),
                "pkg:npm/express@4.18.0",
                List.of(new DepsDevPurlResponse.DepsDevRelatedProject(
                        new DepsDevPurlResponse.DepsDevProjectKey("github.com/expressjs/express"),
                        "SOURCE_REPO_TYPE",
                        "FETCH"
                )),
                List.of()
        );
        DepsDevProject project = new DepsDevProject(
                new DepsDevPurlResponse.DepsDevProjectKey("github.com/expressjs/express"),
                null,
                100,
                null,
                "MIT",
                null,
                null,
                null,
                null
        );
        String encodedPurl = URLEncoder.encode(npmPackage.canonical(), StandardCharsets.UTF_8);
        String encodedProjectId = URLEncoder.encode("github.com/expressjs/express", StandardCharsets.UTF_8);
        when(depsDevClient.lookupPurl(encodedPurl)).thenReturn(purlResponse);
        when(depsDevClient.getProject(encodedProjectId)).thenReturn(project);
        when(depsDevClient.getDependents(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("dependents failed"));

        PartialMetrics partial = collector.collect(npmPackage, Optional.empty());

        // Project info still populated despite dependents failure
        assertThat(partial.getStarsCount()).isEqualTo(100);
        assertThat(partial.getLicense()).isEqualTo("MIT");
        assertThat(partial.getDependentPackagesCount()).isNull();
    }

    @Test
    void source_returnsDepsDev() {
        assertThat(collector.source()).isEqualTo(MetricsSource.DEPS_DEV);
    }

    @Test
    void requiresRepoUrl_returnsFalse() {
        assertThat(collector.requiresRepoUrl()).isFalse();
    }

    @Test
    void supports_returnsTrue() {
        assertThat(collector.supports(mavenPackage)).isTrue();
        assertThat(collector.supports(npmPackage)).isTrue();
    }

    @Test
    void collect_projectLookupFails_continuesWithDependents() {
        DepsDevPurlResponse purlResponse = new DepsDevPurlResponse(
                new DepsDevPurlResponse.DepsDevVersionKey("NPM", "express", "4.18.0"),
                "pkg:npm/express@4.18.0",
                List.of(new DepsDevPurlResponse.DepsDevRelatedProject(
                        new DepsDevPurlResponse.DepsDevProjectKey("github.com/expressjs/express"),
                        "SOURCE_REPO_TYPE",
                        "FETCH"
                )),
                List.of()
        );
        String encodedPurl = URLEncoder.encode(npmPackage.canonical(), StandardCharsets.UTF_8);
        when(depsDevClient.lookupPurl(encodedPurl)).thenReturn(purlResponse);
        when(depsDevClient.getProject(anyString())).thenThrow(new RuntimeException("project lookup failed"));
        when(depsDevClient.getDependents(anyString(), anyString(), anyString()))
                .thenReturn(new DepsDevDependents(7L, 3L, 4L));

        PartialMetrics partial = collector.collect(npmPackage, Optional.empty());

        // Repo URL still resolved, project mapping skipped, dependents lookup still happens
        assertThat(partial.getRepoUrl()).isNotNull();
        assertThat(partial.getStarsCount()).isNull();
        assertThat(partial.getDependentPackagesCount()).isEqualTo(7L);
    }

    @Test
    void collect_unparseableProjectId_continuesWithProjectLookup() {
        // projectId without enough slashes — resolveRepoUrl will throw, but project lookup still proceeds
        DepsDevPurlResponse purlResponse = new DepsDevPurlResponse(
                new DepsDevPurlResponse.DepsDevVersionKey("NPM", "express", "4.18.0"),
                "pkg:npm/express@4.18.0",
                List.of(new DepsDevPurlResponse.DepsDevRelatedProject(
                        new DepsDevPurlResponse.DepsDevProjectKey("github.com/onlyone"),
                        "SOURCE_REPO_TYPE",
                        "FETCH"
                )),
                List.of()
        );
        DepsDevProject project = new DepsDevProject(
                new DepsDevPurlResponse.DepsDevProjectKey("github.com/onlyone"),
                null, 1, 0, "BSD-3-Clause", null, null, null, null
        );
        String encodedPurl = URLEncoder.encode(npmPackage.canonical(), StandardCharsets.UTF_8);
        String encodedProjectId = URLEncoder.encode("github.com/onlyone", StandardCharsets.UTF_8);
        when(depsDevClient.lookupPurl(encodedPurl)).thenReturn(purlResponse);
        when(depsDevClient.getProject(encodedProjectId)).thenReturn(project);

        PartialMetrics partial = collector.collect(npmPackage, Optional.empty());

        // RepoUrl was not set due to parse failure, but project lookup still populated other fields
        assertThat(partial.getRepoUrl()).isNull();
        assertThat(partial.getStarsCount()).isEqualTo(1);
        assertThat(partial.getLicense()).isEqualTo("BSD-3-Clause");
    }

    @Test
    void collect_pypiPurl_mapsToPypiSystem() {
        PackageId pypi = new PackageId("pypi", null, "requests");
        DepsDevPurlResponse purlResponse = new DepsDevPurlResponse(
                new DepsDevPurlResponse.DepsDevVersionKey("PYPI", "requests", "2.31.0"),
                "pkg:pypi/requests@2.31.0",
                null,
                null
        );
        String encodedPurl = URLEncoder.encode(pypi.canonical(), StandardCharsets.UTF_8);
        when(depsDevClient.lookupPurl(encodedPurl)).thenReturn(purlResponse);
        when(depsDevClient.getDependents(anyString(), anyString(), anyString()))
                .thenReturn(new DepsDevDependents(0L, 0L, 0L));

        collector.collect(pypi, Optional.empty());

        verify(depsDevClient).getDependents("PYPI", URLEncoder.encode("requests", StandardCharsets.UTF_8), "2.31.0");
    }

    @Test
    void collect_golangPurl_mapsToGoSystem() {
        PackageId golang = new PackageId("golang", null, "k8s.io/client-go");
        DepsDevPurlResponse purlResponse = new DepsDevPurlResponse(
                new DepsDevPurlResponse.DepsDevVersionKey("GO", "k8s.io/client-go", "v0.30.0"),
                "pkg:golang/k8s.io/client-go@v0.30.0",
                null,
                null
        );
        String encodedPurl = URLEncoder.encode(golang.canonical(), StandardCharsets.UTF_8);
        when(depsDevClient.lookupPurl(encodedPurl)).thenReturn(purlResponse);
        when(depsDevClient.getDependents(anyString(), anyString(), anyString()))
                .thenReturn(new DepsDevDependents(0L, 0L, 0L));

        collector.collect(golang, Optional.empty());

        verify(depsDevClient).getDependents(eq("GO"), anyString(), eq("v0.30.0"));
    }

    @Test
    void collect_unknownPurlType_uppercases() {
        PackageId composer = new PackageId("composer", null, "vendor/pkg");
        DepsDevPurlResponse purlResponse = new DepsDevPurlResponse(
                new DepsDevPurlResponse.DepsDevVersionKey("COMPOSER", "vendor/pkg", "1.0.0"),
                "pkg:composer/vendor/pkg@1.0.0",
                null,
                null
        );
        String encodedPurl = URLEncoder.encode(composer.canonical(), StandardCharsets.UTF_8);
        when(depsDevClient.lookupPurl(encodedPurl)).thenReturn(purlResponse);
        when(depsDevClient.getDependents(anyString(), anyString(), anyString()))
                .thenReturn(new DepsDevDependents(0L, 0L, 0L));

        collector.collect(composer, Optional.empty());

        verify(depsDevClient).getDependents(eq("COMPOSER"), anyString(), anyString());
    }

    @Test
    void collect_mavenPurl_encodesGroupAndArtifact() {
        DepsDevPurlResponse purlResponse = new DepsDevPurlResponse(
                new DepsDevPurlResponse.DepsDevVersionKey("MAVEN", "org.apache.commons:commons-lang3", "3.14.0"),
                "pkg:maven/org.apache.commons/commons-lang3@3.14.0",
                null,
                null
        );
        String encodedPurl = URLEncoder.encode(mavenPackage.canonical(), StandardCharsets.UTF_8);
        when(depsDevClient.lookupPurl(encodedPurl)).thenReturn(purlResponse);
        when(depsDevClient.getDependents(anyString(), anyString(), anyString()))
                .thenReturn(new DepsDevDependents(0L, 0L, 0L));

        collector.collect(mavenPackage, Optional.empty());

        ArgumentCaptor<String> systemCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> nameCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> versionCap = ArgumentCaptor.forClass(String.class);
        verify(depsDevClient).getDependents(systemCap.capture(), nameCap.capture(), versionCap.capture());

        assertThat(systemCap.getValue()).isEqualTo("MAVEN");
        // Maven name is "namespace:name" url-encoded; ":" encodes to %3A
        String expectedName = URLEncoder.encode("org.apache.commons:commons-lang3", StandardCharsets.UTF_8);
        assertThat(nameCap.getValue()).isEqualTo(expectedName);
        assertThat(nameCap.getValue()).contains("%3A");
        assertThat(versionCap.getValue()).isEqualTo("3.14.0");
    }
}
