package com.rota.documents.api;

import java.util.UUID;

/** Published when a version goes live ({@code documents.current_version_id} now points at it). */
public record DocumentPublishedEvent(UUID tenantId, UUID documentId, UUID versionId) {
}
