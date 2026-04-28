-- =====================================================
-- INTEGRATION DEFINITIONS TABLE
-- =====================================================
-- Global catalog of supported integrations (HubSpot, Salesforce, etc.)
-- No workspace_id - all workspaces read from same catalog
-- No RLS - catalog is globally readable

CREATE TABLE IF NOT EXISTS integration_definitions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    slug VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    icon_url TEXT,
    description TEXT,
    category VARCHAR(50) NOT NULL,
    nango_provider_key VARCHAR(100) NOT NULL,
    capabilities JSONB NOT NULL DEFAULT '{}'::jsonb,
    sync_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    auth_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    stale BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =====================================================
-- INTEGRATION CONNECTIONS TABLE
-- =====================================================
-- Workspace-scoped connections to integrations
-- One connection per integration per workspace
-- RLS enforces workspace isolation

CREATE TABLE IF NOT EXISTS integration_connections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    integration_id UUID NOT NULL REFERENCES integration_definitions(id) ON DELETE RESTRICT,
    nango_connection_id TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_AUTHORIZATION',
    connection_metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    UNIQUE(workspace_id, integration_id)
);

-- =====================================================
-- WORKSPACE INTEGRATION INSTALLATIONS TABLE
-- =====================================================
-- Tracks which integrations are enabled per workspace.
-- SoftDeletable: disable sets deleted = true, re-enable restores.

CREATE TABLE IF NOT EXISTS workspace_integration_installations (
    id                        UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id              UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    integration_definition_id UUID NOT NULL REFERENCES integration_definitions(id) ON DELETE RESTRICT,
    manifest_key              VARCHAR(255) NOT NULL,
    installed_by              UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    installed_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    sync_config               JSONB DEFAULT '{}',
    last_synced_at            TIMESTAMPTZ,
    status                    VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    deleted                   BOOLEAN NOT NULL DEFAULT false,
    deleted_at                TIMESTAMPTZ,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_by                UUID REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uq_workspace_integration_installation
        UNIQUE (workspace_id, integration_definition_id)
);

-- =====================================================
-- INTEGRATION SYNC STATE TABLE
-- =====================================================
-- Tracks per-connection per-entity-type sync progress.
-- System-managed (not SoftDeletable) — rows are updated in-place
-- as sync runs complete. Cascades on connection/entity-type removal.

CREATE TABLE IF NOT EXISTS integration_sync_state (
    id                        UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    integration_connection_id UUID NOT NULL REFERENCES integration_connections(id) ON DELETE CASCADE,
    entity_type_id            UUID NOT NULL REFERENCES entity_types(id) ON DELETE CASCADE,
    status                    VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    last_cursor               TEXT,
    consecutive_failure_count INTEGER NOT NULL DEFAULT 0,
    last_error_message        TEXT,
    last_records_synced       INTEGER,
    last_records_failed       INTEGER,
    last_pipeline_step        VARCHAR(50),
    projection_result         JSONB,
    sync_key                  VARCHAR(100),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now()
    -- Uniqueness enforced via two partial unique indexes in 02_indexes/integration_indexes.sql
    -- (Postgres UNIQUE treats NULLs as distinct, which would permit duplicate rows for
    -- sync_key IS NULL; partial indexes make NULL deterministic.)
);

-- =====================================================
-- ENTITY TYPE PROJECTION RULES TABLE
-- =====================================================
-- Maps source entity types (integration) to target entity types (core lifecycle).
-- Installed automatically from core model projectionAccepts during materialization.
-- workspace_id = NULL for system rules (from core model manifests).
-- Future: workspace_id = UUID for user-defined custom projection rules.

CREATE TABLE IF NOT EXISTS entity_type_projection_rules (
    id                     UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id           UUID REFERENCES workspaces(id) ON DELETE CASCADE,
    source_entity_type_id  UUID NOT NULL REFERENCES entity_types(id) ON DELETE CASCADE,
    target_entity_type_id  UUID NOT NULL REFERENCES entity_types(id) ON DELETE CASCADE,
    relationship_def_id    UUID REFERENCES relationship_definitions(id) ON DELETE SET NULL,
    auto_create            BOOLEAN NOT NULL DEFAULT true,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(workspace_id, source_entity_type_id, target_entity_type_id)
);
