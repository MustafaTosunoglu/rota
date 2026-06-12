package com.rota.audit.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Computes {@code record_hash = SHA-256(canonical fields + prev_hash)} (plan §8.6). The canonical
 * form is a fixed-order, {@code |}-separated string so the chain can be re-verified independently.
 */
final class HashChain {

    static String compute(UUID tenantId, UUID actorUserId, String actorIp, OffsetDateTime occurredAt,
                          String entityType, UUID entityId, String action,
                          String beforeJson, String afterJson, UUID correlationId, String prevHash) {
        String canonical = String.join("|",
                str(tenantId),
                str(actorUserId),
                nz(actorIp),
                occurredAt.toInstant().toString(),
                nz(entityType),
                str(entityId),
                nz(action),
                nz(beforeJson),
                nz(afterJson),
                str(correlationId),
                nz(prevHash));
        return sha256Hex(canonical);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String str(UUID value) {
        return value == null ? "" : value.toString();
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private HashChain() {
    }
}
