-- =====================================================
-- USERS TABLE
-- =====================================================

CREATE TABLE IF NOT EXISTS "users"
(
    "id"                   UUID PRIMARY KEY NOT NULL DEFAULT uuid_generate_v4(),
    "name"                 VARCHAR(50)      NOT NULL,
    "email"                VARCHAR(100)     NOT NULL UNIQUE,
    "phone"                VARCHAR(15)      NULL,
    "avatar_url"           TEXT,
    "default_workspace_id" UUID             REFERENCES public.workspaces (id) ON DELETE SET NULL,

    "created_at"           TIMESTAMP WITH TIME ZONE  DEFAULT CURRENT_TIMESTAMP,
    "updated_at"           TIMESTAMP WITH TIME ZONE  DEFAULT CURRENT_TIMESTAMP,
    "created_by"           UUID,
    "updated_by"           UUID,

    "deleted"              BOOLEAN          NOT NULL DEFAULT FALSE,
    "deleted_at"           TIMESTAMP WITH TIME ZONE  DEFAULT NULL,

    "onboarding_completed_at" TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    "acquisition_channels" JSONB            DEFAULT NULL
);

