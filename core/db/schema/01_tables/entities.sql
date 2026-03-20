-- =====================================================
-- ENTITY TABLES
-- =====================================================

-- =====================================================
-- ENTITY TYPES TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS public.entity_types
(
    "id"                    UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    "key"                   TEXT        NOT NULL,
    "workspace_id"          UUID REFERENCES workspaces (id) ON DELETE CASCADE,
    "identifier_key"        UUID        NOT NULL,
    "display_name_singular" TEXT        NOT NULL,
    "display_name_plural"   TEXT        NOT NULL,
    "icon_type"             TEXT        NOT NULL     DEFAULT 'CIRCLE_DASHED', -- Lucide Icon Representation,
    "icon_colour"           TEXT        NOT NULL     DEFAULT 'NEUTRAL',       -- Colour of the icon,
    "protected"             BOOLEAN     NOT NULL     DEFAULT FALSE,
    "schema"                JSONB       NOT NULL,
    "column_configuration"  JSONB,
    "semantic_group"        TEXT        NOT NULL     DEFAULT 'UNCATEGORIZED',
    -- Source discriminator fields for integration entity types
    "source_type"           VARCHAR(50) NOT NULL     DEFAULT 'USER_CREATED',
    "source_integration_id" UUID        REFERENCES integration_definitions (id) ON DELETE SET NULL,
    "readonly"              BOOLEAN     NOT NULL     DEFAULT FALSE,
    -- Denormalized count of entities of this type for faster access
    "count"                 INTEGER     NOT NULL     DEFAULT 0,
    "version"               INTEGER     NOT NULL     DEFAULT 1,
    "deleted"               BOOLEAN     NOT NULL     DEFAULT FALSE,
    "created_at"            TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updated_at"            TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "created_by"            UUID,
    "updated_by"            UUID,
    "deleted_at"            TIMESTAMP WITH TIME ZONE DEFAULT NULL,

    -- Single row per entity type (mutable pattern)
    -- Also creates an index on workspace_id + key for faster lookups
    UNIQUE (workspace_id, key)
);

-- =====================================================
-- ENTITIES TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS public.entities
(
    "id"                    UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    "workspace_id"          UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    "type_id"               UUID        NOT NULL REFERENCES entity_types (id) ON DELETE RESTRICT,
    "type_key"              TEXT        NOT NULL,                       -- Denormalized key from entity_type for easier access

    "identifier_key"        UUID        NOT NULL,                       -- Duplicate key from entity_type for faster lookups without needing separate query
    "icon_type"             TEXT        NOT NULL     DEFAULT 'FILE',    -- Lucide Icon Representation,
    "icon_colour"           TEXT        NOT NULL     DEFAULT 'NEUTRAL', --

    "created_at"            TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updated_at"            TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "created_by"            UUID        REFERENCES users (id) ON DELETE SET NULL,
    "updated_by"            UUID        REFERENCES users (id) ON DELETE SET NULL,

    "deleted"               BOOLEAN     NOT NULL     DEFAULT FALSE,
    "deleted_at"            TIMESTAMP WITH TIME ZONE DEFAULT NULL,

    -- Provenance tracking fields
    "source_type"           VARCHAR(50) NOT NULL     DEFAULT 'USER_CREATED',
    "source_integration_id" UUID        REFERENCES integration_definitions (id) ON DELETE SET NULL,
    "source_external_id"    TEXT,
    "source_url"            TEXT,
    "first_synced_at"       TIMESTAMPTZ,
    "last_synced_at"        TIMESTAMPTZ,
    "sync_version"          BIGINT      NOT NULL     DEFAULT 0,

    -- Denormalized count of notes for faster access (trigger-maintained)
    "note_count"            INTEGER     NOT NULL     DEFAULT 0
);

