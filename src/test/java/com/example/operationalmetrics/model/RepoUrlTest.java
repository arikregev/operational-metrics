package com.example.operationalmetrics.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepoUrlTest {

    @Test
    void parsesGitHubHttps() {
        var r = RepoUrl.parse("https://github.com/apache/logging-log4j2");
        assertThat(r.url()).isEqualTo("https://github.com/apache/logging-log4j2");
        assertThat(r.platform()).isEqualTo("github.com");
        assertThat(r.owner()).isEqualTo("apache");
        assertThat(r.name()).isEqualTo("logging-log4j2");
    }

    @Test
    void parsesUrlWithGitSuffix() {
        var r = RepoUrl.parse("https://github.com/apache/logging-log4j2.git");
        assertThat(r.name()).isEqualTo("logging-log4j2");
    }

    @Test
    void parsesUrlWithTrailingSlash() {
        var r = RepoUrl.parse("https://github.com/apache/logging-log4j2/");
        assertThat(r.name()).isEqualTo("logging-log4j2");
    }

    @Test
    void parsesGitLabUrl() {
        var r = RepoUrl.parse("https://gitlab.com/fdroid/fdroidclient");
        assertThat(r.platform()).isEqualTo("gitlab.com");
        assertThat(r.owner()).isEqualTo("fdroid");
        assertThat(r.name()).isEqualTo("fdroidclient");
    }

    @Test
    void parsesHttpUrl() {
        var r = RepoUrl.parse("http://example.com/a/b");
        assertThat(r.platform()).isEqualTo("example.com");
    }

    @Test
    void trimsWhitespace() {
        var r = RepoUrl.parse("  https://github.com/a/b  ");
        assertThat(r.owner()).isEqualTo("a");
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> RepoUrl.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RepoUrl.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMalformed() {
        assertThatThrownBy(() -> RepoUrl.parse("not a url"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromComponentsBuildsUrl() {
        var r = RepoUrl.fromComponents("github.com", "owner", "repo");
        assertThat(r.url()).isEqualTo("https://github.com/owner/repo");
        assertThat(r.platform()).isEqualTo("github.com");
        assertThat(r.owner()).isEqualTo("owner");
        assertThat(r.name()).isEqualTo("repo");
    }

    @Test
    void recordEqualityWorks() {
        var a = RepoUrl.fromComponents("github.com", "x", "y");
        var b = RepoUrl.fromComponents("github.com", "x", "y");
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
}
