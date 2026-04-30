package com.example.operationalmetrics.resource;

import com.example.operationalmetrics.dto.MetricsResponse;
import com.example.operationalmetrics.dto.SbomMetricsResponse;
import com.example.operationalmetrics.dto.SbomUploadForm;
import com.example.operationalmetrics.model.OperationalMetricsEntity;
import com.example.operationalmetrics.model.PackageId;
import com.example.operationalmetrics.repository.OperationalMetricsDao;
import com.example.operationalmetrics.service.MetricsOrchestrator;
import com.example.operationalmetrics.service.MetricsQueryService;
import com.example.operationalmetrics.service.SbomParserService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jdbi.v3.core.Jdbi;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Path("/api/v1/sbom")
@Produces(MediaType.APPLICATION_JSON)
public class SbomMetricsResource {

    private static final Logger LOG = Logger.getLogger(SbomMetricsResource.class);

    private final SbomParserService sbomParser;
    private final MetricsQueryService queryService;
    private final MetricsOrchestrator orchestrator;
    private final Jdbi jdbi;

    @Inject
    public SbomMetricsResource(SbomParserService sbomParser,
                               MetricsQueryService queryService,
                               MetricsOrchestrator orchestrator,
                               Jdbi jdbi) {
        this.sbomParser = sbomParser;
        this.queryService = queryService;
        this.orchestrator = orchestrator;
        this.jdbi = jdbi;
    }

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @SuppressWarnings("deprecation")
    public Response uploadSbom(@org.jboss.resteasy.reactive.MultipartForm SbomUploadForm form) {
        if (form.file == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("SBOM file is required"))
                    .build();
        }

        List<PackageId> packages;
        try {
            packages = sbomParser.parse(form.file, form.filename);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }

        List<MetricsResponse> results = new ArrayList<>();
        List<SbomMetricsResponse.ErrorEntry> errors = new ArrayList<>();
        int fetchedOnDemand = 0;

        for (PackageId pkg : packages) {
            try {
                Optional<OperationalMetricsEntity> cached = jdbi.withExtension(
                        OperationalMetricsDao.class,
                        dao -> dao.findByCanonical(pkg.canonical()));

                if (cached.isPresent()) {
                    results.add(MetricsQueryService.toResponse(cached.get()));
                } else {
                    OperationalMetricsEntity entity = orchestrator.collectAndStore(pkg, null);
                    results.add(MetricsQueryService.toResponse(entity));
                    fetchedOnDemand++;
                }
            } catch (Exception e) {
                LOG.warnv("SBOM metrics fetch failed for {0}: {1}", pkg.canonical(), e.getMessage());
                errors.add(new SbomMetricsResponse.ErrorEntry(pkg.canonical(), e.getMessage()));
            }
        }

        var response = new SbomMetricsResponse(packages.size(), fetchedOnDemand, results, errors);
        return Response.ok(response).build();
    }

    record ErrorResponse(String error) {}
}
