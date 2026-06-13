package com.rota.documents.api;

import java.util.UUID;

/** Raised when content or metadata of a non-draft (published/archived) version is mutated. */
public class DocumentVersionNotEditableException extends RuntimeException {

    public DocumentVersionNotEditableException(UUID versionId, String status) {
        super("Document version " + versionId + " is " + status + " and can no longer be edited");
    }
}
