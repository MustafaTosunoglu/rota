package com.rota.endpoints.api;

import java.util.List;
import java.util.Map;

/**
 * Neutral content model shared between the importer (which parses OpenAPI / Postman / cURL
 * into it) and the endpoints module (which materialises it). Defined here so the dependency
 * runs importer → endpoints.api (never the reverse). Categories are linked by name.
 */
public final class ImportModel {

    private ImportModel() {
    }

    public enum DedupMode {
        /** Keep the existing endpoint; do not import the incoming one. */
        SKIP,
        /** Replace the existing endpoint (and its sub-resources) with the incoming one. */
        OVERWRITE,
    }

    public record ImportCategory(String name, String description) {
    }

    public record ImportParameter(String name, String location, String dataType, boolean required,
                                  String description, String example) {
    }

    public record ImportRequestBody(String contentType, Map<String, Object> schemaJson,
                                    Map<String, Object> exampleJson) {
    }

    public record ImportResponse(int statusCode, String description, String contentType,
                                 Map<String, Object> schemaJson, Map<String, Object> exampleJson) {
    }

    public record ImportEndpoint(String method, String path, String summary, String descriptionMd,
                                 String authType, Map<String, Object> authConfig, String categoryName,
                                 List<ImportParameter> parameters, List<ImportRequestBody> requestBodies,
                                 List<ImportResponse> responses) {
    }

    public record ImportResult(int created, int overwritten, int skipped) {
    }
}
