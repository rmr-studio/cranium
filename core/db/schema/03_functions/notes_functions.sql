-- =====================================================
-- NOTES FUNCTIONS
-- =====================================================

-- Function to update entity note_count based on note_entity_attachments changes.
-- One note attached to 3 entities = each entity gets +1 note_count.
CREATE OR REPLACE FUNCTION update_entity_note_count() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE entities SET note_count = note_count + 1 WHERE id = NEW.entity_id;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE entities SET note_count = note_count - 1 WHERE id = OLD.entity_id;
        RETURN OLD;
    END IF;
END;
$$ LANGUAGE plpgsql;
