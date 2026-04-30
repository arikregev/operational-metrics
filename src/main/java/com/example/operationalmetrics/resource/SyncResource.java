package com.example.operationalmetrics.resource;

import com.example.operationalmetrics.service.PurlSyncService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/sync")
@Produces(MediaType.APPLICATION_JSON)
public class SyncResource {

    private final PurlSyncService syncService;

    @Inject
    public SyncResource(PurlSyncService syncService) {
        this.syncService = syncService;
    }

    @POST
    @Path("/trigger")
    public Response triggerSync() {
        syncService.triggerAsync();
        return Response.accepted(new StatusResponse("SYNC_TRIGGERED")).build();
    }

    @GET
    @Path("/status")
    public PurlSyncService.SyncStatus getStatus() {
        return syncService.getStatus();
    }

    record StatusResponse(String status) {}
}
