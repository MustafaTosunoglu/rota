package com.rota.importer.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rota.documents.api.EnvironmentSpec;
import com.rota.endpoints.api.ImportModel.ImportCategory;
import com.rota.endpoints.api.ImportModel.ImportEndpoint;
import com.rota.endpoints.api.ImportModel.ImportParameter;
import com.rota.endpoints.api.ImportModel.ImportRequestBody;
import com.rota.endpoints.api.ImportModel.ImportResponse;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Parses an OpenAPI 3.x document (JSON or YAML) into the neutral {@link ParsedImport}. */
@Component
class OpenApiParser {

    private final ObjectMapper schemaMapper;

    OpenApiParser(ObjectMapper objectMapper) {
        // Schemas/examples are stored as JSON objects; drop nulls to keep them compact.
        this.schemaMapper = objectMapper.copy().setSerializationInclusion(
                com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    ParsedImport parse(String content) {
        SwaggerParseResult result = new OpenAPIV3Parser().readContents(content);
        OpenAPI openAPI = result.getOpenAPI();
        if (openAPI == null) {
            String messages = result.getMessages() == null ? "" : String.join("; ", result.getMessages());
            throw new ImportParseException("Not a valid OpenAPI 3 document. " + messages);
        }

        String title = openAPI.getInfo() != null ? openAPI.getInfo().getTitle() : null;
        List<EnvironmentSpec> environments = parseServers(openAPI.getServers());
        List<ImportCategory> categories = parseTags(openAPI.getTags());
        List<ImportEndpoint> endpoints = parsePaths(openAPI);

        return new ParsedImport(title, environments, categories, endpoints);
    }

    private List<EnvironmentSpec> parseServers(List<Server> servers) {
        List<EnvironmentSpec> result = new ArrayList<>();
        if (servers == null) {
            return result;
        }
        int index = 1;
        for (Server server : servers) {
            if (server.getUrl() == null || server.getUrl().isBlank()) {
                continue;
            }
            String name = server.getDescription() != null && !server.getDescription().isBlank()
                    ? server.getDescription()
                    : "server-" + index;
            boolean prod = server.getUrl().toLowerCase().contains("prod");
            result.add(new EnvironmentSpec(name, server.getUrl(), prod));
            index++;
        }
        return result;
    }

    private List<ImportCategory> parseTags(List<Tag> tags) {
        List<ImportCategory> result = new ArrayList<>();
        if (tags == null) {
            return result;
        }
        for (Tag tag : tags) {
            result.add(new ImportCategory(tag.getName(), tag.getDescription()));
        }
        return result;
    }

    private List<ImportEndpoint> parsePaths(OpenAPI openAPI) {
        List<ImportEndpoint> endpoints = new ArrayList<>();
        if (openAPI.getPaths() == null) {
            return endpoints;
        }
        Map<String, SecurityScheme> schemes = openAPI.getComponents() != null
                ? openAPI.getComponents().getSecuritySchemes() : null;

        openAPI.getPaths().forEach((path, pathItem) ->
                pathItem.readOperationsMap().forEach((httpMethod, operation) ->
                        endpoints.add(parseOperation(path, httpMethod, operation, schemes, openAPI.getSecurity()))));
        return endpoints;
    }

    private ImportEndpoint parseOperation(String path, PathItem.HttpMethod httpMethod, Operation operation,
                                          Map<String, SecurityScheme> schemes,
                                          List<SecurityRequirement> globalSecurity) {
        String category = operation.getTags() != null && !operation.getTags().isEmpty()
                ? operation.getTags().get(0) : null;
        String authType = resolveAuth(
                operation.getSecurity() != null ? operation.getSecurity() : globalSecurity, schemes);

        return new ImportEndpoint(
                httpMethod.name(),
                path,
                operation.getSummary(),
                operation.getDescription(),
                authType,
                null,
                category,
                parseParameters(operation.getParameters()),
                parseRequestBody(operation.getRequestBody()),
                parseResponses(operation.getResponses()));
    }

    private List<ImportParameter> parseParameters(List<Parameter> parameters) {
        List<ImportParameter> result = new ArrayList<>();
        if (parameters == null) {
            return result;
        }
        for (Parameter parameter : parameters) {
            String dataType = parameter.getSchema() != null && parameter.getSchema().getType() != null
                    ? parameter.getSchema().getType() : "string";
            String example = parameter.getExample() != null ? parameter.getExample().toString() : null;
            result.add(new ImportParameter(parameter.getName(), parameter.getIn(), dataType,
                    Boolean.TRUE.equals(parameter.getRequired()), parameter.getDescription(), example));
        }
        return result;
    }

    private List<ImportRequestBody> parseRequestBody(RequestBody requestBody) {
        List<ImportRequestBody> result = new ArrayList<>();
        if (requestBody == null || requestBody.getContent() == null) {
            return result;
        }
        requestBody.getContent().forEach((contentType, mediaType) ->
                result.add(new ImportRequestBody(contentType, schemaToMap(mediaType.getSchema()),
                        exampleToMap(mediaType))));
        return result;
    }

    private List<ImportResponse> parseResponses(io.swagger.v3.oas.models.responses.ApiResponses responses) {
        List<ImportResponse> result = new ArrayList<>();
        if (responses == null) {
            return result;
        }
        responses.forEach((status, apiResponse) -> {
            int code = parseStatus(status);
            if (code < 100) {
                return; // skip "default" and unparseable codes
            }
            String contentType = "application/json";
            Map<String, Object> schema = null;
            Map<String, Object> example = null;
            if (apiResponse.getContent() != null && !apiResponse.getContent().isEmpty()) {
                Map.Entry<String, MediaType> first = apiResponse.getContent().entrySet().iterator().next();
                contentType = first.getKey();
                schema = schemaToMap(first.getValue().getSchema());
                example = exampleToMap(first.getValue());
            }
            result.add(new ImportResponse(code, apiResponse.getDescription(), contentType, schema, example));
        });
        return result;
    }

    private Map<String, Object> schemaToMap(Schema<?> schema) {
        if (schema == null) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = schemaMapper.convertValue(schema, Map.class);
            return map == null || map.isEmpty() ? null : map;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Map<String, Object> exampleToMap(MediaType mediaType) {
        Object example = mediaType.getExample();
        if (example == null) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = schemaMapper.convertValue(example, Map.class);
            return map;
        } catch (IllegalArgumentException e) {
            return null; // non-object example (string/number) — omit rather than fail
        }
    }

    private String resolveAuth(List<SecurityRequirement> security, Map<String, SecurityScheme> schemes) {
        if (security == null || security.isEmpty() || schemes == null) {
            return "none";
        }
        for (SecurityRequirement requirement : security) {
            for (String name : requirement.keySet()) {
                SecurityScheme scheme = schemes.get(name);
                if (scheme == null || scheme.getType() == null) {
                    continue;
                }
                switch (scheme.getType()) {
                    case HTTP:
                        return "basic".equalsIgnoreCase(scheme.getScheme()) ? "basic" : "bearer";
                    case APIKEY:
                        return "api_key";
                    case OAUTH2:
                        return "oauth2";
                    default:
                        return "none";
                }
            }
        }
        return "none";
    }

    private int parseStatus(String status) {
        try {
            return Integer.parseInt(status.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
