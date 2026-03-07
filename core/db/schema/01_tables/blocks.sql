-- =====================================================
-- BLOCK TABLES
-- =====================================================

-- =====================================================
-- BLOCK TYPES TABLE
-- =====================================================

CREATE TABLE IF NOT EXISTS public.block_types
(
    "id"                UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    "key"               TEXT  NOT NULL,                                       -- machine key e.g. "contact_card"
    "display_name"      TEXT  NOT NULL,
    "source_id"         UUID  REFERENCES block_types (id) ON DELETE SET NULL, -- refers to the original block type if this is a copy/updated version
    "description"       TEXT,
    "workspace_id"      UUID  REFERENCES workspaces (id) ON DELETE SET NULL,  -- null for global
    "nesting"           JSONB,                                                -- options for storing blocks within this block. Null indicates no children allowed
    "system"            BOOLEAN                  DEFAULT FALSE,               -- system types you control
    "schema"            JSONB NOT NULL,                                       -- JSON Schema for validation
    "display_structure" JSONB NOT NULL,                                       -- UI metadata for frontend display (ie. Form Structure, Display Component Rendering, etc)
    "strictness"        TEXT  NOT NULL           DEFAULT 'strict',
    "version"           INTEGER                  DEFAULT 1,                   -- To handle updates to schema/display_structure over time to ensure that existing blocks are not broken,
    "deleted"           BOOLEAN                  DEFAULT FALSE,
    "deleted_at"        TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    "created_at"        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updated_at"        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "created_by"        UUID,                                                 -- optional user id
    "updated_by"        UUID,                                                 -- optional user id
    UNIQUE (workspace_id, key, version)
);

-- =====================================================
-- BLOCKS TABLE
-- =====================================================

CREATE TABLE IF NOT EXISTS public.blocks
(
    "id"           UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    "workspace_id" UUID NOT NULL REFERENCES workspaces (id),
    "type_id"      UUID NOT NULL REFERENCES block_types (id), -- true if payload contains references to another block/entities
    "name"         TEXT,                                      -- human-friendly title
    "payload"      JSONB                    DEFAULT '{}',     -- flexible content

    "created_by"   UUID REFERENCES public.users (id) ON DELETE SET NULL,
    "updated_by"   UUID REFERENCES public.users (id) ON DELETE SET NULL,
    "created_at"   TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    "updated_at"   TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    "deleted"      BOOLEAN                  DEFAULT FALSE,
    "deleted_at"   TIMESTAMP WITH TIME ZONE DEFAULT NULL
);

-- =====================================================
-- BLOCK CHILDREN TABLE
-- =====================================================

CREATE TABLE IF NOT EXISTS public.block_children
(
    "id"           UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    "workspace_id" UUID NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    "parent_id"    UUID NOT NULL REFERENCES blocks (id) ON DELETE CASCADE,
    "child_id"     UUID NOT NULL REFERENCES blocks (id) ON DELETE CASCADE,
    -- Order index to maintain the order of children within the parent block, given the parent is of 'list' type
    "order_index"  INTEGER                  DEFAULT NULL,
    "deleted"      BOOLEAN                  DEFAULT FALSE,
    "deleted_at"   TIMESTAMP WITH TIME ZONE DEFAULT NULL
);

-- =====================================================
-- BLOCK TREE LAYOUTS TABLE
-- =====================================================

CREATE TABLE IF NOT EXISTS public.block_tree_layouts
(
    "id"           UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    "workspace_id" UUID    NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    "layout"       JSONB   NOT NULL,
    "version"      INTEGER NOT NULL         DEFAULT 1, -- id of client, line item, etc,
    "entity_id"    UUID    NOT NULL REFERENCES public.entities (id) ON DELETE CASCADE,

    "created_at"   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updated_at"   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "created_by"   UUID    REFERENCES public.users (id) ON DELETE SET NULL,
    "updated_by"   UUID    REFERENCES public.users (id) ON DELETE SET NULL,

    "deleted"      BOOLEAN NOT NULL         DEFAULT FALSE,
    "deleted_at"   TIMESTAMP WITH TIME ZONE DEFAULT NULL
);
