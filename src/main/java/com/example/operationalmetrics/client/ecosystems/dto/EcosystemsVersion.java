package com.example.operationalmetrics.client.ecosystems.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EcosystemsVersion {

    private String number;

    @JsonProperty("published_at")
    private Instant publishedAt;

    private String licenses;

    @JsonProperty("pre_release")
    private Boolean preRelease;

    private String status;

    // Getters and setters

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    public String getLicenses() { return licenses; }
    public void setLicenses(String licenses) { this.licenses = licenses; }

    public Boolean getPreRelease() { return preRelease; }
    public void setPreRelease(Boolean preRelease) { this.preRelease = preRelease; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
