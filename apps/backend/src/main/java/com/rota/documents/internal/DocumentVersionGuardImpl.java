package com.rota.documents.internal;

import com.rota.documents.api.DocumentVersionGuard;
import com.rota.documents.api.DocumentVersionNotEditableException;
import com.rota.documents.api.DocumentVersionNotFoundException;
import com.rota.documents.jpa.DocumentVersionEntity;
import com.rota.documents.jpa.DocumentVersionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** {@link DocumentVersionGuard} backed by the (RLS-scoped) version repository. */
@Component
class DocumentVersionGuardImpl implements DocumentVersionGuard {

    private final DocumentVersionRepository versions;

    DocumentVersionGuardImpl(DocumentVersionRepository versions) {
        this.versions = versions;
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
