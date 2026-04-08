-- =====================================================
-- ENRICHMENT PIPELINE INDEXES
-- =====================================================

-- HNSW index for approximate nearest neighbor search on entity embeddings
CREATE INDEX IF NOT EXISTS idx_entity_embeddings_hnsw
    ON entity_embeddings USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- Deduplication: only one PENDING queue item per entity at a time
CREATE UNIQUE INDEX IF NOT EXISTS idx_enrichment_queue_dedup
    ON enrichment_queue (entity_id)
    WHERE status = 'PENDING';

-- Composite index for workspace-scoped queue queries and round-robin dispatch
CREATE INDEX IF NOT EXISTS idx_enrichment_queue_workspace_status
    ON enrichment_queue (workspace_id, status);
