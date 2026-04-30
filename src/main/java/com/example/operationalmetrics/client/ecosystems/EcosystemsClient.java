package com.example.operationalmetrics.client.ecosystems;

import com.example.operationalmetrics.client.ecosystems.dto.EcosystemsBulkRequest;
import com.example.operationalmetrics.client.ecosystems.dto.EcosystemsPackage;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import java.util.List;

@RegisterRestClient(configKey = "ecosystems-api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ClientHeaderParam(name = "User-Agent", value = "operational-metrics/1.0 (mailto:${ecosystems.contact-email:ops@example.com})")
public interface EcosystemsClient {
    @GET @Path("/api/v1/packages/lookup")
    List<EcosystemsPackage> lookupByPurl(@QueryParam("purl") String purl);

    @POST @Path("/api/v1/packages/bulk_lookup")
    List<EcosystemsPackage> bulkLookup(EcosystemsBulkRequest request);
}
