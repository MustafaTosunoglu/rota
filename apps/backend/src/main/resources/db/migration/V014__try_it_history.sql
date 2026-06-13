-- Rota V014 — Phase 5: Try It (proxy) execution history (plan §9.2 try_it_history).
-- Append-and-read log of proxy executions. tenant_id added (CLAUDE.md: every tenant table
-- has tenant_id + RLS, no exceptions) even though the plan's column sketch omitted it.
-- Summaries are JSONB with sensitive headers already redacted by the application.

CREATE TABLE try_it_history (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL REFERENCES tenants(id),
    user_id               UUID NOT NULL REFERENCES users(id),
    endpoint_id           UUID NOT NULL REFERENCES endpoints(id) ON DELETE CASCADE,
    executed_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    method                TEXT NOT NULL,
    url                   TEXT NOT NULL,
    status_code           INTEGER,
    latency_ms            BIGINT,
    request_summary_json  JSONB,
    response_summary_json JSONB
);

CREATE INDEX idx_try_it_history_tenant_endpoint
    ON try_it_history (tenant_id, endpoint_id, executed_at DESC);

GRANT SELECT, INSERT, DELETE ON try_it_history TO rota_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON try_it_history TO rota_admin;

ALTER TABLE try_it_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE try_it_history FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_try_it_history ON try_it_history
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
