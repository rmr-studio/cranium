-- =====================================================
-- IDENTITY RESOLUTION CONSTRAINTS
-- =====================================================

-- Enforce canonical ordering: source_entity_id must be less than target_entity_id (UUID lexicographic comparison)
-- Prevents (A,B) and (B,A) being stored as separate suggestions — callers must always pass the smaller UUID as source
ALTER TABLE public.match_suggestions
    DROP CONSTRAINT IF EXISTS chk_match_suggestions_canonical_order;

ALTER TABLE public.match_suggestions
    ADD CONSTRAINT chk_match_suggestions_canonical_order
    CHECK (source_entity_id < target_entity_id);

-- Unique pair constraint per workspace (only active/non-deleted suggestions)
-- Prevents duplicate active suggestions for the same entity pair within a workspace
DROP INDEX IF EXISTS uq_match_suggestions_pair;
CREATE UNIQUE INDEX IF NOT EXISTS uq_match_suggestions_pair
    ON public.match_suggestions (workspace_id, source_entity_id, target_entity_id)
    WHERE deleted = false;
