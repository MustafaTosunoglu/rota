package com.rota.documents.api;

import java.util.UUID;

/**
 * Published while cloning a version, INSIDE the creating transaction. The endpoints module
 * listens synchronously and copies the source version's content (categories, endpoints,
 * parameters, bodies, responses) to the new draft — the event direction avoids a
 * documents → endpoints dependency cycle, and the synchronous in-transaction listener keeps
 * the clone atomic: if copying fails, the new version is rolled back too.
 */
public record DocumentVersionClonedEvent(UUID tenantId, UUID sourceVersionId, UUID newVersionId) {
}
