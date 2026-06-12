package com.rota.audit.internal;

import com.rota.audit.api.AuditService;
import org.springframework.stereotype.Component;

/**
 * Bridges the Spring-managed {@link AuditService} to {@code AuditEntityListener}, which JPA
 * instantiates outside the Spring container. Same pattern as {@code EncryptionConverterBridge}.
 */
@Component
public class AuditBridge {

    private static AuditService auditService;

    public AuditBridge(AuditService auditService) {
        AuditBridge.auditService = auditService;
    }

    public static AuditService auditService() {
        if (auditService == null) {
            throw new IllegalStateException("AuditBridge not initialised (Spring context not started?)");
        }
        return auditService;
    }
}
