package com.example.operationalmetrics.service;

import com.example.operationalmetrics.model.MetricsSource;
import com.example.operationalmetrics.model.PackageId;
import com.example.operationalmetrics.model.PartialMetrics;
import com.example.operationalmetrics.model.RepoUrl;

import java.util.Optional;

public interface MetricsCollector {

    MetricsSource source();

    boolean requiresRepoUrl();

    boolean supports(PackageId packageId);

    PartialMetrics collect(PackageId packageId, Optional<RepoUrl> repoUrl);
}
