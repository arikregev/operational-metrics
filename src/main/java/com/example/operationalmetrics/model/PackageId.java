package com.example.operationalmetrics.model;

import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;

import java.util.Objects;

public record PackageId(String purlType, String namespace, String name) {

    public PackageId {
        Objects.requireNonNull(purlType, "purlType must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }

    public String canonical() {
        if (namespace != null && !namespace.isBlank()) {
            return "pkg:" + purlType + "/" + namespace + "/" + name;
        }
        return "pkg:" + purlType + "/" + name;
    }

    public static PackageId fromPurl(String purlString) {
        try {
            PackageURL purl = new PackageURL(purlString);
            return new PackageId(purl.getType(), purl.getNamespace(), purl.getName());
        } catch (MalformedPackageURLException e) {
            throw new IllegalArgumentException("Invalid PURL: " + purlString, e);
        }
    }

    public PackageEntity toEntity() {
        var entity = new PackageEntity();
        entity.setPurlType(purlType);
        entity.setPurlNamespace(namespace);
        entity.setPurlName(name);
        entity.setPurlCanonical(canonical());
        return entity;
    }
}
