package com.rota.common.encryption;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the JPA converter encrypts on write and decrypts on read using the resolved DEK.
 * The per-tenant resolution is mocked here; its DB/RLS behaviour is covered by
 * {@code TenantDekServiceRlsTest}.
 */
class EncryptedStringConverterTest {

    private static final String TEST_KEY = "nA/JLgqQZ4abSrjRsOJ9sMYDIgRBW6ggw1XCg+mrQow=";

    @Test
    void encryptsOnWriteAndDecryptsOnRead() {
        EncryptionProperties props = new EncryptionProperties();
        props.setMasterKey(TEST_KEY);
        EncryptionService encryptionService = new EncryptionService(props);
        SecretKey dek = encryptionService.unwrapDek(encryptionService.generateWrappedDek());

        TenantDekService dekService = Mockito.mock(TenantDekService.class);
        Mockito.when(dekService.resolveCurrentTenantDek()).thenReturn(dek);

        // Populate the static bridge JPA-instantiated converters read from.
        new EncryptionConverterBridge(encryptionService, dekService);
        EncryptedStringConverter converter = new EncryptedStringConverter();

        byte[] stored = converter.convertToDatabaseColumn("Bearer abc123");
        assertThat(stored).isNotNull();
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("abc123");
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo("Bearer abc123");
    }

    @Test
    void passesNullThrough() {
        // Bridge still needs to be initialised for the class to be usable, but null is
        // short-circuited before the DEK is touched.
        EncryptionProperties props = new EncryptionProperties();
        props.setMasterKey(TEST_KEY);
        new EncryptionConverterBridge(new EncryptionService(props), Mockito.mock(TenantDekService.class));

        EncryptedStringConverter converter = new EncryptedStringConverter();
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
