package com.rota.endpoints.internal;

import com.rota.common.security.CurrentUser;
import com.rota.common.tenant.TenantContext;
import com.rota.documents.api.DocumentVersionGuard;
import com.rota.endpoints.api.EndpointCreatedEvent;
import com.rota.endpoints.api.EndpointDeletedEvent;
import com.rota.endpoints.api.EndpointUpdatedEvent;
import com.rota.endpoints.jpa.CategoryRepository;
import com.rota.endpoints.jpa.EndpointEntity;
import com.rota.endpoints.jpa.EndpointParameterEntity;
import com.rota.endpoints.jpa.EndpointParameterRepository;
import com.rota.endpoints.jpa.EndpointRepository;
import com.rota.endpoints.jpa.EndpointRequestBodyEntity;
import com.rota.endpoints.jpa.EndpointRequestBodyRepository;
import com.rota.endpoints.jpa.EndpointResponseEntity;
import com.rota.endpoints.jpa.EndpointResponseRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoint CRUD plus its three sub-resources (parameters, request bodies, responses).
 * Every mutation goes through the documents-module version guard: content can only change
 * while the owning version is a draft.
 */
@Service
public class EndpointService {

    private final EndpointRepository endpoints;
    private final CategoryRepository categories;
    private final EndpointParameterRepository parameters;
    private final EndpointRequestBodyRepository requestBodies;
    private final EndpointResponseRepository responses;
    private final DocumentVersionGuard versionGuard;
    private final ApplicationEventPublisher events;

    public EndpointService(EndpointRepository endpoints,
                           CategoryRepository categories,
                           EndpointParameterRepository parameters,
                           EndpointRequestBodyRepository requestBodies,
                           EndpointResponseRepository responses,
                           DocumentVersionGuard versionGuard,
                           ApplicationEventPublisher events) {
        this.endpoints = endpoints;
        this.categories = categories;
        this.parameters = parameters;
        this.requestBodies = requestBodies;
        this.responses = responses;
        this.versionGuard = versionGuard;
        this.events = events;
    }

    // --- endpoints -----------------------------------------------------------------

    public record CreateEndpointCommand(UUID categoryId, String method, String path, String summary,
                                        String descriptionMd, String authType, Map<String, Object> authConfig,
                                        Integer sortOrder, Boolean deprecated) {
    }

    public record UpdateEndpointCommand(UUID categoryId, Boolean clearCategory, String method, String path,
                                        String summary, String descriptionMd, String authType,
                                        Map<String, Object> authConfig, Integer sortOrder,
                                        Boolean mockEnabled, Boolean deprecated) {
    }

    @Transactional(readOnly = true)
    public List<EndpointEntity> list(UUID versionId) {
        versionGuard.requireVersion(versionId);
        return endpoints.findAllByDocumentVersionIdOrderBySortOrderAscPathAsc(versionId);
    }

    @Transactional(readOnly = true)
    public EndpointEntity get(UUID endpointId) {
        return endpoints.findById(endpointId)
                .orElseThrow(() -> new NotFoundException("Endpoint", endpointId));
    }

    @Transactional
    public EndpointEntity create(UUID versionId, CreateEndpointCommand command) {
        versionGuard.requireEditable(versionId);

        String method = command.method().toUpperCase(Locale.ROOT);
        String path = normalizePath(command.path());
        if (endpoints.existsByDocumentVersionIdAndMethodAndPath(versionId, method, path)) {
            throw new DuplicateOperationException(method, path);
        }

        EndpointEntity endpoint = new EndpointEntity();
        endpoint.setTenantId(TenantContext.getTenantId());
        endpoint.setDocumentVersionId(versionId);
        endpoint.setCategoryId(resolveCategory(versionId, command.categoryId()));
        endpoint.setMethod(method);
        endpoint.setPath(path);
        endpoint.setSummary(command.summary());
        endpoint.setDescriptionMd(command.descriptionMd());
        if (command.authType() != null) {
            endpoint.setAuthType(command.authType());
        }
        endpoint.setAuthConfig(command.authConfig());
        endpoint.setSortOrder(command.sortOrder() != null ? command.sortOrder() : 0);
        endpoint.setDeprecated(Boolean.TRUE.equals(command.deprecated()));
        endpoint.setCreatedBy(CurrentUser.requireId());
        endpoint = endpoints.save(endpoint);

        events.publishEvent(new EndpointCreatedEvent(
                endpoint.getTenantId(), versionId, endpoint.getId()));
        return endpoint;
    }

