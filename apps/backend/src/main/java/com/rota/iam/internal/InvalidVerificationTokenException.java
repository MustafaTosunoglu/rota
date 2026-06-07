package com.rota.iam.internal;

/** Thrown when a verification / password-reset token is unknown, expired, already used, or malformed. */
public class InvalidVerificationTokenException extends RuntimeException {

    public InvalidVerificationTokenException() {
        super("This link is invalid or has expired. Please request a new one.");
    }
}
