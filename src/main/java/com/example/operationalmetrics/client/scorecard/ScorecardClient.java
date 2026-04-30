package com.example.operationalmetrics.client.scorecard;

import com.example.operationalmetrics.client.scorecard.dto.ScorecardResult;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "scorecard-api")
@Produces(MediaType.APPLICATION_JSON)
public interface ScorecardClient {
    @GET @Path("/projects/{platform}/{org}/{repo}")
    ScorecardResult getScorecard(@PathParam("platform") String platform,
                                  @PathParam("org") String org,
                                  @PathParam("repo") String repo);
}
