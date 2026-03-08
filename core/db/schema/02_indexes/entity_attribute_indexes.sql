-- =====================================================
-- ENTITY ATTRIBUTE INDEXES
-- =====================================================

-- Primary lookup: attributes for a given entity
CREATE INDEX IF NOT EXISTS idx_entity_attributes_entity_id
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
