-- =====================================================
-- ENTITY CONNOTATION INDEXES
-- =====================================================
-- Functional BTREE indexes on JSONB path expressions inside the SENTIMENT metadata
-- category. Used by Layer 4 Milestone C predicate queries (e.g. "sentiment < -0.5
-- in last 7 days"). WHERE clauses match the column NOT NULL constraint but are
-- retained as a safety net for partial-index intent if the column is ever made nullable.

CREATE INDEX IF NOT EXISTS entity_connotation_sentiment_idx
    ON entity_connotation (((connotation_metadata -> 'metadata' -> 'SENTIMENT' ->> 'sentiment')::float))
    WHERE connotation_metadata IS NOT NULL;

CREATE INDEX IF NOT EXISTS entity_connotation_analyzed_at_idx
    ON entity_connotation (((connotation_metadata -> 'metadata' -> 'SENTIMENT' ->> 'analyzedAt')::timestamptz))
    WHERE connotation_metadata IS NOT NULL;

CREATE INDEX IF NOT EXISTS entity_connotation_workspace_idx
    ON entity_connotation (workspace_id);
