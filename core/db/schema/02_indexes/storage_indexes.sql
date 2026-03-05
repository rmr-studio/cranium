-- =====================================================
-- FILE METADATA INDEXES
-- =====================================================

DROP INDEX IF EXISTS idx_file_metadata_workspace_id;
CREATE INDEX IF NOT EXISTS idx_file_metadata_workspace_id
    ON public.file_metadata (workspace_id);

DROP INDEX IF EXISTS idx_file_metadata_workspace_domain;
CREATE INDEX IF NOT EXISTS idx_file_metadata_workspace_domain
    ON public.file_metadata (workspace_id, domain);

DROP INDEX IF EXISTS uq_file_metadata_storage_key;
CREATE UNIQUE INDEX IF NOT EXISTS uq_file_metadata_storage_key
    ON public.file_metadata (storage_key);
