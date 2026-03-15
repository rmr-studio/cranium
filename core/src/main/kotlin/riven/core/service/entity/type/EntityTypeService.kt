package riven.core.service.entity.type

import jakarta.transaction.Transactional
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import riven.core.entity.entity.EntityTypeEntity
import riven.core.entity.entity.RelationshipDefinitionEntity
import riven.core.enums.activity.Activity
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.core.DataType
import riven.core.enums.entity.EntityPropertyType
import riven.core.enums.entity.semantics.SemanticMetadataTargetType
import riven.core.enums.util.OperationType
import riven.core.models.common.validation.Schema
import riven.core.models.entity.EntityType
import riven.core.models.entity.EntityTypeSchema
import riven.core.models.entity.EntityTypeSemanticMetadata
import riven.core.models.common.markDeleted
import riven.core.models.entity.RelationshipDefinition
import riven.core.models.entity.SemanticMetadataBundle
import riven.core.models.entity.configuration.ColumnConfiguration
import riven.core.models.entity.configuration.EntityTypeAttributeColumn
import riven.core.models.integration.IntegrationSoftDeleteResult
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
            columnConfiguration = ColumnConfiguration(
                order = listOf(primaryId)
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

        if (existing.readonly) {
            // Readonly entity types only allow column configuration changes
            request.columnConfiguration?.let { existing.columnConfiguration = it }
        } else {
            existing.apply {
                displayNameSingular = request.name.singular
                displayNamePlural = request.name.plural
                request.semanticGroup?.let { semanticGroup = it }
                iconType = request.icon.type
                iconColour = request.icon.colour
                request.columnConfiguration?.let { columnConfiguration = it }
            }
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
        val (requestIndex: Int?, definition) = request
        val existing =
            ServiceUtil.findOrThrow { entityTypeRepository.findByworkspaceIdAndKey(workspaceId, definition.key) }
        require(!existing.readonly) { "Cannot modify definitions on a readonly entity type '${existing.key}'" }
        val entityTypeId = requireNotNull(existing.id)

        var resolvedDefinitionId: UUID? = definition.id

        when (definition) {
            is SaveAttributeDefinitionRequest -> {
                entityAttributeService.saveAttributeDefinition(workspaceId, existing, definition)
            }

            is SaveRelationshipDefinitionRequest -> {
                val (resolvedId, impact) = handleSaveRelationshipDefinition(workspaceId, entityTypeId, definition, impactConfirmed)
                resolvedDefinitionId = resolvedId
                if (impact != null) {
                    return EntityTypeImpactResponse(impact = impact)
                }
            }

            else -> throw IllegalArgumentException("Unsupported definition type: ${definition::class.java}")
        }

        // Optionally update column ordering if a specific index was requested
        if (requestIndex != null) {
            val definitionId = resolvedDefinitionId ?: return buildImpactResponse(existing, workspaceId)
            appendToColumnOrder(existing, definitionId, requestIndex)
        }

        return buildImpactResponse(existing, workspaceId)
    }

    /**
     * Remove an attribute or relationship definition from an entity type.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun removeEntityTypeDefinition(
        workspaceId: UUID,
        request: DeleteTypeDefinitionRequest,
        impactConfirmed: Boolean = false
    ): EntityTypeImpactResponse {
        val (definition) = request
        val existing =
            ServiceUtil.findOrThrow { entityTypeRepository.findByworkspaceIdAndKey(workspaceId, definition.key) }
        require(!existing.readonly) { "Cannot remove definitions from a readonly entity type '${existing.key}'" }

        when (definition) {
            is DeleteAttributeDefinitionRequest -> {
                entityAttributeService.removeAttributeDefinition(existing, definition.id)
            }

            is DeleteRelationshipDefinitionRequest -> {
                val entityTypeId = requireNotNull(existing.id)
                val impact = handleDeleteRelationshipDefinition(workspaceId, entityTypeId, existing, definition, impactConfirmed)
                if (impact != null) {
                    return EntityTypeImpactResponse(impact = impact)
                }
            }

            else -> throw IllegalArgumentException("Unsupported definition type: ${definition::class.java}")
        }

        // Optionally clean stale ID from column order (assembly handles stale IDs, but keeping order clean is good hygiene)
        val definitionId = requireNotNull(definition.id) { "Delete requests must have a non-null id" }
        removeFromColumnOrder(existing, definitionId)

        return buildImpactResponse(existing, workspaceId)
    }


    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun deleteEntityType(
        workspaceId: UUID,
        key: String,
        impactConfirmed: Boolean = false
    ): EntityTypeImpactResponse {
        val userId = authTokenService.getUserId()
        val existing = ServiceUtil.findOrThrow { entityTypeRepository.findByworkspaceIdAndKey(workspaceId, key) }
        require(!existing.readonly) { "Cannot delete a readonly entity type '${existing.key}'" }
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
            val defId = requireNotNull(def.id)
            entityTypeRelationshipService.deleteRelationshipDefinition(
                workspaceId = workspaceId,
                definitionId = defId,
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


    // ------ Integration Lifecycle ------

    /**
     * Soft-deletes all entity types and their relationship definitions
     * that belong to the given integration in this workspace.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun softDeleteByIntegration(workspaceId: UUID, integrationId: UUID): IntegrationSoftDeleteResult {
        val userId = authTokenService.getUserId()
        val entityTypes = entityTypeRepository.findBySourceIntegrationIdAndWorkspaceId(integrationId, workspaceId)

        if (entityTypes.isEmpty()) return IntegrationSoftDeleteResult(0, 0)

        val entityTypeIds = entityTypes.mapNotNull { it.id }
        val relationships = definitionRepository.findByWorkspaceIdAndSourceEntityTypeIdIn(workspaceId, entityTypeIds)

        val relationshipsSoftDeleted = softDeleteRelationships(relationships)
        val entityTypesSoftDeleted = softDeleteEntityTypes(entityTypes, userId, workspaceId)

        return IntegrationSoftDeleteResult(entityTypesSoftDeleted, relationshipsSoftDeleted)
    }

    /**
     * Restores soft-deleted entity types for a given integration in this workspace.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun restoreByIntegration(workspaceId: UUID, integrationId: UUID): Int {
        val entityTypes = entityTypeRepository.findSoftDeletedBySourceIntegrationIdAndWorkspaceId(integrationId, workspaceId)

        entityTypes.forEach { type ->
            type.deleted = false
            type.deletedAt = null
            entityTypeRepository.save(type)
        }

        return entityTypes.size
    }

    // ------ Public read operations ------

    /**
     * Get all entity types for a workspace, enriched with relationship definitions,
     * semantic metadata, and derived columns.
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
            val relationships = relationshipMap[et.id] ?: emptyList()
            et.copy(
                relationships = relationships,
                semantics = bundleMap[et.id],
                columns = assembleColumns(et.schema, relationships, et.columnConfiguration),
            )
        }
    }

    /**
     * Get a single entity type by key, enriched with relationship definitions,
     * semantic metadata, and derived columns.
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
            columns = assembleColumns(entityType.schema, relationships, entityType.columnConfiguration),
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
     * Handles deletion of a relationship definition, including source-side and target-side removal.
     * @return impact analysis if confirmation is needed, null if deletion succeeded
     */
    private fun handleDeleteRelationshipDefinition(
        workspaceId: UUID,
        entityTypeId: UUID,
        existing: EntityTypeEntity,
        definition: DeleteRelationshipDefinitionRequest,
        impactConfirmed: Boolean,
    ): DeleteDefinitionImpact? {
        val relationshipDef = ServiceUtil.findOrThrow {
            definitionRepository.findByIdAndWorkspaceId(definition.id, workspaceId)
        }
        val isTargetSide = entityTypeId != relationshipDef.sourceEntityTypeId

        return if (isTargetSide) {
            entityTypeRelationshipService.removeTargetRule(
                workspaceId = workspaceId,
                definitionId = definition.id,
                entityTypeId = entityTypeId,
                impactConfirmed = impactConfirmed,
            )
        } else {
            entityTypeRelationshipService.deleteRelationshipDefinition(
                workspaceId = workspaceId,
                definitionId = definition.id,
                impactConfirmed = impactConfirmed,
            )
        }
    }

    // ------ Column Configuration Helpers ------

    /**
     * Appends a definition ID to the column order at the specified index.
     */
    private fun appendToColumnOrder(entity: EntityTypeEntity, definitionId: UUID, index: Int) {
        val config = entity.columnConfiguration ?: ColumnConfiguration()
        val currentOrder = config.order.toMutableList()

        // Remove if already present (reorder case)
        currentOrder.remove(definitionId)

        val insertIndex = index.coerceIn(0, currentOrder.size)
        currentOrder.add(insertIndex, definitionId)

        entity.columnConfiguration = config.copy(order = currentOrder)
    }

    /**
     * Removes a definition ID from the column order.
     */
    private fun removeFromColumnOrder(entity: EntityTypeEntity, definitionId: UUID) {
        val config = entity.columnConfiguration ?: return
        if (definitionId !in config.order) return

        entity.columnConfiguration = config.copy(
            order = config.order.filterNot { it == definitionId },
            overrides = config.overrides.filterKeys { it != definitionId }
        )
    }

    /**
     * Builds an EntityTypeImpactResponse after saving the entity type.
     */
    private fun buildImpactResponse(entity: EntityTypeEntity, workspaceId: UUID): EntityTypeImpactResponse {
        entity.version += 1
        val saved = entityTypeRepository.save(entity)
        val savedId = requireNotNull(saved.id)
        val savedModel = saved.toModel()

        val relationships = entityTypeRelationshipService.getDefinitionsForEntityType(workspaceId, savedId)
        val allMetadata = semanticMetadataService.getAllMetadataForEntityType(workspaceId, savedId)
        val bundle = buildSemanticBundle(savedId, allMetadata)

        val enrichedModel = savedModel.copy(
            relationships = relationships,
            semantics = bundle,
            columns = assembleColumns(savedModel.schema, relationships, savedModel.columnConfiguration),
        )

        return EntityTypeImpactResponse(
            impact = null,
            updatedEntityTypes = mapOf(saved.key to enrichedModel),
            error = null
        )
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

    // ------ Integration Lifecycle Helpers ------

    private fun softDeleteEntityTypes(entityTypes: List<EntityTypeEntity>, userId: UUID, workspaceId: UUID): Int {
        entityTypes.forEach { type ->
            type.markDeleted()
            entityTypeRepository.save(type)

            activityService.log(
                activity = Activity.ENTITY_TYPE,
                operation = OperationType.DELETE,
                userId = userId,
                workspaceId = workspaceId,
                entityType = ApplicationEntityType.ENTITY_TYPE,
                entityId = requireNotNull(type.id),
                "type" to type.key,
                "reason" to "integration_disabled",
            )
        }
        return entityTypes.size
    }

    private fun softDeleteRelationships(relationships: List<RelationshipDefinitionEntity>): Int {
        relationships.forEach { rel ->
            rel.markDeleted()
            definitionRepository.save(rel)
        }
        return relationships.size
    }

    companion object {
        const val DEFAULT_COLUMN_WIDTH = 150

        /**
         * Derives columns at read-time from schema attributes + relationship definitions + stored configuration.
         *
         * Column order is determined by:
         * 1. Explicit `config.order` list (stale IDs filtered out)
         * 2. Newly added IDs not yet in the order list are appended at the end
         * 3. If no config or empty order, falls back to schema attribute order then relationships
         *
         * @param schema the entity type schema containing attribute definitions
         * @param relationships the relationship definitions for this entity type
         * @param config optional stored column configuration with ordering and display overrides
         */
        fun assembleColumns(
            schema: EntityTypeSchema,
            relationships: List<RelationshipDefinition>,
            config: ColumnConfiguration?
        ): List<EntityTypeAttributeColumn> {
            val attributeIds = schema.properties?.keys ?: emptySet()
            val relationshipIds = relationships.map { it.id }.toSet()
            val allIds = attributeIds + relationshipIds

            val orderedIds = config?.order
                ?.filter { it in allIds }  // skip stale IDs
                ?.distinct()               // deduplicate
                ?.takeIf { it.isNotEmpty() }
                ?: (attributeIds.toList() + relationshipIds.toList())  // default order

            val unorderedIds = allIds - orderedIds.toSet()  // newly added, not yet positioned

            return (orderedIds + unorderedIds).map { id ->
                val type = if (id in attributeIds) EntityPropertyType.ATTRIBUTE else EntityPropertyType.RELATIONSHIP
                val override = config?.overrides?.get(id)
                EntityTypeAttributeColumn(
                    key = id,
                    type = type,
                    width = override?.width ?: DEFAULT_COLUMN_WIDTH,
                    visible = override?.visible ?: true
                )
            }
        }
    }
}
