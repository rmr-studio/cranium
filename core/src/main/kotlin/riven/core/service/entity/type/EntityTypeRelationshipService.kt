package riven.core.service.entity.type

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.entity.RelationshipDefinitionEntity
import riven.core.entity.entity.RelationshipTargetRuleEntity
import riven.core.enums.activity.Activity
import org.springframework.dao.DataIntegrityViolationException
import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.entity.SystemRelationshipType
import riven.core.enums.entity.semantics.SemanticMetadataTargetType
import riven.core.enums.util.OperationType
import riven.core.models.entity.RelationshipDefinition
import riven.core.models.entity.RelationshipTargetRule
import riven.core.models.request.entity.type.SaveRelationshipDefinitionRequest
import riven.core.models.request.entity.type.SaveTargetRuleRequest
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.entity.RelationshipDefinitionRepository
import riven.core.repository.entity.RelationshipTargetRuleRepository
import riven.core.service.activity.ActivityService
import riven.core.service.activity.log
import riven.core.service.auth.AuthTokenService
import riven.core.models.response.entity.type.DeleteDefinitionImpact
import riven.core.service.entity.EntityTypeSemanticMetadataService
import riven.core.exceptions.NotFoundException
import riven.core.util.ServiceUtil
import java.util.*

/**
 * Service for managing relationship definitions and their target rules.
 *
 * Handles CRUD for type-level relationship configuration stored in
 * `relationship_definitions` and `relationship_target_rules` tables.
 */
