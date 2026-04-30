package com.example.operationalmetrics.service.collector;

import com.example.operationalmetrics.client.scorecard.ScorecardClient;
import com.example.operationalmetrics.client.scorecard.dto.ScorecardResult;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

@ApplicationScoped
public class ScorecardCollector implements MetricsCollector {

    private static final Logger LOG = Logger.getLogger(ScorecardCollector.class);

    private final ScorecardClient scorecardClient;
    private final ObjectMapper objectMapper;

    @Inject
    public ScorecardCollector(@RestClient ScorecardClient scorecardClient, ObjectMapper objectMapper) {
        this.scorecardClient = scorecardClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public MetricsSource source() {
        return MetricsSource.SCORECARD;
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
            LOG.debugv("Scorecard only supports github.com, skipping {0}", repo.platform());
            return partial;
        }

        ScorecardResult result = scorecardClient.getScorecard(repo.platform(), repo.owner(), repo.name());

        partial.setScorecardOverallScore(result.score());
        partial.setScorecardSource(MetricsSource.SCORECARD.name());

        if (result.checks() != null) {
            try {
                partial.setScorecardChecks(objectMapper.writeValueAsString(result.checks()));
            } catch (JsonProcessingException e) {
                LOG.warn("Failed to serialize scorecard checks", e);
            }
        }

        if (result.date() != null) {
            try {
                partial.setScorecardDate(LocalDate.parse(result.date()).atStartOfDay().toInstant(ZoneOffset.UTC));
            } catch (Exception e) {
                LOG.debugv("Could not parse scorecard date: {0}", result.date());
            }
        }

        return partial;
    }
}
