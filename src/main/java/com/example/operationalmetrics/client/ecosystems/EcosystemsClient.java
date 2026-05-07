package com.example.operationalmetrics.client.ecosystems;

import com.example.operationalmetrics.client.ecosystems.dto.EcosystemsBulkRequest;
import com.example.operationalmetrics.client.ecosystems.dto.EcosystemsPackage;
import com.example.operationalmetrics.client.ecosystems.dto.EcosystemsPackageRef;
import com.example.operationalmetrics.client.ecosystems.dto.EcosystemsVersion;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import java.util.List;

@RegisterRestClient(configKey = "ecosystems-api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ClientHeaderParam(name = "User-Agent", value = "{userAgent}")
public interface EcosystemsClient {

    /**
     * ecosyste.ms qualifies callers for the higher rate-limit "polite pool"
     * (15K/hr vs. 5K/hr) when the User-Agent header includes a contact email.
     *
     * <p>Resolved per-call so the configured email is honoured. The
     * {@code @ClientHeaderParam} value uses the {@code {methodName}} form
     * because {@code ${...}} property substitution does not work inside a
     * mixed-literal annotation value in Quarkus REST Client — the entire
     * expression is treated as the property key and the lookup fails.
     */
    default String userAgent() {
        String email = ConfigProvider.getConfig()
                .getOptionalValue("ecosystems.contact-email", String.class)
                .orElse("ops@example.com");
        return "operational-metrics/1.0 (mailto:" + email + ")";
    }

    @GET @Path("/api/v1/packages/lookup")
    List<EcosystemsPackage> lookupByPurl(@QueryParam("purl") String purl);

    @POST @Path("/api/v1/packages/bulk_lookup")
    List<EcosystemsPackage> bulkLookup(EcosystemsBulkRequest request);

    @GET @Path("/api/v1/registries/{registry}/packages/{packageName}/versions")
    List<EcosystemsVersion> versionsList(@PathParam("registry") String registry,
                                         @PathParam("packageName") String packageName);

    @GET @Path("/api/v1/registries/{registry}/packages/{packageName}/versions/{versionNumber}")
    EcosystemsVersion versionInfo(@PathParam("registry") String registry,
                                  @PathParam("packageName") String packageName,
                                  @PathParam("versionNumber") String versionNumber);

    /**
     * Recently-released packages on a given registry, sorted descending by
     * publish date. Used by VersionsChangesFeedService to discover new
     * versions of packages we already track within ~15 min of release.
     *
     * <p>The endpoint has no native {@code since=} filter; the caller is
     * expected to paginate descending and stop when results dip below a
     * watermark.
     *
     * @param sort  use {@code "latest_release_published_at"} for the
     *              changes-feed semantic. Do NOT use {@code "updated_at"} —
     *              that bumps on metadata re-scrape and produces false
     *              positives.
     * @param order use {@code "desc"} for newest-first.
     */
    @GET @Path("/api/v1/registries/{registry}/packages")
    List<EcosystemsPackageRef> recentPackages(@PathParam("registry") String registry,
                                               @QueryParam("sort") String sort,
                                               @QueryParam("order") String order,
                                               @QueryParam("page") int page,
                                               @QueryParam("per_page") int perPage);
}
