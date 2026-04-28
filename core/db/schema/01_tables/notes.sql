-- =====================================================
-- NOTES TABLE
-- =====================================================
-- Notes support both user-created and integration-embedded content.
-- entity_id is nullable — multi-entity attachment is via note_entity_attachments.
-- Integration notes are readonly and tracked by source_external_id for dedup.
CREATE TABLE IF NOT EXISTS public.notes
(
    "id"                      UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    "entity_id"               UUID        REFERENCES entities (id) ON DELETE SET NULL,
    "workspace_id"            UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    "title"                   VARCHAR(255) NOT NULL    DEFAULT '',
    "content"                 JSONB       NOT NULL     DEFAULT '[]'::jsonb,
    "plaintext"               TEXT        NOT NULL     DEFAULT '',
    "search_vector"           tsvector GENERATED ALWAYS AS (to_tsvector('english', plaintext)) STORED,
    "source_type"             VARCHAR(20) NOT NULL     DEFAULT 'USER',
    "source_integration_id"   UUID        REFERENCES integration_definitions (id) ON DELETE SET NULL,
    "source_external_id"      VARCHAR(255),
    "readonly"                BOOLEAN     NOT NULL     DEFAULT false,
    "pending_associations"    JSONB,
    "created_at"              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updated_at"              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "created_by"              UUID        REFERENCES users (id) ON DELETE SET NULL,
    "updated_by"              UUID        REFERENCES users (id) ON DELETE SET NULL
);

-- Migrate existing notes to note_entity_attachments (idempotent)
INSERT INTO note_entity_attachments (note_id, entity_id)
SELECT id, entity_id FROM notes WHERE entity_id IS NOT NULL
ON CONFLICT DO NOTHING;
