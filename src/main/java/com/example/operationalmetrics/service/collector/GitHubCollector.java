package com.example.operationalmetrics.service.collector;

import com.example.operationalmetrics.client.github.GitHubRestClient;
import com.example.operationalmetrics.client.github.dto.GitHubCommitActivity;
import com.example.operationalmetrics.client.github.dto.GitHubCommunityProfile;
import com.example.operationalmetrics.client.github.dto.GitHubRepo;
import com.example.operationalmetrics.model.MetricsSource;
import com.example.operationalmetrics.model.PackageId;
import com.example.operationalmetrics.model.PartialMetrics;
import com.example.operationalmetrics.model.RepoUrl;
import com.example.operationalmetrics.service.MetricsCollector;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class GitHubCollector implements MetricsCollector {

    private static final Logger LOG = Logger.getLogger(GitHubCollector.class);

    private final GitHubRestClient gitHubClient;
    private final ObjectMapper objectMapper;

    @Inject
    public GitHubCollector(@RestClient GitHubRestClient gitHubClient, ObjectMapper objectMapper) {
        this.gitHubClient = gitHubClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public MetricsSource source() {
        return MetricsSource.GITHUB;
    }

    @Override
    public boolean requiresRepoUrl() {
        return true;
    }

    @Override
    public boolean supports(PackageId packageId) {
        return true;
    }

    @Override
    public PartialMetrics collect(PackageId packageId, Optional<RepoUrl> repoUrl) {
        var partial = new PartialMetrics();
        if (repoUrl.isEmpty()) {
            return partial;
        }

        RepoUrl repo = repoUrl.get();
        if (!"github.com".equals(repo.platform())) {
            return partial;
        }

        try {
            GitHubRepo ghRepo = gitHubClient.getRepo(repo.owner(), repo.name());
            partial.setIsArchived(ghRepo.archived());

            if (ghRepo.pushedAt() != null) {
                partial.setLastCommitAt(ghRepo.pushedAt());
            }
        } catch (Exception e) {
            LOG.warnv("GitHub repo lookup failed for {0}/{1}: {2}", repo.owner(), repo.name(), e.getMessage());
        }

        try {
            GitHubCommunityProfile profile = gitHubClient.getCommunityProfile(repo.owner(), repo.name());
            if (profile.healthPercentage() != null) {
                partial.setCommunityHealthPct(profile.healthPercentage().floatValue());
            }
        } catch (Exception e) {
            LOG.debugv("GitHub community profile failed for {0}/{1}: {2}", repo.owner(), repo.name(), e.getMessage());
        }

        try {
            List<GitHubCommitActivity> activity = gitHubClient.getCommitActivity(repo.owner(), repo.name());
            if (activity != null && !activity.isEmpty()) {
                List<Integer> weeklyCounts = activity.stream()
                        .map(GitHubCommitActivity::total)
                        .toList();
                partial.setCommitFrequency52w(objectMapper.writeValueAsString(weeklyCounts));
                partial.setContributorCount(null);
            }
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to serialize commit activity", e);
        } catch (Exception e) {
            LOG.debugv("GitHub commit activity failed for {0}/{1}: {2}", repo.owner(), repo.name(), e.getMessage());
        }

        return partial;
    }
}
