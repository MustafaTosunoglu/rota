-- Rota V004 — table privileges + Row-Level Security policies (plan §8.1).
--
-- RLS is only effective for a role that is NEITHER a superuser NOR has BYPASSRLS.
-- The runtime role rota_app satisfies this; rota_admin (BYPASSRLS) deliberately
-- bypasses RLS for system jobs. FORCE ROW LEVEL SECURITY additionally subjects the
-- table owner to the policies (defense in depth) — note that superusers still
-- bypass RLS regardless, which is why the app must NOT connect as a superuser.
--
-- Every policy keys on the GUC `app.current_tenant_id`, set per-connection by the
-- application (Phase 1B). current_setting(..., true) returns NULL when unset, so a
-- request with no tenant context sees ZERO rows rather than erroring.

-- ---------------------------------------------------------------------------
-- Table privileges (RLS gates row visibility on TOP of these grants)
-- ---------------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO rota_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO rota_admin;
-- Audit is append-only for the app role; full access for the system role.
GRANT SELECT, INSERT ON audit.events TO rota_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON audit.events TO rota_admin;

-- ---------------------------------------------------------------------------
-- tenants — a tenant may only see/modify its own row
-- ---------------------------------------------------------------------------
ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenants FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_tenants ON tenants
    USING (id = current_setting('app.current_tenant_id', true)::uuid)
    WITH CHECK (id = current_setting('app.current_tenant_id', true)::uuid);

-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_users ON users
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

-- ---------------------------------------------------------------------------
-- roles
-- ---------------------------------------------------------------------------
ALTER TABLE roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE roles FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_roles ON roles
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

-- ---------------------------------------------------------------------------
-- user_roles
-- ---------------------------------------------------------------------------
ALTER TABLE user_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_roles FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_user_roles ON user_roles
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

-- ---------------------------------------------------------------------------
-- audit.events
-- ---------------------------------------------------------------------------
ALTER TABLE audit.events ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit.events FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_audit_events ON audit.events
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id', true)::uuid);
