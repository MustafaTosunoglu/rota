package com.rota.documents.internal;

import com.rota.documents.api.DocumentPublishedEvent;
import com.rota.documents.api.DocumentVersionClonedEvent;
import com.rota.documents.api.DocumentVersionCreatedEvent;
import com.rota.documents.api.DocumentVersionNotEditableException;
import com.rota.documents.api.DocumentVersionNotFoundException;
import com.rota.documents.jpa.DocumentEntity;
import com.rota.documents.jpa.DocumentRepository;
import com.rota.documents.jpa.DocumentVersionEntity;
import com.rota.documents.jpa.DocumentVersionRepository;
import com.rota.documents.jpa.EnvironmentEntity;
import com.rota.documents.jpa.EnvironmentRepository;
import com.rota.documents.api.DocumentNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Version lifecycle: draft → published → archived (plan §13.4 simplified for Phase 2).
 *
 * <p>Rules: only DRAFT versions are editable (content and metadata); publishing archives
 * the previously published version and repoints {@code documents.current_version_id};
 * cloning copies environments here and lets the endpoints module copy version content via
 * the synchronous in-transaction {@link DocumentVersionClonedEvent} listener.
 */
@Service
public class VersionService {

    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final EnvironmentRepository environments;
    private final ApplicationEventPublisher events;

    public VersionService(DocumentRepository documents,
                          DocumentVersionRepository versions,
                          EnvironmentRepository environments,
                          ApplicationEventPublisher events) {
        this.documents = documents;
        this.versions = versions;
        this.environments = environments;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<DocumentVersionEntity> list(UUID documentId) {
        if (!documents.existsById(documentId)) {
            throw new DocumentNotFoundException(documentId);
        }
        return versions.findAllByDocumentIdOrderByCreatedAtDesc(documentId);
    }

    @Transactional(readOnly = true)
    public DocumentVersionEntity get(UUID versionId) {
        return versions.findById(versionId)
                .orElseThrow(() -> new DocumentVersionNotFoundException(versionId));
    }

    @Transactional
    public DocumentVersionEntity create(UUID documentId, String versionLabel, UUID cloneFromVersionId) {
        DocumentEntity document = documents.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        DocumentVersionEntity version = new DocumentVersionEntity();
        version.setTenantId(document.getTenantId());
        version.setDocumentId(document.getId());
        version.setVersionLabel(versionLabel.trim());
        version = versions.save(version);
        try {
            versions.flush(); // surface the (document_id, version_label) unique violation here
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new VersionLabelAlreadyInUseException(version.getVersionLabel());
        }

        if (cloneFromVersionId != null) {
            DocumentVersionEntity source = get(cloneFromVersionId);
            if (!source.getDocumentId().equals(documentId)) {
                throw new DocumentVersionNotFoundException(cloneFromVersionId);
            }
            cloneEnvironments(source.getId(), version);
            // Synchronous, same transaction: the endpoints module copies categories,
            // endpoints, parameters, bodies and responses before this method returns.
            events.publishEvent(new DocumentVersionClonedEvent(
                    version.getTenantId(), source.getId(), version.getId()));
        }

        events.publishEvent(new DocumentVersionCreatedEvent(
                version.getTenantId(), documentId, version.getId()));
        return version;
    }

    @Transactional
    public DocumentVersionEntity updateMeta(UUID versionId, String versionLabel, String changelogMd) {
        DocumentVersionEntity version = get(versionId);
        requireDraft(version);
        if (versionLabel != null && !versionLabel.isBlank()) {
            version.setVersionLabel(versionLabel.trim());
        }
        if (changelogMd != null) {
            version.setChangelogMd(changelogMd);
        }
        return version;
    }

    @Transactional
    public DocumentVersionEntity publish(UUID versionId) {
        DocumentVersionEntity version = get(versionId);
        requireDraft(version);

        // At most one published version per document: the previous one gets archived.
        versions.findByDocumentIdAndStatus(version.getDocumentId(), DocumentVersionEntity.STATUS_PUBLISHED)
                .ifPresent(previous -> previous.setStatus(DocumentVersionEntity.STATUS_ARCHIVED));

        OffsetDateTime now = OffsetDateTime.now();
        version.setStatus(DocumentVersionEntity.STATUS_PUBLISHED);
        version.setPublishedAt(now);

        DocumentEntity document = documents.findById(version.getDocumentId())
                .orElseThrow(() -> new DocumentNotFoundException(version.getDocumentId()));
        document.setCurrentVersionId(version.getId());
        if (document.getPublishedAt() == null) {
            document.setPublishedAt(now);
        }

        events.publishEvent(new DocumentPublishedEvent(
                version.getTenantId(), document.getId(), version.getId()));
        return version;
    }

    @Transactional
    public DocumentVersionEntity archive(UUID versionId) {
        DocumentVersionEntity version = get(versionId);
        if (DocumentVersionEntity.STATUS_ARCHIVED.equals(version.getStatus())) {
            return version; // idempotent
        }
        version.setStatus(DocumentVersionEntity.STATUS_ARCHIVED);

        DocumentEntity document = documents.findById(version.getDocumentId())
                .orElseThrow(() -> new DocumentNotFoundException(version.getDocumentId()));
        if (version.getId().equals(document.getCurrentVersionId())) {
            document.setCurrentVersionId(null); // nothing is live for this document any more
        }
        return version;
    }

    private void cloneEnvironments(UUID sourceVersionId, DocumentVersionEntity target) {
        for (EnvironmentEntity env : environments.findAllByDocumentVersionId(sourceVersionId)) {
            EnvironmentEntity copy = new EnvironmentEntity();
            copy.setTenantId(target.getTenantId());
            copy.setDocumentVersionId(target.getId());
            copy.setName(env.getName());
            copy.setBaseUrl(env.getBaseUrl());
            copy.setProductionWarn(env.isProductionWarn());
            environments.save(copy);
        }
    }

    private void requireDraft(DocumentVersionEntity version) {
        if (!version.isDraft()) {
            throw new DocumentVersionNotEditableException(version.getId(), version.getStatus());
        }
    }
}
