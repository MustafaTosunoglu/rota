package com.rota.common.encryption;

/** Thrown when an encryption / decryption operation fails (including auth-tag mismatch). */
public class EncryptionException extends RuntimeException {

    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
    }

    public EncryptionException(String message) {
        super(message);
    }
}
