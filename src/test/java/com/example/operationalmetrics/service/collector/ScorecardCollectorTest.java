package com.example.operationalmetrics.service.collector;

import com.example.operationalmetrics.client.scorecard.ScorecardClient;
import com.example.operationalmetrics.client.scorecard.dto.ScorecardCheck;
import com.example.operationalmetrics.client.scorecard.dto.ScorecardResult;
import com.example.operationalmetrics.model.MetricsSource;
import com.example.operationalmetrics.model.PackageId;
import com.example.operationalmetrics.model.PartialMetrics;
import com.example.operationalmetrics.model.RepoUrl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScorecardCollectorTest {

    @Mock
    private ScorecardClient scorecardClient;

    private ObjectMapper objectMapper;
    private ScorecardCollector collector;
    private PackageId packageId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        collector = new ScorecardCollector(scorecardClient, objectMapper);
        packageId = new PackageId("maven", "org.example", "lib");
    }

    @Test
    void collect_withGitHubRepo_returnsPopulatedMetrics() {
        RepoUrl repoUrl = RepoUrl.fromComponents("github.com", "octocat", "hello-world");

        ScorecardCheck check = new ScorecardCheck(
                "Maintained",
                10,
                "30 commit(s) and 5 issue activity found in the last 90 days",
                List.of("detail-1"),
                new ScorecardCheck.ScorecardDocumentation("short", "https://example.com")
        );
        ScorecardResult result = new ScorecardResult(
                "2026-04-27",
                new ScorecardResult.ScorecardRepo("github.com/octocat/hello-world", "abc123"),
                new ScorecardResult.ScorecardVersion("v4.0.0", "def456"),
                7.5f,
                List.of(check)
        );
        when(scorecardClient.getScorecard("github.com", "octocat", "hello-world")).thenReturn(result);

        PartialMetrics partial = collector.collect(packageId, Optional.of(repoUrl));

        assertThat(partial.getScorecardOverallScore()).isEqualTo(7.5f);
        assertThat(partial.getScorecardSource()).isEqualTo(MetricsSource.SCORECARD.name());
        assertThat(partial.getScorecardChecks()).contains("Maintained").contains("\"score\":10");
        Instant expected = LocalDate.parse("2026-04-27").atStartOfDay().toInstant(ZoneOffset.UTC);
        assertThat(partial.getScorecardDate()).isEqualTo(expected);
    }

    @Test
    void collect_withNoRepoUrl_returnsEmpty() {
        PartialMetrics partial = collector.collect(packageId, Optional.empty());

        assertThat(partial.getScorecardOverallScore()).isNull();
        assertThat(partial.getScorecardSource()).isNull();
        assertThat(partial.getScorecardChecks()).isNull();
        assertThat(partial.getScorecardDate()).isNull();
        verify(scorecardClient, never()).getScorecard(anyString(), anyString(), anyString());
    }

    @Test
    void collect_withGitlabRepo_returnsEmpty() {
        RepoUrl repoUrl = RepoUrl.fromComponents("gitlab.com", "group", "project");

        PartialMetrics partial = collector.collect(packageId, Optional.of(repoUrl));

        assertThat(partial.getScorecardOverallScore()).isNull();
        assertThat(partial.getScorecardSource()).isNull();
        verify(scorecardClient, never()).getScorecard(anyString(), anyString(), anyString());
    }

    @Test
    void source_returnsScorecard() {
        assertThat(collector.source()).isEqualTo(MetricsSource.SCORECARD);
    }

    @Test
    void requiresRepoUrl_returnsTrue() {
        assertThat(collector.requiresRepoUrl()).isTrue();
    }

    @Test
    void supports_returnsTrue() {
        assertThat(collector.supports(packageId)).isTrue();
        assertThat(collector.supports(new PackageId("npm", null, "express"))).isTrue();
    }

    @Test
    void collect_withNullChecks_skipsChecksField() {
        RepoUrl repoUrl = RepoUrl.fromComponents("github.com", "octocat", "hello-world");
        ScorecardResult result = new ScorecardResult(
                "2026-04-27",
                null,
                null,
                4.2f,
                null
        );
        when(scorecardClient.getScorecard("github.com", "octocat", "hello-world")).thenReturn(result);

        PartialMetrics partial = collector.collect(packageId, Optional.of(repoUrl));

        assertThat(partial.getScorecardOverallScore()).isEqualTo(4.2f);
        assertThat(partial.getScorecardSource()).isEqualTo(MetricsSource.SCORECARD.name());
        assertThat(partial.getScorecardChecks()).isNull();
    }

    @Test
    void collect_withMalformedDate_doesNotThrow() {
        RepoUrl repoUrl = RepoUrl.fromComponents("github.com", "octocat", "hello-world");
        ScorecardResult result = new ScorecardResult(
                "not-a-date",
                null,
                null,
                3.0f,
                List.of()
        );
        when(scorecardClient.getScorecard("github.com", "octocat", "hello-world")).thenReturn(result);

        PartialMetrics partial = collector.collect(packageId, Optional.of(repoUrl));

        assertThat(partial.getScorecardOverallScore()).isEqualTo(3.0f);
        assertThat(partial.getScorecardSource()).isEqualTo(MetricsSource.SCORECARD.name());
        assertThat(partial.getScorecardDate()).isNull();
    }
}
