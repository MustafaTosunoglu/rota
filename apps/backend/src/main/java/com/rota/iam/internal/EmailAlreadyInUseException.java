package com.rota.iam.internal;

/** Raised when registration hits the global unique-email constraint. Mapped to HTTP 409. */
public class EmailAlreadyInUseException extends RuntimeException {

    public EmailAlreadyInUseException(String email) {
        super("Email already in use: " + email);
    }
}
