package com.rota.common.encryption;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Envelope encryption primitives (plan §8.2), built on the JDK's built-in JCE provider
 * (AES-256-GCM). No external crypto dependency.
 *
 * <p>Two layers:
 * <ul>
 *   <li><b>KEK</b> (master key) — injected from config; wraps/unwraps per-tenant DEKs.</li>
 *   <li><b>DEK</b> (data encryption key) — random per tenant, stored wrapped in
 *       {@code tenants.encrypted_dek}; encrypts the actual sensitive field values.</li>
 * </ul>
 *
 * <p>Wire format of every ciphertext: {@code [1B version][12B IV][ciphertext + 16B GCM tag]}.
 * GCM authenticates the data, so any tampering fails decryption with an
 * {@link EncryptionException}.
 */
@Service
public class EncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int KEY_BYTES = 32; // 256-bit
    private static final byte FORMAT_VERSION = 1;

    private final SecretKey masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptionService(EncryptionProperties properties) {
        String configured = properties.getMasterKey();
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException(
                    "rota.encryption.master-key is not set. Provide a base64 32-byte key "
                            + "(dev: application-local.yml; prod: secret manager).");
        }
        byte[] raw = Base64.getDecoder().decode(configured.trim());
        if (raw.length != KEY_BYTES) {
            throw new IllegalStateException(
                    "rota.encryption.master-key must decode to " + KEY_BYTES + " bytes, got " + raw.length);
        }
        this.masterKey = new SecretKeySpec(raw, "AES");
        Arrays.fill(raw, (byte) 0);
    }

    // --- Per-tenant DEK lifecycle -------------------------------------------------

    /** Generate a fresh random DEK and return it wrapped (encrypted) with the KEK. */
    public byte[] generateWrappedDek() {
        byte[] dekRaw = new byte[KEY_BYTES];
        secureRandom.nextBytes(dekRaw);
        try {
            return encrypt(dekRaw, masterKey);
        } finally {
            Arrays.fill(dekRaw, (byte) 0);
        }
    }

    /** Unwrap a DEK previously produced by {@link #generateWrappedDek()}. */
    public SecretKey unwrapDek(byte[] wrappedDek) {
        byte[] dekRaw = decrypt(wrappedDek, masterKey);
        try {
            return new SecretKeySpec(dekRaw, "AES");
        } finally {
            Arrays.fill(dekRaw, (byte) 0);
        }
    }

    // --- Field encryption with a DEK ----------------------------------------------

    public byte[] encryptWithDek(String plaintext, SecretKey dek) {
        return encrypt(plaintext.getBytes(StandardCharsets.UTF_8), dek);
    }

    public String decryptWithDek(byte[] ciphertext, SecretKey dek) {
        return new String(decrypt(ciphertext, dek), StandardCharsets.UTF_8);
    }

    // --- Core AES-256-GCM ---------------------------------------------------------

    private byte[] encrypt(byte[] plaintext, SecretKey key) {
        byte[] iv = new byte[GCM_IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(1 + iv.length + ciphertext.length)
                    .put(FORMAT_VERSION)
                    .put(iv)
                    .put(ciphertext)
                    .array();
        } catch (GeneralSecurityException e) {
            throw new EncryptionException("Encryption failed", e);
        }
    }

    private byte[] decrypt(byte[] packaged, SecretKey key) {
        if (packaged == null || packaged.length < 1 + GCM_IV_BYTES) {
            throw new EncryptionException("Ciphertext is too short or null");
        }
        ByteBuffer buffer = ByteBuffer.wrap(packaged);
        byte version = buffer.get();
        if (version != FORMAT_VERSION) {
            throw new EncryptionException("Unsupported ciphertext version: " + version);
        }
        byte[] iv = new byte[GCM_IV_BYTES];
        buffer.get(iv);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new EncryptionException("Decryption failed (corrupt data or wrong key)", e);
        }
    }
}
