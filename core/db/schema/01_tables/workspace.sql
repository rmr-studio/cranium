CREATE TABLE IF NOT EXISTS "workspaces"
(
    "id"               UUID PRIMARY KEY NOT NULL DEFAULT uuid_generate_v4(),
    "name"             VARCHAR(100)     NOT NULL,
    "plan"             VARCHAR          NOT NULL DEFAULT 'FREE' CHECK (plan IN ('FREE', 'STARTUP', 'SCALE', 'ENTERPRISE')),
    "default_currency" VARCHAR(3)       NOT NULL DEFAULT 'AUD',
    "avatar_url"       TEXT,
    "member_count"     INTEGER          NOT NULL DEFAULT 0,
    "connotation_enabled" BOOLEAN       NOT NULL DEFAULT FALSE,

    "created_at"       TIMESTAMP WITH TIME ZONE  DEFAULT CURRENT_TIMESTAMP,
    "updated_at"       TIMESTAMP WITH TIME ZONE  DEFAULT CURRENT_TIMESTAMP,
    "created_by"       UUID,
    "updated_by"       UUID,

    "deleted"          BOOLEAN          NOT NULL DEFAULT FALSE,
    "deleted_at"       TIMESTAMP WITH TIME ZONE  DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS "workspace_members"
(
    "id"           UUID PRIMARY KEY NOT NULL DEFAULT uuid_generate_v4(),
    "workspace_id" UUID             NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    "user_id"      UUID             NOT NULL REFERENCES public.users (id) ON DELETE CASCADE,
    "role"         VARCHAR          NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    "member_since" TIMESTAMP WITH TIME ZONE  DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS "workspace_invites"
(
    "id"           UUID PRIMARY KEY NOT NULL DEFAULT uuid_generate_v4(),
    "workspace_id" UUID             NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    "email"        VARCHAR(100)     NOT NULL,
    "status"       VARCHAR(100)     NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED')),
    "invite_code"  VARCHAR(12)      NOT NULL CHECK (LENGTH(invite_code) = 12),
    "role"         VARCHAR          NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    "invited_by"   UUID             NOT NULL REFERENCES public.users (id) ON DELETE CASCADE,
    "created_at"   TIMESTAMP WITH TIME ZONE  DEFAULT CURRENT_TIMESTAMP,
    "expires_at"   TIMESTAMP WITH TIME ZONE  DEFAULT CURRENT_TIMESTAMP + INTERVAL '1 days'
);