-- =====================================================
-- ENTITY ATTRIBUTES TABLE
-- =====================================================
-- Normalized storage for entity attribute values.
-- Replaces the JSONB payload column on the entities table to enable
-- indexed cross-entity queries, EXISTS-based filtering, and future
-- trigram fuzzy matching.
CREATE TABLE IF NOT EXISTS public.entity_attributes
(
    "id"           UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    "entity_id"    UUID        NOT NULL REFERENCES entities (id) ON DELETE CASCADE,
    "workspace_id" UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    "type_id"      UUID        NOT NULL REFERENCES entity_types (id) ON DELETE CASCADE,
    "attribute_id" UUID        NOT NULL,
    "schema_type"  VARCHAR(50) NOT NULL,
    "value"        JSONB       NOT NULL,
    "deleted"      BOOLEAN     NOT NULL     DEFAULT FALSE,
    "deleted_at"   TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    "created_at"   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updated_at"   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "created_by"   UUID,
    "updated_by"   UUID
);


-- =====================================================
-- ENTITIES UNIQUE VALUES TABLE
-- =====================================================

-- Easy way to enforce unique fields per entity type. This table will store unique field values for entities.
-- When an entity is created or updated, the relevant unique fields will be deleted and re-inserted/updated here
CREATE TABLE IF NOT EXISTS public.entities_unique_values
(
    "id"           UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    "workspace_id" UUID    NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    "type_id"      UUID    NOT NULL REFERENCES entity_types (id) ON DELETE CASCADE,
    "entity_id"    UUID    NOT NULL REFERENCES entities (id) ON DELETE CASCADE,
    "field_id"     UUID    NOT NULL,
    "field_value"  TEXT    NOT NULL,

    "deleted"      BOOLEAN NOT NULL         DEFAULT FALSE,
    "deleted_at"   TIMESTAMP WITH TIME ZONE DEFAULT NULL
);

-- =====================================================
-- RELATIONSHIP DEFINITIONS TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS public.relationship_definitions
(
    "id"                    UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    "workspace_id"          UUID    NOT NULL REFERENCES public.workspaces (id) ON DELETE CASCADE,
    "source_entity_type_id" UUID    NOT NULL REFERENCES public.entity_types (id) ON DELETE CASCADE,
    "name"                  TEXT    NOT NULL,
    "icon_type"             TEXT    NOT NULL,
    "icon_value"            TEXT    NOT NULL,
    "cardinality_default"   TEXT    NOT NULL CHECK (cardinality_default IN
                                                    ('ONE_TO_ONE', 'ONE_TO_MANY', 'MANY_TO_ONE', 'MANY_TO_MANY')),
    "protected"             BOOLEAN NOT NULL         DEFAULT FALSE,
    "system_type"           TEXT                     DEFAULT NULL,
    "created_at"            TIMESTAMP WITH TIME ZONE DEFAULT now(),
    "updated_at"            TIMESTAMP WITH TIME ZONE DEFAULT now(),
    "created_by"            UUID,
    "updated_by"            UUID,
    "deleted"               BOOLEAN NOT NULL         DEFAULT FALSE,
    "deleted_at"            TIMESTAMP WITH TIME ZONE DEFAULT NULL
);

-- =====================================================
-- RELATIONSHIP TARGET RULES TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS public.relationship_target_rules
(
    "id"                         UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    "relationship_definition_id" UUID NOT NULL REFERENCES public.relationship_definitions (id) ON DELETE CASCADE,
    "target_entity_type_id"      UUID NOT NULL REFERENCES public.entity_types (id) ON DELETE CASCADE,
    "cardinality_override"       TEXT CHECK (cardinality_override IN
                                             ('ONE_TO_ONE', 'ONE_TO_MANY', 'MANY_TO_ONE', 'MANY_TO_MANY')),
    "inverse_name"               TEXT NOT NULL,
    "created_at"                 TIMESTAMP WITH TIME ZONE DEFAULT now(),
    "updated_at"                 TIMESTAMP WITH TIME ZONE DEFAULT now(),
    "created_by"                 UUID,
    "updated_by"                 UUID
);

