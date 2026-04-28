-- =====================================================
-- NOTE ENTITY ATTACHMENTS TABLE
-- =====================================================
-- Join table for entity-spanning notes. One note can attach to
-- multiple entities. Cascade: entity deletion removes the attachment
-- row but does NOT delete the note itself.

CREATE TABLE IF NOT EXISTS note_entity_attachments (
    note_id   UUID NOT NULL REFERENCES notes (id) ON DELETE CASCADE,
    entity_id UUID NOT NULL REFERENCES entities (id) ON DELETE CASCADE,
    PRIMARY KEY (note_id, entity_id)
);
