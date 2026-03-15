-- Notification tables
-- Stores workspace-scoped notifications with polymorphic JSONB content

CREATE TABLE IF NOT EXISTS "notifications" (
    "id"              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    "workspace_id"    UUID         NOT NULL REFERENCES "workspaces" ("id"),
    "user_id"         UUID                  REFERENCES "users" ("id"),
    "type"            VARCHAR(50)  NOT NULL,
    "content"         JSONB        NOT NULL,
    "reference_type"  VARCHAR(50),
    "reference_id"    UUID,
    "resolved"        BOOLEAN      NOT NULL DEFAULT FALSE,
    "resolved_at"     TIMESTAMPTZ,
    "expires_at"      TIMESTAMPTZ,
    "created_at"      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at"      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "created_by"      UUID,
    "updated_by"      UUID,
    "deleted"         BOOLEAN      NOT NULL DEFAULT FALSE,
    "deleted_at"      TIMESTAMPTZ,
    CONSTRAINT "ck_notifications_reference_pair"
        CHECK (
            ("reference_type" IS NULL AND "reference_id" IS NULL) OR
            ("reference_type" IS NOT NULL AND "reference_id" IS NOT NULL)
        )
);

CREATE TABLE IF NOT EXISTS "notification_reads" (
    "id"              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    "user_id"         UUID         NOT NULL REFERENCES "users" ("id"),
    "notification_id" UUID         NOT NULL REFERENCES "notifications" ("id") ON DELETE CASCADE,
    "read_at"         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "uq_notification_reads_user_notification" UNIQUE ("user_id", "notification_id")
);