-- =====================================================
-- ENTITY RELATIONSHIPS TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS public.entity_relationships
(
    "id"                         UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    "workspace_id"               UUID    NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    "source_entity_id"           UUID    NOT NULL REFERENCES entities (id) ON DELETE CASCADE,
    "target_entity_id"           UUID    NOT NULL REFERENCES entities (id) ON DELETE CASCADE,
    "relationship_definition_id" UUID    NOT NULL REFERENCES relationship_definitions (id) ON DELETE RESTRICT,

    -- Semantic context for fallback connections (why these entities are linked)
    "semantic_context"           TEXT                     DEFAULT NULL,
    -- Source of this link (USER_CREATED, INTEGRATION, etc.)
    "link_source"                TEXT    NOT NULL         DEFAULT 'USER_CREATED',

    -- Additional metadata about the relationship
    "created_at"                 TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updated_at"                 TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "created_by"                 UUID    REFERENCES users (id) ON DELETE SET NULL,
    "updated_by"                 UUID    REFERENCES users (id) ON DELETE SET NULL,

    "deleted"                    BOOLEAN NOT NULL         DEFAULT FALSE,
    "deleted_at"                 TIMESTAMP WITH TIME ZONE DEFAULT NULL
);

-- =====================================================
-- ENTITY SEMANTIC METADATA TABLE
-- =====================================================
-- Stores semantic metadata (definition, classification, tags) for entity types,
-- their attributes, and their relationships. Uses a target_type discriminator
-- to identify which domain object the metadata describes.
--
-- target_type = 'ENTITY_TYPE'  → target_id is the entity_type_id itself
-- target_type = 'ATTRIBUTE'    → target_id is a UUID attribute key from the schema
-- target_type = 'RELATIONSHIP' → target_id is a UUID relationship definition ID

CREATE TABLE IF NOT EXISTS public.entity_type_semantic_metadata
(
    "id"             UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    "workspace_id"   UUID    NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    "entity_type_id" UUID    NOT NULL REFERENCES entity_types (id) ON DELETE CASCADE,
    "target_type"    TEXT    NOT NULL CHECK (target_type IN ('ENTITY_TYPE', 'ATTRIBUTE', 'RELATIONSHIP')),
    "target_id"      UUID    NOT NULL,
    "definition"     TEXT,
    "classification" TEXT CHECK (classification IS NULL OR classification IN (
                                                                              'IDENTIFIER', 'CATEGORICAL',
                                                                              'QUANTITATIVE',
                                                                              'TEMPORAL', 'FREETEXT',
                                                                              'RELATIONAL_REFERENCE'
        )),
    "tags"           JSONB   NOT NULL         DEFAULT '[]'::jsonb,
    "deleted"        BOOLEAN NOT NULL         DEFAULT FALSE,
    "deleted_at"     TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    "created_at"     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updated_at"     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "created_by"     UUID,
    "updated_by"     UUID,

    -- Non-partial UNIQUE: soft-deleted rows still occupy the unique tuple.
    -- Any future restore path must UPDATE (un-soft-delete) existing rows rather than INSERT,
    -- otherwise a constraint violation will occur. Current code paths are safe because:
    -- (1) new entity types get new UUIDs, (2) attribute/relationship removal hard-deletes metadata,
    -- (3) soft-delete only occurs on entity type deletion, and (4) restore is explicitly unimplemented.
    UNIQUE (entity_type_id, target_type, target_id)
);

-- =====================================================
-- ENTITY TYPE SEQUENCES TABLE
-- =====================================================
-- Stores monotonically increasing counters for ID-type attributes.
-- Each row tracks the current sequence value for one (entity_type, attribute) pair.
-- Counter only increments — never decremented on soft-delete — so IDs are never reused.
CREATE TABLE IF NOT EXISTS public.entity_type_sequences
(
    "entity_type_id" UUID   NOT NULL REFERENCES entity_types (id) ON DELETE CASCADE,
    "attribute_id"   UUID   NOT NULL,
    "current_value"  BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (entity_type_id, attribute_id)
);

CREATE INDEX IF NOT EXISTS idx_entity_semantic_metadata_workspace
    ON public.entity_type_semantic_metadata (workspace_id);

CREATE INDEX IF NOT EXISTS idx_entity_semantic_metadata_entity_type
    ON public.entity_type_semantic_metadata (entity_type_id)
    WHERE deleted = false;

CREATE INDEX IF NOT EXISTS idx_entity_semantic_metadata_target
    ON public.entity_type_semantic_metadata (target_type, target_id)
    WHERE deleted = false;