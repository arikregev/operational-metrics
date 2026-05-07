package com.example.operationalmetrics.client.ecosystems.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Minimal subset of the ecosyste.ms package-list response. The full response
 * has dozens of fields; we only care about identifying the package and
 * deciding whether its latest release crossed the watermark.
 *
 * <p>{@code latest_release_published_at} is the field the changes-feed sorts
 * by. Do NOT use {@code updated_at} — that bumps on any metadata re-scrape
 * and produces false positives.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EcosystemsPackageRef(
        String name,
        String ecosystem,
        @JsonProperty("latest_release_number") String latestReleaseNumber,
        @JsonProperty("latest_release_published_at") Instant latestReleasePublishedAt
) {}
