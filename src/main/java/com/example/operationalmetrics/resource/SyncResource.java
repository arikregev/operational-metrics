package com.example.operationalmetrics.resource;

import com.example.operationalmetrics.service.PurlSyncService;
import com.example.operationalmetrics.service.VersionsSyncService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/api/v1/sync")
@Produces(MediaType.APPLICATION_JSON)
public class SyncResource {

    private final PurlSyncService syncService;
    private final VersionsSyncService versionsService;

    @Inject
    public SyncResource(PurlSyncService syncService, VersionsSyncService versionsService) {
        this.syncService = syncService;
        this.versionsService = versionsService;
    }

    @POST
    @Path("/trigger")
    public Response triggerSync() {
        syncService.triggerAsync();
        return Response.accepted(new StatusResponse("FULL_SYNC_TRIGGERED")).build();
    }

    @POST
    @Path("/refresh")
    public Response triggerRefresh() {
        syncService.triggerRefreshAsync();
        return Response.accepted(new StatusResponse("REFRESH_TRIGGERED")).build();
    }

    @POST
    @Path("/discovery")
    public Response triggerDiscovery() {
        syncService.triggerDiscoveryAsync();
        return Response.accepted(new StatusResponse("DISCOVERY_TRIGGERED")).build();
    }

    @POST
    @Path("/versions")
    public Response triggerVersionsSweep() {
        versionsService.triggerAsync();
        return Response.accepted(new StatusResponse("VERSIONS_SWEEP_TRIGGERED")).build();
    }

    @GET
    @Path("/status")
    public Map<String, Object> getStatus() {
        // Combined view across the metrics-sync state and the versions-sweep state.
        // Both can run independently, so the response surfaces both.
        return Map.of(
                "metrics", syncService.getStatus(),
                "versions", versionsService.getStatus()
        );
    }

    record StatusResponse(String status) {}
}
