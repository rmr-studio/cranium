-- =====================================================
-- NOTES TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS public.notes
(
    "id"            UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    "entity_id"     UUID        NOT NULL REFERENCES entities (id) ON DELETE CASCADE,
    "workspace_id"  UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    "title"         VARCHAR(255) NOT NULL    DEFAULT '',
    "content"       JSONB       NOT NULL     DEFAULT '[]'::jsonb,
    "plaintext"     TEXT        NOT NULL     DEFAULT '',
    "search_vector" tsvector GENERATED ALWAYS AS (to_tsvector('english', plaintext)) STORED,
    "created_at"    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updated_at"    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "created_by"    UUID        REFERENCES users (id) ON DELETE SET NULL,
    "updated_by"    UUID        REFERENCES users (id) ON DELETE SET NULL
);
