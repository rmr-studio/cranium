-- =====================================================
-- ENTITY INDEXES
-- =====================================================

-- Entity Types Indexes
DROP INDEX IF EXISTS idx_entity_types_workspace;
CREATE INDEX IF NOT EXISTS idx_entity_types_workspace
    ON entity_types (workspace_id)
    WHERE deleted = FALSE;


DROP INDEX IF EXISTS idx_entity_types_workspace_key;
CREATE INDEX IF NOT EXISTS idx_entities_workspace_type
    ON entities (workspace_id, type_id)
    WHERE deleted = FALSE;

-- Lifecycle Domain Index (for Knowledge API filtering by lifecycle domain)
DROP INDEX IF EXISTS idx_entity_types_lifecycle_domain;
CREATE INDEX IF NOT EXISTS idx_entity_types_lifecycle_domain
    ON entity_types (workspace_id, lifecycle_domain)
    WHERE deleted = FALSE;

-- Relationship Definitions Indexes
DROP INDEX IF EXISTS idx_rel_def_workspace_source;
CREATE INDEX IF NOT EXISTS idx_rel_def_workspace_source
    ON relationship_definitions (workspace_id, source_entity_type_id)
    WHERE deleted = FALSE;

-- Relationship Target Rules Indexes
DROP INDEX IF EXISTS idx_target_rule_def;
CREATE INDEX IF NOT EXISTS idx_target_rule_def
    ON relationship_target_rules (relationship_definition_id);

DROP INDEX IF EXISTS idx_target_rule_type;
CREATE INDEX IF NOT EXISTS idx_target_rule_type
    ON relationship_target_rules (target_entity_type_id);

-- Entity Relationships Indexes
DROP INDEX IF EXISTS idx_entity_relationships_workspace_source;
CREATE INDEX IF NOT EXISTS idx_entity_relationships_workspace_source
    ON entity_relationships (workspace_id, source_entity_id)
    WHERE deleted = FALSE AND deleted_at IS NULL;

DROP INDEX IF EXISTS idx_entity_relationships_workspace_target;
CREATE INDEX IF NOT EXISTS idx_entity_relationships_workspace_target
    ON entity_relationships (workspace_id, target_entity_id)
    WHERE deleted = FALSE AND deleted_at IS NULL;

DROP INDEX IF EXISTS idx_entity_relationships_definition;
CREATE INDEX IF NOT EXISTS idx_entity_relationships_definition
    ON entity_relationships (relationship_definition_id)
    WHERE deleted = FALSE AND deleted_at IS NULL;

-- Entity Integration Source Indexes
CREATE INDEX IF NOT EXISTS idx_entities_source_integration
    ON entities (source_integration_id)
    WHERE source_integration_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_entities_source_external_id
    ON entities (source_external_id)
    WHERE source_external_id IS NOT NULL;

-- Integration Dedup Index
-- Unique partial index for integration entity deduplication.
-- Enforces one entity per (workspace, integration, external-id) tuple
-- across non-deleted rows only.
CREATE UNIQUE INDEX IF NOT EXISTS idx_entities_integration_dedup
    ON entities (workspace_id, source_integration_id, source_external_id)
    WHERE deleted = false AND source_external_id IS NOT NULL;

-- =====================================================
-- ENTITY ATTRIBUTE INDEXES
-- =====================================================

-- Primary lookup: attributes for a given entity
CREATE UNIQUE INDEX IF NOT EXISTS idx_entity_attributes_entity_id
    ON public.entity_attributes (entity_id, attribute_id) WHERE deleted = false;

-- Filter by attribute across a type
CREATE INDEX IF NOT EXISTS idx_entity_attributes_type_attribute
    ON public.entity_attributes (attribute_id, type_id) WHERE deleted = false;

-- Value lookup for equality/range filters
CREATE INDEX IF NOT EXISTS idx_entity_attributes_value_lookup
    ON public.entity_attributes (attribute_id, type_id, value) WHERE deleted = false;

-- Workspace-scoped queries
CREATE INDEX IF NOT EXISTS idx_entity_attributes_workspace
    ON public.entity_attributes (workspace_id) WHERE deleted = false;
