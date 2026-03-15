-- =====================================================
-- IDENTITY RESOLUTION TABLES
-- =====================================================

-- match_suggestions — candidate entity pairs for human review
CREATE TABLE IF NOT EXISTS public.match_suggestions (
    id                UUID        NOT NULL DEFAULT uuid_generate_v4(),
    workspace_id      UUID        NOT NULL,
    source_entity_id  UUID        NOT NULL,
    target_entity_id  UUID        NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    confidence_score  NUMERIC(5, 4) NOT NULL,
    signals           JSONB       NOT NULL DEFAULT '[]'::jsonb,
    rejection_signals JSONB,
    resolved_by       UUID,
    resolved_at       TIMESTAMPTZ,
    deleted           BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        UUID,
    updated_by        UUID,

    CONSTRAINT pk_match_suggestions PRIMARY KEY (id),
    CONSTRAINT fk_match_suggestions_workspace  FOREIGN KEY (workspace_id)     REFERENCES public.workspaces(id)  ON DELETE CASCADE,
    CONSTRAINT fk_match_suggestions_source     FOREIGN KEY (source_entity_id) REFERENCES public.entities(id)    ON DELETE CASCADE,
    CONSTRAINT fk_match_suggestions_target     FOREIGN KEY (target_entity_id) REFERENCES public.entities(id)    ON DELETE CASCADE,
    CONSTRAINT fk_match_suggestions_resolved_by FOREIGN KEY (resolved_by)     REFERENCES public.users(id)       ON DELETE SET NULL
);

-- identity_clusters — groups of entities confirmed as the same real-world identity
CREATE TABLE IF NOT EXISTS public.identity_clusters (
    id           UUID        NOT NULL DEFAULT uuid_generate_v4(),
    workspace_id UUID        NOT NULL,
    name         TEXT,
    member_count INTEGER     NOT NULL DEFAULT 0,
    deleted      BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   UUID,
    updated_by   UUID,

    CONSTRAINT pk_identity_clusters           PRIMARY KEY (id),
    CONSTRAINT fk_identity_clusters_workspace FOREIGN KEY (workspace_id) REFERENCES public.workspaces(id) ON DELETE CASCADE
);

-- identity_cluster_members — join table linking entities to a cluster
-- Hard-deleted when clusters merge; no soft-delete
CREATE TABLE IF NOT EXISTS public.identity_cluster_members (
    id         UUID        NOT NULL DEFAULT uuid_generate_v4(),
    cluster_id UUID        NOT NULL,
    entity_id  UUID        NOT NULL,
    joined_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    joined_by  UUID,

    CONSTRAINT pk_identity_cluster_members           PRIMARY KEY (id),
    CONSTRAINT fk_identity_cluster_members_cluster   FOREIGN KEY (cluster_id) REFERENCES public.identity_clusters(id) ON DELETE CASCADE,
    CONSTRAINT fk_identity_cluster_members_entity    FOREIGN KEY (entity_id)  REFERENCES public.entities(id)          ON DELETE CASCADE
);
