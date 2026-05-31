-- Rota V006 — make RLS policies robust to an EMPTY-STRING tenant GUC.
--
-- A custom GUC (app.current_tenant_id) that is SET and later cleared on a POOLED
-- connection comes back as '' (empty string), not NULL. The V004 policies cast the raw
-- value with ''::uuid, which raises: invalid input syntax for type uuid: "".
--
-- NULLIF(current_setting(...), '') maps BOTH "never set" (NULL) and "reset to empty" ('')
-- to NULL, so a request with no tenant context matches zero rows (fail closed) instead of
-- erroring. Forward-only fix migration (plan §9.4): drop + recreate each policy.

DROP POLICY tenant_isolation_tenants ON tenants;
CREATE POLICY tenant_isolation_tenants ON tenants
    USING (id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

DROP POLICY tenant_isolation_users ON users;
CREATE POLICY tenant_isolation_users ON users
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

DROP POLICY tenant_isolation_roles ON roles;
CREATE POLICY tenant_isolation_roles ON roles
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

DROP POLICY tenant_isolation_user_roles ON user_roles;
CREATE POLICY tenant_isolation_user_roles ON user_roles
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

DROP POLICY tenant_isolation_audit_events ON audit.events;
CREATE POLICY tenant_isolation_audit_events ON audit.events
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
