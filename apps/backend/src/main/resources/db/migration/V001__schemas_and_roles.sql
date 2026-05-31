-- Rota V001 — schemas + application database roles + baseline schema grants.
--
-- Forward-only (plan §9.4). Role creation is guarded with IF NOT EXISTS because
-- Postgres roles are CLUSTER-global: a local DB reset can drop the database while
-- the role still exists. The guard keeps the migration re-runnable on dev resets
-- without violating forward-only (it never alters an already-applied migration).

CREATE SCHEMA IF NOT EXISTS audit;
CREATE SCHEMA IF NOT EXISTS analytics;

-- rota_app — the RUNTIME application role.
-- Deliberately NOT a superuser and WITHOUT BYPASSRLS, so Row-Level Security is
-- actually enforced for every query the app makes. The application starts
-- connecting as this role in Phase 1B (connection hook). Until then it exists so
-- the RLS leak test can prove isolation at the database level.
-- The password is supplied via a Flyway placeholder; it never appears in commited SQL.
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'rota_app') THEN
    CREATE ROLE rota_app LOGIN PASSWORD '${rotaAppPassword}';
  END IF;
END
$$;

-- rota_admin — privileged system role (BYPASSRLS) for maintenance / cross-tenant
-- background jobs ONLY. Never used to serve a tenant request.
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'rota_admin') THEN
    CREATE ROLE rota_admin LOGIN BYPASSRLS PASSWORD '${rotaAdminPassword}';
  END IF;
END
$$;

GRANT USAGE ON SCHEMA public, audit, analytics TO rota_app, rota_admin;
