package riven.core.service.entity

import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.activity.ActivityLogEntity
import riven.core.entity.entity.EntityEntity
import riven.core.entity.entity.EntityTypeEntity
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.util.OperationType
import riven.core.exceptions.SchemaValidationException
import riven.core.models.common.Icon
import riven.core.models.entity.Entity
import riven.core.models.entity.EntityLink
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.models.entity.payload.EntityAttributeRelationPayloadReference
import riven.core.models.entity.payload.EntityAttributeRequest
import riven.core.models.request.entity.SaveEntityRequest
import riven.core.models.response.entity.DeleteEntityResponse
import riven.core.models.response.entity.SaveEntityResponse
import riven.core.repository.entity.EntityRepository
import riven.core.service.activity.ActivityService
import riven.core.service.activity.log
import riven.core.service.auth.AuthTokenService
import riven.core.service.entity.type.EntityTypeAttributeService
import riven.core.service.entity.type.EntityTypeRelationshipService
import riven.core.service.entity.type.EntityTypeService
import riven.core.util.ServiceUtil.findManyResults
import riven.core.util.ServiceUtil.findOrThrow
import java.util.*

/**
 * Service for managing entity instances.
 */
@Service
class EntityService(
    private val entityRepository: EntityRepository,
    private val entityTypeService: EntityTypeService,
    private val entityRelationshipService: EntityRelationshipService,
    private val entityTypeRelationshipService: EntityTypeRelationshipService,
    private val entityValidationService: EntityValidationService,
    private val entityTypeAttributeService: EntityTypeAttributeService,
    private val entityAttributeService: EntityAttributeService,
    private val authTokenService: AuthTokenService,
    private val activityService: ActivityService
) {


    @PostAuthorize("@workspaceSecurity.hasWorkspace(returnObject.workspaceId)")
    fun getEntity(id: UUID): Entity {
        val entity = findOrThrow { entityRepository.findById(id) }
        val relationships = entityRelationshipService.findRelatedEntities(
            entityId = id,
            workspaceId = entity.workspaceId
        )
        val attributes = entityAttributeService.getAttributes(id)

        return entity.toModel(relationships = relationships, attributes = attributes)
    }

    fun getEntitiesByIds(ids: Set<UUID>): List<EntityEntity> {
        return findManyResults { entityRepository.findAllById(ids) }
    }

    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getEntitiesByTypeId(
        workspaceId: UUID,
        typeId: UUID
    ): List<Entity> {
        val entities = findManyResults {
            entityRepository.findByTypeId(typeId)
        }

        require(entities.all { it.workspaceId == workspaceId }) { "One or more entities do not belong to the specified workspace" }
        val entityIds = entities.mapNotNull { it.id }.toSet()
        val relationships = entityRelationshipService.findRelatedEntities(
            entityIds = entityIds,
            workspaceId = workspaceId
        )
        val allAttributes = entityAttributeService.getAttributesForEntities(entityIds)

        return entities.map {
            val id = requireNotNull(it.id)
            it.toModel(
                audit = true,
                relationships = relationships[id] ?: emptyMap(),
                attributes = allAttributes[id] ?: emptyMap(),
            )
        }
    }

    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getEntitiesByTypeIds(
        workspaceId: UUID,
        typeIds: List<UUID>
    ): Map<UUID, List<Entity>> {
        val entities = findManyResults {
            entityRepository.findByTypeIdIn(
                typeIds = typeIds
            )
        }

        require(entities.all { it.workspaceId == workspaceId }) { "One or more entities do not belong to the specified workspace" }

        val entityIds = entities.mapNotNull { it.id }.toSet()
        val relationships = entityRelationshipService.findRelatedEntities(
            entityIds = entityIds,
            workspaceId = workspaceId
        )
        val allAttributes = entityAttributeService.getAttributesForEntities(entityIds)

        return entities.map {
            val id = requireNotNull(it.id)
            it.toModel(
                audit = true,
                relationships = relationships[id] ?: emptyMap(),
                attributes = allAttributes[id] ?: emptyMap(),
            )
        }.groupBy { it.typeId }

    }

    /**
     * Create or update an entity with validation.
     * If request.id is provided, updates the existing entity; otherwise creates a new one.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun saveEntity(
        workspaceId: UUID,
        entityTypeId: UUID,
        request: SaveEntityRequest,
    ): SaveEntityResponse {
        try {
            val (id: UUID?, payload: Map<UUID, EntityAttributeRequest>, icon: Icon?) = request
            val userId = authTokenService.getUserId()
            val type: EntityTypeEntity = entityTypeService.getById(entityTypeId)
            val typeId = requireNotNull(type.id) { "Entity type ID cannot be null" }

            // Check if this is an update (existing entity) or create (new entity)
            val prev: EntityEntity? = id?.let { findOrThrow { entityRepository.findById(it) } }

            val attributePayload: Map<UUID, EntityAttributePrimitivePayload> = payload.mapNotNull { (key, value) ->
                key to value.payload.let {
                    when (it) {
                        is EntityAttributePrimitivePayload -> it
                        else -> return@mapNotNull null
                    }
                }
            }.toMap()

            val relationshipPayload: Map<UUID, List<UUID>> = payload.mapNotNull { (key, value) ->
                when (val pl = value.payload) {
                    is EntityAttributeRelationPayloadReference -> {
                        key to pl.relations
                    }

                    else -> return@mapNotNull null
                }
            }.toMap()

            prev?.run {
                require(this.workspaceId == workspaceId) { "Entity does not belong to the specified workspace" }
                require(this.typeId == entityTypeId) { "Entity type cannot be changed" }
            }

            // Either update the existing entity or create a new one
            val entity = prev.let {
                if (it != null) {
                    return@let it.copy(
                        iconType = icon?.type ?: it.iconType,
                        iconColour = icon?.colour ?: it.iconColour,
                    )
                }

                EntityEntity(
                    workspaceId = workspaceId,
                    typeId = entityTypeId,
                    typeKey = type.key,
                    iconType = icon?.type ?: type.iconType,
                    iconColour = icon?.colour ?: type.iconColour,
                    identifierKey = type.identifierKey,
                )
            }

            // Validate payload against schema
            entityValidationService.validateEntity(entity, type, attributes = attributePayload).run {
                if (isNotEmpty()) {
                    throw SchemaValidationException(this)
                }
            }

            return entityRepository.save(entity).run {
                val entityId = requireNotNull(this.id) { "Saved entity ID cannot be null" }

                // Save normalized attributes
                entityAttributeService.saveAttributes(
                    entityId = entityId,
                    workspaceId = workspaceId,
                    typeId = typeId,
                    attributes = attributePayload,
                )

                // Handle Unique Constraints
                val uniqueAttributes = entityTypeAttributeService.extractUniqueAttributes(type, payload)

                val uniqueValuesToSave = uniqueAttributes
                    .filterValues { it != null }
                    .mapNotNull { (fieldId, value) ->
                        if (value == null) return@mapNotNull null
                        entityTypeAttributeService.checkAttributeUniqueness(
                            typeId = typeId,
                            fieldId = fieldId,
                            value = value.value,
                            excludeEntityId = entityId
                        )
                        fieldId to value.value.toString()
                    }
                    .toMap()

                entityTypeAttributeService.saveUniqueValues(
                    workspaceId = workspaceId,
                    entityId = entityId,
                    typeId = typeId,
                    uniqueValues = uniqueValuesToSave
                )

                // Save relationships per definition
                saveRelationshipsPerDefinition(entityId, workspaceId, entityTypeId, relationshipPayload)

                activityService.log(
                    activity = Activity.ENTITY,
                    operation = if (prev != null) OperationType.UPDATE else OperationType.CREATE,
                    userId = userId,
                    workspaceId = workspaceId,
                    entityType = ApplicationEntityType.ENTITY,
                    entityId = this.id,
                    "type" to type.key,
                    "category" to type.displayNameSingular
                )

                // Reload links after save
                val links: Map<UUID, List<EntityLink>> = entityRelationshipService.findRelatedEntities(entityId, workspaceId)

                SaveEntityResponse(
                    entity = entity.toModel(
                        audit = true,
                        relationships = links,
                        attributes = attributePayload,
                    ),
                    impactedEntities = null
                )
            }
        } catch (e: SchemaValidationException) {
            return SaveEntityResponse(
                errors = e.reasons
            )
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Saves relationships for each definition in the payload.
     *
     * Loads relationship definitions for the entity type, then calls
     * EntityRelationshipService.saveRelationships for each definition.
     */
    private fun saveRelationshipsPerDefinition(
        entityId: UUID,
        workspaceId: UUID,
        entityTypeId: UUID,
        relationshipPayload: Map<UUID, List<UUID>>,
    ) {
        if (relationshipPayload.isEmpty()) return

        val definitions = entityTypeRelationshipService.getDefinitionsForEntityType(workspaceId, entityTypeId)
        val definitionsById = definitions.associateBy { it.id }

        relationshipPayload.forEach { (definitionId, targetIds) ->
            val definition = definitionsById[definitionId]
                ?: throw IllegalArgumentException("Unknown relationship definition: $definitionId")
            require(definition.sourceEntityTypeId == entityTypeId) {
                "Definition $definitionId belongs to source type ${definition.sourceEntityTypeId}, not $entityTypeId. " +
                    "Inverse definitions cannot be used to create relationships."
            }
            entityRelationshipService.saveRelationships(
                id = entityId,
                workspaceId = workspaceId,
                definitionId = definitionId,
                definition = definition,
                targetIds = targetIds,
            )
        }
    }


    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun deleteEntities(workspaceId: UUID, ids: List<UUID>): DeleteEntityResponse {
        val userId = authTokenService.getUserId()
        if (ids.isEmpty()) {
            return DeleteEntityResponse(
                error = "No entity IDs provided for deletion"
            )
        }

        // Find all relationships where deleted entities are targets (to identify impacted entities)
        val impactedEntityIds: List<UUID> = entityRelationshipService.findByTargetIdIn(ids).flatMap { it.value }
            .map { it.sourceId }
            .toSet()
            .filter { !ids.contains(it) } // Exclude entities being deleted


        // Archive entities, their unique values, and relationships
        val deletedEntities = entityRepository.deleteByIds(ids.toTypedArray(), workspaceId)
        val deletedRowIds = deletedEntities.mapNotNull { it.id }.toSet()

        if (deletedRowIds.isEmpty()) {
            return DeleteEntityResponse(
                error = "No entities were deleted. Please check the provided IDs."
            )
        }

        entityTypeAttributeService.deleteEntities(workspaceId, deletedRowIds)
        entityAttributeService.softDeleteByEntityIds(workspaceId, deletedRowIds)
        entityRelationshipService.archiveEntities(deletedRowIds, workspaceId)

        // Log activity for each deleted entity
        activityService.logActivities(
            deletedEntities.map { entity ->
                ActivityLogEntity(
                    activity = Activity.ENTITY,
                    operation = OperationType.DELETE,
                    userId = userId,
                    workspaceId = workspaceId,
                    entityId = entity.id,
                    entityType = ApplicationEntityType.ENTITY,
                    details = mapOf(
                        "typeId" to entity.typeId.toString(),
                        "typeKey" to entity.typeKey
                    )
                )
            }
        )

        // Fetch impacted entities with their updated relationships
        val updatedEntities: Map<UUID, List<Entity>>? = if (impactedEntityIds.isNotEmpty()) {
            val impactedEntityEntities = entityRepository.findAllById(impactedEntityIds)
            val impactedRelationships = entityRelationshipService.findRelatedEntities(
                entityIds = impactedEntityIds.toSet(),
                workspaceId = workspaceId
            )
            val impactedAttributes = entityAttributeService.getAttributesForEntities(impactedEntityIds.toSet())
            impactedEntityEntities
                .map { impactedEntity ->
                    val impactedId = requireNotNull(impactedEntity.id)
                    impactedEntity.toModel(
                        audit = true,
                        relationships = impactedRelationships[impactedId] ?: emptyMap(),
                        attributes = impactedAttributes[impactedId] ?: emptyMap(),
                    )
                }
                .groupBy { it.typeId }
        } else {
            null
        }

        return DeleteEntityResponse(
            deletedCount = deletedRowIds.size,
            updatedEntities = updatedEntities
        )
    }


    /**
     * Get all entities for an workspace.
     */
    fun getWorkspaceEntities(workspaceId: UUID): List<Entity> {
        val entities = findManyResults { entityRepository.findByWorkspaceId(workspaceId) }
        val entityIds = entities.mapNotNull { it.id }.toSet()
        val allAttributes = entityAttributeService.getAttributesForEntities(entityIds)
        return entities.map {
            val id = requireNotNull(it.id)
            it.toModel(
                relationships = emptyMap(),
                attributes = allAttributes[id] ?: emptyMap(),
            )
        }
    }


}
