-- =====================================================
-- INTEGRATION DEFINITIONS INDEXES
-- =====================================================

CREATE INDEX idx_integration_definitions_category ON integration_definitions(category);
CREATE INDEX idx_integration_definitions_stale ON integration_definitions(stale) WHERE stale = false;
-- Note: slug index is provided by the UNIQUE constraint, no explicit index needed

-- =====================================================
-- INTEGRATION CONNECTIONS INDEXES
-- =====================================================

-- Note: workspace_id index is provided as the leading column of UNIQUE(workspace_id, integration_id)
CREATE INDEX idx_integration_connections_status ON integration_connections(status);
CREATE INDEX idx_integration_connections_integration ON integration_connections(integration_id);

-- =====================================================
-- INTEGRATION SYNC STATE INDEXES
-- =====================================================

-- Two partial unique indexes replace a single UNIQUE(...sync_key) because
-- Postgres treats NULLs as distinct, which would permit duplicate rows with
-- sync_key IS NULL and break deterministic single-result lookups.
CREATE UNIQUE INDEX IF NOT EXISTS uq_sync_state_conn_type_null_key
    ON integration_sync_state (integration_connection_id, entity_type_id)
    WHERE sync_key IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_sync_state_conn_type_sync_key
    ON integration_sync_state (integration_connection_id, entity_type_id, sync_key)
    WHERE sync_key IS NOT NULL;

-- =====================================================
-- PROJECTION RULE INDEXES
-- =====================================================

-- Lookup projection rules by source entity type during projection step
CREATE INDEX IF NOT EXISTS idx_projection_rules_source
    ON entity_type_projection_rules (source_entity_type_id);

-- Workspace-scoped projection rule queries
CREATE INDEX IF NOT EXISTS idx_projection_rules_workspace
    ON entity_type_projection_rules (workspace_id)
    WHERE workspace_id IS NOT NULL;
