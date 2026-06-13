package com.rota.endpoints.internal;

import com.rota.documents.api.DocumentVersionGuard;
import com.rota.endpoints.api.ContentImporter;
import com.rota.endpoints.api.ImportModel.DedupMode;
import com.rota.endpoints.api.ImportModel.ImportCategory;
import com.rota.endpoints.api.ImportModel.ImportEndpoint;
import com.rota.endpoints.api.ImportModel.ImportResult;
import com.rota.endpoints.internal.EndpointService.CreateEndpointCommand;
import com.rota.endpoints.internal.EndpointService.ParameterCommand;
import com.rota.endpoints.internal.EndpointService.RequestBodyCommand;
import com.rota.endpoints.internal.EndpointService.ResponseCommand;
import com.rota.endpoints.jpa.CategoryEntity;
import com.rota.endpoints.jpa.CategoryRepository;
import com.rota.endpoints.jpa.EndpointEntity;
import com.rota.endpoints.jpa.EndpointRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Materialises parsed content into a draft version. Categories are find-or-created by name
 * (case-insensitive); endpoints reuse the existing CRUD services so the same validation,
 * auditing and events apply as manual editing. Runs in one transaction — a failure rolls the
 * whole import back.
 */
@Component
class ContentImporterImpl implements ContentImporter {

    private final DocumentVersionGuard versionGuard;
    private final CategoryService categoryService;
    private final EndpointService endpointService;
    private final CategoryRepository categories;
    private final EndpointRepository endpoints;

    ContentImporterImpl(DocumentVersionGuard versionGuard,
                        CategoryService categoryService,
                        EndpointService endpointService,
                        CategoryRepository categories,
                        EndpointRepository endpoints) {
        this.versionGuard = versionGuard;
        this.categoryService = categoryService;
        this.endpointService = endpointService;
        this.categories = categories;
        this.endpoints = endpoints;
    }

    @Override
    @Transactional
    public ImportResult importInto(UUID versionId, List<ImportCategory> incomingCategories,
                                   List<ImportEndpoint> incomingEndpoints, DedupMode mode) {
        versionGuard.requireEditable(versionId);

        Map<String, UUID> categoryByName = upsertCategories(versionId, incomingCategories);

        int created = 0;
        int overwritten = 0;
        int skipped = 0;
        for (ImportEndpoint endpoint : incomingEndpoints) {
            String method = endpoint.method().toUpperCase(Locale.ROOT);
            String path = normalizePath(endpoint.path());

            var existing = endpoints.findByDocumentVersionIdAndMethodAndPath(versionId, method, path);
            if (existing.isPresent()) {
                if (mode == DedupMode.SKIP) {
                    skipped++;
                    continue;
                }
                endpointService.delete(existing.get().getId()); // OVERWRITE: replace wholesale
                overwritten++;
            } else {
                created++;
            }

            UUID categoryId = resolveCategory(versionId, endpoint.categoryName(), categoryByName);
            EndpointEntity saved = endpointService.create(versionId, new CreateEndpointCommand(
                    categoryId, method, path, endpoint.summary(), endpoint.descriptionMd(),
                    endpoint.authType(), endpoint.authConfig(), null, false));

            createSubResources(saved.getId(), endpoint);
        }
        return new ImportResult(created, overwritten, skipped);
    }

    private Map<String, UUID> upsertCategories(UUID versionId, List<ImportCategory> incoming) {
        Map<String, UUID> byName = new HashMap<>();
        for (CategoryEntity existing : categories.findAllByDocumentVersionId(versionId)) {
            byName.put(key(existing.getName()), existing.getId());
        }
        for (ImportCategory category : incoming) {
            if (category.name() == null || category.name().isBlank()) {
                continue;
            }
            byName.computeIfAbsent(key(category.name()), k ->
                    categoryService.create(versionId, category.name(), category.description(), null).getId());
        }
        return byName;
    }

    /** Resolves an endpoint's category, creating it on demand if referenced but not pre-listed. */
    private UUID resolveCategory(UUID versionId, String categoryName, Map<String, UUID> byName) {
        if (categoryName == null || categoryName.isBlank()) {
            return null;
        }
        return byName.computeIfAbsent(key(categoryName), k ->
                categoryService.create(versionId, categoryName, null, null).getId());
    }

    private void createSubResources(UUID endpointId, ImportEndpoint endpoint) {
        if (endpoint.parameters() != null) {
            for (var p : endpoint.parameters()) {
                endpointService.addParameter(endpointId, new ParameterCommand(
                        p.name(), p.location(), p.dataType(), p.required(), p.description(), null,
                        p.example(), null));
            }
        }
        if (endpoint.requestBodies() != null) {
            for (var b : endpoint.requestBodies()) {
                endpointService.addRequestBody(endpointId, new RequestBodyCommand(
                        b.contentType(), b.schemaJson(), b.exampleJson()));
            }
        }
        if (endpoint.responses() != null) {
            for (var r : endpoint.responses()) {
                endpointService.addResponse(endpointId, new ResponseCommand(
                        r.statusCode(), r.description(), r.contentType(), r.schemaJson(), r.exampleJson()));
            }
        }
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizePath(String path) {
        String trimmed = path.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }
}
