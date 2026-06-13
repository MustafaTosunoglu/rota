-- Rota V012 — grants + Row-Level Security for the Phase 2 content tables (V011).
-- Same pattern as V004/V006: ENABLE + FORCE, single tenant-isolation policy per table,
-- GUC read with NULLIF(...) so a cleared-but-set-to-'' setting never crashes the cast.
-- rota_app (runtime) gets full CRUD here: unlike auth tables, content is user-deletable.

GRANT SELECT, INSERT, UPDATE, DELETE ON documents,
                                       document_versions,
                                       categories,
                                       endpoints,
                                       endpoint_parameters,
                                       endpoint_request_bodies,
                                       endpoint_responses,
                                       environments TO rota_app;

GRANT SELECT, INSERT, UPDATE, DELETE ON documents,
                                       document_versions,
                                       categories,
                                       endpoints,
                                       endpoint_parameters,
                                       endpoint_request_bodies,
                                       endpoint_responses,
                                       environments TO rota_admin;

DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'documents',
        'document_versions',
        'categories',
        'endpoints',
        'endpoint_parameters',
        'endpoint_request_bodies',
        'endpoint_responses',
        'environments'
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
