-- =====================================================
-- ENRICHMENT PIPELINE INDEXES
-- =====================================================

-- HNSW index for approximate nearest neighbor search on entity embeddings
CREATE INDEX IF NOT EXISTS idx_entity_embeddings_hnsw
    ON entity_embeddings USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- Enrichment queue deduplication is handled by the existing partial unique index
-- uq_execution_queue_pending_identity_match on execution_queue (workspace_id, entity_id, job_type)
-- which covers all job types including ENRICHMENT.
