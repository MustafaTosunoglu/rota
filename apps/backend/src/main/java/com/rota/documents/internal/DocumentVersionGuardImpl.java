package com.rota.documents.internal;

import com.rota.documents.api.DocumentGuard;
import com.rota.documents.api.DocumentNotFoundException;
import com.rota.documents.api.DocumentVersionGuard;
import com.rota.documents.api.DocumentVersionNotEditableException;
import com.rota.documents.api.DocumentVersionNotFoundException;
import com.rota.documents.jpa.DocumentRepository;
import com.rota.documents.jpa.DocumentVersionEntity;
import com.rota.documents.jpa.DocumentVersionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** {@link DocumentVersionGuard} + {@link DocumentGuard} backed by the RLS-scoped repositories. */
@Component
class DocumentVersionGuardImpl implements DocumentVersionGuard, DocumentGuard {

    private final DocumentVersionRepository versions;
    private final DocumentRepository documents;

    DocumentVersionGuardImpl(DocumentVersionRepository versions, DocumentRepository documents) {
        this.versions = versions;
        this.documents = documents;
    }

    @Override
    @Transactional(readOnly = true)
    public void requireDocument(UUID documentId) {
        if (!documents.existsById(documentId)) {
            throw new DocumentNotFoundException(documentId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void requireVersion(UUID versionId) {
        if (!versions.existsById(versionId)) {
            throw new DocumentVersionNotFoundException(versionId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void requireEditable(UUID versionId) {
        DocumentVersionEntity version = versions.findById(versionId)
                .orElseThrow(() -> new DocumentVersionNotFoundException(versionId));
        if (!version.isDraft()) {
            throw new DocumentVersionNotEditableException(versionId, version.getStatus());
        }
    }
}
