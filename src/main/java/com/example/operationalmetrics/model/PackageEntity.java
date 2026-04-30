package com.example.operationalmetrics.model;

import java.time.Instant;

public class PackageEntity {

    private Long id;
    private String purlType;
    private String purlNamespace;
    private String purlName;
    private String purlCanonical;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPurlType() { return purlType; }
    public void setPurlType(String purlType) { this.purlType = purlType; }

    public String getPurlNamespace() { return purlNamespace; }
    public void setPurlNamespace(String purlNamespace) { this.purlNamespace = purlNamespace; }

    public String getPurlName() { return purlName; }
    public void setPurlName(String purlName) { this.purlName = purlName; }

    public String getPurlCanonical() { return purlCanonical; }
    public void setPurlCanonical(String purlCanonical) { this.purlCanonical = purlCanonical; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public PackageId toPackageId() {
        return new PackageId(purlType, purlNamespace, purlName);
    }
}
