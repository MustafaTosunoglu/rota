package com.rota.documents.internal;

import com.rota.documents.api.DocumentExport;
import com.rota.documents.api.DocumentExportReader;
import com.rota.documents.api.DocumentVersionNotFoundException;
import com.rota.documents.api.EnvironmentSpec;
import com.rota.documents.api.EnvironmentWriter;
import com.rota.documents.jpa.DocumentEntity;
import com.rota.documents.jpa.DocumentRepository;
import com.rota.documents.jpa.DocumentVersionEntity;
import com.rota.documents.jpa.DocumentVersionRepository;
import com.rota.documents.jpa.EnvironmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Implements the documents-module cross-module ports used by the importer/exporter.
 * Everything is RLS-scoped through the repositories; an out-of-tenant version is invisible.
 */
@Component
class DocumentPortsImpl implements EnvironmentWriter, DocumentExportReader {

    private final DocumentVersionRepository versions;
    private final DocumentRepository documents;
    private final EnvironmentRepository environments;
    private final EnvironmentService environmentService;

    DocumentPortsImpl(DocumentVersionRepository versions,
                      DocumentRepository documents,
                      EnvironmentRepository environments,
                      EnvironmentService environmentService) {
        this.versions = versions;
        this.documents = documents;
        this.environments = environments;
        this.environmentService = environmentService;
    }

    @Override
    @Transactional
    public void addIfAbsent(UUID versionId, EnvironmentSpec environment) {
        boolean exists = environments.findAllByDocumentVersionId(versionId).stream()
                .anyMatch(e -> e.getName().equalsIgnoreCase(environment.name()));
        if (!exists) {
            environmentService.create(versionId, environment.name(), environment.baseUrl(),
                    environment.productionWarn());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentExport read(UUID versionId) {
        DocumentVersionEntity version = versions.findById(versionId)
                .orElseThrow(() -> new DocumentVersionNotFoundException(versionId));
        DocumentEntity document = documents.findById(version.getDocumentId())
                .orElseThrow(() -> new DocumentVersionNotFoundException(versionId));
        List<EnvironmentSpec> envs = environments.findAllByDocumentVersionIdOrderByName(versionId).stream()
                .map(e -> new EnvironmentSpec(e.getName(), e.getBaseUrl(), e.isProductionWarn()))
                .toList();
        return new DocumentExport(document.getName(), document.getDescription(),
                version.getVersionLabel(), envs);
    }
}
