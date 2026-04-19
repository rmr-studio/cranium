-- =====================================================
-- CONNECTOR FIELD MAPPINGS
-- =====================================================
-- Phase 3 plan 03-01 (PG-05 / MAP-08).
--
-- One row per (workspace, connection, source-table, source-column).
-- Captures the per-column mapping state that drives Phase 4 sync
-- (isSyncCursor, stale), Phase 5 identity resolution (isIdentifier), and
-- the Phase 7 mapping UI.
--
-- The three flag columns — is_identifier, is_sync_cursor, is_primary_key —
-- are conceptually independent. Identifier = cross-source match key,
-- sync cursor = polling-cursor column, primary key = intra-source upsert
-- fallback. They can reference the same physical column.
--
-- Declarative — edit in place.

CREATE TABLE IF NOT EXISTS connector_field_mappings (
    id                    UUID PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
    workspace_id          UUID NOT NULL,
    connection_id         UUID NOT NULL,
    table_name            VARCHAR(255) NOT NULL,
    column_name           VARCHAR(255) NOT NULL,
    pg_data_type          VARCHAR(255) NOT NULL,
    nullable              BOOLEAN NOT NULL,
    is_primary_key        BOOLEAN NOT NULL DEFAULT FALSE,
    is_foreign_key        BOOLEAN NOT NULL DEFAULT FALSE,
    fk_target_table       VARCHAR(255),
    fk_target_column      VARCHAR(255),
    attribute_name        VARCHAR(255) NOT NULL,
    schema_type           VARCHAR(50) NOT NULL,
    is_identifier         BOOLEAN NOT NULL DEFAULT FALSE,
    is_sync_cursor        BOOLEAN NOT NULL DEFAULT FALSE,
    is_mapped             BOOLEAN NOT NULL DEFAULT FALSE,
    stale                 BOOLEAN NOT NULL DEFAULT FALSE,

    -- audit cols (match AuditableEntity)
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ,
    created_by            UUID,
    updated_by            UUID,

    -- soft-delete cols (match AuditableSoftDeletableEntity / SoftDeletable)
    deleted               BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at            TIMESTAMPTZ,

    CONSTRAINT uq_connector_field_mappings_ws_conn_table_col
        UNIQUE (workspace_id, connection_id, table_name, column_name)
);

-- Enforce a single active sync cursor per (workspace, connection, table).
-- Read path (DataConnectorSchemaInferenceService) uses firstOrNull and assumes
-- singularity. Partial unique index prevents concurrent-save nondeterminism.
CREATE UNIQUE INDEX IF NOT EXISTS ux_connector_field_mappings_one_cursor
    ON connector_field_mappings (workspace_id, connection_id, table_name)
    WHERE is_sync_cursor = TRUE;
