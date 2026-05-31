-- Rota V002 — core IAM + tenancy tables.
--
-- Every table that holds tenant data carries a tenant_id (CLAUDE.md security rule).
-- RLS policies for these tables are added in V004 so the schema and the isolation
-- rules read as two distinct, reviewable steps.
--
-- Note: `plan` / `role name` use TEXT + CHECK rather than native ENUM types. CHECK
-- constraints are far easier to evolve with forward-only migrations than ENUMs
-- (which need ALTER TYPE ... ADD VALUE and cannot remove values). Flagged for review.

CREATE TABLE tenants (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug          TEXT NOT NULL UNIQUE,                 -- subdomain: rotadoc.com/{slug}
    name          TEXT NOT NULL,
    plan          TEXT NOT NULL DEFAULT 'free'
                       CHECK (plan IN ('free', 'pro', 'business', 'enterprise')),
    encrypted_dek BYTEA,                                -- per-tenant DEK (envelope), set in Phase 1C/1D
    suspended_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email              TEXT NOT NULL,
    email_verified     BOOLEAN NOT NULL DEFAULT false,
    password_hash      TEXT,                            -- Argon2id, set in Phase 1D
    display_name       TEXT,
    locale             TEXT NOT NULL DEFAULT 'tr',
    mfa_enabled        BOOLEAN NOT NULL DEFAULT false,
    mfa_secret         BYTEA,                           -- encrypted (Phase 1C+)
    refresh_token_hash TEXT,                            -- hashed, not encrypted (plan §8.2)
    last_login_at      TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Email is globally unique in v1 (plan §9.2). Multi-tenant identities are a
    -- deferred concern handled by a future linking table.
    CONSTRAINT users_email_unique UNIQUE (email)
);
CREATE INDEX idx_users_tenant ON users (tenant_id);

CREATE TABLE roles (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name       TEXT NOT NULL CHECK (name IN ('owner', 'admin', 'editor', 'viewer')),
    system     BOOLEAN NOT NULL DEFAULT true,           -- system roles cannot be deleted
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT roles_tenant_name_unique UNIQUE (tenant_id, name)
);
CREATE INDEX idx_roles_tenant ON roles (tenant_id);

-- user_roles carries its own tenant_id (denormalized) so it gets a direct RLS
-- policy and a (tenant_id, ...) index, per CLAUDE.md "every tenant-data table".
CREATE TABLE user_roles (
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id    UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    tenant_id  UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX idx_user_roles_tenant ON user_roles (tenant_id);
CREATE INDEX idx_user_roles_role ON user_roles (role_id);
