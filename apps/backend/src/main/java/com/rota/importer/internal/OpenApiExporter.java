package com.rota.importer.internal;

import com.rota.documents.api.DocumentExport;
import com.rota.documents.api.DocumentExportReader;
import com.rota.documents.api.EnvironmentSpec;
import com.rota.endpoints.api.ContentExportReader;
import com.rota.endpoints.api.ExportModel.ContentExport;
import com.rota.endpoints.api.ExportModel.ExportEndpoint;
import com.rota.endpoints.api.ExportModel.ExportParameter;
import com.rota.endpoints.api.ExportModel.ExportRequestBody;
import com.rota.endpoints.api.ExportModel.ExportResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Builds an OpenAPI 3.0.3 document (as an ordered map, serialised by the controller) from a
 * version's content (plan task 4.7). Reads through the cross-module read ports — RLS-scoped.
 */
@Service
public class OpenApiExporter {

    private final DocumentExportReader documentReader;
    private final ContentExportReader contentReader;

    public OpenApiExporter(DocumentExportReader documentReader, ContentExportReader contentReader) {
        this.documentReader = documentReader;
        this.contentReader = contentReader;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> export(UUID versionId) {
        DocumentExport document = documentReader.read(versionId);
        ContentExport content = contentReader.read(versionId);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("openapi", "3.0.3");

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", document.name() != null ? document.name() : "API");
        info.put("version", document.versionLabel() != null ? document.versionLabel() : "1.0.0");
        if (document.description() != null) {
            info.put("description", document.description());
        }
        root.put("info", info);

        if (document.environments() != null && !document.environments().isEmpty()) {
            List<Map<String, Object>> servers = new ArrayList<>();
            for (EnvironmentSpec env : document.environments()) {
                Map<String, Object> server = new LinkedHashMap<>();
                server.put("url", env.baseUrl());
                if (env.name() != null) {
                    server.put("description", env.name());
                }
                servers.add(server);
            }
            root.put("servers", servers);
        }

        addTags(root, content);
        root.put("paths", buildPaths(content));
        return root;
    }

    private void addTags(Map<String, Object> root, ContentExport content) {
        var names = new TreeSet<String>();
        for (ExportEndpoint endpoint : content.endpoints()) {
            if (endpoint.categoryName() != null && !endpoint.categoryName().isBlank()) {
                names.add(endpoint.categoryName());
            }
        }
        if (!names.isEmpty()) {
            List<Map<String, Object>> tags = new ArrayList<>();
            for (String name : names) {
                tags.add(new LinkedHashMap<>(Map.of("name", name)));
            }
            root.put("tags", tags);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildPaths(ContentExport content) {
        Map<String, Object> paths = new LinkedHashMap<>();
        for (ExportEndpoint endpoint : content.endpoints()) {
            Map<String, Object> pathItem =
                    (Map<String, Object>) paths.computeIfAbsent(endpoint.path(), k -> new LinkedHashMap<>());
            pathItem.put(endpoint.method().toLowerCase(Locale.ROOT), buildOperation(endpoint));
        }
        return paths;
    }

    private Map<String, Object> buildOperation(ExportEndpoint endpoint) {
        Map<String, Object> operation = new LinkedHashMap<>();
        if (endpoint.summary() != null) {
            operation.put("summary", endpoint.summary());
        }
        if (endpoint.descriptionMd() != null) {
            operation.put("description", endpoint.descriptionMd());
        }
        if (endpoint.categoryName() != null && !endpoint.categoryName().isBlank()) {
            operation.put("tags", List.of(endpoint.categoryName()));
        }
        if (endpoint.deprecated()) {
            operation.put("deprecated", true);
        }

        List<Map<String, Object>> parameters = new ArrayList<>();
        for (ExportParameter parameter : endpoint.parameters()) {
            if ("path".equals(parameter.location()) || "query".equals(parameter.location())
                    || "header".equals(parameter.location()) || "cookie".equals(parameter.location())) {
                parameters.add(buildParameter(parameter));
            }
        }
        if (!parameters.isEmpty()) {
            operation.put("parameters", parameters);
        }

        if (endpoint.requestBodies() != null && !endpoint.requestBodies().isEmpty()) {
            operation.put("requestBody", buildRequestBody(endpoint.requestBodies()));
        }
        operation.put("responses", buildResponses(endpoint.responses()));
        return operation;
    }

    private Map<String, Object> buildParameter(ExportParameter parameter) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", parameter.name());
        result.put("in", parameter.location());
        result.put("required", "path".equals(parameter.location()) || parameter.required());
        if (parameter.description() != null) {
            result.put("description", parameter.description());
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", parameter.dataType() != null ? parameter.dataType() : "string");
        result.put("schema", schema);
        if (parameter.example() != null) {
            result.put("example", parameter.example());
        }
        return result;
    }

    private Map<String, Object> buildRequestBody(List<ExportRequestBody> bodies) {
        Map<String, Object> content = new LinkedHashMap<>();
        for (ExportRequestBody body : bodies) {
            content.put(body.contentType() != null ? body.contentType() : "application/json",
                    mediaType(body.schemaJson(), body.exampleJson()));
        }
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("content", content);
        return requestBody;
    }

    private Map<String, Object> buildResponses(List<ExportResponse> responses) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (responses == null || responses.isEmpty()) {
            // OpenAPI requires at least one response.
            result.put("default", new LinkedHashMap<>(Map.of("description", "")));
            return result;
        }
        for (ExportResponse response : responses) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("description", response.description() != null ? response.description() : "");
            if (response.schemaJson() != null || response.exampleJson() != null) {
                entry.put("content", new LinkedHashMap<>(Map.of(
                        response.contentType() != null ? response.contentType() : "application/json",
                        mediaType(response.schemaJson(), response.exampleJson()))));
            }
            result.put(String.valueOf(response.statusCode()), entry);
        }
        return result;
    }

    private Map<String, Object> mediaType(Map<String, Object> schema, Map<String, Object> example) {
        Map<String, Object> media = new LinkedHashMap<>();
        if (schema != null) {
            media.put("schema", schema);
        }
        if (example != null) {
            media.put("example", example);
        }
        return media;
    }
}
