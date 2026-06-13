package com.rota.endpoints.web;

import com.rota.endpoints.jpa.CategoryEntity;
import com.rota.endpoints.jpa.EndpointEntity;
import com.rota.endpoints.jpa.EndpointParameterEntity;
import com.rota.endpoints.jpa.EndpointRequestBodyEntity;
import com.rota.endpoints.jpa.EndpointResponseEntity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request/response records of the endpoints module's REST API. */
public final class EndpointDtos {

    private EndpointDtos() {
    }

    private static final String METHOD_PATTERN = "(?i)GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS";
    private static final String AUTH_TYPE_PATTERN = "none|bearer|api_key|basic|oauth2";
    private static final String LOCATION_PATTERN = "path|query|header|cookie";

    // --- categories ------------------------------------------------------------

    public record CreateCategoryRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            Integer sortOrder) {
    }

    public record UpdateCategoryRequest(
            @Size(max = 200) String name,
            @Size(max = 2000) String description,
            Integer sortOrder) {
    }

    public record CategoryResponse(UUID id, UUID documentVersionId, String name,
                                   String description, int sortOrder) {

        public static CategoryResponse from(CategoryEntity entity) {
            return new CategoryResponse(entity.getId(), entity.getDocumentVersionId(),
                    entity.getName(), entity.getDescription(), entity.getSortOrder());
        }
    }

    // --- endpoints ---------------------------------------------------------------

    public record CreateEndpointRequest(
            UUID categoryId,
            @NotBlank @Pattern(regexp = METHOD_PATTERN) String method,
            @NotBlank @Size(max = 2000) String path,
            @Size(max = 500) String summary,
            @Size(max = 100000) String descriptionMd,
            @Pattern(regexp = AUTH_TYPE_PATTERN) String authType,
            Map<String, Object> authConfig,
            Integer sortOrder,
            Boolean deprecated) {
    }

    public record UpdateEndpointRequest(
            UUID categoryId,
            Boolean clearCategory,
            @Pattern(regexp = METHOD_PATTERN) String method,
            @Size(max = 2000) String path,
            @Size(max = 500) String summary,
            @Size(max = 100000) String descriptionMd,
            @Pattern(regexp = AUTH_TYPE_PATTERN) String authType,
            Map<String, Object> authConfig,
            Integer sortOrder,
            Boolean mockEnabled,
            Boolean deprecated) {
    }

    public record EndpointSummaryResponse(UUID id, UUID documentVersionId, UUID categoryId,
                                          String method, String path, String summary,
                                          String authType, int sortOrder, boolean mockEnabled,
                                          boolean deprecated, OffsetDateTime updatedAt) {

        public static EndpointSummaryResponse from(EndpointEntity entity) {
            return new EndpointSummaryResponse(entity.getId(), entity.getDocumentVersionId(),
                    entity.getCategoryId(), entity.getMethod(), entity.getPath(), entity.getSummary(),
                    entity.getAuthType(), entity.getSortOrder(), entity.isMockEnabled(),
                    entity.isDeprecated(), entity.getUpdatedAt());
        }
    }

    public record EndpointDetailResponse(UUID id, UUID documentVersionId, UUID categoryId,
                                         String method, String path, String summary,
                                         String descriptionMd, String authType,
                                         Map<String, Object> authConfig, int sortOrder,
                                         boolean mockEnabled, boolean deprecated,
                                         OffsetDateTime createdAt, OffsetDateTime updatedAt,
                                         List<ParameterResponse> parameters,
                                         List<RequestBodyResponse> requestBodies,
                                         List<ResponseResponse> responses) {

        public static EndpointDetailResponse from(EndpointEntity entity,
                                                  List<EndpointParameterEntity> parameters,
                                                  List<EndpointRequestBodyEntity> requestBodies,
                                                  List<EndpointResponseEntity> responses) {
            return new EndpointDetailResponse(entity.getId(), entity.getDocumentVersionId(),
                    entity.getCategoryId(), entity.getMethod(), entity.getPath(), entity.getSummary(),
                    entity.getDescriptionMd(), entity.getAuthType(), entity.getAuthConfig(),
                    entity.getSortOrder(), entity.isMockEnabled(), entity.isDeprecated(),
                    entity.getCreatedAt(), entity.getUpdatedAt(),
                    parameters.stream().map(ParameterResponse::from).toList(),
                    requestBodies.stream().map(RequestBodyResponse::from).toList(),
                    responses.stream().map(ResponseResponse::from).toList());
        }
    }

    // --- parameters ----------------------------------------------------------------

    public record CreateParameterRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Pattern(regexp = LOCATION_PATTERN) String location,
            @Size(max = 100) String dataType,
            Boolean required,
            @Size(max = 2000) String description,
            @Size(max = 2000) String defaultValue,
            @Size(max = 2000) String example,
            Integer sortOrder) {
    }

    public record UpdateParameterRequest(
            @Size(max = 200) String name,
            @Pattern(regexp = LOCATION_PATTERN) String location,
            @Size(max = 100) String dataType,
            Boolean required,
            @Size(max = 2000) String description,
            @Size(max = 2000) String defaultValue,
            @Size(max = 2000) String example,
            Integer sortOrder) {
    }

    public record ParameterResponse(UUID id, UUID endpointId, String name, String location,
                                    String dataType, boolean required, String description,
                                    String defaultValue, String example, int sortOrder) {

        public static ParameterResponse from(EndpointParameterEntity entity) {
            return new ParameterResponse(entity.getId(), entity.getEndpointId(), entity.getName(),
                    entity.getLocation(), entity.getDataType(), entity.isRequired(),
                    entity.getDescription(), entity.getDefaultValue(), entity.getExample(),
                    entity.getSortOrder());
        }
    }

    // --- request bodies --------------------------------------------------------------

    public record CreateRequestBodyRequest(
            @Size(max = 200) String contentType,
            Map<String, Object> schemaJson,
            Map<String, Object> exampleJson) {
    }

    public record RequestBodyResponse(UUID id, UUID endpointId, String contentType,
                                      Map<String, Object> schemaJson, Map<String, Object> exampleJson) {

        public static RequestBodyResponse from(EndpointRequestBodyEntity entity) {
            return new RequestBodyResponse(entity.getId(), entity.getEndpointId(),
                    entity.getContentType(), entity.getSchemaJson(), entity.getExampleJson());
        }
    }

    // --- responses ---------------------------------------------------------------------

    public record CreateResponseRequest(
            @NotNull @Min(100) @Max(599) Integer statusCode,
            @Size(max = 2000) String description,
            @Size(max = 200) String contentType,
            Map<String, Object> schemaJson,
            Map<String, Object> exampleJson) {
    }

    public record UpdateResponseRequest(
            @Min(100) @Max(599) Integer statusCode,
            @Size(max = 2000) String description,
            @Size(max = 200) String contentType,
            Map<String, Object> schemaJson,
            Map<String, Object> exampleJson) {
    }

    public record ResponseResponse(UUID id, UUID endpointId, int statusCode, String description,
                                   String contentType, Map<String, Object> schemaJson,
                                   Map<String, Object> exampleJson) {

        public static ResponseResponse from(EndpointResponseEntity entity) {
            return new ResponseResponse(entity.getId(), entity.getEndpointId(), entity.getStatusCode(),
                    entity.getDescription(), entity.getContentType(), entity.getSchemaJson(),
                    entity.getExampleJson());
        }
    }
}
