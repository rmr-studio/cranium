DROP INDEX IF EXISTS uq_unique_attribute_per_type;
CREATE UNIQUE INDEX uq_unique_attribute_per_type
    ON public.entities_unique_values (type_id, field_id, field_value)
    WHERE deleted = FALSE AND deleted_at IS NULL;

ALTER TABLE public.entity_relationships
    drop constraint IF EXISTS uq_relationship_source_target_field;

ALTER TABLE public.entity_relationships
    drop constraint IF EXISTS uq_entity_relationship;

DROP INDEX IF EXISTS uq_entity_relationship;
CREATE UNIQUE INDEX uq_entity_relationship
    ON public.entity_relationships (source_entity_id, relationship_definition_id, target_id)
    WHERE deleted = FALSE AND deleted_at IS NULL;

DROP INDEX IF EXISTS uq_relationship_definition_system_type;
CREATE UNIQUE INDEX uq_relationship_definition_system_type
    ON public.relationship_definitions (workspace_id, source_entity_type_id, system_type)
    WHERE system_type IS NOT NULL AND deleted = FALSE;