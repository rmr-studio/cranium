-- =====================================================
-- NOTES TRIGGERS
-- =====================================================

-- Trigger fires on note_entity_attachments, not notes directly.
-- This correctly counts notes per entity when notes span multiple entities.
CREATE OR REPLACE TRIGGER trg_note_attachment_count
    AFTER INSERT OR DELETE ON note_entity_attachments
    FOR EACH ROW EXECUTE FUNCTION update_entity_note_count();
