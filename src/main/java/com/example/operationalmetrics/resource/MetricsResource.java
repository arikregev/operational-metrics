package com.example.operationalmetrics.resource;

import com.example.operationalmetrics.dto.MetricsBulkRequest;
import com.example.operationalmetrics.dto.MetricsBulkResponse;
import com.example.operationalmetrics.dto.MetricsResponse;
import com.example.operationalmetrics.service.MetricsQueryService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/metrics")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MetricsResource {

    private final MetricsQueryService queryService;

    @Inject
    public MetricsResource(MetricsQueryService queryService) {
        this.queryService = queryService;
    }

    @GET
    public Response getByPurl(@QueryParam("purl") String purl) {
        if (purl == null || purl.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("purl query parameter is required"))
                    .build();
        }

        try {
            MetricsResponse response = queryService.findByPurl(purl);
            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/coordinates")
    public Response getByCoordinates(@QueryParam("type") String type,
                                     @QueryParam("namespace") String namespace,
                                     @QueryParam("name") String name) {
        if (type == null || type.isBlank() || name == null || name.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("type and name query parameters are required"))
                    .build();
        }

        try {
            MetricsResponse response = queryService.findByCoordinates(type, namespace, name);
            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/bulk")
    public Response bulkLookup(MetricsBulkRequest request) {
        if (request == null || request.purls() == null || request.purls().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("purls list is required and must not be empty"))
                    .build();
        }

        MetricsBulkResponse response = queryService.findBulk(request.purls());
        return Response.ok(response).build();
    }

    record ErrorResponse(String error) {}
}
