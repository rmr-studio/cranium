-- =====================================================
-- CONNECTOR MAPPINGS — INDEXES
-- =====================================================
-- Phase 3 plan 03-01. Indexes supporting derived-query access patterns
-- on connector_table_mappings and connector_field_mappings.
--
-- Workspace + connection indexes are the primary fan-in for the Phase 7
-- mapping UI and Phase 4 sync orchestration.

-- Table mappings
CREATE INDEX IF NOT EXISTS idx_connector_table_mappings_connection
    ON connector_table_mappings(connection_id) WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_connector_table_mappings_workspace
    ON connector_table_mappings(workspace_id) WHERE deleted = FALSE;

-- Field mappings
CREATE INDEX IF NOT EXISTS idx_connector_field_mappings_conn_table
    ON connector_field_mappings(connection_id, table_name) WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_connector_field_mappings_workspace
    ON connector_field_mappings(workspace_id) WHERE deleted = FALSE;