    @Transactional
    public EndpointEntity update(UUID endpointId, UpdateEndpointCommand command) {
        EndpointEntity endpoint = get(endpointId);
        versionGuard.requireEditable(endpoint.getDocumentVersionId());

        String method = command.method() != null
                ? command.method().toUpperCase(Locale.ROOT) : endpoint.getMethod();
        String path = command.path() != null ? normalizePath(command.path()) : endpoint.getPath();
        boolean operationChanged = !method.equals(endpoint.getMethod()) || !path.equals(endpoint.getPath());
        if (operationChanged && endpoints.existsByDocumentVersionIdAndMethodAndPath(
                endpoint.getDocumentVersionId(), method, path)) {
            throw new DuplicateOperationException(method, path);
        }
        endpoint.setMethod(method);
        endpoint.setPath(path);

        if (Boolean.TRUE.equals(command.clearCategory())) {
            endpoint.setCategoryId(null);
        } else if (command.categoryId() != null) {
            endpoint.setCategoryId(resolveCategory(endpoint.getDocumentVersionId(), command.categoryId()));
        }
        if (command.summary() != null) {
            endpoint.setSummary(command.summary());
        }
        if (command.descriptionMd() != null) {
            endpoint.setDescriptionMd(command.descriptionMd());
        }
        if (command.authType() != null) {
            endpoint.setAuthType(command.authType());
        }
        if (command.authConfig() != null) {
            endpoint.setAuthConfig(command.authConfig());
        }
        if (command.sortOrder() != null) {
            endpoint.setSortOrder(command.sortOrder());
        }
        if (command.mockEnabled() != null) {
            endpoint.setMockEnabled(command.mockEnabled());
        }
        if (command.deprecated() != null) {
            endpoint.setDeprecated(command.deprecated());
        }

        events.publishEvent(new EndpointUpdatedEvent(
                endpoint.getTenantId(), endpoint.getDocumentVersionId(), endpoint.getId()));
        return endpoint;
    }

    @Transactional
    public void delete(UUID endpointId) {
        EndpointEntity endpoint = get(endpointId);
        versionGuard.requireEditable(endpoint.getDocumentVersionId());
        endpoints.delete(endpoint); // parameters/bodies/responses go with it (FK CASCADE)
        events.publishEvent(new EndpointDeletedEvent(
                endpoint.getTenantId(), endpoint.getDocumentVersionId(), endpointId));
    }

    // --- parameters ----------------------------------------------------------------

    public record ParameterCommand(String name, String location, String dataType, Boolean required,
                                   String description, String defaultValue, String example,
                                   Integer sortOrder) {
    }

    @Transactional(readOnly = true)
    public List<EndpointParameterEntity> listParameters(UUID endpointId) {
        get(endpointId);
        return parameters.findAllByEndpointIdOrderBySortOrderAscNameAsc(endpointId);
    }

    @Transactional
    public EndpointParameterEntity addParameter(UUID endpointId, ParameterCommand command) {
        EndpointEntity endpoint = get(endpointId);
        versionGuard.requireEditable(endpoint.getDocumentVersionId());
        EndpointParameterEntity parameter = new EndpointParameterEntity();
        parameter.setTenantId(endpoint.getTenantId());
        parameter.setEndpointId(endpointId);
        applyParameter(parameter, command);
        return parameters.save(parameter);
    }

    @Transactional
    public EndpointParameterEntity updateParameter(UUID parameterId, ParameterCommand command) {
        EndpointParameterEntity parameter = parameters.findById(parameterId)
                .orElseThrow(() -> new NotFoundException("Parameter", parameterId));
        versionGuard.requireEditable(get(parameter.getEndpointId()).getDocumentVersionId());
        applyParameter(parameter, command);
        return parameter;
    }

    @Transactional
    public void deleteParameter(UUID parameterId) {
        EndpointParameterEntity parameter = parameters.findById(parameterId)
                .orElseThrow(() -> new NotFoundException("Parameter", parameterId));
        versionGuard.requireEditable(get(parameter.getEndpointId()).getDocumentVersionId());
        parameters.delete(parameter);
    }

    private void applyParameter(EndpointParameterEntity parameter, ParameterCommand command) {
        if (command.name() != null && !command.name().isBlank()) {
            parameter.setName(command.name().trim());
        }
        if (command.location() != null) {
            parameter.setLocation(command.location());
        }
        if (command.dataType() != null && !command.dataType().isBlank()) {
            parameter.setDataType(command.dataType());
        }
        if (command.required() != null) {
            parameter.setRequired(command.required());
        }
        if (command.description() != null) {
            parameter.setDescription(command.description());
        }
        if (command.defaultValue() != null) {
            parameter.setDefaultValue(command.defaultValue());
        }
        if (command.example() != null) {
            parameter.setExample(command.example());
        }
        if (command.sortOrder() != null) {
            parameter.setSortOrder(command.sortOrder());
        }
    }

    // --- request bodies ------------------------------------------------------------

