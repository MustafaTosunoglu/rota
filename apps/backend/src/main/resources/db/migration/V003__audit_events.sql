-- Rota V003 — append-only domain audit log with hash-chain columns (plan §8.6).
--
-- This migration only creates the structure. The hash-chain COMPUTATION (prev_hash
-- linkage + record_hash = SHA-256 of the record) is implemented by the audit module
-- in Phase 1E. record_hash is NOT NULL: every inserted row must already carry its hash.

CREATE TABLE audit.events (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL,                 -- intentionally no FK: audit outlives tenant rows
    actor_user_id  UUID,
    actor_ip       INET,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    entity_type    TEXT NOT NULL,                 -- 'endpoint', 'document', 'user', ...
    entity_id      UUID NOT NULL,
    action         TEXT NOT NULL,                 -- 'create', 'update', 'delete', 'publish', ...
    before_state   JSONB,
    after_state    JSONB,
    correlation_id UUID,
    prev_hash      TEXT,                          -- previous record's record_hash
    record_hash    TEXT NOT NULL                  -- SHA-256(this record w/o record_hash + prev_hash)
);

CREATE INDEX idx_audit_events_tenant_time ON audit.events (tenant_id, occurred_at DESC);

-- Append-only enforcement at the database level: updates and deletes are silently
-- discarded so even a compromised application role cannot rewrite history.
CREATE RULE no_update_audit AS ON UPDATE TO audit.events DO INSTEAD NOTHING;
CREATE RULE no_delete_audit AS ON DELETE TO audit.events DO INSTEAD NOTHING;
