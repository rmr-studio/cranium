-- =====================================================
-- SYSTEM USERS
-- =====================================================
-- System user for automated operations (integration sync, Temporal workers).
-- FK target for created_by/updated_by on system-managed records.

INSERT INTO users (id, email)
VALUES ('00000000-0000-0000-0000-000000000000', 'system@riven.internal')
ON CONFLICT DO NOTHING;
