-- Rota V007 — opaque refresh tokens (plan §8.3): long-lived, stored HASHED, rotated on use.
--
-- The raw token handed to the client is {tenant_id}.{secret}; only its SHA-256 hash is
-- stored here. The tenant_id prefix lets the refresh endpoint scope its RLS lookup without
-- a privileged path (it is not a secret; security rests on the hashed secret matching).

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  TEXT NOT NULL UNIQUE,
    issued_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    replaced_by UUID
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

GRANT SELECT, INSERT, UPDATE ON refresh_tokens TO rota_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON refresh_tokens TO rota_admin;

ALTER TABLE refresh_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_tokens FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_refresh_tokens ON refresh_tokens
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
