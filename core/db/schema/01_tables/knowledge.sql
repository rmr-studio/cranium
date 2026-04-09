-- =====================================================
-- WORKSPACE BUSINESS DEFINITIONS TABLE
-- =====================================================

CREATE TABLE IF NOT EXISTS public.workspace_business_definitions
(
    "id"               UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    "workspace_id"     UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    "term"             VARCHAR(255) NOT NULL,
    "normalized_term"  VARCHAR(255) NOT NULL,                                      -- LOWER(TRIM(term)) with trailing plural stripped. Used for uniqueness.
    "definition"       TEXT        NOT NULL,
    "category"         VARCHAR(50) NOT NULL,
    "compiled_params"  JSONB                    DEFAULT NULL,                      -- AI-compiled structured query parameters. Null if not yet compiled.
    "status"           VARCHAR(50) NOT NULL     DEFAULT 'ACTIVE',
    "source"           VARCHAR(50) NOT NULL     DEFAULT 'MANUAL',
    "entity_type_refs" JSONB       NOT NULL     DEFAULT '[]',
    "attribute_refs"   JSONB       NOT NULL     DEFAULT '[]',
    "is_customised"    BOOLEAN     NOT NULL     DEFAULT FALSE,                   -- Whether the user modified the default definition text during onboarding.
    "version"          INTEGER     NOT NULL     DEFAULT 0,

    "created_by"       UUID        REFERENCES public.users (id) ON DELETE SET NULL,
    "updated_by"       UUID        REFERENCES public.users (id) ON DELETE SET NULL,
    "created_at"       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updated_at"       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    "deleted"          BOOLEAN     NOT NULL     DEFAULT FALSE,
    "deleted_at"       TIMESTAMP WITH TIME ZONE DEFAULT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_workspace_business_definitions_active_term
    ON public.workspace_business_definitions (workspace_id, normalized_term)
    WHERE deleted = FALSE;
