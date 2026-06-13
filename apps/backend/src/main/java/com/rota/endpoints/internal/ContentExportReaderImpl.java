package com.rota.endpoints.internal;

import com.rota.endpoints.api.ContentExportReader;
import com.rota.endpoints.api.ExportModel.ContentExport;
import com.rota.endpoints.api.ExportModel.ExportEndpoint;
import com.rota.endpoints.api.ExportModel.ExportParameter;
import com.rota.endpoints.api.ExportModel.ExportRequestBody;
import com.rota.endpoints.api.ExportModel.ExportResponse;
import com.rota.endpoints.jpa.CategoryRepository;
import com.rota.endpoints.jpa.EndpointEntity;
import com.rota.endpoints.jpa.EndpointParameterRepository;
import com.rota.endpoints.jpa.EndpointRepository;
import com.rota.endpoints.jpa.EndpointRequestBodyRepository;
import com.rota.endpoints.jpa.EndpointResponseRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reads a version's full content (RLS-scoped) into the neutral export model. */
@Component
class ContentExportReaderImpl implements ContentExportReader {

    private final EndpointRepository endpoints;
    private final CategoryRepository categories;
    private final EndpointParameterRepository parameters;
    private final EndpointRequestBodyRepository requestBodies;
    private final EndpointResponseRepository responses;

    ContentExportReaderImpl(EndpointRepository endpoints,
                            CategoryRepository categories,
                            EndpointParameterRepository parameters,
                            EndpointRequestBodyRepository requestBodies,
                            EndpointResponseRepository responses) {
        this.endpoints = endpoints;
        this.categories = categories;
        this.parameters = parameters;
        this.requestBodies = requestBodies;
        this.responses = responses;
    }

    @Override
    @Transactional(readOnly = true)
    public ContentExport read(UUID versionId) {
        Map<UUID, String> categoryNames = new HashMap<>();
        categories.findAllByDocumentVersionId(versionId)
                .forEach(c -> categoryNames.put(c.getId(), c.getName()));

        List<ExportEndpoint> exported = endpoints
                .findAllByDocumentVersionIdOrderBySortOrderAscPathAsc(versionId).stream()
                .map(endpoint -> toExport(endpoint, categoryNames))
                .toList();
        return new ContentExport(exported);
    }

    private ExportEndpoint toExport(EndpointEntity endpoint, Map<UUID, String> categoryNames) {
        List<ExportParameter> params = parameters
                .findAllByEndpointIdOrderBySortOrderAscNameAsc(endpoint.getId()).stream()
                .map(p -> new ExportParameter(p.getName(), p.getLocation(), p.getDataType(),
                        p.isRequired(), p.getDescription(), p.getExample()))
                .toList();
        List<ExportRequestBody> bodies = requestBodies
                .findAllByEndpointIdOrderByContentType(endpoint.getId()).stream()
                .map(b -> new ExportRequestBody(b.getContentType(), b.getSchemaJson(), b.getExampleJson()))
                .toList();
        List<ExportResponse> resps = responses
                .findAllByEndpointIdOrderByStatusCode(endpoint.getId()).stream()
                .map(r -> new ExportResponse(r.getStatusCode(), r.getDescription(), r.getContentType(),
                        r.getSchemaJson(), r.getExampleJson()))
                .toList();
        return new ExportEndpoint(endpoint.getMethod(), endpoint.getPath(), endpoint.getSummary(),
                endpoint.getDescriptionMd(), endpoint.getAuthType(),
                endpoint.getCategoryId() != null ? categoryNames.get(endpoint.getCategoryId()) : null,
                endpoint.isDeprecated(), params, bodies, resps);
    }
}
