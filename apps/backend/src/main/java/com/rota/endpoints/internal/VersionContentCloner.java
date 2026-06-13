package com.rota.endpoints.internal;

import com.rota.documents.api.DocumentVersionClonedEvent;
import com.rota.endpoints.jpa.CategoryEntity;
import com.rota.endpoints.jpa.CategoryRepository;
import com.rota.endpoints.jpa.EndpointEntity;
import com.rota.endpoints.jpa.EndpointParameterEntity;
import com.rota.endpoints.jpa.EndpointParameterRepository;
import com.rota.endpoints.jpa.EndpointRepository;
import com.rota.endpoints.jpa.EndpointRequestBodyEntity;
import com.rota.endpoints.jpa.EndpointRequestBodyRepository;
import com.rota.endpoints.jpa.EndpointResponseEntity;
import com.rota.endpoints.jpa.EndpointResponseRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Copies a version's content (categories → endpoints → parameters/bodies/responses) when the
 * documents module clones a version. The listener is SYNCHRONOUS and runs inside the cloning
 * transaction (plain {@code @EventListener}): either the new version gets all its content or
 * the whole clone rolls back. Direction documents → endpoints stays event-only to avoid a
 * dependency cycle (endpoints already uses documents.api for the version guard).
 */
@Component
class VersionContentCloner {

    private final CategoryRepository categories;
    private final EndpointRepository endpoints;
    private final EndpointParameterRepository parameters;
    private final EndpointRequestBodyRepository requestBodies;
    private final EndpointResponseRepository responses;

    VersionContentCloner(CategoryRepository categories,
                         EndpointRepository endpoints,
                         EndpointParameterRepository parameters,
                         EndpointRequestBodyRepository requestBodies,
                         EndpointResponseRepository responses) {
        this.categories = categories;
        this.endpoints = endpoints;
        this.parameters = parameters;
        this.requestBodies = requestBodies;
        this.responses = responses;
    }

    @EventListener
    public void on(DocumentVersionClonedEvent event) {
        Map<UUID, UUID> categoryIdMap = cloneCategories(event);
        cloneEndpoints(event, categoryIdMap);
    }

    private Map<UUID, UUID> cloneCategories(DocumentVersionClonedEvent event) {
        Map<UUID, UUID> idMap = new HashMap<>();
        for (CategoryEntity source : categories.findAllByDocumentVersionId(event.sourceVersionId())) {
            CategoryEntity copy = new CategoryEntity();
            copy.setTenantId(event.tenantId());
            copy.setDocumentVersionId(event.newVersionId());
            copy.setName(source.getName());
            copy.setDescription(source.getDescription());
            copy.setSortOrder(source.getSortOrder());
            idMap.put(source.getId(), categories.save(copy).getId());
        }
        return idMap;
    }

    private void cloneEndpoints(DocumentVersionClonedEvent event, Map<UUID, UUID> categoryIdMap) {
        for (EndpointEntity source : endpoints.findAllByDocumentVersionId(event.sourceVersionId())) {
            EndpointEntity copy = new EndpointEntity();
            copy.setTenantId(event.tenantId());
            copy.setDocumentVersionId(event.newVersionId());
            copy.setCategoryId(source.getCategoryId() != null
                    ? categoryIdMap.get(source.getCategoryId()) : null);
            copy.setMethod(source.getMethod());
            copy.setPath(source.getPath());
            copy.setSummary(source.getSummary());
            copy.setDescriptionMd(source.getDescriptionMd());
            copy.setAuthType(source.getAuthType());
            copy.setAuthConfig(source.getAuthConfig());
            copy.setSortOrder(source.getSortOrder());
            copy.setMockEnabled(source.isMockEnabled());
            copy.setDeprecated(source.isDeprecated());
            copy.setCreatedBy(source.getCreatedBy());
            UUID newEndpointId = endpoints.save(copy).getId();

            cloneParameters(event.tenantId(), source.getId(), newEndpointId);
            cloneRequestBodies(event.tenantId(), source.getId(), newEndpointId);
            cloneResponses(event.tenantId(), source.getId(), newEndpointId);
        }
    }

    private void cloneParameters(UUID tenantId, UUID fromEndpointId, UUID toEndpointId) {
        for (EndpointParameterEntity source : parameters.findAllByEndpointId(fromEndpointId)) {
            EndpointParameterEntity copy = new EndpointParameterEntity();
            copy.setTenantId(tenantId);
            copy.setEndpointId(toEndpointId);
            copy.setName(source.getName());
            copy.setLocation(source.getLocation());
            copy.setDataType(source.getDataType());
            copy.setRequired(source.isRequired());
            copy.setDescription(source.getDescription());
            copy.setDefaultValue(source.getDefaultValue());
            copy.setExample(source.getExample());
            copy.setSortOrder(source.getSortOrder());
            parameters.save(copy);
        }
    }

    private void cloneRequestBodies(UUID tenantId, UUID fromEndpointId, UUID toEndpointId) {
        for (EndpointRequestBodyEntity source : requestBodies.findAllByEndpointId(fromEndpointId)) {
            EndpointRequestBodyEntity copy = new EndpointRequestBodyEntity();
            copy.setTenantId(tenantId);
            copy.setEndpointId(toEndpointId);
            copy.setContentType(source.getContentType());
            copy.setSchemaJson(source.getSchemaJson());
            copy.setExampleJson(source.getExampleJson());
            requestBodies.save(copy);
        }
    }

    private void cloneResponses(UUID tenantId, UUID fromEndpointId, UUID toEndpointId) {
        for (EndpointResponseEntity source : responses.findAllByEndpointId(fromEndpointId)) {
            EndpointResponseEntity copy = new EndpointResponseEntity();
            copy.setTenantId(tenantId);
            copy.setEndpointId(toEndpointId);
            copy.setStatusCode(source.getStatusCode());
            copy.setDescription(source.getDescription());
            copy.setContentType(source.getContentType());
            copy.setSchemaJson(source.getSchemaJson());
            copy.setExampleJson(source.getExampleJson());
            responses.save(copy);
        }
    }
}
