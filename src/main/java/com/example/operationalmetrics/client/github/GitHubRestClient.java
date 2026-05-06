package com.example.operationalmetrics.client.github;

import com.example.operationalmetrics.client.github.dto.GitHubCommitActivity;
import com.example.operationalmetrics.client.github.dto.GitHubCommunityProfile;
import com.example.operationalmetrics.client.github.dto.GitHubRepo;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import java.util.List;

@RegisterRestClient(configKey = "github-api")
@Produces(MediaType.APPLICATION_JSON)
@ClientHeaderParam(name = "Authorization", value = "{authorization}")
@ClientHeaderParam(name = "Accept", value = "application/vnd.github+json")
public interface GitHubRestClient {

    /**
     * Resolved per-call so the configured token is honoured. The
     * {@code @ClientHeaderParam} value uses the {@code {methodName}}
     * form because {@code ${...}} property substitution does not work
     * inside a mixed-literal annotation value in Quarkus REST Client —
     * the entire expression is treated as the property key and the
     * lookup fails.
     *
     * <p>If no token is configured (the project supports running against
     * unauthenticated GitHub at 60 req/hr), an empty Bearer is sent and
     * GitHub responds with a 401 that the collector logs and degrades
     * gracefully on. Returning {@code null} would suppress the header
     * but Quarkus treats null as a configuration error, so we always
     * emit one.
     */
    default String authorization() {
        return ConfigProvider.getConfig()
                .getOptionalValue("github.token", String.class)
                .map(t -> "Bearer " + t)
                .orElse("Bearer ");
    }

    @GET @Path("/repos/{owner}/{repo}")
    GitHubRepo getRepo(@PathParam("owner") String owner, @PathParam("repo") String repo);

    @GET @Path("/repos/{owner}/{repo}/community/profile")
    GitHubCommunityProfile getCommunityProfile(@PathParam("owner") String owner, @PathParam("repo") String repo);

    @GET @Path("/repos/{owner}/{repo}/stats/commit_activity")
    List<GitHubCommitActivity> getCommitActivity(@PathParam("owner") String owner, @PathParam("repo") String repo);
}
