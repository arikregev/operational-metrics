package com.example.operationalmetrics.resource;

import com.example.operationalmetrics.service.PurlSyncService;
import com.example.operationalmetrics.service.VersionsChangesFeedService;
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
    private final VersionsChangesFeedService versionsFeedService;

    @Inject
    public SyncResource(PurlSyncService syncService,
                        VersionsSyncService versionsService,
                        VersionsChangesFeedService versionsFeedService) {
        this.syncService = syncService;
        this.versionsService = versionsService;
        this.versionsFeedService = versionsFeedService;
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

    /**
     * Manually fire the changes-feed poller. Use sparingly — it's normally
     * driven by the every-15-min cron and runs on a separate rate budget
     * from the safety-net sweep. A manual trigger is mainly useful for
     * smoke-testing a newly added registry, or after operator config edits.
     */
    @POST
    @Path("/versions-feed")
    public Response triggerVersionsFeed() {
        versionsFeedService.triggerAsync();
        return Response.accepted(new StatusResponse("VERSIONS_FEED_TRIGGERED")).build();
    }

    @GET
    @Path("/status")
    public Map<String, Object> getStatus() {
        // Combined view across the three independently-scheduled subsystems:
        // metrics sync (full / refresh / discovery), versions sweep, and the
        // changes-feed poller. All can be running simultaneously.
        return Map.of(
                "metrics", syncService.getStatus(),
                "versions", versionsService.getStatus(),
                "versionsFeed", versionsFeedService.getStatus()
        );
    }

    record StatusResponse(String status) {}
}
