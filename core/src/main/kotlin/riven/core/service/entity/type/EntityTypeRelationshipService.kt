package riven.core.service.entity.type

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
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
import riven.core.entity.entity.RelationshipDefinitionExclusionEntity
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.entity.RelationshipDefinitionExclusionRepository
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
    private val exclusionRepository: RelationshipDefinitionExclusionRepository,
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

        val entity = RelationshipDefinitionEntity(
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = request.name,
            iconType = request.iconType ?: IconType.LINK,
            iconColour = request.iconColour ?: IconColour.NEUTRAL,
            allowPolymorphic = request.allowPolymorphic,
            cardinalityDefault = request.cardinalityDefault,
        )
        val savedDefinition = definitionRepository.save(entity)
        val defId = requireNotNull(savedDefinition.id)

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

        activityService.log(
            activity = Activity.ENTITY_RELATIONSHIP,
            operation = OperationType.CREATE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.ENTITY_TYPE,
            entityId = sourceEntityTypeId,
            "relationshipId" to defId.toString(),
            "relationshipName" to request.name,
        )

        logger.info { "Created relationship definition '${request.name}' ($defId) for entity type $sourceEntityTypeId" }

        return savedDefinition.toModel(savedRules.map { it.toModel() })
    }

    // ------ Update ------

    /**
     * Updates an existing relationship definition and diffs its target rules.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun updateRelationshipDefinition(
        workspaceId: UUID,
        definitionId: UUID,
        request: SaveRelationshipDefinitionRequest,
    ): RelationshipDefinition {
        val userId = authTokenService.getUserId()
        val existing = ServiceUtil.findOrThrow { definitionRepository.findByIdAndWorkspaceId(definitionId, workspaceId) }

        existing.name = request.name
        existing.iconType = request.iconType ?: existing.iconType
        existing.iconColour = request.iconColour ?: existing.iconColour
        existing.allowPolymorphic = request.allowPolymorphic
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

        return savedDefinition.toModel(updatedRules)
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

        // Hard-delete target rules and exclusions (configuration, not user data)
        targetRuleRepository.deleteByRelationshipDefinitionId(definitionId)
        exclusionRepository.deleteByRelationshipDefinitionId(definitionId)

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

        // Inverse definitions: this type is a target in another definition's rules
        val inverseRules = targetRuleRepository.findByTargetEntityTypeId(entityTypeId)
        val inverseDefIds = inverseRules.map { it.relationshipDefinitionId }.distinct()
        val inverseEntities = if (inverseDefIds.isNotEmpty()) {
            definitionRepository.findAllById(inverseDefIds)
        } else {
            emptyList()
        }

        // Filter out inverse definitions where this entity type is excluded
        val exclusions = exclusionRepository.findByEntityTypeId(entityTypeId)
        val excludedDefIds = exclusions.map { it.relationshipDefinitionId }.toSet()
        val filteredInverseEntities = inverseEntities.filter { it.id !in excludedDefIds }

        // Hydrate forward definitions with their rules and exclusions
        val forwardDefIds = forwardEntities.mapNotNull { it.id }
        val allDefIds = forwardDefIds + filteredInverseEntities.mapNotNull { it.id }
        val allRulesByDef = if (allDefIds.isNotEmpty()) {
            targetRuleRepository.findByRelationshipDefinitionIdIn(allDefIds)
                .groupBy { it.relationshipDefinitionId }
        } else {
            emptyMap()
        }
        val allExclusionsByDef = if (allDefIds.isNotEmpty()) {
            exclusionRepository.findByRelationshipDefinitionIdIn(allDefIds)
                .groupBy { it.relationshipDefinitionId }
        } else {
            emptyMap()
        }

        val forwardModels = forwardEntities.map { entity ->
            val rules = allRulesByDef[entity.id]?.map { it.toModel() } ?: emptyList()
            val excl = allExclusionsByDef[entity.id]?.map { it.entityTypeId } ?: emptyList()
            entity.toModel(rules, excl)
        }

        val inverseModels = filteredInverseEntities.map { entity ->
            val rules = allRulesByDef[entity.id]?.map { it.toModel() } ?: emptyList()
            val excl = allExclusionsByDef[entity.id]?.map { it.entityTypeId } ?: emptyList()
            entity.toModel(rules, excl)
        }

        return forwardModels + inverseModels
    }

    /**
     * Batch-fetches relationship definitions for multiple entity types using a single
     * JPQL query that LEFT JOINs definitions with their target rules.
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

        val rows = definitionRepository.findDefinitionsWithRulesForEntityTypes(workspaceId, entityTypeIds)

        val definitions = linkedMapOf<UUID, RelationshipDefinitionEntity>()
        val rulesByDefId = mutableMapOf<UUID, MutableList<RelationshipTargetRule>>()

        for (row in rows) {
            val def = row[0] as RelationshipDefinitionEntity
            val rule = row[1] as? RelationshipTargetRuleEntity
            val defId = requireNotNull(def.id)

            definitions[defId] = def
            if (rule != null) {
                rulesByDefId.getOrPut(defId) { mutableListOf() }.add(rule.toModel())
            }
        }

        // Load exclusions for all queried entity types
        val exclusionsByEntityType = exclusionRepository.findByEntityTypeIdIn(entityTypeIds)
            .groupBy({ it.entityTypeId }, { it.relationshipDefinitionId })
            .mapValues { (_, defIds) -> defIds.toSet() }

        // Load exclusions per definition for the model
        val defIds = definitions.keys.toList()
        val exclusionsByDefId = if (defIds.isNotEmpty()) {
            exclusionRepository.findByRelationshipDefinitionIdIn(defIds)
                .groupBy({ it.relationshipDefinitionId }, { it.entityTypeId })
        } else {
            emptyMap()
        }

        val models = definitions.values.map { def ->
            def.toModel(
                rulesByDefId[def.id] ?: emptyList(),
                exclusionsByDefId[def.id] ?: emptyList(),
            )
        }

        return entityTypeIds.associateWith { entityTypeId ->
            val excludedDefs = exclusionsByEntityType[entityTypeId] ?: emptySet()
            models.filter { def ->
                if (def.sourceEntityTypeId == entityTypeId) {
                    true // Forward definitions are never filtered for the source
                } else {
                    def.id !in excludedDefs &&
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
        val excludedTypeIds = exclusionRepository.findByRelationshipDefinitionId(definitionId).map { it.entityTypeId }
        return entity.toModel(rules, excludedTypeIds)
    }

    // ------ Exclusions ------

    /**
     * Excludes an entity type from a relationship definition (target-side opt-out).
     *
     * If the entity type has an explicit target rule, deletes the rule instead.
     * Soft-deletes any existing instance links between this entity type and the definition.
     *
     * @return Impact analysis if confirmation needed, null if exclusion was executed
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun excludeEntityTypeFromDefinition(
        workspaceId: UUID,
        definitionId: UUID,
        entityTypeId: UUID,
        impactConfirmed: Boolean,
    ): DeleteDefinitionImpact? {
        val userId = authTokenService.getUserId()
        val definition = ServiceUtil.findOrThrow { definitionRepository.findByIdAndWorkspaceId(definitionId, workspaceId) }

        require(definition.sourceEntityTypeId != entityTypeId) {
            "Cannot exclude the source entity type from its own definition"
        }

        validateEntityTypeBelongsToWorkspace(entityTypeId, workspaceId)

        val linkCount = entityRelationshipRepository.countByDefinitionIdAndTargetEntityTypeId(definitionId, entityTypeId)

        if (linkCount > 0 && !impactConfirmed) {
            return DeleteDefinitionImpact(
                definitionId = definitionId,
                definitionName = definition.name,
                impactedLinkCount = linkCount,
            )
        }

        // Soft-delete instance links for this entity type
        if (linkCount > 0) {
            entityRelationshipRepository.softDeleteByDefinitionIdAndTargetEntityTypeId(definitionId, entityTypeId)
        }

        if (definition.allowPolymorphic) {
            // Polymorphic definitions have no explicit rules — exclusion is the only mechanism
            saveExclusionRecord(definitionId, entityTypeId)
        } else {
            // Rule-based definition: delete explicit rule if present, then check remaining reachability
            val existingRule = targetRuleRepository.findByRelationshipDefinitionId(definitionId)
                .find { it.targetEntityTypeId == entityTypeId }

            if (existingRule != null) {
                targetRuleRepository.delete(existingRule)
                logger.info { "Deleted explicit target rule for entity type $entityTypeId from definition $definitionId" }
            }

            if (isReachableViaSemanticRules(definitionId, entityTypeId)) {
                saveExclusionRecord(definitionId, entityTypeId)
            }
        }

        activityService.log(
            activity = Activity.ENTITY_RELATIONSHIP,
            operation = OperationType.UPDATE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.ENTITY_TYPE,
            entityId = entityTypeId,
            "relationshipId" to definitionId.toString(),
            "action" to "exclude",
        )

        return null
    }

    /**
     * Removes an exclusion, re-enabling the relationship for the entity type.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun removeExclusion(workspaceId: UUID, definitionId: UUID, entityTypeId: UUID) {
        val userId = authTokenService.getUserId()
        ServiceUtil.findOrThrow { definitionRepository.findByIdAndWorkspaceId(definitionId, workspaceId) }
        validateEntityTypeBelongsToWorkspace(entityTypeId, workspaceId)

        val exclusionExists = exclusionRepository.findByRelationshipDefinitionIdAndEntityTypeId(definitionId, entityTypeId).isPresent
        if (exclusionExists) {
            exclusionRepository.deleteByRelationshipDefinitionIdAndEntityTypeId(definitionId, entityTypeId)
            entityRelationshipRepository.restoreByDefinitionIdAndTargetEntityTypeId(definitionId, entityTypeId)
        }

        activityService.log(
            activity = Activity.ENTITY_RELATIONSHIP,
            operation = OperationType.UPDATE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.ENTITY_TYPE,
            entityId = entityTypeId,
            "relationshipId" to definitionId.toString(),
            "action" to "remove_exclusion",
        )

        logger.info { "Removed exclusion for entity type $entityTypeId from definition $definitionId" }
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
     * Checks whether an entity type is still reachable on a definition via semantic constraint rules
     * (i.e. rules with a `semanticTypeConstraint` that matches the entity type's semantic group).
     */
    private fun isReachableViaSemanticRules(definitionId: UUID, entityTypeId: UUID): Boolean {
        val semanticRules = targetRuleRepository.findByRelationshipDefinitionId(definitionId)
            .filter { it.semanticTypeConstraint != null }
        if (semanticRules.isEmpty()) return false

        val entityType = ServiceUtil.findOrThrow { entityTypeRepository.findById(entityTypeId) }
        return semanticRules.any { it.semanticTypeConstraint == entityType.semanticGroup }
    }

    /**
     * Saves an exclusion record, handling concurrent duplicates as a no-op.
     */
    private fun saveExclusionRecord(definitionId: UUID, entityTypeId: UUID) {
        try {
            exclusionRepository.save(
                RelationshipDefinitionExclusionEntity(
                    relationshipDefinitionId = definitionId,
                    entityTypeId = entityTypeId,
                )
            )
            logger.info { "Created exclusion for entity type $entityTypeId from definition $definitionId" }
        } catch (e: DataIntegrityViolationException) {
            val isUniqueViolation = e.rootCause?.message?.contains("uq_exclusion_def_type") == true
            if (isUniqueViolation) {
                logger.warn { "Exclusion already exists for entity type $entityTypeId on definition $definitionId, treating as no-op" }
            } else {
                throw e
            }
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

        // Rules with an ID that matches existing → keep/update; without → new; existing not in request → remove
        val requestedIds = requestedRules.mapNotNull { it.id }.toSet()
        val toRemove = existingRules.filter { it.id !in requestedIds }
        if (toRemove.isNotEmpty()) {
            targetRuleRepository.deleteAll(toRemove)
        }

        val toSave = requestedRules.map { req ->
            require(req.targetEntityTypeId != null || req.semanticTypeConstraint != null) {
                "Target rule must specify either targetEntityTypeId or semanticTypeConstraint"
            }
            if (req.id != null && existingById.containsKey(req.id)) {
                // Update existing rule
                val existing = existingById[req.id]!!
                existing.copy(
                    targetEntityTypeId = req.targetEntityTypeId,
                    semanticTypeConstraint = req.semanticTypeConstraint,
                    cardinalityOverride = req.cardinalityOverride,
                    inverseName = req.inverseName,
                )
            } else {
                // New rule
                RelationshipTargetRuleEntity(
                    relationshipDefinitionId = definitionId,
                    targetEntityTypeId = req.targetEntityTypeId,
                    semanticTypeConstraint = req.semanticTypeConstraint,
                    cardinalityOverride = req.cardinalityOverride,
                    inverseName = req.inverseName,
                )
            }
        }

        return targetRuleRepository.saveAll(toSave).map { it.toModel() }
    }

    private fun buildTargetRuleEntities(
        definitionId: UUID,
        rules: List<SaveTargetRuleRequest>,
    ): List<RelationshipTargetRuleEntity> {
        return rules.map { rule ->
            require(rule.targetEntityTypeId != null || rule.semanticTypeConstraint != null) {
                "Target rule must specify either targetEntityTypeId or semanticTypeConstraint"
            }
            RelationshipTargetRuleEntity(
                relationshipDefinitionId = definitionId,
                targetEntityTypeId = rule.targetEntityTypeId,
                semanticTypeConstraint = rule.semanticTypeConstraint,
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
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun createFallbackDefinition(workspaceId: UUID, entityTypeId: UUID): RelationshipDefinitionEntity {
        val entity = RelationshipDefinitionEntity(
            workspaceId = workspaceId,
            sourceEntityTypeId = entityTypeId,
            name = "Connected Entities",
            iconType = IconType.LINK,
            iconColour = IconColour.NEUTRAL,
            allowPolymorphic = true,
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
            protected = true,
            systemType = SystemRelationshipType.CONNECTED_ENTITIES,
        )
        val saved = definitionRepository.save(entity)
        logger.info { "Created CONNECTED_ENTITIES fallback definition for entity type $entityTypeId" }
        return saved
    }

    /**
     * Returns the existing fallback definition or creates one if absent.
     * Handles concurrent creation via unique constraint by catching DataIntegrityViolationException
     * and retrying with a read.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getOrCreateFallbackDefinition(workspaceId: UUID, entityTypeId: UUID): RelationshipDefinitionEntity {
        val existing = definitionRepository.findBySourceEntityTypeIdAndSystemType(
            entityTypeId, SystemRelationshipType.CONNECTED_ENTITIES,
        )
        if (existing.isPresent) return existing.get()

        return try {
            createFallbackDefinition(workspaceId, entityTypeId)
        } catch (e: DataIntegrityViolationException) {
            logger.warn { "Concurrent fallback definition creation for entity type $entityTypeId, retrying read" }
            definitionRepository.findBySourceEntityTypeIdAndSystemType(
                entityTypeId, SystemRelationshipType.CONNECTED_ENTITIES,
            ).orElseThrow { e }
        }
    }

    /**
     * Read-only lookup returning the fallback definition ID, or null if none exists.
     */
    fun getFallbackDefinitionId(entityTypeId: UUID): UUID? {
        return definitionRepository.findBySourceEntityTypeIdAndSystemType(
            entityTypeId, SystemRelationshipType.CONNECTED_ENTITIES,
        ).map { it.id }.orElse(null)
    }
}
