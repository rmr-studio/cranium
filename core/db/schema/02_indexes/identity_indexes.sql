-- =====================================================
-- IDENTITY RESOLUTION INDEXES
-- =====================================================

-- match_suggestions indexes
CREATE INDEX IF NOT EXISTS idx_match_suggestions_workspace
    ON public.match_suggestions (workspace_id, status)
    WHERE deleted = false;

CREATE INDEX IF NOT EXISTS idx_match_suggestions_source_entity
    ON public.match_suggestions (source_entity_id)
    WHERE deleted = false;

CREATE INDEX IF NOT EXISTS idx_match_suggestions_target_entity
    ON public.match_suggestions (target_entity_id)
    WHERE deleted = false;

-- identity_clusters indexes
CREATE INDEX IF NOT EXISTS idx_identity_clusters_workspace
    ON public.identity_clusters (workspace_id)
    WHERE deleted = false;

-- identity_cluster_members indexes
CREATE INDEX IF NOT EXISTS idx_identity_cluster_members_cluster
    ON public.identity_cluster_members (cluster_id);

-- Enforces one-cluster-per-entity: an entity can belong to at most one identity cluster
CREATE UNIQUE INDEX IF NOT EXISTS idx_identity_cluster_members_entity
    ON public.identity_cluster_members (entity_id);
