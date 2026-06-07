package com.rota.common.security;

import java.time.Duration;

/**
 * Revocation list for still-valid access tokens (keyed by {@code jti}). Backed by Redis so a
 * logged-out short-lived token is rejected immediately instead of living out its TTL.
 */
public interface TokenBlacklist {

    /** Mark a token revoked for {@code ttl} (typically the token's remaining lifetime). */
    void blacklist(String jti, Duration ttl);

    boolean isBlacklisted(String jti);
}
