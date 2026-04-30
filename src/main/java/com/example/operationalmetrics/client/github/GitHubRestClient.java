package com.example.operationalmetrics.client.github;

import com.example.operationalmetrics.client.github.dto.GitHubCommitActivity;
import com.example.operationalmetrics.client.github.dto.GitHubCommunityProfile;
import com.example.operationalmetrics.client.github.dto.GitHubRepo;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import java.util.List;

@RegisterRestClient(configKey = "github-api")
@Produces(MediaType.APPLICATION_JSON)
@ClientHeaderParam(name = "Authorization", value = "Bearer ${github.token}")
@ClientHeaderParam(name = "Accept", value = "application/vnd.github+json")
public interface GitHubRestClient {
    @GET @Path("/repos/{owner}/{repo}")
    GitHubRepo getRepo(@PathParam("owner") String owner, @PathParam("repo") String repo);

    @GET @Path("/repos/{owner}/{repo}/community/profile")
    GitHubCommunityProfile getCommunityProfile(@PathParam("owner") String owner, @PathParam("repo") String repo);

    @GET @Path("/repos/{owner}/{repo}/stats/commit_activity")
    List<GitHubCommitActivity> getCommitActivity(@PathParam("owner") String owner, @PathParam("repo") String repo);
}