@Service
class EntityTypeRelationshipService(
    private val definitionRepository: RelationshipDefinitionRepository,
    private val targetRuleRepository: RelationshipTargetRuleRepository,
    private val entityRelationshipRepository: EntityRelationshipRepository,
    private val entityTypeRepository: EntityTypeRepository,
    private val activityService: ActivityService,
    private val authTokenService: AuthTokenService,
    private val semanticMetadataService: EntityTypeSemanticMetadataService,
    private val logger: KLogger,
) {

    // ------ Create ------

    /**
     * Creates a new relationship definition with its target rules.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun createRelationshipDefinition(
        workspaceId: UUID,
        sourceEntityTypeId: UUID,
        request: SaveRelationshipDefinitionRequest,
    ): RelationshipDefinition {
        val userId = authTokenService.getUserId()
        return createRelationshipDefinitionInternal(workspaceId, sourceEntityTypeId, request, userId)
    }

    /**
     * Internal variant of [createRelationshipDefinition] without @PreAuthorize.
     *
     * Used by [riven.core.service.catalog.TemplateInstallationService] during onboarding
     * when the workspace was just created and the JWT does not yet contain the new
     * workspace's role authorities.
     *
     * @param userId the user performing the operation (passed explicitly since JWT may not reflect workspace membership)
     */
    @Transactional
    internal fun createRelationshipDefinitionInternal(
        workspaceId: UUID,
        sourceEntityTypeId: UUID,
        request: SaveRelationshipDefinitionRequest,
        userId: UUID,
    ): RelationshipDefinition {
        val sourceEntityType = ServiceUtil.findOrThrow { entityTypeRepository.findById(sourceEntityTypeId) }
        require(sourceEntityType.workspaceId == workspaceId) { "Entity type $sourceEntityTypeId not found in workspace $workspaceId" }
        require(!sourceEntityType.readonly) { "Cannot create relationships on a readonly entity type '${sourceEntityType.key}'" }

        val entity = RelationshipDefinitionEntity(
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = request.name,
            iconType = request.iconType ?: IconType.LINK,
            iconColour = request.iconColour ?: IconColour.NEUTRAL,
            cardinalityDefault = request.cardinalityDefault,
        )
        val savedDefinition = definitionRepository.save(entity)
        val defId = requireNotNull(savedDefinition.id)

        validateTargetEntityTypeWorkspaces(request.targetRules, workspaceId)

        val ruleEntities = buildTargetRuleEntities(defId, request.targetRules)
        val savedRules = targetRuleRepository.saveAll(ruleEntities)

        if (request.semantics != null) {
            semanticMetadataService.upsertMetadataInternal(
                workspaceId, sourceEntityTypeId, SemanticMetadataTargetType.RELATIONSHIP, defId, request.semantics,
            )
        } else {
            semanticMetadataService.initializeForTarget(
                entityTypeId = sourceEntityTypeId,
                workspaceId = workspaceId,
                targetType = SemanticMetadataTargetType.RELATIONSHIP,
                targetId = defId,
            )
        }

        activityService.logActivity(
            activity = Activity.ENTITY_RELATIONSHIP,
            operation = OperationType.CREATE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.ENTITY_TYPE,
            entityId = sourceEntityTypeId,
            details = mapOf(
                "relationshipId" to defId.toString(),
                "relationshipName" to request.name,
            ),
        )

        logger.info { "Created relationship definition '${request.name}' ($defId) for entity type $sourceEntityTypeId" }

        return savedDefinition.toModel(savedRules.map { it.toModel() })
    }

    // ------ Update ------

    /**
     * Updates an existing relationship definition and diffs its target rules.
     *
     * @return the updated definition model paired with null (impact analysis no longer applicable)
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun updateRelationshipDefinition(
        workspaceId: UUID,
        definitionId: UUID,
        request: SaveRelationshipDefinitionRequest,
        impactConfirmed: Boolean = false,
    ): Pair<RelationshipDefinition?, DeleteDefinitionImpact?> {
        val userId = authTokenService.getUserId()
        val existing = ServiceUtil.findOrThrow { definitionRepository.findByIdAndWorkspaceId(definitionId, workspaceId) }
        existing.name = request.name
        existing.iconType = request.iconType ?: existing.iconType
        existing.iconColour = request.iconColour ?: existing.iconColour
        existing.cardinalityDefault = request.cardinalityDefault

        val savedDefinition = definitionRepository.save(existing)
        val updatedRules = diffTargetRules(definitionId, request.targetRules)

        if (request.semantics != null) {
            semanticMetadataService.upsertMetadataInternal(
                workspaceId, existing.sourceEntityTypeId,
                SemanticMetadataTargetType.RELATIONSHIP, definitionId, request.semantics,
            )
        }

        activityService.log(
            activity = Activity.ENTITY_RELATIONSHIP,
            operation = OperationType.UPDATE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.ENTITY_TYPE,
            entityId = existing.sourceEntityTypeId,
            "relationshipId" to definitionId.toString(),
            "relationshipName" to request.name,
        )

        logger.info { "Updated relationship definition '${request.name}' ($definitionId)" }

        return savedDefinition.toModel(updatedRules) to null
    }

    // ------ Delete ------

    /**
     * Deletes a relationship definition using the two-pass impact pattern.
     *
     * If instance data exists and `impactConfirmed` is false, returns impact analysis.
     * If `impactConfirmed` is true or no instance data exists, soft-deletes the definition,
     * hard-deletes the rules, and cleans up semantic metadata.
     *
     * @return Impact analysis if confirmation needed, null if deletion was executed
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun deleteRelationshipDefinition(
        workspaceId: UUID,
        definitionId: UUID,
        impactConfirmed: Boolean,
    ): DeleteDefinitionImpact? {
        val userId = authTokenService.getUserId()
        val existing = ServiceUtil.findOrThrow { definitionRepository.findByIdAndWorkspaceId(definitionId, workspaceId) }

        check(!existing.protected) {
            "Cannot delete protected relationship '${existing.name}' ($definitionId). Protected relationships are system-managed."
        }

        val linkCount = entityRelationshipRepository.countByDefinitionId(definitionId)

        if (linkCount > 0 && !impactConfirmed) {
            return DeleteDefinitionImpact(
                definitionId = definitionId,
                definitionName = existing.name,
                impactedLinkCount = linkCount,
            )
        }

        executeDeletion(existing, definitionId, workspaceId, userId)
        return null
    }

    private fun executeDeletion(
        entity: RelationshipDefinitionEntity,
        definitionId: UUID,
        workspaceId: UUID,
        userId: UUID,
    ) {
        // Soft-delete associated relationship links
        entityRelationshipRepository.softDeleteByDefinitionId(definitionId)

        // Soft-delete the definition
        entity.deleted = true
        entity.deletedAt = java.time.ZonedDateTime.now()
        definitionRepository.save(entity)

        // Hard-delete target rules (configuration, not user data)
        targetRuleRepository.deleteByRelationshipDefinitionId(definitionId)

        // Clean up semantic metadata
        semanticMetadataService.deleteForTarget(
            entityTypeId = entity.sourceEntityTypeId,
            targetType = SemanticMetadataTargetType.RELATIONSHIP,
            targetId = definitionId,
        )

        activityService.log(
            activity = Activity.ENTITY_RELATIONSHIP,
            operation = OperationType.DELETE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.ENTITY_TYPE,
            entityId = entity.sourceEntityTypeId,
            "relationshipId" to definitionId.toString(),
            "relationshipName" to entity.name,
        )

        logger.info { "Deleted relationship definition '${entity.name}' ($definitionId)" }
    }

    // ------ Read ------

    /**
     * Returns all relationship definitions an entity type participates in,
     * including forward definitions (where the type is the source) and
     * inverse definitions (where the type is a target of another definition).
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getDefinitionsForEntityType(
        workspaceId: UUID,
        entityTypeId: UUID,
    ): List<RelationshipDefinition> {
        // Forward definitions: this type is the source
        val forwardEntities = definitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, entityTypeId)

        // Inverse definitions: this type is a target via explicit rule
        val inverseRules = targetRuleRepository.findByTargetEntityTypeId(entityTypeId)
        val inverseDefIds = inverseRules.map { it.relationshipDefinitionId }.distinct().toMutableSet()

        // Exclude forward definition IDs to prevent duplicates when source type matches its own rules
        val forwardDefIds = forwardEntities.mapNotNull { it.id }.toSet()
        inverseDefIds.removeAll(forwardDefIds)

        val inverseEntities = if (inverseDefIds.isNotEmpty()) {
            definitionRepository.findAllById(inverseDefIds.toList())
                .filter { it.workspaceId == workspaceId }
        } else {
            emptyList()
        }

        // Hydrate all definitions with their rules
        val allDefIds = forwardDefIds.toList() + inverseEntities.mapNotNull { it.id }
        val allRulesByDef = if (allDefIds.isNotEmpty()) {
            targetRuleRepository.findByRelationshipDefinitionIdIn(allDefIds)
                .groupBy { it.relationshipDefinitionId }
        } else {
            emptyMap()
        }

        val forwardModels = forwardEntities.map { entity ->
            val rules = allRulesByDef[entity.id]?.map { it.toModel() } ?: emptyList()
            entity.toModel(rules)
        }

        val inverseModels = inverseEntities.map { entity ->
            val rules = allRulesByDef[entity.id]?.map { it.toModel() } ?: emptyList()
            entity.toModel(rules)
        }

        return forwardModels + inverseModels
    }

    /**
     * Batch-fetches relationship definitions for multiple entity types.
     *
     * Returns a map keyed by entity type ID, where each value contains both forward
     * definitions (type is source) and inverse definitions (type is target).
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getDefinitionsForEntityTypes(
        workspaceId: UUID,
        entityTypeIds: List<UUID>,
    ): Map<UUID, List<RelationshipDefinition>> {
        if (entityTypeIds.isEmpty()) return emptyMap()

        // Forward definitions: these types are the source
        val forwardEntities = definitionRepository.findByWorkspaceIdAndSourceEntityTypeIdIn(workspaceId, entityTypeIds)

        // Inverse definitions: these types are a target in another definition's rules
        val inverseRules = targetRuleRepository.findByTargetEntityTypeIdIn(entityTypeIds)
        val inverseDefIds = inverseRules.map { it.relationshipDefinitionId }.distinct()
        val inverseEntities = if (inverseDefIds.isNotEmpty()) {
            definitionRepository.findAllById(inverseDefIds)
        } else {
            emptyList()
        }

        // Hydrate all definitions with their rules in a single batch
        val allDefIds = (forwardEntities.mapNotNull { it.id } + inverseDefIds).distinct()
        val rulesByDefId = if (allDefIds.isNotEmpty()) {
            targetRuleRepository.findByRelationshipDefinitionIdIn(allDefIds)
                .groupBy { it.relationshipDefinitionId }
        } else {
            emptyMap()
        }

        // Deduplicate definitions (a definition can appear in both forward and inverse)
        val allDefinitions = (forwardEntities + inverseEntities)
            .distinctBy { it.id }

        val models = allDefinitions.map { entity ->
            val rules = rulesByDefId[entity.id]?.map { it.toModel() } ?: emptyList()
            entity.toModel(rules)
        }

        return entityTypeIds.associateWith { entityTypeId ->
            models.filter { def ->
                if (def.sourceEntityTypeId == entityTypeId) {
                    true // Forward definitions are never filtered for the source
                } else {
                    def.targetRules.any { it.targetEntityTypeId == entityTypeId }
                }
            }
        }
    }

    /**
     * Returns all relationship definitions for an entity type, keyed by definition ID.
     * Includes both forward and inverse definitions.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getDefinitionsForEntityTypeAsMap(
        workspaceId: UUID,
        entityTypeId: UUID,
    ): Map<UUID, RelationshipDefinition> {
        return getDefinitionsForEntityType(workspaceId, entityTypeId).associateBy { it.id }
    }

    /**
     * Retrieves a single relationship definition by ID with its target rules.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getDefinitionById(
        workspaceId: UUID,
        definitionId: UUID,
    ): RelationshipDefinition {
        val entity = ServiceUtil.findOrThrow { definitionRepository.findByIdAndWorkspaceId(definitionId, workspaceId) }
        val rules = targetRuleRepository.findByRelationshipDefinitionId(definitionId).map { it.toModel() }
        return entity.toModel(rules)
    }

    // ------ Remove Target Rule ------

    /**
     * Removes a target rule for an entity type from a relationship definition.
     *
     * If this is the last target rule on the definition, the entire definition is deleted
     * (cascading to all instance links). Otherwise, only the rule and its associated
     * instance links are removed.
     *
     * Uses the two-pass impact pattern: if instance data exists and `impactConfirmed`
     * is false, returns impact analysis for user confirmation.
     *
     * @return Impact analysis if confirmation needed, null if removal was executed
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun removeTargetRule(
        workspaceId: UUID,
        definitionId: UUID,
        entityTypeId: UUID,
        impactConfirmed: Boolean,
    ): DeleteDefinitionImpact? {
        val userId = authTokenService.getUserId()
        val definition = ServiceUtil.findOrThrow { definitionRepository.findByIdAndWorkspaceId(definitionId, workspaceId) }

        require(definition.sourceEntityTypeId != entityTypeId) {
            "Cannot remove the source entity type from its own definition"
        }

        validateEntityTypeBelongsToWorkspace(entityTypeId, workspaceId)

        val allRules = targetRuleRepository.findByRelationshipDefinitionId(definitionId)
        val targetRule = allRules.find { it.targetEntityTypeId == entityTypeId }
            ?: throw NotFoundException("No target rule for entity type $entityTypeId on definition $definitionId")

        val isLastRule = allRules.size == 1

        if (isLastRule) {
            return deleteRelationshipDefinition(workspaceId, definitionId, impactConfirmed)
        }

        val linkCount = entityRelationshipRepository.countByDefinitionIdAndTargetEntityTypeId(definitionId, entityTypeId)

        if (linkCount > 0 && !impactConfirmed) {
            return DeleteDefinitionImpact(
                definitionId = definitionId,
                definitionName = definition.name,
                impactedLinkCount = linkCount,
                deletesDefinition = false,
            )
        }

        if (linkCount > 0) {
            entityRelationshipRepository.softDeleteByDefinitionIdAndTargetEntityTypeId(definitionId, entityTypeId)
        }

        targetRuleRepository.delete(targetRule)

        activityService.log(
            activity = Activity.ENTITY_RELATIONSHIP,
            operation = OperationType.DELETE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.ENTITY_TYPE,
            entityId = entityTypeId,
            "relationshipId" to definitionId.toString(),
            "action" to "remove_target_rule",
            "isLastRule" to false,
        )

        logger.info { "Removed target rule for entity type $entityTypeId from definition $definitionId" }
        return null
    }

    // ------ Private helpers ------

    /**
     * Validates that the given entity type belongs to the specified workspace.
     * Inline check required because findById doesn't filter by workspace.
     */
    private fun validateEntityTypeBelongsToWorkspace(entityTypeId: UUID, workspaceId: UUID) {
        val entityType = ServiceUtil.findOrThrow { entityTypeRepository.findById(entityTypeId) }
        if (entityType.workspaceId != workspaceId) {
            throw NotFoundException("Entity type $entityTypeId not found in workspace $workspaceId")
        }
    }

    /**
     * Diffs the requested target rules against existing rules, performing add/remove/update operations.
     */
    private fun diffTargetRules(
        definitionId: UUID,
        requestedRules: List<SaveTargetRuleRequest>,
    ): List<RelationshipTargetRule> {
        val existingRules = targetRuleRepository.findByRelationshipDefinitionId(definitionId)
        val existingById = existingRules.associateBy { it.id }

        // Rules with an ID that matches existing -> keep/update; without -> new; existing not in request -> remove
        val requestedIds = requestedRules.mapNotNull { it.id }.toSet()
        val toRemove = existingRules.filter { it.id !in requestedIds }
        if (toRemove.isNotEmpty()) {
            targetRuleRepository.deleteAll(toRemove)
        }

        val toSave = requestedRules.map { req ->
            requireNotNull(req.targetEntityTypeId) {
                "Target rule must specify targetEntityTypeId"
            }
            if (req.id != null && existingById.containsKey(req.id)) {
                // Update existing rule
                val existing = existingById[req.id]!!
                existing.copy(
                    targetEntityTypeId = req.targetEntityTypeId,
                    cardinalityOverride = req.cardinalityOverride,
                    inverseName = req.inverseName,
                )
            } else {
                // New rule
                RelationshipTargetRuleEntity(
                    relationshipDefinitionId = definitionId,
                    targetEntityTypeId = req.targetEntityTypeId,
                    cardinalityOverride = req.cardinalityOverride,
                    inverseName = req.inverseName,
                )
            }
        }

        return targetRuleRepository.saveAll(toSave).map { it.toModel() }
    }

    private fun validateTargetEntityTypeWorkspaces(rules: List<SaveTargetRuleRequest>, workspaceId: UUID) {
        rules.forEach { rule ->
            val targetEntityTypeId = requireNotNull(rule.targetEntityTypeId) { "Target rule must specify targetEntityTypeId" }
            val targetEntityType = ServiceUtil.findOrThrow { entityTypeRepository.findById(targetEntityTypeId) }
            require(targetEntityType.workspaceId == workspaceId) {
                "Target entity type $targetEntityTypeId does not belong to workspace $workspaceId"
            }
        }
    }

    private fun buildTargetRuleEntities(
        definitionId: UUID,
        rules: List<SaveTargetRuleRequest>,
    ): List<RelationshipTargetRuleEntity> {
        return rules.map { rule ->
            requireNotNull(rule.targetEntityTypeId) {
                "Target rule must specify targetEntityTypeId"
            }
            RelationshipTargetRuleEntity(
                relationshipDefinitionId = definitionId,
                targetEntityTypeId = rule.targetEntityTypeId,
                cardinalityOverride = rule.cardinalityOverride,
                inverseName = rule.inverseName,
            )
        }
    }

    // ------ System Definitions ------

    /**
     * Creates a CONNECTED_ENTITIES fallback definition for an entity type.
     * Used at publish time to ensure every entity type has a system-managed connection definition.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun createFallbackDefinition(workspaceId: UUID, entityTypeId: UUID): RelationshipDefinitionEntity {
        val userId = authTokenService.getUserId()
        return createSystemDefinitionInternal(workspaceId, entityTypeId, SystemRelationshipType.CONNECTED_ENTITIES, userId)
    }

    /**
     * Internal variant of [createFallbackDefinition] without @PreAuthorize.
     *
     * Used by [riven.core.service.catalog.TemplateInstallationService] during onboarding
     * when the JWT does not yet contain the new workspace's role authorities.
     */
    @Transactional
    internal fun createFallbackDefinitionInternal(
        workspaceId: UUID,
        entityTypeId: UUID,
        userId: UUID? = null,
    ): RelationshipDefinitionEntity =
        createSystemDefinitionInternal(workspaceId, entityTypeId, SystemRelationshipType.CONNECTED_ENTITIES, userId)

    /**
     * Returns the existing system relationship definition for `(sourceEntityTypeId, systemType)`,
     * creating it if absent. Handles concurrent creation via the unique constraint
     * (catches DataIntegrityViolationException and retries the read).
     *
     * The same enum kind (e.g. ATTACHMENT, MENTION, DEFINES) is reused across knowledge
     * entity types — concrete semantics live in the (sourceEntityType, systemType) pair.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getOrCreateSystemDefinition(
        workspaceId: UUID,
        sourceEntityTypeId: UUID,
        systemType: SystemRelationshipType,
    ): RelationshipDefinitionEntity {
        val userId = authTokenService.getUserId()
        return getOrCreateSystemDefinitionInternal(workspaceId, sourceEntityTypeId, systemType, userId = userId)
    }

    /**
     * Internal variant of [getOrCreateSystemDefinition] without @PreAuthorize. Used by
     * [riven.core.service.catalog.TemplateInstallationService] during onboarding where the JWT
     * does not yet contain the new workspace's role authorities, and by the reused-entity-type
     * promotion path where the seeding must be idempotent. Activity logging is skipped when
     * [userId] is null (background / pre-auth contexts).
     */
    @Transactional
    internal fun getOrCreateSystemDefinitionInternal(
        workspaceId: UUID,
        sourceEntityTypeId: UUID,
        systemType: SystemRelationshipType,
        userId: UUID? = null,
    ): RelationshipDefinitionEntity {
        val existing = definitionRepository.findBySourceEntityTypeIdAndSystemType(sourceEntityTypeId, systemType)
        if (existing.isPresent) {
            val def = existing.get()
            require(def.workspaceId == workspaceId) {
                "System definition ${def.id} for source $sourceEntityTypeId belongs to workspace " +
                    "${def.workspaceId} but was requested under workspace $workspaceId"
            }
            return def
        }

        return try {
            createSystemDefinitionInternal(workspaceId, sourceEntityTypeId, systemType, userId = userId)
        } catch (e: DataIntegrityViolationException) {
            logger.warn { "Concurrent $systemType creation for entity type $sourceEntityTypeId, retrying read" }
            definitionRepository.findBySourceEntityTypeIdAndSystemType(sourceEntityTypeId, systemType)
                .orElseThrow { e }
        }
    }

    /**
     * Generic system relationship definition factory. Default values per system type
     * are documented inline; all use MANY_TO_MANY cardinality and are protected.
     */
    /**
     * REQUIRES_NEW so a concurrent unique-constraint collision rolls back ONLY this inner
     * transaction — leaving the calling [getOrCreateSystemDefinitionInternal] free to retry
     * the read in its own transaction. Without this, a parent transaction marked
     * rollback-only by the inner save() failure would block the catch-and-retry path.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    internal fun createSystemDefinitionInternal(
        workspaceId: UUID,
        sourceEntityTypeId: UUID,
        systemType: SystemRelationshipType,
        userId: UUID? = null,
    ): RelationshipDefinitionEntity {
        val entityType = ServiceUtil.findOrThrow { entityTypeRepository.findById(sourceEntityTypeId) }
        require(entityType.workspaceId == workspaceId) { "Entity type $sourceEntityTypeId not found in workspace $workspaceId" }

        val name = when (systemType) {
            SystemRelationshipType.CONNECTED_ENTITIES -> "Connected Entities"
            SystemRelationshipType.ATTACHMENT -> "Attachments"
            SystemRelationshipType.MENTION -> "Mentions"
            SystemRelationshipType.DEFINES -> "Defines"
            SystemRelationshipType.INCLUDES -> "Includes"
        }
        val entity = RelationshipDefinitionEntity(
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = name,
            iconType = IconType.LINK,
            iconColour = IconColour.NEUTRAL,
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
            protected = true,
            systemType = systemType,
        )
        val saved = definitionRepository.save(entity)

        userId?.let { uid ->
            activityService.logActivity(
                activity = Activity.ENTITY_RELATIONSHIP,
                operation = OperationType.CREATE,
                userId = uid,
                workspaceId = workspaceId,
                entityType = ApplicationEntityType.ENTITY_TYPE,
                entityId = sourceEntityTypeId,
                details = mapOf(
                    "relationshipId" to requireNotNull(saved.id).toString(),
                    "relationshipName" to saved.name,
                    "systemType" to systemType.name,
                ),
            )
        }

        logger.info { "Created $systemType definition for entity type $sourceEntityTypeId" }
        return saved
    }

    /**
     * Returns the existing fallback definition or creates one if absent.
     * Handles concurrent creation via unique constraint by catching DataIntegrityViolationException
     * and retrying with a read.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getOrCreateFallbackDefinition(workspaceId: UUID, entityTypeId: UUID): RelationshipDefinitionEntity =
        getOrCreateSystemDefinition(workspaceId, entityTypeId, SystemRelationshipType.CONNECTED_ENTITIES)

    /**
     * Read-only lookup returning the fallback definition ID, or null if none exists.
     */
    fun getFallbackDefinitionId(entityTypeId: UUID): UUID? {
        return definitionRepository.findBySourceEntityTypeIdAndSystemType(
            entityTypeId, SystemRelationshipType.CONNECTED_ENTITIES,
        ).map { it.id }.orElse(null)
    }
}
