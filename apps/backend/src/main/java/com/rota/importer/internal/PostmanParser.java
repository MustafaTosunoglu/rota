package com.rota.importer.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rota.endpoints.api.ImportModel.ImportCategory;
import com.rota.endpoints.api.ImportModel.ImportEndpoint;
import com.rota.endpoints.api.ImportModel.ImportParameter;
import com.rota.endpoints.api.ImportModel.ImportRequestBody;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a Postman Collection v2.1 by hand (no Java SDK, plan §13.6). Folders become
 * categories (immediate parent folder), requests become endpoints.
 */
@Component
class PostmanParser {

    private final ObjectMapper objectMapper;

    PostmanParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ParsedImport parse(String content) {
        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (Exception e) {
            throw new ImportParseException("Not valid JSON for a Postman collection.", e);
        }
        if (root == null || !root.has("item")) {
            throw new ImportParseException("Not a Postman v2.1 collection (missing 'item').");
        }

        String title = root.path("info").path("name").asText(null);
        List<ImportCategory> categories = new ArrayList<>();
        List<ImportEndpoint> endpoints = new ArrayList<>();
        walk(root.get("item"), null, categories, endpoints);

        return new ParsedImport(title, List.of(), categories, endpoints);
    }

    private void walk(JsonNode items, String folderName, List<ImportCategory> categories,
                      List<ImportEndpoint> endpoints) {
        if (items == null || !items.isArray()) {
            return;
        }
        for (JsonNode item : items) {
            if (item.has("item")) {
                String name = item.path("name").asText(folderName);
                categories.add(new ImportCategory(name, item.path("description").asText(null)));
                walk(item.get("item"), name, categories, endpoints); // children carry this folder
            } else if (item.has("request")) {
                endpoints.add(parseRequest(item, folderName));
            }
        }
    }

    private ImportEndpoint parseRequest(JsonNode item, String categoryName) {
        JsonNode request = item.get("request");
        String method = request.path("method").asText("GET").toUpperCase();
        JsonNode url = request.get("url");

        List<ImportParameter> parameters = new ArrayList<>();
        String path = extractPath(url, parameters);
        extractHeaders(request.get("header"), parameters);

        List<ImportRequestBody> bodies = extractBody(request.get("body"));

        return new ImportEndpoint(method, path, item.path("name").asText(null),
                request.path("description").asText(null), "none", null, categoryName,
                parameters, bodies, List.of());
    }

    /** Path from a url object ({@code path[]}, {@code query[]}) or a raw URL string. */
    private String extractPath(JsonNode url, List<ImportParameter> parameters) {
        if (url == null) {
            return "/";
        }
        if (url.isTextual()) {
            return pathFromRaw(url.asText());
        }
        StringBuilder path = new StringBuilder();
        JsonNode segments = url.get("path");
        if (segments != null && segments.isArray()) {
            for (JsonNode segment : segments) {
                path.append('/').append(segment.asText());
            }
        }
        JsonNode query = url.get("query");
        if (query != null && query.isArray()) {
            for (JsonNode q : query) {
                String key = q.path("key").asText(null);
                if (key != null && !key.isBlank()) {
                    parameters.add(new ImportParameter(key, "query", "string", false,
                            q.path("description").asText(null), q.path("value").asText(null)));
                }
            }
        }
        String result = path.toString();
        if (result.isBlank()) {
            return pathFromRaw(url.path("raw").asText("/"));
        }
        return result;
    }

    private String pathFromRaw(String raw) {
        try {
            // raw may contain Postman {{vars}} which break URI parsing — strip the scheme/host crudely.
            String withoutQuery = raw.split("\\?", 2)[0];
            int schemeIdx = withoutQuery.indexOf("://");
            if (schemeIdx >= 0) {
                int slash = withoutQuery.indexOf('/', schemeIdx + 3);
                return slash >= 0 ? withoutQuery.substring(slash) : "/";
            }
            return withoutQuery.startsWith("/") ? withoutQuery : "/" + withoutQuery;
        } catch (RuntimeException e) {
            return "/";
        }
    }

    private void extractHeaders(JsonNode headers, List<ImportParameter> parameters) {
        if (headers == null || !headers.isArray()) {
            return;
        }
        for (JsonNode header : headers) {
            String key = header.path("key").asText(null);
            if (key != null && !key.isBlank()) {
                parameters.add(new ImportParameter(key, "header", "string", false,
                        header.path("description").asText(null), header.path("value").asText(null)));
            }
        }
    }

    private List<ImportRequestBody> extractBody(JsonNode body) {
        if (body == null || !"raw".equals(body.path("mode").asText(null))) {
            return List.of();
        }
        String raw = body.path("raw").asText(null);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        Map<String, Object> example = tryParseJsonObject(raw);
        String contentType = "application/json";
        String language = body.path("options").path("raw").path("language").asText(null);
        if (language != null && !language.equalsIgnoreCase("json")) {
            contentType = "text/plain";
        }
        List<ImportRequestBody> result = new ArrayList<>();
        result.add(new ImportRequestBody(contentType, null, example));
        return result;
    }

    private Map<String, Object> tryParseJsonObject(String raw) {
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node != null && node.isObject()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = objectMapper.convertValue(node, LinkedHashMap.class);
                return map;
            }
        } catch (Exception ignored) {
            // Non-JSON body — keep the endpoint, just without a structured example.
        }
        return null;
    }
}
