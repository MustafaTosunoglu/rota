package com.rota.documents.api;

import java.util.UUID;

/**
 * Lets other modules (endpoints) validate a document version before attaching content to it.
 * Lookups are RLS-scoped: another tenant's version behaves exactly like a missing one.
 */
public interface DocumentVersionGuard {

    /** Asserts the version exists in the current tenant. */
    void requireVersion(UUID versionId);

    /**
     * Asserts the version exists AND is still a draft — published/archived versions are
     * immutable snapshots; their content can never be edited.
     */
    void requireEditable(UUID versionId);
}
