package com.example.operationalmetrics.service;

import com.example.operationalmetrics.model.PackageId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SbomParserServiceTest {

    private SbomParserService service;

    @BeforeEach
    void setUp() {
        service = new SbomParserService();
    }

    @Test
    void parse_validCycloneDxJson_returnsPackageIds() {
        String json = """
                {
                  "bomFormat": "CycloneDX",
                  "specVersion": "1.5",
                  "version": 1,
                  "components": [
                    {"type": "library", "name": "log4j-core", "version": "2.17.1", "purl": "pkg:maven/org.apache.logging.log4j/log4j-core@2.17.1"},
                    {"type": "library", "name": "express", "version": "4.18.0", "purl": "pkg:npm/express@4.18.0"}
                  ]
                }
                """;
        InputStream input = toStream(json);

        List<PackageId> result = service.parse(input, "test.json");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(PackageId::canonical)
                .containsExactlyInAnyOrder(
                        "pkg:maven/org.apache.logging.log4j/log4j-core",
                        "pkg:npm/express");
    }

    @Test
    void parse_invalidContent_throwsIllegalArgumentException() {
        InputStream input = toStream("this is not a valid sbom");

        assertThatThrownBy(() -> service.parse(input, "bad.json"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_emptyComponents_returnsEmptyList() {
        String json = """
                {
                  "bomFormat": "CycloneDX",
                  "specVersion": "1.5",
                  "version": 1,
                  "components": []
                }
                """;
        InputStream input = toStream(json);

        List<PackageId> result = service.parse(input, "empty.json");

        assertThat(result).isEmpty();
    }

    @Test
    void parse_componentsWithoutPurl_skipped() {
        String json = """
                {
                  "bomFormat": "CycloneDX",
                  "specVersion": "1.5",
                  "version": 1,
                  "components": [
                    {"type": "library", "name": "no-purl", "version": "1.0.0"},
                    {"type": "library", "name": "log4j-core", "version": "2.17.1", "purl": "pkg:maven/org.apache.logging.log4j/log4j-core@2.17.1"},
                    {"type": "library", "name": "blank-purl", "version": "1.0.0", "purl": ""}
                  ]
                }
                """;
        InputStream input = toStream(json);

        List<PackageId> result = service.parse(input, "skip-purl.json");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).canonical()).isEqualTo("pkg:maven/org.apache.logging.log4j/log4j-core");
    }

    @Test
    void parse_invalidPurl_skippedButValidPurlsKept() {
        String json = """
                {
                  "bomFormat": "CycloneDX",
                  "specVersion": "1.5",
                  "version": 1,
                  "components": [
                    {"type": "library", "name": "good", "version": "1.0", "purl": "pkg:npm/express@4.18.0"},
                    {"type": "library", "name": "bad", "version": "1.0", "purl": "this-is-not-a-purl"}
                  ]
                }
                """;
        InputStream input = toStream(json);

        List<PackageId> result = service.parse(input, "mixed.json");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).canonical()).isEqualTo("pkg:npm/express");
    }

    @Test
    void parse_dedupes() {
        String json = """
                {
                  "bomFormat": "CycloneDX",
                  "specVersion": "1.5",
                  "version": 1,
                  "components": [
                    {"type": "library", "name": "express", "version": "4.18.0", "purl": "pkg:npm/express@4.18.0"},
                    {"type": "library", "name": "express", "version": "4.18.0", "purl": "pkg:npm/express@4.18.0"},
                    {"type": "library", "name": "express", "version": "5.0.0", "purl": "pkg:npm/express@5.0.0"}
                  ]
                }
                """;
        InputStream input = toStream(json);

        List<PackageId> result = service.parse(input, "dupes.json");

        // (type, namespace, name) — version is irrelevant — only 1 unique entry
        assertThat(result).hasSize(1);
        assertThat(result.get(0).canonical()).isEqualTo("pkg:npm/express");
    }

    @Test
    void parse_inputStreamReadFailure_throwsIllegalArgumentException() {
        InputStream throwing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Boom");
            }
        };

        assertThatThrownBy(() -> service.parse(throwing, "bad.json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to read SBOM file");
    }

    private static InputStream toStream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }
}
