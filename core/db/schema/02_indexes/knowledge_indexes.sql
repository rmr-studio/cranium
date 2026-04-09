-- =====================================================
-- KNOWLEDGE INDEXES
-- =====================================================

-- List definitions for a workspace
CREATE INDEX IF NOT EXISTS idx_wbd_workspace_id
    ON public.workspace_business_definitions (workspace_id);

-- Active definitions query (filtered by status, excluding soft-deleted)
CREATE INDEX IF NOT EXISTS idx_wbd_workspace_status
    ON public.workspace_business_definitions (workspace_id, status)
    WHERE deleted = false;
