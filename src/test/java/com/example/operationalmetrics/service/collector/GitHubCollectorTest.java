package com.example.operationalmetrics.service.collector;

import com.example.operationalmetrics.client.github.GitHubRestClient;
import com.example.operationalmetrics.client.github.dto.GitHubCommitActivity;
import com.example.operationalmetrics.client.github.dto.GitHubCommunityProfile;
import com.example.operationalmetrics.client.github.dto.GitHubRepo;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubCollectorTest {

    @Mock
    private GitHubRestClient gitHubClient;

    private ObjectMapper objectMapper;
    private GitHubCollector collector;
    private PackageId packageId;
    private RepoUrl gitHubRepo;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        collector = new GitHubCollector(gitHubClient, objectMapper);
        packageId = new PackageId("npm", null, "express");
        gitHubRepo = RepoUrl.fromComponents("github.com", "octocat", "hello-world");
    }

    @Test
    void collect_fullResponse_mapsAllFields() {
        Instant pushedAt = Instant.parse("2026-04-20T10:00:00Z");
        GitHubRepo repo = new GitHubRepo(
                1L,
                "hello-world",
                "octocat/hello-world",
                500,
                75,
                12,
                false,
                false,
                pushedAt,
                new GitHubRepo.GitHubLicense("mit", "MIT", "MIT License")
        );
        GitHubCommunityProfile profile = new GitHubCommunityProfile(85, "desc", "docs");
        List<GitHubCommitActivity> activity = List.of(
                new GitHubCommitActivity(List.of(0, 1, 2), 5, 1234L),
                new GitHubCommitActivity(List.of(0, 0, 1), 3, 1235L)
        );

        when(gitHubClient.getRepo("octocat", "hello-world")).thenReturn(repo);
        when(gitHubClient.getCommunityProfile("octocat", "hello-world")).thenReturn(profile);
        when(gitHubClient.getCommitActivity("octocat", "hello-world")).thenReturn(activity);

        PartialMetrics partial = collector.collect(packageId, Optional.of(gitHubRepo));

        assertThat(partial.getStarsCount()).isEqualTo(500);
        assertThat(partial.getForksCount()).isEqualTo(75);
        assertThat(partial.getOpenIssuesCount()).isEqualTo(12);
        assertThat(partial.getIsArchived()).isFalse();
        assertThat(partial.getLastCommitAt()).isEqualTo(pushedAt);
        assertThat(partial.getLicense()).isEqualTo("MIT");
        assertThat(partial.getCommunityHealthPct()).isEqualTo(85.0f);
        assertThat(partial.getCommitFrequency52w()).isEqualTo("[5,3]");
    }

    @Test
    void collect_noRepoUrl_returnsEmpty() {
        PartialMetrics partial = collector.collect(packageId, Optional.empty());

        assertThat(partial.getStarsCount()).isNull();
        assertThat(partial.getForksCount()).isNull();
        verify(gitHubClient, never()).getRepo(anyString(), anyString());
        verify(gitHubClient, never()).getCommunityProfile(anyString(), anyString());
        verify(gitHubClient, never()).getCommitActivity(anyString(), anyString());
    }

    @Test
    void collect_nonGithubPlatform_returnsEmpty() {
        RepoUrl gitlab = RepoUrl.fromComponents("gitlab.com", "group", "repo");

        PartialMetrics partial = collector.collect(packageId, Optional.of(gitlab));

        assertThat(partial.getStarsCount()).isNull();
        verify(gitHubClient, never()).getRepo(anyString(), anyString());
    }

    @Test
    void collect_getRepoFails_continuesWithOtherCalls() {
        when(gitHubClient.getRepo(anyString(), anyString())).thenThrow(new RuntimeException("404"));
        when(gitHubClient.getCommunityProfile("octocat", "hello-world"))
                .thenReturn(new GitHubCommunityProfile(70, null, null));
        when(gitHubClient.getCommitActivity("octocat", "hello-world"))
                .thenReturn(List.of(new GitHubCommitActivity(List.of(), 1, 100L)));

        PartialMetrics partial = collector.collect(packageId, Optional.of(gitHubRepo));

        assertThat(partial.getStarsCount()).isNull();
        assertThat(partial.getCommunityHealthPct()).isEqualTo(70.0f);
        assertThat(partial.getCommitFrequency52w()).isEqualTo("[1]");
    }

    @Test
    void collect_getCommunityProfileFails_continues() {
        Instant pushedAt = Instant.parse("2026-04-20T10:00:00Z");
        GitHubRepo repo = new GitHubRepo(
                1L, "hello-world", "octocat/hello-world",
                10, 1, 0, false, false, pushedAt,
                new GitHubRepo.GitHubLicense("mit", "MIT", "MIT License")
        );
        when(gitHubClient.getRepo("octocat", "hello-world")).thenReturn(repo);
        when(gitHubClient.getCommunityProfile(anyString(), anyString()))
                .thenThrow(new RuntimeException("403"));
        when(gitHubClient.getCommitActivity("octocat", "hello-world"))
                .thenReturn(List.of(new GitHubCommitActivity(List.of(), 7, 999L)));

        PartialMetrics partial = collector.collect(packageId, Optional.of(gitHubRepo));

        assertThat(partial.getStarsCount()).isEqualTo(10);
        assertThat(partial.getLicense()).isEqualTo("MIT");
        assertThat(partial.getCommunityHealthPct()).isNull();
        assertThat(partial.getCommitFrequency52w()).isEqualTo("[7]");
    }

    @Test
    void collect_getCommitActivityFails_continues() {
        Instant pushedAt = Instant.parse("2026-04-20T10:00:00Z");
        GitHubRepo repo = new GitHubRepo(
                1L, "hello-world", "octocat/hello-world",
                10, 1, 0, false, false, pushedAt,
                new GitHubRepo.GitHubLicense("mit", "MIT", "MIT License")
        );
        when(gitHubClient.getRepo("octocat", "hello-world")).thenReturn(repo);
        when(gitHubClient.getCommunityProfile("octocat", "hello-world"))
                .thenReturn(new GitHubCommunityProfile(60, null, null));
        when(gitHubClient.getCommitActivity(anyString(), anyString()))
                .thenThrow(new RuntimeException("503"));

        PartialMetrics partial = collector.collect(packageId, Optional.of(gitHubRepo));

        assertThat(partial.getStarsCount()).isEqualTo(10);
        assertThat(partial.getCommunityHealthPct()).isEqualTo(60.0f);
        assertThat(partial.getCommitFrequency52w()).isNull();
    }

    @Test
    void collect_serializesWeeklyTotalsAsJson() {
        Instant pushedAt = Instant.parse("2026-04-20T10:00:00Z");
        GitHubRepo repo = new GitHubRepo(
                1L, "hello-world", "octocat/hello-world",
                0, 0, 0, false, false, pushedAt, null
        );
        when(gitHubClient.getRepo("octocat", "hello-world")).thenReturn(repo);
        when(gitHubClient.getCommunityProfile(anyString(), anyString()))
                .thenReturn(new GitHubCommunityProfile(null, null, null));
        when(gitHubClient.getCommitActivity("octocat", "hello-world"))
                .thenReturn(List.of(
                        new GitHubCommitActivity(List.of(), 1, 1L),
                        new GitHubCommitActivity(List.of(), 2, 2L),
                        new GitHubCommitActivity(List.of(), 3, 3L),
                        new GitHubCommitActivity(List.of(), 4, 4L)
                ));

        PartialMetrics partial = collector.collect(packageId, Optional.of(gitHubRepo));

        assertThat(partial.getCommitFrequency52w()).isEqualTo("[1,2,3,4]");
        assertThat(partial.getLicense()).isNull();
    }

    @Test
    void source_returnsGithub() {
        assertThat(collector.source()).isEqualTo(MetricsSource.GITHUB);
    }

    @Test
    void requiresRepoUrl_returnsTrue() {
        assertThat(collector.requiresRepoUrl()).isTrue();
    }
}
