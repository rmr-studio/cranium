-- =====================================================
-- ENRICHMENT PIPELINE ROW LEVEL SECURITY POLICIES
-- =====================================================

-- =====================================================
-- ENTITY EMBEDDINGS RLS
-- =====================================================

-- Enable RLS on entity_embeddings
ALTER TABLE entity_embeddings
    ENABLE ROW LEVEL SECURITY;

-- Entity embeddings can be selected by workspace members
CREATE POLICY "entity_embeddings_select" ON entity_embeddings
    FOR SELECT TO authenticated
    USING (
    workspace_id IN (SELECT workspace_id
                     FROM workspace_members
                     WHERE user_id = auth.uid())
    );

-- Entity embeddings can be written by workspace members
CREATE POLICY "entity_embeddings_write" ON entity_embeddings
    FOR ALL TO authenticated
    USING (
    workspace_id IN (SELECT workspace_id
                     FROM workspace_members
                     WHERE user_id = auth.uid())
    )
    WITH CHECK (
    workspace_id IN (SELECT workspace_id
                     FROM workspace_members
                     WHERE user_id = auth.uid())
    );

-- No RLS on enrichment queue items: they use execution_queue which is system-managed, accessed by service role only
