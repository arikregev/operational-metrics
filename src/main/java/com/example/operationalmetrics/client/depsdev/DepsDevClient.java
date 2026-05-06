package com.example.operationalmetrics.client.depsdev;

import com.example.operationalmetrics.client.depsdev.dto.DepsDevDependents;
import com.example.operationalmetrics.client.depsdev.dto.DepsDevPackage;
import com.example.operationalmetrics.client.depsdev.dto.DepsDevProject;
import com.example.operationalmetrics.client.depsdev.dto.DepsDevPurlResponse;
import com.example.operationalmetrics.client.depsdev.dto.DepsDevVersionInfo;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "depsdev-api")
@Produces(MediaType.APPLICATION_JSON)
public interface DepsDevClient {
    @GET @Path("/v3alpha/purl/{purl}")
    DepsDevPurlResponse lookupPurl(@PathParam("purl") String purl);

    @GET @Path("/v3/projects/{projectId}")
    DepsDevProject getProject(@PathParam("projectId") String projectId);

    @GET @Path("/v3alpha/systems/{system}/packages/{name}/versions/{version}:dependents")
    DepsDevDependents getDependents(@PathParam("system") String system,
                                    @PathParam("name") String name,
                                    @PathParam("version") String version);

    @GET @Path("/v3/systems/{system}/packages/{name}")
    DepsDevPackage getPackage(@PathParam("system") String system,
                              @PathParam("name") String name);

    @GET @Path("/v3/systems/{system}/packages/{name}/versions/{version}")
    DepsDevVersionInfo getVersionInfo(@PathParam("system") String system,
                                      @PathParam("name") String name,
                                      @PathParam("version") String version);
}
