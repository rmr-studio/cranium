-- =====================================================
-- ENTITY CONNOTATION ROW LEVEL SECURITY POLICIES
-- =====================================================

-- Enable RLS on entity_connotation
ALTER TABLE entity_connotation
    ENABLE ROW LEVEL SECURITY;

-- Workspace members can select connotation envelopes for their workspace's entities
CREATE POLICY "entity_connotation_select" ON entity_connotation
    FOR SELECT TO authenticated
    USING (
    workspace_id IN (SELECT workspace_id
                     FROM workspace_members
                     WHERE user_id = auth.uid())
    );

-- Workspace members can write connotation envelopes for their workspace's entities.
-- Authoring is gated to system code paths (enrichment pipeline) at the application layer.
--
-- The `FOR ALL TO authenticated` grant is intentional and matches every other policy
-- in 05_rls/ for system-managed sibling tables (entity_embeddings, integrations, etc.).
-- Write authorisation for these tables is enforced by the application layer
-- (EnrichmentService for envelopes); RLS provides workspace isolation, not write gating.
--
-- Tightening these grants to `service_role` is captured as a codebase-wide sweep in
-- core/TODOS.md → Backend → "Tighten RLS write policies on system-managed tables to
-- service_role". Doing it consistently across 05_rls/ in one pass requires confirming
-- the backend Postgres role used by the app.
CREATE POLICY "entity_connotation_write" ON entity_connotation
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
