package com.rota.documents.api;

import java.util.UUID;

public class DocumentVersionNotFoundException extends RuntimeException {

    public DocumentVersionNotFoundException(UUID versionId) {
        super("Document version not found: " + versionId);
    }
}
