-- =====================================================
-- NOTES INDEXES
-- =====================================================
CREATE INDEX IF NOT EXISTS idx_notes_entity_id ON notes(entity_id);
CREATE INDEX IF NOT EXISTS idx_notes_workspace_id ON notes(workspace_id);
CREATE INDEX IF NOT EXISTS idx_notes_search_vector ON notes USING gin(search_vector);
CREATE INDEX IF NOT EXISTS idx_notes_created_at ON notes(entity_id, created_at DESC);

-- Workspace-scoped composite indexes for cursor-based pagination
CREATE INDEX IF NOT EXISTS idx_notes_workspace_created_at ON notes(workspace_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notes_workspace_updated_at ON notes(workspace_id, updated_at DESC);

-- Dedup index for integration notes (partial — only rows with source_external_id)
CREATE UNIQUE INDEX IF NOT EXISTS idx_notes_source_dedup
    ON notes(source_external_id) WHERE source_external_id IS NOT NULL;

-- =====================================================
-- NOTE ENTITY ATTACHMENT INDEXES
-- =====================================================
CREATE INDEX IF NOT EXISTS idx_note_attachments_entity ON note_entity_attachments(entity_id);
