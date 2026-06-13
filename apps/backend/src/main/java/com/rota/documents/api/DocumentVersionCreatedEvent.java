package com.rota.documents.api;

import java.util.UUID;

/** Published after a new draft version is created (plan §10). */
public record DocumentVersionCreatedEvent(UUID tenantId, UUID documentId, UUID versionId) {
}
