-- Rota V013 — Phase 2E: consumer groups, email-based membership (invitation flow) and
-- per-document / per-endpoint access grants (plan §9.2, task 2.5/2.6).
--
-- Membership is EMAIL-based (user decision): a member row is created at invite time with a
-- single-use hashed token; user_id is linked when the invite is accepted. Same token posture
-- as refresh/verification tokens: raw token is {tenant_id}.{secret}, only SHA-256 stored.

CREATE TABLE consumer_groups (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    name        TEXT NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT consumer_groups_name_unique UNIQUE (tenant_id, name)
);

CREATE TABLE consumer_group_members (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    group_id    UUID NOT NULL REFERENCES consumer_groups(id) ON DELETE CASCADE,
    email       TEXT NOT NULL,
    -- Linked when the invite is accepted; the accepting user may belong to ANOTHER tenant
    -- (external API consumer) — FK integrity checks are not subject to RLS.
    user_id     UUID REFERENCES users(id) ON DELETE SET NULL,
    status      TEXT NOT NULL DEFAULT 'invited' CHECK (status IN ('invited', 'accepted')),
    token_hash  TEXT UNIQUE,           -- single-use; cleared on accept
    invited_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    CONSTRAINT consumer_group_members_email_unique UNIQUE (group_id, email)
);
CREATE INDEX idx_consumer_group_members_group ON consumer_group_members (group_id);

CREATE TABLE consumer_group_document_access (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL REFERENCES tenants(id),
    group_id     UUID NOT NULL REFERENCES consumer_groups(id) ON DELETE CASCADE,
    document_id  UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    can_view     BOOLEAN NOT NULL DEFAULT true,
    can_try      BOOLEAN NOT NULL DEFAULT false,
    can_loadtest BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT consumer_group_document_access_unique UNIQUE (group_id, document_id)
);
CREATE INDEX idx_cg_document_access_group ON consumer_group_document_access (group_id);

-- Endpoint-level OVERRIDE of the document-level grant (plan §9.2).
CREATE TABLE consumer_group_endpoint_access (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL REFERENCES tenants(id),
    group_id     UUID NOT NULL REFERENCES consumer_groups(id) ON DELETE CASCADE,
    endpoint_id  UUID NOT NULL REFERENCES endpoints(id) ON DELETE CASCADE,
    can_view     BOOLEAN NOT NULL DEFAULT true,
    can_try      BOOLEAN NOT NULL DEFAULT false,
    can_loadtest BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT consumer_group_endpoint_access_unique UNIQUE (group_id, endpoint_id)
);
CREATE INDEX idx_cg_endpoint_access_group ON consumer_group_endpoint_access (group_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON consumer_groups,
                                       consumer_group_members,
                                       consumer_group_document_access,
                                       consumer_group_endpoint_access TO rota_app;

GRANT SELECT, INSERT, UPDATE, DELETE ON consumer_groups,
                                       consumer_group_members,
                                       consumer_group_document_access,
                                       consumer_group_endpoint_access TO rota_admin;

DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'consumer_groups',
        'consumer_group_members',
        'consumer_group_document_access',
        'consumer_group_endpoint_access'
    ] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format($p$
            CREATE POLICY tenant_isolation_%s ON %I
                USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
                WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
        $p$, t, t);
    END LOOP;
END $$;
