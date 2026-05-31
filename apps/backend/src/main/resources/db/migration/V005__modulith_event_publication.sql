-- Rota V005 — Spring Modulith event publication registry table.
--
-- Created here by Flyway (privileged role) instead of Modulith's runtime auto-init,
-- because the runtime role rota_app has no CREATE privilege on schema public (PG15+).
-- Schema matches what Spring Modulith's JDBC event repository expects. No tenant_id /
-- RLS: this is infrastructure state, not tenant data.
--
-- IF NOT EXISTS guards the case where an earlier Phase 0 run already auto-created the
-- table on the dev database (adopted via baseline-on-migrate).

CREATE TABLE IF NOT EXISTS event_publication (
    id               UUID NOT NULL,
    listener_id      TEXT NOT NULL,
    event_type       TEXT NOT NULL,
    serialized_event TEXT NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date  TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx
    ON event_publication USING hash (serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx
    ON event_publication (completion_date);

GRANT SELECT, INSERT, UPDATE, DELETE ON event_publication TO rota_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON event_publication TO rota_admin;
