package riven.core.service.entity.type

import jakarta.transaction.Transactional
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import riven.core.entity.entity.EntityTypeEntity
import riven.core.enums.activity.Activity
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.core.DataType
import riven.core.enums.entity.EntityPropertyType
import riven.core.enums.entity.semantics.SemanticMetadataTargetType
import riven.core.enums.util.OperationType
import riven.core.models.common.validation.Schema
import riven.core.models.entity.EntityType
import riven.core.models.entity.EntityTypeSemanticMetadata
import riven.core.models.entity.SemanticMetadataBundle
import riven.core.models.entity.configuration.EntityTypeAttributeColumn
import riven.core.models.request.entity.type.*
import riven.core.models.response.entity.type.DeleteDefinitionImpact
import riven.core.models.response.entity.type.EntityTypeImpactResponse
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.entity.RelationshipDefinitionRepository
import riven.core.repository.entity.RelationshipTargetRuleRepository
import riven.core.service.activity.ActivityService
import riven.core.service.activity.log
import riven.core.service.auth.AuthTokenService
import riven.core.service.entity.EntityTypeSemanticMetadataService
import riven.core.util.ServiceUtil
import java.util.*

/**
 * Service for managing entity types.
 *
 * Key difference from BlockTypeService: EntityTypes are MUTABLE.
 * Updates modify the existing row rather than creating new versions.
 */
