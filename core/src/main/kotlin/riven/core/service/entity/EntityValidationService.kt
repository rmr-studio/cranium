package riven.core.service.entity

import org.springframework.stereotype.Service
import riven.core.entity.entity.EntityEntity
import riven.core.entity.entity.EntityTypeEntity
import riven.core.enums.common.validation.ValidationScope
import riven.core.enums.entity.validation.EntityTypeChangeType
import riven.core.models.entity.EntityTypeSchema
import riven.core.models.entity.RelationshipDefinition
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.models.entity.validation.EntityTypeSchemaChange
import riven.core.models.entity.validation.EntityTypeValidationSummary
import riven.core.models.entity.validation.EntityValidationError
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.service.schema.SchemaService
import java.util.*

/**
 * Service for validating entities and detecting breaking schema changes.
 */
@Service
class EntityValidationService(
    private val schemaService: SchemaService,
    private val entityRelationshipRepository: EntityRelationshipRepository
) {

    /**
     * Validate entity payload against its type schema.
     * Accepts either a pre-extracted attributes map or falls back to the entity's JSONB payload.
     */
    fun validateEntity(
        entity: EntityEntity,
        entityType: EntityTypeEntity,
        attributes: Map<UUID, EntityAttributePrimitivePayload> = emptyMap(),
    ): List<String> {
        val payloadForValidation: Map<String, Any?> =
            attributes.map { (key, value) -> key.toString() to value.value }.toMap()

        return schemaService.validate(
            schema = entityType.schema,
            payload = payloadForValidation,
            scope = ValidationScope.STRICT
        )
    }

    /**
     * Validate relationship entity constraints.
     * Ensures RELATIONSHIP entities have all required relationships with correct types.
     */
    fun validateRelationshipEntity(
        entityId: UUID,
        definitions: List<RelationshipDefinition>
    ): List<String> {
        TODO()
//        val errors = mutableListOf<String>()
//        val relatedEntities: List<EntityRelationshipEntity> =
//            entityRelationshipRepository.findByRelationshipEntityId(entityId)
//
//        // Validate each defined relationship
//        relationships.forEach { definition ->
//            val matchingRels = relatedEntities.filter { rel ->
//                rel.relationshipType == defin
//            }
//
//            // Check required relationships are present
//            if (definition.required && matchingRels.isEmpty()) {
//                errors.add(
//                    "Relationship entity missing required '${definition.role}' relationship"
//                )
//            }
//
//            // Validate entity type matches requirement (for both required and optional)
//            matchingRels.forEach { rel ->
//                if (!definition.allowPolymorphic && definition.entityTypeKeys != null) {
//                    val targetType = rel.targetEntity.type.key
//                    if (targetType !in definition.entityTypeKeys) {
//                        errors.add(
//                            "Relationship '${definition.role}' requires entity type to be one of " +
//                                    "${definition.entityTypeKeys}, but found '$targetType'"
//                        )
//                    }
//                }
//            }
//        }
//
//        return errors
    }

    /**
     * Detect breaking changes between two schemas.
     * Returns a list of changes with breaking flag.
     */
    fun detectSchemaBreakingChanges(
        oldSchema: EntityTypeSchema,
        newSchema: EntityTypeSchema
    ): List<EntityTypeSchemaChange> {
        val changes = mutableListOf<EntityTypeSchemaChange>()

        // Detect removed fields (breaking if required)
        oldSchema.properties?.forEach { (key, oldField) ->
            if (newSchema.properties?.get(key) == null) {
                changes.add(
                    EntityTypeSchemaChange(
                        type = EntityTypeChangeType.FIELD_REMOVED,
                        path = key.toString(),
                        description = "Field '$key' removed",
                        breaking = oldField.required
                    )
                )
            }
        }

        // Detect added required fields (breaking - no default value)
        newSchema.properties?.forEach { (key, newField) ->
            if (oldSchema.properties?.get(key) == null && newField.required) {
                changes.add(
                    EntityTypeSchemaChange(
                        type = EntityTypeChangeType.FIELD_REQUIRED_ADDED,
                        path = key.toString(),
                        description = "Required field '$key' added",
                        breaking = true
                    )
                )
            }
        }

        // Detect type changes (breaking)
        oldSchema.properties?.forEach { (key, oldField) ->
            newSchema.properties?.get(key)?.let { newField ->
                if (oldField.type != newField.type) {
                    changes.add(
                        EntityTypeSchemaChange(
                            type = EntityTypeChangeType.FIELD_TYPE_CHANGED,
                            path = key.toString(),
                            description = "Field '$key' type changed from ${oldField.type} to ${newField.type}",
                            breaking = true
                        )
                    )
                }

                // Detect required flag changes
                /**
                 * TODO - Eventually we would need to consider querying the current
                 * data to instead just determine if this change can be automatically applied
                 * without causing existing data to become invalid. (Ie. if all current entities have a value for this field)
                 */
                if (!oldField.required && newField.required) {
                    changes.add(
                        EntityTypeSchemaChange(
                            type = EntityTypeChangeType.FIELD_REQUIRED_ADDED,
                            path = key.toString(),
                            description = "Field '$key' changed from optional to required",
                            breaking = true
                        )
                    )
                }

                // Detect unique flag changes
                /**
                 * TODO - Eventually we would need to consider querying the current
                 * data to instead just determine if this change can be automatically applied
                 * without causing existing data to become invalid. (Ie. if all existing values are unique)
                 * We could then automatically perform the necessary data migration to enforce uniqueness
                 * and move all data to the normalized unique table
                 */
                if (!oldField.unique && newField.unique) {
                    changes.add(
                        EntityTypeSchemaChange(
                            type = EntityTypeChangeType.FIELD_UNIQUE_ADDED,
                            path = key.toString(),
                            description = "Field '$key' changed from optional to required",
                            breaking = true
                        )
                    )
                }
            }
        }

        return changes
    }

    /**
     * Validate all existing entities against a new schema.
     * Returns count of entities that would become invalid.
     */
    fun validateExistingEntitiesAgainstNewSchema(
        entities: List<EntityEntity>,
        newSchema: EntityTypeSchema,
        attributesByEntityId: Map<UUID, Map<UUID, EntityAttributePrimitivePayload>> = emptyMap(),
    ): EntityTypeValidationSummary {
        var validCount = 0
        var invalidCount = 0
        val sampleErrors = mutableListOf<EntityValidationError>()

        entities.forEach { entity ->
            val entityId = requireNotNull(entity.id)
            val attrs = attributesByEntityId[entityId] ?: emptyMap()
            val payloadForValidation: Map<String, Any?> =
                attrs.map { (key, value) -> key.toString() to value.value }.toMap()

            val errors = schemaService.validate(
                schema = newSchema,
                payload = payloadForValidation,
                scope = ValidationScope.STRICT
            )

            if (errors.isEmpty()) {
                validCount++
            } else {
                invalidCount++
                // Keep first 10 sample errors
                if (sampleErrors.size < 10) {
                    sampleErrors.add(
                        EntityValidationError(
                            entityId = entity.id!!,
                            // todo. Use Entity type identifier
                            entityName = "",
                            errors = errors
                        )
                    )
                }
            }
        }

        return EntityTypeValidationSummary(
            totalEntities = entities.size,
            validCount = validCount,
            invalidCount = invalidCount,
            sampleErrors = sampleErrors
        )
    }
}
