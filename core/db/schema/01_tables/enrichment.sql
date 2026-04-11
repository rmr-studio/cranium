-- =====================================================
-- ENRICHMENT PIPELINE TABLES
-- =====================================================

-- =====================================================
-- ENTITY EMBEDDINGS TABLE
-- =====================================================
-- Stores vector embeddings for entities. One embedding per entity (upsert pattern).
-- System-managed: no soft-delete, no audit columns.
CREATE TABLE IF NOT EXISTS public.entity_embeddings
(
    "id"               UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    "workspace_id"     UUID         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    "entity_id"        UUID         NOT NULL REFERENCES entities (id) ON DELETE CASCADE,
    "entity_type_id"   UUID         NOT NULL REFERENCES entity_types (id) ON DELETE CASCADE,
    "embedding"        vector(1536) NOT NULL,
    "embedded_at"      TIMESTAMPTZ  NOT NULL     DEFAULT CURRENT_TIMESTAMP,
    "embedding_model"  TEXT         NOT NULL,
    "schema_version"   INTEGER      NOT NULL     DEFAULT 1,
    "truncated"        BOOLEAN      NOT NULL     DEFAULT FALSE,

    -- One embedding per entity, upsert pattern
    UNIQUE (entity_id)
);

-- Enrichment jobs use the common execution_queue table (job_type = 'ENRICHMENT')
-- in db/schema/01_tables/workflow.sql. No separate enrichment queue table needed.
