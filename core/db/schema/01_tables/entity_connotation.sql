-- =====================================================
-- ENTITY CONNOTATION TABLE
-- =====================================================
-- Stores per-entity polymorphic semantic envelope (SENTIMENT, RELATIONAL,
-- STRUCTURAL axes) captured at last enrichment time. One envelope per entity.
-- System-managed: no soft-delete, no audit columns. The envelope is internally
-- system-write-only and intentionally lives on a sibling table to avoid
-- polluting entities.audit fields when re-enrichment writes occur.
CREATE TABLE IF NOT EXISTS public.entity_connotation
(
    "id"                    UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    "entity_id"             UUID        NOT NULL,
    "workspace_id"          UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    "connotation_metadata"  JSONB       NOT NULL,
    "created_at"            TIMESTAMPTZ NOT NULL     DEFAULT CURRENT_TIMESTAMP,
    "updated_at"            TIMESTAMPTZ NOT NULL     DEFAULT CURRENT_TIMESTAMP,

    -- One envelope per entity (upsert pattern)
    UNIQUE (entity_id),

    -- Composite FK enforces that entity_id and workspace_id agree with the
    -- referenced entities row. RLS filters by workspace_id, so a divergent pair
    -- would produce rows invisible to their own workspace. Targets the
    -- UNIQUE (id, workspace_id) constraint on entities. Cascade on entity delete
    -- subsumes the prior single-column FK on entity_id.
    FOREIGN KEY (entity_id, workspace_id) REFERENCES entities (id, workspace_id) ON DELETE CASCADE
);