@Service
class EntityTypeService(
    private val entityTypeRepository: EntityTypeRepository,
    private val entityTypeRelationshipService: EntityTypeRelationshipService,
    private val entityAttributeService: EntityTypeAttributeService,
    private val definitionRepository: RelationshipDefinitionRepository,
    private val entityRelationshipRepository: EntityRelationshipRepository,
    private val targetRuleRepository: RelationshipTargetRuleRepository,
    private val authTokenService: AuthTokenService,
    private val activityService: ActivityService,
    private val semanticMetadataService: EntityTypeSemanticMetadataService,
) {

    /**
     * Create and publish a new entity type.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun publishEntityType(workspaceId: UUID, request: CreateEntityTypeRequest): EntityType {
        val userId = authTokenService.getUserId()
        val primaryId: UUID = UUID.randomUUID()

        val entity = EntityTypeEntity(
            displayNameSingular = request.name.singular,
            displayNamePlural = request.name.plural,
            key = request.key,
            workspaceId = workspaceId,
            identifierKey = primaryId,
            semanticGroup = request.semanticGroup,
            iconType = request.icon.type,
            iconColour = request.icon.colour,
            protected = false,
            schema = Schema(
                type = DataType.OBJECT,
                key = SchemaType.OBJECT,
                protected = true,
                required = true,
                properties = mapOf(
                    primaryId to Schema(
                        type = DataType.STRING,
                        key = SchemaType.TEXT,
                        label = "Name",
                        unique = true,
                        protected = true,
                        required = true,
                    ),
                )
            ),
            columns = listOf(
                EntityTypeAttributeColumn(
                    key = primaryId,
                    type = EntityPropertyType.ATTRIBUTE
                )
            )
        )

        val saved = entityTypeRepository.save(entity)
        val savedId = requireNotNull(saved.id)

        semanticMetadataService.initializeForEntityType(
            entityTypeId = savedId,
            workspaceId = workspaceId,
            attributeIds = listOf(primaryId)
        )

        if (request.semantics != null) {
            semanticMetadataService.upsertMetadataInternal(
                workspaceId, savedId, SemanticMetadataTargetType.ENTITY_TYPE, savedId, request.semantics,
            )
        }

        entityTypeRelationshipService.createFallbackDefinition(workspaceId, savedId)

        activityService.log(
            activity = Activity.ENTITY_TYPE,
            operation = OperationType.CREATE,
            userId = userId,
            workspaceId = requireNotNull(saved.workspaceId) { "Cannot create system entity type" },
            entityType = ApplicationEntityType.ENTITY_TYPE,
            entityId = savedId,
            "type" to saved.key,
            "version" to 1,
        )

        return saved.toModel()
    }

    /**
     * Update an existing entity type (MUTABLE - updates in place).
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun updateEntityTypeConfiguration(
        workspaceId: UUID,
        request: UpdateEntityTypeConfigurationRequest,
    ): EntityType {
        val userId = authTokenService.getUserId()
        val existing: EntityTypeEntity = ServiceUtil.findOrThrow { entityTypeRepository.findById(request.id) }

        if (existing.workspaceId != workspaceId) {
            throw AccessDeniedException("Entity type does not belong to the specified workspace")
        }

        val oldSemanticGroup = existing.semanticGroup

        existing.apply {
            displayNameSingular = request.name.singular
            displayNamePlural = request.name.plural
            request.semanticGroup?.let { semanticGroup = it }
            iconType = request.icon.type
            iconColour = request.icon.colour
            columns = request.columns
        }

        val saved = entityTypeRepository.save(existing)
        val savedId = requireNotNull(saved.id)

        activityService.log(
            activity = Activity.ENTITY_TYPE,
            operation = OperationType.UPDATE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.ENTITY_TYPE,
            entityId = savedId,
            "type" to existing.key,
        )

        if (request.semantics != null) {
            semanticMetadataService.upsertMetadataInternal(
                workspaceId, savedId, SemanticMetadataTargetType.ENTITY_TYPE, savedId, request.semantics,
            )
        }

        return saved.toModel()
    }

    /**
     * Save an attribute or relationship definition for an entity type.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun saveEntityTypeDefinition(
        workspaceId: UUID,
        request: SaveTypeDefinitionRequest,
        impactConfirmed: Boolean = false
    ): EntityTypeImpactResponse {
        val (_: Int?, definition) = request
        val existing =
            ServiceUtil.findOrThrow { entityTypeRepository.findByworkspaceIdAndKey(workspaceId, definition.key) }
        val entityTypeId = requireNotNull(existing.id)

        val impactedEntityTypes = mutableMapOf<String, EntityType>()

        val (resolvedId: UUID, propertyType: EntityPropertyType) = when (definition) {
            is SaveAttributeDefinitionRequest -> {
                entityAttributeService.saveAttributeDefinition(workspaceId, existing, definition).also {
                    impactedEntityTypes[existing.key] = existing.toModel()
                }
                definition.id to EntityPropertyType.ATTRIBUTE
            }

            is SaveRelationshipDefinitionRequest -> {
                val (id, impact) = handleSaveRelationshipDefinition(workspaceId, entityTypeId, definition, impactConfirmed)
                if (impact != null) {
                    return EntityTypeImpactResponse(impact = impact)
                }
                id to EntityPropertyType.RELATIONSHIP
            }

            else -> throw IllegalArgumentException("Unsupported definition type: ${definition::class.java}")
        }

        // Handle column ordering
        updateColumnOrdering(existing, resolvedId, propertyType, request.index)

        entityTypeRepository.save(existing).also {
            impactedEntityTypes[existing.key] = it.toModel()
            return EntityTypeImpactResponse(
                impact = null,
                updatedEntityTypes = impactedEntityTypes,
                error = null
            )
        }
    }

    /**
     * Remove an attribute or relationship definition from an entity type.
     */
    fun removeEntityTypeDefinition(
        workspaceId: UUID,
        request: DeleteTypeDefinitionRequest,
        impactConfirmed: Boolean = false
    ): EntityTypeImpactResponse {
        val (definition) = request
        val existing =
            ServiceUtil.findOrThrow { entityTypeRepository.findByworkspaceIdAndKey(workspaceId, definition.key) }

        val impactedEntityTypes = mutableMapOf<String, EntityType>()

        when (definition) {
            is DeleteAttributeDefinitionRequest -> {
                entityAttributeService.removeAttributeDefinition(existing, definition.id)
            }

            is DeleteRelationshipDefinitionRequest -> {
                if (definition.sourceEntityTypeKey != null) {
                    // Target-side exclusion: the key field is the target type opting out
                    val entityTypeId = requireNotNull(existing.id)
                    val impact = entityTypeRelationshipService.excludeEntityTypeFromDefinition(
                        workspaceId = workspaceId,
                        definitionId = definition.id,
                        entityTypeId = entityTypeId,
                        impactConfirmed = impactConfirmed,
                    )
                    if (impact != null) {
                        return EntityTypeImpactResponse(
                            error = null,
                            updatedEntityTypes = null,
                            impact = impact,
                        )
                    }
                } else {
                    // Source-side deletion (existing flow)
                    val impact = entityTypeRelationshipService.deleteRelationshipDefinition(
                        workspaceId = workspaceId,
                        definitionId = definition.id,
                        impactConfirmed = impactConfirmed,
                    )
                    if (impact != null) {
                        return EntityTypeImpactResponse(
                            error = null,
                            updatedEntityTypes = null,
                            impact = impact,
                        )
                    }
                }
            }

            else -> throw IllegalArgumentException("Unsupported definition type: ${definition::class.java}")
        }

        // Remove from entity type ordering
        val definitionId = requireNotNull(definition.id) { "Delete requests must have a non-null id" }
        existing.apply {
            columns = columns.filterNot { it.key == definitionId }
        }.let {
            entityTypeRepository.save(it).also {
                impactedEntityTypes[existing.key] = it.toModel()
                return EntityTypeImpactResponse(
                    impact = null,
                    updatedEntityTypes = impactedEntityTypes,
                    error = null
                )
            }
        }
    }

    /**
     * Reorder entity type columns (attributes/relationships).
     */
    fun reorderEntityTypeColumns(
        order: List<EntityTypeAttributeColumn>,
        key: EntityTypeAttributeColumn,
        prev: Int?,
        new: Int
    ): List<EntityTypeAttributeColumn> {
        val mutableOrder = order.toMutableList()

        if (prev != null) {
            mutableOrder.removeAt(prev)
        }

        val insertIndex = new.coerceIn(0, mutableOrder.size)
        mutableOrder.add(insertIndex, key)

        return mutableOrder
    }


    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun deleteEntityType(
        workspaceId: UUID,
        key: String,
        impactConfirmed: Boolean = false
    ): EntityTypeImpactResponse {
        val userId = authTokenService.getUserId()
        val existing = ServiceUtil.findOrThrow { entityTypeRepository.findByworkspaceIdAndKey(workspaceId, key) }
        val entityTypeId = requireNotNull(existing.id)
        requireNotNull(existing.workspaceId) { "Cannot delete system entity type" }

        // Check for relationship definition impact
        val definitions = definitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, entityTypeId)
        if (!impactConfirmed && definitions.isNotEmpty()) {
            val totalLinks =
                definitions.sumOf { entityRelationshipRepository.countByDefinitionId(requireNotNull(it.id)) }
            if (totalLinks > 0) {
                val firstDef = definitions.first()
                return EntityTypeImpactResponse(
                    impact = DeleteDefinitionImpact(
                        definitionId = requireNotNull(firstDef.id),
                        definitionName = "${existing.displayNameSingular} (${definitions.size} definition(s))",
                        impactedLinkCount = totalLinks,
                    ),
                    updatedEntityTypes = null,
                    error = null,
                )
            }
        }

        // Delete all relationship definitions for this entity type
        definitions.forEach { def ->
            entityTypeRelationshipService.deleteRelationshipDefinition(
                workspaceId = workspaceId,
                definitionId = requireNotNull(def.id),
                impactConfirmed = true,
            )
        }

        // Clean up target rules from OTHER definitions that point TO this entity type
        targetRuleRepository.deleteByTargetEntityTypeId(entityTypeId)

        semanticMetadataService.softDeleteForEntityType(entityTypeId)
        entityTypeRepository.delete(existing)

        activityService.log(
            activity = Activity.ENTITY_TYPE,
            operation = OperationType.DELETE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.ENTITY_TYPE,
            entityId = entityTypeId,
            "type" to existing.key
        )

        return EntityTypeImpactResponse(
            impact = null,
            updatedEntityTypes = null,
            error = null
        )
    }


    // ------ Public read operations ------

    /**
     * Get all entity types for a workspace, enriched with relationship definitions
     * and semantic metadata.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getWorkspaceEntityTypesWithIncludes(workspaceId: UUID): List<EntityType> {
        val entityTypes = getWorkspaceEntityTypes(workspaceId)
        val entityTypeIds = entityTypes.map { it.id }

        val relationshipMap = entityTypeRelationshipService.getDefinitionsForEntityTypes(workspaceId, entityTypeIds)

        val allMetadata = semanticMetadataService.getMetadataForEntityTypes(entityTypeIds)
        val metadataByEntityType = allMetadata.groupBy { it.entityTypeId }
        val bundleMap = entityTypes.associate { et ->
            et.id to buildSemanticBundle(et.id, metadataByEntityType[et.id] ?: emptyList())
        }

        return entityTypes.map { et ->
            et.copy(
                relationships = relationshipMap[et.id] ?: emptyList(),
                semantics = bundleMap[et.id],
            )
        }
    }

    /**
     * Get a single entity type by key, enriched with relationship definitions
     * and semantic metadata.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getEntityTypeByKeyWithIncludes(workspaceId: UUID, key: String): EntityType {
        val entityType = getByKey(key, workspaceId).toModel()

        val relationships = entityTypeRelationshipService.getDefinitionsForEntityType(workspaceId, entityType.id)

        val allMetadata = semanticMetadataService.getAllMetadataForEntityType(workspaceId, entityType.id)
        val bundle = buildSemanticBundle(entityType.id, allMetadata)

        return entityType.copy(
            relationships = relationships,
            semantics = bundle,
        )
    }

    /**
     * Get all entity types for a workspace (including system types).
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getWorkspaceEntityTypes(workspaceId: UUID): List<EntityType> {
        return ServiceUtil.findManyResults {
            entityTypeRepository.findByworkspaceId(workspaceId)
        }.map { it.toModel() }
    }

    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getByKey(key: String, workspaceId: UUID): EntityTypeEntity {
        return ServiceUtil.findOrThrow { entityTypeRepository.findByworkspaceIdAndKey(workspaceId, key) }
    }

    /**
     * Get entity type by ID.
     */
    fun getById(id: UUID): EntityTypeEntity {
        return ServiceUtil.findOrThrow { entityTypeRepository.findById(id) }
    }

    /**
     * Get entity types by IDs.
     */
    fun getByIds(ids: Collection<UUID>): List<EntityTypeEntity> {
        return ServiceUtil.findManyResults { entityTypeRepository.findAllById(ids) }
    }

    // ------ Relationship Helpers ------
    /**
     * Delegates relationship definition create/update to EntityTypeRelationshipService.
     * @return the resolved definition ID and optional impact analysis
     */
    private fun handleSaveRelationshipDefinition(
        workspaceId: UUID,
        entityTypeId: UUID,
        request: SaveRelationshipDefinitionRequest,
        impactConfirmed: Boolean,
    ): Pair<UUID, DeleteDefinitionImpact?> {
        return if (request.id == null) {
            entityTypeRelationshipService.createRelationshipDefinition(workspaceId, entityTypeId, request).id to null
        } else {
            val (_, impact) = entityTypeRelationshipService.updateRelationshipDefinition(
                workspaceId, request.id, request, impactConfirmed,
            )
            request.id to impact
        }
    }

    /**
     * Updates column ordering when a definition is added or reordered.
     */
    private fun updateColumnOrdering(
        existing: EntityTypeEntity,
        resolvedId: UUID,
        propertyType: EntityPropertyType,
        requestIndex: Int?,
    ) {
        val currentIndex = existing.columns.indexOfFirst { it.key == resolvedId }

        if (currentIndex == -1) {
            // New definition being added
            existing.columns = reorderEntityTypeColumns(
                order = existing.columns,
                key = EntityTypeAttributeColumn(key = resolvedId, type = propertyType),
                prev = null,
                new = requestIndex ?: existing.columns.size
            )
        } else {
            // Existing definition being reordered
            requestIndex?.let { newIndex ->
                if (newIndex != currentIndex) {
                    existing.columns = reorderEntityTypeColumns(
                        order = existing.columns,
                        key = EntityTypeAttributeColumn(key = resolvedId, type = propertyType),
                        prev = currentIndex,
                        new = newIndex
                    )
                }
            }
        }
    }

    // ------ Semantic helpers ------

    fun buildSemanticBundle(
        entityTypeId: UUID,
        metadata: List<EntityTypeSemanticMetadata>,
    ): SemanticMetadataBundle {
        return SemanticMetadataBundle(
            entityType = metadata.firstOrNull { it.targetType == SemanticMetadataTargetType.ENTITY_TYPE },
            attributes = metadata.filter { it.targetType == SemanticMetadataTargetType.ATTRIBUTE }
                .associateBy { it.targetId },
            relationships = metadata.filter { it.targetType == SemanticMetadataTargetType.RELATIONSHIP }
                .associateBy { it.targetId },
        )
    }
}