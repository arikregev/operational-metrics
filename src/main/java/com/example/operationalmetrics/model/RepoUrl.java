package com.example.operationalmetrics.model;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record RepoUrl(String url, String platform, String owner, String name) {

    private static final Pattern REPO_PATTERN = Pattern.compile(
            "https?://([^/]+)/([^/]+)/([^/]+?)(?:\\.git)?/?$"
    );

    public RepoUrl {
        Objects.requireNonNull(url);
        Objects.requireNonNull(platform);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(name);
    }

    public static RepoUrl parse(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Repository URL must not be blank");
        }
        Matcher m = REPO_PATTERN.matcher(url.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Cannot parse repository URL: " + url);
        }
        return new RepoUrl(url.trim(), m.group(1), m.group(2), m.group(3));
    }

    public static RepoUrl fromComponents(String platform, String owner, String name) {
        String url = "https://" + platform + "/" + owner + "/" + name;
        return new RepoUrl(url, platform, owner, name);
    }
}
