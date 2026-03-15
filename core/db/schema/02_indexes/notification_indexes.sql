-- Notification indexes

-- Inbox query: workspace + user, ordered by created_at
CREATE INDEX IF NOT EXISTS "idx_notifications_workspace_user"
    ON "notifications" ("workspace_id", "user_id", "created_at" DESC)
    WHERE "deleted" = FALSE;

-- Expiration cleanup: workspace + expires_at
CREATE INDEX IF NOT EXISTS "idx_notifications_workspace_expires"
    ON "notifications" ("workspace_id", "expires_at")
    WHERE "deleted" = FALSE AND "expires_at" IS NOT NULL;

-- Feed ordering: workspace + created_at
CREATE INDEX IF NOT EXISTS "idx_notifications_workspace_created"
    ON "notifications" ("workspace_id", "created_at" DESC)
    WHERE "deleted" = FALSE;

-- Reference lookup: resolve notifications tied to a domain entity
CREATE INDEX IF NOT EXISTS "idx_notifications_reference"
    ON "notifications" ("reference_type", "reference_id")
    WHERE "deleted" = FALSE AND "reference_type" IS NOT NULL;

-- Read-status lookup by notification
CREATE INDEX IF NOT EXISTS "idx_notification_reads_notification"
    ON "notification_reads" ("notification_id");
