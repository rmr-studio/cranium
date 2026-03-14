package riven.core.service.entity.type

import org.springframework.stereotype.Service
import riven.core.entity.entity.EntityTypeEntity
import riven.core.enums.core.DataType
import riven.core.exceptions.SchemaValidationException
import riven.core.exceptions.UniqueConstraintViolationException
import riven.core.models.common.json.JsonValue
import riven.core.models.entity.EntityTypeSchema
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.models.entity.payload.EntityAttributeRequest
import riven.core.models.request.entity.type.SaveAttributeDefinitionRequest
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.EntityUniqueValuesRepository
import riven.core.enums.common.validation.SchemaType
import riven.core.service.entity.EntityAttributeService
import riven.core.enums.entity.semantics.SemanticMetadataTargetType
import riven.core.service.entity.EntityTypeSemanticMetadataService
import riven.core.service.entity.EntityValidationService
import riven.core.service.schema.SchemaService
import java.util.*

@Service
class EntityTypeAttributeService(
    private val entityValidationService: EntityValidationService,
    private val entityRepository: EntityRepository,
    private val uniqueEntityValueRepository: EntityUniqueValuesRepository,
    private val semanticMetadataService: EntityTypeSemanticMetadataService,
    private val entityAttributeService: EntityAttributeService,
    private val schemaService: SchemaService,
    private val sequenceService: EntityTypeSequenceService,
) {

    fun saveAttributeDefinition(
        workspaceId: UUID,
        type: EntityTypeEntity,
        request: SaveAttributeDefinitionRequest
    ) {
        require(!type.readonly) { "Cannot modify schema attributes of a readonly entity type '${type.key}'" }
        val typeId = requireNotNull(type.id) { "Entity type ID must not be null when saving attribute definition" }
        val (_, id: UUID, attribute: EntityTypeSchema) = request

        // Detect whether this is a new attribute before we upsert it, so we can initialize semantic metadata if needed
        val isNewAttribute = type.schema.properties?.containsKey(id) != true

        // Validate some basic constraints on the attribute definition itself
        if (attribute.unique) {
            // Assert that the data type itself supports uniqueness (ie. STRING, NUMBER)
            if (attribute.type != DataType.STRING && attribute.type != DataType.NUMBER) {
                throw SchemaValidationException(
                    listOf("Attribute with 'unique' constraint must be of type STRING or NUMBER")
                )
            }
        }

        // Validate ID attributes have a prefix
        if (attribute.key == SchemaType.ID && attribute.options?.prefix.isNullOrBlank()) {
            throw SchemaValidationException(
                listOf("ID attribute must have a non-blank 'prefix' in options")
            )
        }

        // Validate default value if present
        attribute.options?.default?.let { defaultValue ->
            val defaultErrors = schemaService.validateDefault(attribute, defaultValue)
            if (defaultErrors.isNotEmpty()) {
                throw SchemaValidationException(
                    defaultErrors.map { "Attribute '${id}': $it" }
                )
            }
        }

        // Initialize sequence for new ID-type attributes
        if (isNewAttribute && attribute.key == SchemaType.ID) {
            sequenceService.initializeSequence(typeId, id)
        }

        val updatedSchema = type.schema.copy(
            properties = type.schema.properties?.toMutableMap()?.also {
                // Upsert new attribute definition
                it[id] = attribute
            }
        )

        // Validate updated schema to determine if breaking changes could arise
        val breakingChanges = entityValidationService.detectSchemaBreakingChanges(
            type.schema,
            updatedSchema
        )

        if (breakingChanges.any { it.breaking }) {
            val existingEntities = entityRepository.findByTypeId(typeId)
            val entityIds = existingEntities.mapNotNull { it.id }.toSet()
            val attributesByEntityId = entityAttributeService.getAttributesForEntities(entityIds)
            val validationSummary = entityValidationService.validateExistingEntitiesAgainstNewSchema(
                existingEntities,
                updatedSchema,
                attributesByEntityId = attributesByEntityId,
            )

            if (validationSummary.invalidCount > 0) {
                throw SchemaValidationException(
                    listOf(
                        "Cannot apply breaking schema changes: ${validationSummary.invalidCount} entities would become invalid. " +
                                "Sample errors: ${
                                    validationSummary.sampleErrors.take(3).map { it.errors.joinToString() }
                                }"
                    )
                )
            }
        }

        type.apply {
            schema = updatedSchema
        }

        if (isNewAttribute && request.semantics == null) {
            semanticMetadataService.initializeForTarget(
                entityTypeId = typeId,
                workspaceId = requireNotNull(type.workspaceId),
                targetType = SemanticMetadataTargetType.ATTRIBUTE,
                targetId = id,
            )
        } else if (request.semantics != null) {
            semanticMetadataService.upsertMetadataInternal(
                workspaceId, typeId, SemanticMetadataTargetType.ATTRIBUTE, id, request.semantics,
            )
        }
    }

    fun removeAttributeDefinition(
        type: EntityTypeEntity,
        attributeId: UUID
    ) {
        require(!type.readonly) { "Cannot remove schema attributes from a readonly entity type '${type.key}'" }
        val updatedSchema = type.schema.copy(
            properties = type.schema.properties?.toMutableMap()?.also {
                // Remove attribute definition
                it.remove(attributeId)
            }
        )

        type.apply {
            schema = updatedSchema
        }

        semanticMetadataService.deleteForTarget(
            entityTypeId = requireNotNull(type.id),
            targetType = SemanticMetadataTargetType.ATTRIBUTE,
            targetId = attributeId,
        )
    }

    fun extractUniqueAttributes(
        type: EntityTypeEntity,
        payload: Map<UUID, EntityAttributeRequest>
    ): Map<UUID, EntityAttributePrimitivePayload?> {
        val uniqueProperties = type.schema.properties.let {
            if (it == null) return emptyMap<UUID, EntityAttributePrimitivePayload?>()
            it.filter { (_, schema) -> schema.unique }.keys
        }

        return payload.filter { (key, _) -> uniqueProperties.contains(key) }
            .mapValues { (_, value) ->
                when (value.payload) {
                    is EntityAttributePrimitivePayload -> value.payload
                    else -> null
                }
            }
    }

    /**
     * Check if a unique constraint would be violated.
     * Excludes the current entity from the check to allow updates.
     */
    fun checkAttributeUniqueness(
        typeId: UUID,
        fieldId: UUID,
        value: JsonValue,
        excludeEntityId: UUID? = null
    ) {
        val hasConflict = uniqueEntityValueRepository.existsConflict(
            typeId = typeId,
            fieldId = fieldId,
            fieldValue = value.toString(),
            excludeEntityId = excludeEntityId
        )
        if (hasConflict) {
            throw UniqueConstraintViolationException(
                "Unique constraint violation for attribute '$fieldId' with value '$value' on entity type '$typeId'"
            )
        }
    }

    /**
     * Save unique values for an entity.
     * Deletes existing values first, then inserts new ones.
     * Uses native SQL for both operations to completely bypass Hibernate entity tracking.
     */
    fun saveUniqueValues(
        workspaceId: UUID,
        entityId: UUID,
        typeId: UUID,
        uniqueValues: Map<UUID, String>
    ) {
        // Delete existing values using native SQL
        uniqueEntityValueRepository.deleteAllByEntityId(entityId)

        // Insert new values using native SQL
        uniqueValues.forEach { (fieldId, fieldValue) ->
            uniqueEntityValueRepository.insertUniqueValue(
                id = UUID.randomUUID(),
                workspaceId = workspaceId,
                typeId = typeId,
                entityId = entityId,
                fieldId = fieldId,
                fieldValue = fieldValue
            )
        }
    }

    fun deleteEntities(workspaceId: UUID, ids: Collection<UUID>): Int {
        return uniqueEntityValueRepository.deleteEntities(workspaceId, ids)
    }

    fun deleteType(workspaceId: UUID, typeId: UUID): Int {
        return uniqueEntityValueRepository.deleteType(workspaceId, typeId)
    }
}