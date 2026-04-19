package riven.core.service.entity

import tools.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KLogger
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.entity.EntityAttributeEntity
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.repository.entity.EntityAttributeRepository
import java.util.*

/**
 * Service for managing normalized entity attribute values.
 *
 * Handles CRUD operations on the entity_attributes table, which stores
 * individual attribute values per entity instance. Uses a delete-all + re-insert
 * pattern for saves to avoid merge complexity.
 */
@Service
class EntityAttributeService(
    private val entityAttributeRepository: EntityAttributeRepository,
    private val objectMapper: ObjectMapper,
    private val logger: KLogger,
) {

    // ------ Public Mutations ------

    /**
     * Save attributes for an entity using delete-all + re-insert.
     *
     * @param entityId The entity instance ID
     * @param workspaceId The workspace ID
     * @param typeId The entity type ID
     * @param attributes Map of attribute ID to primitive payload
     */
    @Transactional
    fun saveAttributes(
        entityId: UUID,
        workspaceId: UUID,
        typeId: UUID,
        attributes: Map<UUID, EntityAttributePrimitivePayload>,
    ) {
        entityAttributeRepository.hardDeleteByEntityId(entityId)

        if (attributes.isEmpty()) return

        val entities = attributes.mapNotNull { (attributeId, payload) ->
            val value = payload.value ?: return@mapNotNull null
            EntityAttributeEntity(
                entityId = entityId,
                workspaceId = workspaceId,
                typeId = typeId,
                attributeId = attributeId,
                schemaType = payload.schemaType,
                value = objectMapper.valueToTree(value),
            )
        }

        entityAttributeRepository.saveAll(entities)
        logger.debug { "Saved ${entities.size} attributes for entity $entityId" }
    }

    /**
     * Soft-delete attributes when parent entities are soft-deleted.
     *
     * @param workspaceId The workspace ID
     * @param entityIds The entity IDs whose attributes should be soft-deleted
     */
    @Transactional
    fun softDeleteByEntityIds(workspaceId: UUID, entityIds: Collection<UUID>) {
        if (entityIds.isEmpty()) return
        entityAttributeRepository.softDeleteByEntityIds(entityIds.toTypedArray(), workspaceId)
        logger.debug { "Soft-deleted attributes for ${entityIds.size} entities" }
    }

    // ------ Public Read Operations ------

    /**
     * Load attributes for a single entity.
     *
     * @param entityId The entity instance ID
     * @return Map of attribute ID to primitive payload
     */
    fun getAttributes(entityId: UUID): Map<UUID, EntityAttributePrimitivePayload> {
        return entityAttributeRepository.findByEntityId(entityId)
            .associate { it.attributeId to it.toPrimitivePayload() }
    }

    /**
     * Batch-load attributes for multiple entities.
     *
     * @param entityIds The entity instance IDs
     * @return Map of entity ID to (attribute ID to primitive payload)
     */
    fun getAttributesForEntities(entityIds: Collection<UUID>): Map<UUID, Map<UUID, EntityAttributePrimitivePayload>> {
        if (entityIds.isEmpty()) return emptyMap()

        return entityAttributeRepository.findByEntityIdIn(entityIds)
            .groupBy { it.entityId }
            .mapValues { (_, attrs) ->
                attrs.associate { it.attributeId to it.toPrimitivePayload() }
            }
    }
}
