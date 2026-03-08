-- =====================================================
-- ENTITY ATTRIBUTES TABLE
-- =====================================================
-- Normalized storage for entity attribute values.
-- Replaces the JSONB payload column on the entities table to enable
-- indexed cross-entity queries, EXISTS-based filtering, and future
-- trigram fuzzy matching.
CREATE TABLE IF NOT EXISTS public.entity_attributes
(
    "id"           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    "entity_id"    UUID         NOT NULL REFERENCES entities (id) ON DELETE CASCADE,
    "workspace_id" UUID         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    "type_id"      UUID         NOT NULL REFERENCES entity_types (id) ON DELETE CASCADE,
    "attribute_id" UUID         NOT NULL,
    "schema_type"  VARCHAR(50)  NOT NULL,
    "value"        JSONB        NOT NULL,
    "deleted"      BOOLEAN      NOT NULL DEFAULT FALSE,
    "deleted_at"   TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    "created_at"   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updated_at"   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "created_by"   UUID,
    "updated_by"   UUID
);
