package com.rota.consumers.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Invite token helpers — same posture as refresh/verification tokens: the raw value is
 * {@code {tenantId}.{secret}} (tenant prefix lets the accept endpoint bind the RLS scope),
 * and only the SHA-256 hex of the FULL raw value is ever stored.
 */
final class InviteTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private InviteTokens() {
    }

    static String newRawToken(UUID tenantId) {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        return tenantId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** @throws InvalidInvitationTokenException if the value has no parseable tenant prefix */
    static UUID parseTenantId(String rawToken) {
        int dot = rawToken.indexOf('.');
        if (dot <= 0) {
            throw new InvalidInvitationTokenException();
        }
        try {
            return UUID.fromString(rawToken.substring(0, dot));
        } catch (IllegalArgumentException e) {
            throw new InvalidInvitationTokenException();
        }
    }
}