    public record RequestBodyCommand(String contentType, Map<String, Object> schemaJson,
                                     Map<String, Object> exampleJson) {
    }

    @Transactional(readOnly = true)
    public List<EndpointRequestBodyEntity> listRequestBodies(UUID endpointId) {
        get(endpointId);
        return requestBodies.findAllByEndpointIdOrderByContentType(endpointId);
    }

    @Transactional
    public EndpointRequestBodyEntity addRequestBody(UUID endpointId, RequestBodyCommand command) {
        EndpointEntity endpoint = get(endpointId);
        versionGuard.requireEditable(endpoint.getDocumentVersionId());
        EndpointRequestBodyEntity body = new EndpointRequestBodyEntity();
        body.setTenantId(endpoint.getTenantId());
        body.setEndpointId(endpointId);
        applyRequestBody(body, command);
        return requestBodies.save(body);
    }

    @Transactional
    public EndpointRequestBodyEntity updateRequestBody(UUID requestBodyId, RequestBodyCommand command) {
        EndpointRequestBodyEntity body = requestBodies.findById(requestBodyId)
                .orElseThrow(() -> new NotFoundException("Request body", requestBodyId));
        versionGuard.requireEditable(get(body.getEndpointId()).getDocumentVersionId());
        applyRequestBody(body, command);
        return body;
    }

    @Transactional
    public void deleteRequestBody(UUID requestBodyId) {
        EndpointRequestBodyEntity body = requestBodies.findById(requestBodyId)
                .orElseThrow(() -> new NotFoundException("Request body", requestBodyId));
        versionGuard.requireEditable(get(body.getEndpointId()).getDocumentVersionId());
        requestBodies.delete(body);
    }

    private void applyRequestBody(EndpointRequestBodyEntity body, RequestBodyCommand command) {
        if (command.contentType() != null && !command.contentType().isBlank()) {
            body.setContentType(command.contentType().trim());
        }
        if (command.schemaJson() != null) {
            body.setSchemaJson(command.schemaJson());
        }
        if (command.exampleJson() != null) {
            body.setExampleJson(command.exampleJson());
        }
    }

    // --- responses -----------------------------------------------------------------

    public record ResponseCommand(Integer statusCode, String description, String contentType,
                                  Map<String, Object> schemaJson, Map<String, Object> exampleJson) {
    }

    @Transactional(readOnly = true)
    public List<EndpointResponseEntity> listResponses(UUID endpointId) {
        get(endpointId);
        return responses.findAllByEndpointIdOrderByStatusCode(endpointId);
    }

    @Transactional
    public EndpointResponseEntity addResponse(UUID endpointId, ResponseCommand command) {
        EndpointEntity endpoint = get(endpointId);
        versionGuard.requireEditable(endpoint.getDocumentVersionId());
        EndpointResponseEntity response = new EndpointResponseEntity();
        response.setTenantId(endpoint.getTenantId());
        response.setEndpointId(endpointId);
        applyResponse(response, command);
        return responses.save(response);
    }

    @Transactional
    public EndpointResponseEntity updateResponse(UUID responseId, ResponseCommand command) {
        EndpointResponseEntity response = responses.findById(responseId)
                .orElseThrow(() -> new NotFoundException("Response", responseId));
        versionGuard.requireEditable(get(response.getEndpointId()).getDocumentVersionId());
        applyResponse(response, command);
        return response;
    }

    @Transactional
    public void deleteResponse(UUID responseId) {
        EndpointResponseEntity response = responses.findById(responseId)
                .orElseThrow(() -> new NotFoundException("Response", responseId));
        versionGuard.requireEditable(get(response.getEndpointId()).getDocumentVersionId());
        responses.delete(response);
    }

    private void applyResponse(EndpointResponseEntity response, ResponseCommand command) {
        if (command.statusCode() != null) {
            response.setStatusCode(command.statusCode());
        }
        if (command.description() != null) {
            response.setDescription(command.description());
        }
        if (command.contentType() != null && !command.contentType().isBlank()) {
            response.setContentType(command.contentType().trim());
        }
        if (command.schemaJson() != null) {
            response.setSchemaJson(command.schemaJson());
        }
        if (command.exampleJson() != null) {
            response.setExampleJson(command.exampleJson());
        }
    }

    // --- helpers -------------------------------------------------------------------

    /** A category may only be attached if it belongs to the same document version. */
    private UUID resolveCategory(UUID versionId, UUID categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categories.findById(categoryId)
                .filter(category -> category.getDocumentVersionId().equals(versionId))
                .orElseThrow(() -> new NotFoundException("Category", categoryId))
                .getId();
    }

    private static String normalizePath(String path) {
        String trimmed = path.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }
}
