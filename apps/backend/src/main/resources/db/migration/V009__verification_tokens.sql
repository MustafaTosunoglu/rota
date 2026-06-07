-- Rota V009 — single-use verification tokens for email verification and password reset.
--
-- Same posture as refresh_tokens (V007): the raw token handed to the user is
-- {tenant_id}.{secret} and ONLY its SHA-256 hash is stored. The tenant_id prefix lets the
-- consume endpoints scope their RLS lookup without a privileged path; security rests on the
-- hashed secret matching. `purpose` distinguishes the two flows in one table.

CREATE TABLE verification_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    purpose     TEXT NOT NULL CHECK (purpose IN ('email_verify', 'password_reset')),
    token_hash  TEXT NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);

CREATE INDEX idx_verification_tokens_user ON verification_tokens (user_id);

GRANT SELECT, INSERT, UPDATE ON verification_tokens TO rota_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON verification_tokens TO rota_admin;

ALTER TABLE verification_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE verification_tokens FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_verification_tokens ON verification_tokens
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
