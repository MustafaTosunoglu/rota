package com.rota.audit.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rota.audit.api.AuditEvent;
import com.rota.audit.api.AuditService;
import com.rota.common.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * Writes audit events into the append-only, hash-chained {@code audit.events} log.
 *
 * <p>Each event is written immediately on the current transaction's (RLS-scoped) connection — in
 * particular this runs inside Hibernate's commit-time flush when triggered by the entity listener
 * (a {@code beforeCommit} hook would run BEFORE that flush, when no entity events exist yet). A
 * {@code pg_advisory_xact_lock} per tenant prevents concurrent transactions from forking a chain;
 * the previous hash is read back via the monotonic {@code seq} column (our own prior inserts in the
 * same transaction are visible), so successive events chain deterministically.
 */
@Service
public class AuditServiceImpl implements AuditService {

    private static final String INSERT_SQL = """
            INSERT INTO audit.events
                (id, tenant_id, actor_user_id, actor_ip, occurred_at, entity_type, entity_id,
                 action, before_state, after_state, correlation_id, prev_hash, record_hash)
            VALUES (?, ?, ?, CAST(? AS inet), ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper canonicalMapper;
    private final CurrentActor currentActor;
    private final TransactionTemplate transactionTemplate;

    public AuditServiceImpl(JdbcTemplate jdbcTemplate,
                            ObjectMapper objectMapper,
                            CurrentActor currentActor,
                            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        // Key-sorted JSON so the hashed state is reproducible from the stored jsonb (which does
        // not preserve key order) when the chain is independently re-verified.
        this.canonicalMapper = objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.currentActor = currentActor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void record(AuditEvent event) {
        AuditEvent resolved = resolve(event);
        if (resolved == null) {
            return; // no tenant in scope → cannot write an RLS-scoped audit row
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            writeOne(resolved);
        } else {
            // No surrounding transaction: write in its own (caller must have a tenant bound).
            transactionTemplate.executeWithoutResult(status -> writeOne(resolved));
        }
    }

    private AuditEvent resolve(AuditEvent event) {
        UUID tenantId = event.tenantId() != null ? event.tenantId() : TenantContext.getTenantId();
        if (tenantId == null) {
            return null;
        }
        UUID actor = event.actorUserId() != null ? event.actorUserId() : currentActor.userId();
        String ip = event.actorIp() != null ? event.actorIp() : currentActor.ip();
        // entity_id is NOT NULL; a security event with no explicit subject targets the actor.
        UUID entityId = event.entityId() != null ? event.entityId() : actor;
        if (entityId == null) {
            return null; // nothing to anchor the row to (e.g. unauthenticated, no subject) → skip
        }
        return new AuditEvent(tenantId, actor, ip, event.entityType(), entityId,
                event.action(), event.beforeState(), event.afterState(), event.correlationId());
    }

    private void writeOne(AuditEvent event) {
        UUID tenantId = event.tenantId();
        // Serialise this tenant's appends within the transaction so the chain cannot fork.
        jdbcTemplate.queryForObject("SELECT pg_advisory_xact_lock(?)", (rs, n) -> 1,
                tenantId.getMostSignificantBits());

        String prevHash = latestHash(tenantId);
        OffsetDateTime occurredAt = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
        String beforeJson = toJson(event.beforeState());
        String afterJson = toJson(event.afterState());
        String recordHash = HashChain.compute(tenantId, event.actorUserId(), event.actorIp(),
                occurredAt, event.entityType(), event.entityId(), event.action(),
                beforeJson, afterJson, event.correlationId(), prevHash);

        jdbcTemplate.update(INSERT_SQL,
                UUID.randomUUID(), tenantId, event.actorUserId(), event.actorIp(), occurredAt,
                event.entityType(), event.entityId(), event.action(), beforeJson, afterJson,
                event.correlationId(), prevHash, recordHash);
    }

    /** The most recent record_hash for the tenant (our own in-transaction inserts are visible). */
    private String latestHash(UUID tenantId) {
        return jdbcTemplate.query(
                "SELECT record_hash FROM audit.events WHERE tenant_id = ? ORDER BY seq DESC LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null,
                tenantId);
    }

    private String toJson(Map<String, Object> state) {
        if (state == null) {
            return null;
        }
        try {
            return canonicalMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise audit state", e);
        }
    }
}
