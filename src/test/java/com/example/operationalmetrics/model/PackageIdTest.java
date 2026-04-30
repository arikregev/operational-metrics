package com.example.operationalmetrics.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PackageIdTest {

    @Test
    void canonicalIncludesNamespaceWhenPresent() {
        var id = new PackageId("maven", "org.apache.logging.log4j", "log4j-core");
        assertThat(id.canonical()).isEqualTo("pkg:maven/org.apache.logging.log4j/log4j-core");
    }

    @Test
    void canonicalOmitsNamespaceWhenNull() {
        var id = new PackageId("npm", null, "express");
        assertThat(id.canonical()).isEqualTo("pkg:npm/express");
    }

    @Test
    void canonicalOmitsBlankNamespace() {
        var id = new PackageId("npm", "  ", "express");
        assertThat(id.canonical()).isEqualTo("pkg:npm/express");
    }

    @Test
    void fromPurlParsesMaven() {
        var id = PackageId.fromPurl("pkg:maven/org.apache.logging.log4j/log4j-core@2.17.1");
        assertThat(id.purlType()).isEqualTo("maven");
        assertThat(id.namespace()).isEqualTo("org.apache.logging.log4j");
        assertThat(id.name()).isEqualTo("log4j-core");
    }

    @Test
    void fromPurlParsesNpmWithoutNamespace() {
        var id = PackageId.fromPurl("pkg:npm/express@4.18.0");
        assertThat(id.purlType()).isEqualTo("npm");
        assertThat(id.namespace()).isNull();
        assertThat(id.name()).isEqualTo("express");
    }

    @Test
    void fromPurlStripsVersion() {
        var versioned = PackageId.fromPurl("pkg:pypi/requests@2.31.0");
        var unversioned = PackageId.fromPurl("pkg:pypi/requests");
        assertThat(versioned).isEqualTo(unversioned);
    }

    @Test
    void fromPurlThrowsOnInvalidPurl() {
        assertThatThrownBy(() -> PackageId.fromPurl("not-a-purl"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid PURL");
    }

    @Test
    void toEntityCopiesAllFields() {
        var id = new PackageId("maven", "com.example", "lib");
        var entity = id.toEntity();
        assertThat(entity.getPurlType()).isEqualTo("maven");
        assertThat(entity.getPurlNamespace()).isEqualTo("com.example");
        assertThat(entity.getPurlName()).isEqualTo("lib");
        assertThat(entity.getPurlCanonical()).isEqualTo("pkg:maven/com.example/lib");
    }

    @Test
    void constructorRejectsNullType() {
        assertThatThrownBy(() -> new PackageId(null, "ns", "name"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorRejectsNullName() {
        assertThatThrownBy(() -> new PackageId("maven", "ns", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void recordEqualityIgnoresVersion() {
        var a = new PackageId("maven", "g", "a");
        var b = new PackageId("maven", "g", "a");
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
}
