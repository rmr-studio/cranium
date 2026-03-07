-- =====================================================
-- FILE METADATA INDEXES
-- =====================================================

CREATE INDEX IF NOT EXISTS idx_file_metadata_workspace_id
    ON public.file_metadata (workspace_id);

CREATE INDEX IF NOT EXISTS idx_file_metadata_workspace_domain
    ON public.file_metadata (workspace_id, domain);
