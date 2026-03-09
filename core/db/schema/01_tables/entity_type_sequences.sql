-- =====================================================
-- ENTITY TYPE SEQUENCES TABLE
-- =====================================================
-- Stores monotonically increasing counters for ID-type attributes.
-- Each row tracks the current sequence value for one (entity_type, attribute) pair.
-- Counter only increments — never decremented on soft-delete — so IDs are never reused.
CREATE TABLE IF NOT EXISTS public.entity_type_sequences
(
    "entity_type_id" UUID NOT NULL REFERENCES entity_types (id) ON DELETE CASCADE,
    "attribute_id"   UUID NOT NULL,
    "current_value"  BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (entity_type_id, attribute_id)
);
