package com.example.operationalmetrics.client.snyk;

import com.example.operationalmetrics.client.snyk.dto.SnykPackageResponse;
import com.example.operationalmetrics.client.snyk.dto.SnykPackageVersionResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "snyk-api")
@Produces(MediaType.APPLICATION_JSON)
@ClientHeaderParam(name = "Authorization", value = "{authorization}")
public interface SnykClient {

    /**
     * Resolves the Snyk auth header per-call via {@link ConfigProvider}.
     *
     * <p>The {@code @ClientHeaderParam} value uses the {@code {methodName}}
     * form because {@code ${...}} property substitution does not work inside
     * a mixed-literal annotation value in Quarkus REST Client — the entire
     * expression is treated as the property key and the lookup fails. This
     * matches the pattern used by {@code EcosystemsClient.userAgent()} (PR #4).
     */
    default String authorization() {
        String token = ConfigProvider.getConfig()
                .getOptionalValue("snyk.token", String.class)
                .orElse("");
        return "token " + token;
    }

    /**
     * Get package-level data (latest version, health, popularity, security, maintenance).
     *
     * <p>Path parameters are passed RAW — JAX-RS percent-encodes them exactly
     * once. Pre-encoding (e.g., via {@code URLEncoder}) would produce
     * double-encoding, mirroring the bug fixed for {@code DepsDevClient}.
     */
    @GET
    @Path("/rest/orgs/{orgId}/ecosystems/{ecosystem}/packages/{packageName}")
    SnykPackageResponse getPackage(
            @PathParam("orgId") String orgId,
            @PathParam("ecosystem") String ecosystem,
            @PathParam("packageName") String packageName,
            @QueryParam("version") String apiVersion);

    /** Per-version endpoint — adds {@code published_at}, {@code package_version}, etc. */
    @GET
    @Path("/rest/orgs/{orgId}/ecosystems/{ecosystem}/packages/{packageName}/versions/{packageVersion}")
    SnykPackageVersionResponse getPackageVersion(
            @PathParam("orgId") String orgId,
            @PathParam("ecosystem") String ecosystem,
            @PathParam("packageName") String packageName,
            @PathParam("packageVersion") String packageVersion,
            @QueryParam("version") String apiVersion);
}
