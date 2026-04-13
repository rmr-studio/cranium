-- =====================================================
-- CONNECTOR TABLE MAPPINGS
-- =====================================================
-- Phase 3 plan 03-01 (PG-02 / MAP-02).
--
-- One row per (workspace, connection, source-table). Captures the user's
-- table-level mapping configuration: LifecycleDomain + SemanticGroup
-- selectors, the generated EntityType id once Saved, the schema hash used
-- for drift detection, and the `published` flag that gates Phase 4 sync.
--
-- Declarative — not incremental. Edit this file in place when shape
-- changes. No migration scripts.

CREATE TABLE IF NOT EXISTS connector_table_mappings (
    id                    UUID PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
    workspace_id          UUID NOT NULL,
    connection_id         UUID NOT NULL,
    table_name            VARCHAR(255) NOT NULL,
    lifecycle_domain      VARCHAR(50) NOT NULL DEFAULT 'UNCATEGORIZED',
    semantic_group        VARCHAR(50) NOT NULL DEFAULT 'UNCATEGORIZED',
    entity_type_id        UUID,
    schema_hash           VARCHAR(128) NOT NULL,
    last_introspected_at  TIMESTAMPTZ NOT NULL,
    published             BOOLEAN NOT NULL DEFAULT FALSE,

    -- audit cols (match AuditableEntity)
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ,
    created_by            UUID,
    updated_by            UUID,

    -- soft-delete cols (match AuditableSoftDeletableEntity / SoftDeletable)
    deleted               BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at            TIMESTAMPTZ,

    CONSTRAINT uq_connector_table_mappings_ws_conn_table
        UNIQUE (workspace_id, connection_id, table_name)
);
