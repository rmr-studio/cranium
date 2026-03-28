-- =====================================================
-- NOTES TRIGGERS
-- =====================================================

CREATE OR REPLACE TRIGGER trg_notes_count
    AFTER INSERT OR DELETE ON notes
    FOR EACH ROW EXECUTE FUNCTION update_entity_note_count();
