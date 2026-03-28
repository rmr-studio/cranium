CREATE TABLE IF NOT EXISTS "workflow_definitions"
(
    "id"                UUID PRIMARY KEY                NOT NULL DEFAULT uuid_generate_v4(),
    "workspace_id"      UUID REFERENCES workspaces (id) NOT NULL,
    "name"              TEXT                            NOT NULL,
    "description"       TEXT,
    "published_version" INTEGER,
    "status"            TEXT                            NOT NULL,
    "icon_type"         TEXT                            NOT NULL,
    "icon_colour"       TEXT                            NOT NULL,
    "tags"              JSONB                           NOT NULL DEFAULT '[]'::JSONB,
    "updated_at"        TIMESTAMPTZ                     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "created_at"        TIMESTAMPTZ                     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_by"        UUID,
    "created_by"        UUID,
    "deleted"           BOOLEAN                         NOT NULL DEFAULT FALSE,
    "deleted_at"        TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS "workflow_definition_versions"
(
    "id"                     UUID PRIMARY KEY                          NOT NULL DEFAULT uuid_generate_v4(),
    "workspace_id"           UUID REFERENCES workspaces (id)           NOT NULL,
    "workflow_definition_id" UUID REFERENCES workflow_definitions (id) NOT NULL,
    "version_number"         INTEGER                                   NOT NULL,
    "workflow"               JSONB                                     NOT NULL,
    "canvas"                 JSONB                                     NOT NULL,
    "updated_at"             TIMESTAMPTZ                               NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "created_at"             TIMESTAMPTZ                               NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_by"             UUID,
    "created_by"             UUID,
    "deleted"                BOOLEAN                                   NOT NULL DEFAULT FALSE,
    "deleted_at"             TIMESTAMPTZ
);



CREATE TABLE IF NOT EXISTS "workflow_nodes"
(
    "id"           UUID PRIMARY KEY                NOT NULL DEFAULT uuid_generate_v4(),
    "workspace_id" UUID REFERENCES workspaces (id) NOT NULL,
    "key"          TEXT                            NOT NULL,
    "name"         TEXT                            NOT NULL,
    "description"  TEXT,
    "type"         TEXT                            NOT NULL,
    "version"      INTEGER                         NOT NULL DEFAULT 1,
    "source_id"    UUID                            REFERENCES workflow_nodes (id) ON DELETE SET NULL,
    "config"       JSONB                           NOT NULL,
    "system"       BOOLEAN                         NOT NULL DEFAULT FALSE,
    "deleted"      BOOLEAN                         NOT NULL DEFAULT FALSE,
    "deleted_at"   TIMESTAMPTZ,
    "created_at"   TIMESTAMPTZ                     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at"   TIMESTAMPTZ                     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "created_by"   UUID,
    "updated_by"   UUID
);


CREATE TABLE IF NOT EXISTS "workflow_edges"
(
    "id"             UUID PRIMARY KEY                    NOT NULL DEFAULT uuid_generate_v4(),
    "workspace_id"   UUID REFERENCES workspaces (id)     NOT NULL,
    "source_node_id" UUID REFERENCES workflow_nodes (id) NOT NULL,
    "target_node_id" UUID REFERENCES workflow_nodes (id) NOT NULL,
    "label"          TEXT,
    "deleted"        BOOLEAN                             NOT NULL DEFAULT FALSE,
    "deleted_at"     TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS "workflow_executions"
(
    "id"                             UUID PRIMARY KEY                                  NOT NULL DEFAULT uuid_generate_v4(),
    "workspace_id"                   UUID REFERENCES workspaces (id)                   NOT NULL,
    "workflow_definition_id"         UUID REFERENCES workflow_definitions (id)         NOT NULL,
    "workflow_definition_version_id" UUID REFERENCES workflow_definition_versions (id) NOT NULL,
    "status"                         TEXT                                              NOT NULL,
    "trigger_type"                   TEXT                                              NOT NULL,
    "started_at"                     TIMESTAMPTZ                                       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "completed_at"                   TIMESTAMPTZ,
    "duration_ms"                    BIGINT                                            NOT NULL DEFAULT 0,
    "error"                          JSONB,
    "input"                          JSONB,
    "output"                         JSONB
);

CREATE TABLE IF NOT EXISTS "execution_queue"
(
    "id"                     UUID PRIMARY KEY                                            NOT NULL DEFAULT uuid_generate_v4(),
    "workspace_id"           UUID REFERENCES workspaces (id) ON DELETE CASCADE           NOT NULL,
    "job_type"               VARCHAR(30)                                                 NOT NULL DEFAULT 'WORKFLOW_EXECUTION',
    "entity_id"              UUID REFERENCES entities (id) ON DELETE CASCADE,
    "workflow_definition_id" UUID REFERENCES workflow_definitions (id) ON DELETE CASCADE,
    "execution_id"           UUID                                                        REFERENCES workflow_executions (id) ON DELETE SET NULL,
    "status"                 TEXT                                                        NOT NULL DEFAULT 'PENDING',
    "created_at"             TIMESTAMPTZ                                                 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "claimed_at"             TIMESTAMPTZ,
    "dispatched_at"          TIMESTAMPTZ,
    "input"                  JSONB,
    "attempts"               INTEGER                                                     NOT NULL DEFAULT 0,
    "last_error"             TEXT,

    CONSTRAINT chk_job_type_fields CHECK (
        (job_type = 'IDENTITY_MATCH' AND entity_id IS NOT NULL AND workflow_definition_id IS NULL) OR
        (job_type = 'WORKFLOW_EXECUTION' AND workflow_definition_id IS NOT NULL)
    )
);

-- Tracks each workflow node execution during a workflow run.
-- Stores input/output payloads per node execution.
CREATE TABLE IF NOT EXISTS "workflow_node_executions"
(
    "id"                    UUID PRIMARY KEY                         NOT NULL DEFAULT uuid_generate_v4(),
    "workspace_id"          UUID REFERENCES workspaces (id)          NOT NULL,
    "workflow_execution_id" UUID REFERENCES workflow_executions (id) NOT NULL,
    "workflow_node_id"      UUID REFERENCES workflow_nodes (id)      NOT NULL,
    "sequence_index"        INTEGER                                  NOT NULL,
    "status"                TEXT                                     NOT NULL,
    "started_at"            TIMESTAMPTZ                              NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "completed_at"          TIMESTAMPTZ,
    "duration_ms"           BIGINT                                   NOT NULL DEFAULT 0,
    "attempt"               INTEGER                                  NOT NULL DEFAULT 1,
    "error"                 JSONB,
    "input"                 JSONB,
    "output"                JSONB
);


-- Stores serialized execution context snapshots.
CREATE TABLE IF NOT EXISTS "workflow_execution_context"
(
);

-- Tracks execution of each node during a workflow run.
-- Stores input/output payloads per node execution.
CREATE TABLE IF NOT EXISTS "workflow_node_context"
(
);