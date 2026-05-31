package com.rota.common.encryption;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.SecretKey;

/**
 * Transparent field-level encryption for JPA entities (plan §8.2):
 *
 * <pre>{@code
 * @Convert(converter = EncryptedStringConverter.class)
 * private String apiKey;
 * }</pre>
 *
 * <p>Encrypts on write and decrypts on read using the CURRENT tenant's DEK (resolved from
 * {@link com.rota.common.tenant.TenantContext} via {@link TenantDekService}). This is safe
 * because RLS guarantees a request only ever reads/writes rows of its own tenant, so the
 * bound tenant always owns the field being converted. The column type is {@code bytea}.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, byte[]> {

    @Override
    public byte[] convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        SecretKey dek = EncryptionConverterBridge.tenantDekService().resolveCurrentTenantDek();
        return EncryptionConverterBridge.encryptionService().encryptWithDek(attribute, dek);
    }

    @Override
    public String convertToEntityAttribute(byte[] dbData) {
        if (dbData == null) {
            return null;
        }
        SecretKey dek = EncryptionConverterBridge.tenantDekService().resolveCurrentTenantDek();
        return EncryptionConverterBridge.encryptionService().decryptWithDek(dbData, dek);
    }
}
