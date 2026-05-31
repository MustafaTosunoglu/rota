package com.rota.common.encryption;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the AES-256-GCM envelope primitives (no Spring context). */
class EncryptionServiceTest {

    private static final String TEST_KEY = "nA/JLgqQZ4abSrjRsOJ9sMYDIgRBW6ggw1XCg+mrQow=";

    private EncryptionService newService(String base64Key) {
        EncryptionProperties props = new EncryptionProperties();
        props.setMasterKey(base64Key);
        return new EncryptionService(props);
    }

    @Test
    void dekWrapUnwrapAndFieldRoundTrip() {
        EncryptionService service = newService(TEST_KEY);

        SecretKey dek = service.unwrapDek(service.generateWrappedDek());
        byte[] ciphertext = service.encryptWithDek("sk_live_super_secret", dek);

        assertThat(ciphertext).isNotEmpty();
        // Ciphertext must not contain the plaintext.
        assertThat(new String(ciphertext, java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain("super_secret");
        assertThat(service.decryptWithDek(ciphertext, dek)).isEqualTo("sk_live_super_secret");
    }

    @Test
    void eachEncryptionUsesAFreshIv() {
        EncryptionService service = newService(TEST_KEY);
        SecretKey dek = service.unwrapDek(service.generateWrappedDek());

        byte[] a = service.encryptWithDek("same-value", dek);
        byte[] b = service.encryptWithDek("same-value", dek);

        // Random IV per call => identical plaintext yields different ciphertext.
        assertThat(a).isNotEqualTo(b);
        assertThat(service.decryptWithDek(a, dek)).isEqualTo("same-value");
        assertThat(service.decryptWithDek(b, dek)).isEqualTo("same-value");
    }

    @Test
    void tamperedCiphertextFailsAuthentication() {
        EncryptionService service = newService(TEST_KEY);
        SecretKey dek = service.unwrapDek(service.generateWrappedDek());

        byte[] ciphertext = service.encryptWithDek("integrity-matters", dek);
        ciphertext[ciphertext.length - 1] ^= 0x01; // flip a bit in the GCM tag region

        assertThatThrownBy(() -> service.decryptWithDek(ciphertext, dek))
                .isInstanceOf(EncryptionException.class);
    }

    @Test
    void dekFromADifferentMasterKeyCannotUnwrap() {
        EncryptionService a = newService(TEST_KEY);
        EncryptionService b = newService("QT2k03e8qxL65Vm0hYYlJSP/L6cMNG5y2nHGYyjrGg8=");

        byte[] wrapped = a.generateWrappedDek();
        assertThatThrownBy(() -> b.unwrapDek(wrapped)).isInstanceOf(EncryptionException.class);
    }

    @Test
    void rejectsWrongSizedMasterKey() {
        assertThatThrownBy(() -> newService("dG9vc2hvcnQ=")) // "tooshort" -> 8 bytes
                .isInstanceOf(IllegalStateException.class);
    }
}
