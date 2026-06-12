package com.rota.audit.api;

import java.util.Map;
import java.util.UUID;

/**
 * A single audit entry. {@code tenantId}, {@code actorUserId} and {@code actorIp} may be left
 * null — the audit service fills them from the current {@code TenantContext} / security context
 * when omitted (entity-listener captures rely on this; explicit security events pass them).
 */
public record AuditEvent(
        UUID tenantId,
        UUID actorUserId,
        String actorIp,
        String entityType,
        UUID entityId,
        String action,
        Map<String, Object> beforeState,
        Map<String, Object> afterState,
        UUID correlationId) {

    /** Entity lifecycle event — tenant/actor resolved by the service from context. */
    public static AuditEvent entity(String entityType, UUID entityId, String action,
                                    Map<String, Object> beforeState, Map<String, Object> afterState) {
        return new AuditEvent(null, null, null, entityType, entityId, action,
                beforeState, afterState, null);
    }

    /** Security event with an explicitly known tenant and actor. */
    public static AuditEvent security(String action, UUID tenantId, UUID actorUserId, UUID entityId,
                                      Map<String, Object> details) {
        return new AuditEvent(tenantId, actorUserId, null, "auth", entityId, action,
                null, details, null);
    }
}
