package com.rota.documents.api;

import java.util.UUID;

/** Lets other modules (consumers) assert a document exists in the current tenant. */
public interface DocumentGuard {

    /** @throws DocumentNotFoundException when absent (or RLS-hidden, which looks the same) */
    void requireDocument(UUID documentId);
}
