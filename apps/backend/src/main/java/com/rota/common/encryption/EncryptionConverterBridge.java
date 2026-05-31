package com.rota.common.encryption;

import org.springframework.stereotype.Component;

/**
 * Bridges Spring-managed encryption services to JPA's {@link jakarta.persistence.AttributeConverter}
 * instances, which JPA instantiates itself (outside the Spring container).
 *
 * <p>Populated once at startup; {@link EncryptedStringConverter} reads the services statically.
 */
@Component
public class EncryptionConverterBridge {

    private static EncryptionService encryptionService;
    private static TenantDekService tenantDekService;

    public EncryptionConverterBridge(EncryptionService encryptionService, TenantDekService tenantDekService) {
        EncryptionConverterBridge.encryptionService = encryptionService;
        EncryptionConverterBridge.tenantDekService = tenantDekService;
    }

    static EncryptionService encryptionService() {
        if (encryptionService == null) {
            throw new IllegalStateException("EncryptionConverterBridge not initialised (Spring context not started?)");
        }
        return encryptionService;
    }

    static TenantDekService tenantDekService() {
        if (tenantDekService == null) {
            throw new IllegalStateException("EncryptionConverterBridge not initialised (Spring context not started?)");
        }
        return tenantDekService;
    }
}
