package com.rota.endpoints.api;

import java.util.List;
import java.util.Map;

/** Read-side content model for exporters (e.g. OpenAPI). */
public final class ExportModel {

    private ExportModel() {
    }

    public record ExportParameter(String name, String location, String dataType, boolean required,
                                  String description, String example) {
    }

    public record ExportRequestBody(String contentType, Map<String, Object> schemaJson,
                                    Map<String, Object> exampleJson) {
    }

    public record ExportResponse(int statusCode, String description, String contentType,
                                 Map<String, Object> schemaJson, Map<String, Object> exampleJson) {
    }

    public record ExportEndpoint(String method, String path, String summary, String descriptionMd,
                                 String authType, String categoryName, boolean deprecated,
                                 List<ExportParameter> parameters, List<ExportRequestBody> requestBodies,
                                 List<ExportResponse> responses) {
    }

    public record ContentExport(List<ExportEndpoint> endpoints) {
    }
}
