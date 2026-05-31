package com.rota.common.encryption;

import com.rota.common.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provisions and resolves per-tenant Data Encryption Keys (DEKs).
 *
 * <p>The wrapped DEK lives in {@code tenants.encrypted_dek}. Reads/writes go through the
 * normal RLS-scoped datasource, so a tenant only ever touches its OWN DEK row — the
 * {@link TenantContext} must be set to that tenant. Unwrapped DEKs are cached in memory
 * keyed by tenant id to avoid a KEK-unwrap on every field access.
 */
@Service
public class TenantDekService {

    private final JdbcTemplate jdbcTemplate;
    private final EncryptionService encryptionService;
    private final ConcurrentHashMap<UUID, SecretKey> dekCache = new ConcurrentHashMap<>();

    public TenantDekService(JdbcTemplate jdbcTemplate, EncryptionService encryptionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.encryptionService = encryptionService;
    }

    /**
     * Generate a DEK for the given tenant and persist it (wrapped) on its row. Called once
     * at tenant creation (Phase 1D). Requires the current tenant context to be {@code tenantId}
     * (RLS gates the UPDATE).
     */
    public void provisionDek(UUID tenantId) {
        byte[] wrapped = encryptionService.generateWrappedDek();
        int updated = jdbcTemplate.update(
                "UPDATE tenants SET encrypted_dek = ? WHERE id = ?", wrapped, tenantId);
        if (updated != 1) {
            throw new IllegalStateException(
                    "Could not provision DEK: tenant " + tenantId + " not visible/updatable in current context");
        }
        dekCache.remove(tenantId);
    }

    /** Resolve (and cache) the unwrapped DEK for an explicit tenant. */
    public SecretKey resolveDek(UUID tenantId) {
        return dekCache.computeIfAbsent(tenantId, id -> {
            byte[] wrapped = jdbcTemplate.queryForObject(
                    "SELECT encrypted_dek FROM tenants WHERE id = ?", byte[].class, id);
            if (wrapped == null) {
                throw new IllegalStateException("Tenant " + id + " has no provisioned DEK");
            }
            return encryptionService.unwrapDek(wrapped);
        });
    }

    /** Resolve the DEK for the tenant bound to the current thread. */
    public SecretKey resolveCurrentTenantDek() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant context bound; cannot resolve DEK");
        }
        return resolveDek(tenantId);
    }

    /** Drop a cached DEK (e.g. after key rotation). */
    public void evict(UUID tenantId) {
        dekCache.remove(tenantId);
    }
}
