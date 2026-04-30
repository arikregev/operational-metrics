package com.example.operationalmetrics.client.dependencytrack;

import com.example.operationalmetrics.client.dependencytrack.dto.DtComponent;
import com.example.operationalmetrics.client.dependencytrack.dto.DtProject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import java.util.List;

@RegisterRestClient(configKey = "dependency-track-api")
@Produces(MediaType.APPLICATION_JSON)
@ClientHeaderParam(name = "X-Api-Key", value = "${dependency-track.api-key}")
public interface DependencyTrackClient {
    @GET @Path("/api/v1/project")
    List<DtProject> listProjects(@QueryParam("page") int page, @QueryParam("size") int size);

    @GET @Path("/api/v1/component/project/{projectUuid}")
    List<DtComponent> listComponents(@PathParam("projectUuid") String uuid,
                                     @QueryParam("page") int page, @QueryParam("size") int size);
}
