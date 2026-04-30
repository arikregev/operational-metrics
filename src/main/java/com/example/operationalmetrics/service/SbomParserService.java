package com.example.operationalmetrics.service;

import com.example.operationalmetrics.model.PackageId;
import jakarta.enterprise.context.ApplicationScoped;
import org.cyclonedx.exception.ParseException;
import org.cyclonedx.parsers.BomParserFactory;
import org.cyclonedx.parsers.Parser;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class SbomParserService {

    private static final Logger LOG = Logger.getLogger(SbomParserService.class);

    public List<PackageId> parse(InputStream inputStream, String filename) {
        byte[] data;
        try {
            data = inputStream.readAllBytes();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read SBOM file", e);
        }

        return parseCycloneDx(data);
    }

    private List<PackageId> parseCycloneDx(byte[] data) {
        try {
            Parser parser = BomParserFactory.createParser(data);
            var bom = parser.parse(data);

            Set<PackageId> packages = new LinkedHashSet<>();
            if (bom.getComponents() != null) {
                for (var component : bom.getComponents()) {
                    String purl = component.getPurl();
                    if (purl != null && !purl.isBlank()) {
                        try {
                            packages.add(PackageId.fromPurl(purl));
                        } catch (Exception e) {
                            LOG.debugv("Skipping invalid PURL in SBOM: {0}", purl);
                        }
                    }
                }
            }

            LOG.infov("Parsed CycloneDX SBOM: {0} unique packages extracted", packages.size());
            return new ArrayList<>(packages);

        } catch (ParseException e) {
            throw new IllegalArgumentException("Failed to parse SBOM as CycloneDX: " + e.getMessage(), e);
        }
    }
}
