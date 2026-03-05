-- =====================================================
-- FILE METADATA TABLE
-- =====================================================
DROP TABLE IF EXISTS "file_metadata" CASCADE;
CREATE TABLE IF NOT EXISTS "file_metadata"
(
    "id"                UUID PRIMARY KEY         NOT NULL DEFAULT gen_random_uuid(),
    "workspace_id"      UUID                     NOT NULL,
    "domain"            VARCHAR(50)              NOT NULL,
    "storage_key"       VARCHAR(500)             NOT NULL UNIQUE,
    "original_filename" VARCHAR(500)             NOT NULL,
    "content_type"      VARCHAR(255)             NOT NULL,
    "file_size"         BIGINT                   NOT NULL,
    "uploaded_by"       UUID                     NOT NULL,
    "deleted"           BOOLEAN                  NOT NULL DEFAULT FALSE,
    "deleted_at"        TIMESTAMPTZ,
    "created_at"        TIMESTAMPTZ              NOT NULL DEFAULT NOW(),
    "updated_at"        TIMESTAMPTZ              NOT NULL DEFAULT NOW(),
    "created_by"        UUID,
    "updated_by"        UUID
);
