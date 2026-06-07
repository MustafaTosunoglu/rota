package com.rota.iam.internal;

/** Wrong email/password at login. Mapped to HTTP 401. Message kept generic (no user enumeration). */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
