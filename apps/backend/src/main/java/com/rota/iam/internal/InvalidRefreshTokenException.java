package com.rota.iam.internal;

/** Refresh token missing, malformed, expired, revoked, or unknown. Mapped to HTTP 401. */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Invalid or expired refresh token");
    }
}
