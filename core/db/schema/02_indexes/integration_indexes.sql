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
-- PROJECTION RULE INDEXES
-- =====================================================

-- Lookup projection rules by source entity type during projection step
CREATE INDEX IF NOT EXISTS idx_projection_rules_source
    ON entity_type_projection_rules (source_entity_type_id);

-- Workspace-scoped projection rule queries
CREATE INDEX IF NOT EXISTS idx_projection_rules_workspace
    ON entity_type_projection_rules (workspace_id)
    WHERE workspace_id IS NOT NULL;
