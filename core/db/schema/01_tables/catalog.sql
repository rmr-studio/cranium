-- =====================================================
-- MANIFEST CATALOG TABLE
-- =====================================================
-- Global catalog of loaded manifests (models, templates, integrations)
-- No workspace_id - global catalog like integration_definitions
-- No RLS - catalog is globally readable
-- No soft-delete - catalog entries are permanent

CREATE TABLE IF NOT EXISTS manifest_catalog (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    key VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    manifest_type VARCHAR(50) NOT NULL CHECK (manifest_type IN ('MODEL', 'TEMPLATE', 'INTEGRATION')),
    manifest_version VARCHAR(50),
    last_loaded_at TIMESTAMPTZ,
    stale BOOLEAN NOT NULL DEFAULT false,
    content_hash VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (key, manifest_type)
);

-- =====================================================
-- CATALOG ENTITY TYPES TABLE
-- =====================================================
-- Entity type definitions within a manifest
-- Each manifest can define multiple entity types

CREATE TABLE IF NOT EXISTS catalog_entity_types (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    manifest_id UUID NOT NULL REFERENCES manifest_catalog(id) ON DELETE CASCADE,
    key VARCHAR(255) NOT NULL,
    display_name_singular VARCHAR(255) NOT NULL,
    display_name_plural VARCHAR(255) NOT NULL,
    icon_type TEXT NOT NULL DEFAULT 'CIRCLE_DASHED',
    icon_colour TEXT NOT NULL DEFAULT 'NEUTRAL',
    semantic_group TEXT NOT NULL DEFAULT 'UNCATEGORIZED',
    identifier_key VARCHAR(255),
    readonly BOOLEAN NOT NULL DEFAULT FALSE,
    schema JSONB NOT NULL DEFAULT '{}'::jsonb,
    columns JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (manifest_id, key),
    CHECK (jsonb_typeof(schema) = 'object')
);

-- =====================================================
-- CATALOG RELATIONSHIPS TABLE
-- =====================================================
-- Relationship definitions within a manifest
-- Links source entity type keys to target rules

CREATE TABLE IF NOT EXISTS catalog_relationships (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    manifest_id UUID NOT NULL REFERENCES manifest_catalog(id) ON DELETE CASCADE,
    key VARCHAR(255) NOT NULL,
    source_entity_type_key VARCHAR(255) NOT NULL,
    name TEXT NOT NULL,
    icon_type TEXT NOT NULL DEFAULT 'LINK',
    icon_colour TEXT NOT NULL DEFAULT 'NEUTRAL',
    allow_polymorphic BOOLEAN NOT NULL DEFAULT FALSE,
    cardinality_default TEXT NOT NULL CHECK (cardinality_default IN ('ONE_TO_ONE', 'ONE_TO_MANY', 'MANY_TO_ONE', 'MANY_TO_MANY')),
    protected BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (manifest_id, key),
    CONSTRAINT fk_catalog_relationships_source_entity
        FOREIGN KEY (manifest_id, source_entity_type_key)
        REFERENCES catalog_entity_types(manifest_id, key)
        ON DELETE CASCADE
);

-- =====================================================
-- CATALOG RELATIONSHIP TARGET RULES TABLE
-- =====================================================
-- Target rules for polymorphic or multi-target relationships
-- Child of catalog_relationships

CREATE TABLE IF NOT EXISTS catalog_relationship_target_rules (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    catalog_relationship_id UUID NOT NULL REFERENCES catalog_relationships(id) ON DELETE CASCADE,
    target_entity_type_key VARCHAR(255) NOT NULL,
    semantic_type_constraint TEXT CHECK (semantic_type_constraint IN ('CUSTOMER', 'PRODUCT', 'TRANSACTION', 'COMMUNICATION', 'SUPPORT', 'FINANCIAL', 'OPERATIONAL', 'CUSTOM', 'UNCATEGORIZED')),
    cardinality_override TEXT CHECK (cardinality_override IN ('ONE_TO_ONE', 'ONE_TO_MANY', 'MANY_TO_ONE', 'MANY_TO_MANY')),
    inverse_visible BOOLEAN NOT NULL DEFAULT FALSE,
    inverse_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (catalog_relationship_id, target_entity_type_key)
);

-- =====================================================
-- CATALOG FIELD MAPPINGS TABLE
-- =====================================================
-- Field mappings for integration manifests
-- Maps external provider fields to entity type attributes

CREATE TABLE IF NOT EXISTS catalog_field_mappings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    manifest_id UUID NOT NULL REFERENCES manifest_catalog(id) ON DELETE CASCADE,
    entity_type_key VARCHAR(255) NOT NULL,
    mappings JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (manifest_id, entity_type_key),
    CHECK (jsonb_typeof(mappings) = 'object')
);

-- =====================================================
-- CATALOG SEMANTIC METADATA TABLE
-- =====================================================
-- Semantic metadata for catalog entity types, attributes, and relationships
-- target_id is VARCHAR to accommodate string attribute keys

CREATE TABLE IF NOT EXISTS catalog_semantic_metadata (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    catalog_entity_type_id UUID NOT NULL REFERENCES catalog_entity_types(id) ON DELETE CASCADE,
    target_type TEXT NOT NULL CHECK (target_type IN ('ENTITY_TYPE', 'ATTRIBUTE', 'RELATIONSHIP')),
    target_id VARCHAR(255) NOT NULL,
    definition TEXT,
    classification TEXT CHECK (classification IN ('IDENTIFIER', 'CATEGORICAL', 'QUANTITATIVE', 'TEMPORAL', 'FREETEXT', 'RELATIONAL_REFERENCE')),
    tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (catalog_entity_type_id, target_type, target_id),
    CHECK (jsonb_typeof(tags) = 'array')
);
