package com.example.operationalmetrics.dto;

import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;
import java.io.InputStream;

public class SbomUploadForm {

    @RestForm("file")
    @PartType(MediaType.APPLICATION_OCTET_STREAM)
    public InputStream file;

    @RestForm("filename")
    @PartType(MediaType.TEXT_PLAIN)
    public String filename;
}
