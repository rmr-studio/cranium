-- =====================================================
-- MANIFEST CATALOG INDEXES
-- =====================================================

CREATE INDEX IF NOT EXISTS idx_manifest_catalog_type ON manifest_catalog(manifest_type);

-- =====================================================
-- CATALOG ENTITY TYPES INDEXES
-- =====================================================

CREATE INDEX IF NOT EXISTS idx_catalog_entity_types_manifest ON catalog_entity_types(manifest_id);
CREATE INDEX IF NOT EXISTS idx_catalog_entity_types_surface_role ON catalog_entity_types(surface_role);

-- =====================================================
-- CATALOG RELATIONSHIPS INDEXES
-- =====================================================

CREATE INDEX IF NOT EXISTS idx_catalog_relationships_manifest ON catalog_relationships(manifest_id);
CREATE INDEX IF NOT EXISTS idx_catalog_relationships_source_key ON catalog_relationships(source_entity_type_key);

-- =====================================================
-- CATALOG RELATIONSHIP TARGET RULES INDEXES
-- =====================================================

CREATE INDEX IF NOT EXISTS idx_catalog_rel_target_rules_relationship ON catalog_relationship_target_rules(catalog_relationship_id);

-- =====================================================
-- CATALOG FIELD MAPPINGS INDEXES
-- =====================================================

CREATE INDEX IF NOT EXISTS idx_catalog_field_mappings_manifest ON catalog_field_mappings(manifest_id);

-- =====================================================
-- CATALOG SEMANTIC METADATA INDEXES
-- =====================================================

CREATE INDEX IF NOT EXISTS idx_catalog_semantic_metadata_entity_type ON catalog_semantic_metadata(catalog_entity_type_id);
CREATE INDEX IF NOT EXISTS idx_catalog_semantic_metadata_target ON catalog_semantic_metadata(target_type, target_id);
