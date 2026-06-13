-- Rota V011 — Phase 2 content tables: documents, versions, categories, endpoints
-- (+ parameters / request bodies / responses) and per-version environments (plan §9.2).
--
-- Every table carries tenant_id (CLAUDE.md security rule: no exceptions, even where it is
-- derivable through the parent) so RLS can be enforced directly on each table (V012).
-- Ids are application-assigned UUIDs (same pattern as tenants/users: the id must be known
-- before INSERT so the RLS WITH CHECK can be satisfied within the entity lifecycle).

CREATE TABLE documents (
    id                        UUID PRIMARY KEY,
    tenant_id                 UUID NOT NULL REFERENCES tenants(id),
    slug                      TEXT NOT NULL,
    name                      TEXT NOT NULL,
    description               TEXT,
    visibility                TEXT NOT NULL DEFAULT 'private'
                              CHECK (visibility IN ('public', 'unlisted', 'private')),
    unlisted_access_code_hash TEXT,
    -- FK to document_versions added below (the two tables reference each other).
    current_version_id        UUID,
    branding                  JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by                UUID NOT NULL REFERENCES users(id),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at              TIMESTAMPTZ,
    CONSTRAINT documents_slug_unique_per_tenant UNIQUE (tenant_id, slug)
);

CREATE TABLE document_versions (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL REFERENCES tenants(id),
    document_id   UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    version_label TEXT NOT NULL,
    status        TEXT NOT NULL DEFAULT 'draft'
                  CHECK (status IN ('draft', 'published', 'archived')),
    changelog_md  TEXT,
    auto_diff_json JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ,
    CONSTRAINT document_versions_label_unique UNIQUE (document_id, version_label)
);

ALTER TABLE documents
    ADD CONSTRAINT documents_current_version_fkey
    FOREIGN KEY (current_version_id) REFERENCES document_versions(id) ON DELETE SET NULL;

CREATE TABLE categories (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    document_version_id UUID NOT NULL REFERENCES document_versions(id) ON DELETE CASCADE,
    name                TEXT NOT NULL,
    description         TEXT,
    sort_order          INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE endpoints (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    document_version_id UUID NOT NULL REFERENCES document_versions(id) ON DELETE CASCADE,
    category_id         UUID REFERENCES categories(id) ON DELETE SET NULL,
    method              TEXT NOT NULL
                        CHECK (method IN ('GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS')),
    path                TEXT NOT NULL,
    summary             TEXT,
    description_md      TEXT,
    auth_type           TEXT NOT NULL DEFAULT 'none'
                        CHECK (auth_type IN ('none', 'bearer', 'api_key', 'basic', 'oauth2')),
    auth_config         JSONB,
    sort_order          INTEGER NOT NULL DEFAULT 0,
    mock_enabled        BOOLEAN NOT NULL DEFAULT false,
    deprecated          BOOLEAN NOT NULL DEFAULT false,
    created_by          UUID NOT NULL REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- §9.3: an operation is unique within a version.
    CONSTRAINT endpoints_method_path_unique UNIQUE (document_version_id, method, path)
);

CREATE TABLE endpoint_parameters (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL REFERENCES tenants(id),
    endpoint_id   UUID NOT NULL REFERENCES endpoints(id) ON DELETE CASCADE,
    name          TEXT NOT NULL,
    location      TEXT NOT NULL CHECK (location IN ('path', 'query', 'header', 'cookie')),
    data_type     TEXT NOT NULL DEFAULT 'string',
    required      BOOLEAN NOT NULL DEFAULT false,
    description   TEXT,
    default_value TEXT,
    example       TEXT,
    sort_order    INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE endpoint_request_bodies (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL REFERENCES tenants(id),
    endpoint_id  UUID NOT NULL REFERENCES endpoints(id) ON DELETE CASCADE,
    content_type TEXT NOT NULL DEFAULT 'application/json',
    schema_json  JSONB,
    example_json JSONB,
    CONSTRAINT endpoint_request_bodies_content_type_unique UNIQUE (endpoint_id, content_type)
);

CREATE TABLE endpoint_responses (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL REFERENCES tenants(id),
    endpoint_id  UUID NOT NULL REFERENCES endpoints(id) ON DELETE CASCADE,
    status_code  INTEGER NOT NULL CHECK (status_code BETWEEN 100 AND 599),
    description  TEXT,
    content_type TEXT NOT NULL DEFAULT 'application/json',
    schema_json  JSONB,
    example_json JSONB
);

CREATE TABLE environments (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    document_version_id UUID NOT NULL REFERENCES document_versions(id) ON DELETE CASCADE,
    name                TEXT NOT NULL,
    base_url            TEXT NOT NULL,
    is_production_warn  BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT environments_name_unique UNIQUE (document_version_id, name)
);

-- §9.3: composite tenant-first indexes speed up RLS-scoped queries; FK-side indexes
-- support the cascades and the common "children of X" lookups.
CREATE INDEX idx_document_versions_tenant_doc ON document_versions (tenant_id, document_id);
CREATE INDEX idx_categories_tenant_version    ON categories (tenant_id, document_version_id, sort_order);
CREATE INDEX idx_endpoints_tenant_version     ON endpoints (tenant_id, document_version_id, sort_order);
CREATE INDEX idx_endpoints_category           ON endpoints (category_id);
CREATE INDEX idx_endpoint_parameters_endpoint ON endpoint_parameters (endpoint_id, sort_order);
CREATE INDEX idx_endpoint_request_bodies_endpoint ON endpoint_request_bodies (endpoint_id);
CREATE INDEX idx_endpoint_responses_endpoint  ON endpoint_responses (endpoint_id, status_code);
CREATE INDEX idx_environments_version         ON environments (document_version_id);
