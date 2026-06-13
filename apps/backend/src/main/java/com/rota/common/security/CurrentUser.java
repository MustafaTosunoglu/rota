package com.rota.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

/** Reads the authenticated user's id (JWT {@code sub}) from the security context. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID idOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            try {
                return UUID.fromString(jwt.getName());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    public static UUID requireId() {
        UUID id = idOrNull();
        if (id == null) {
            throw new IllegalStateException("No authenticated user in the security context");
        }
        return id;
    }
}
