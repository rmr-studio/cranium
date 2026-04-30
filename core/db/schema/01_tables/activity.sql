-- =====================================================
-- ACTIVITY LOGS TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS "activity_logs"
(
    "id"           UUID PRIMARY KEY         NOT NULL DEFAULT uuid_generate_v4(),
    "activity"     VARCHAR(100)             NOT NULL,
    "operation"    VARCHAR(20)              NOT NULL CHECK (operation IN
                                                            ('CREATE', 'UPDATE', 'DELETE', 'READ', 'RESTORE',
                                                             'ANALYZE', 'REANALYZE')),
    "workspace_id" UUID                     NOT NULL REFERENCES public.workspaces (id) ON DELETE CASCADE,
    "user_id"      UUID                     REFERENCES public.users (id) ON DELETE SET NULL,
    "entity_type"  VARCHAR(50)              NOT NULL,
    "entity_id"    UUID,
    "details"      JSONB                    NOT NULL DEFAULT '{}'::jsonb,
    "timestamp"    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
