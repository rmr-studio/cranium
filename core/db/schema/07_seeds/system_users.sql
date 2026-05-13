-- =====================================================
-- SYSTEM USERS
-- =====================================================
-- System user for automated operations (integration sync, Temporal workers).
-- FK target for created_by/updated_by on system-managed records.

INSERT INTO users (id, name, email)
VALUES ('00000000-0000-0000-0000-000000000000', 'System' ,'system@cranium.internal')
ON CONFLICT (id) DO NOTHING;
