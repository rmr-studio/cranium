package riven.core.service.entity

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.entity.EntityRelationshipEntity
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.entity.RelationshipTargetKind
import riven.core.enums.integration.SourceType
import riven.core.enums.util.OperationType
import riven.core.exceptions.ConflictException
import riven.core.exceptions.InvalidRelationshipException
import riven.core.models.common.markDeleted
import riven.core.models.entity.EntityLink
import riven.core.models.entity.RelationshipDefinition
import riven.core.models.entity.RelationshipTargetRule
import riven.core.models.request.entity.AddRelationshipRequest
import riven.core.models.request.entity.UpdateRelationshipRequest
import riven.core.models.response.entity.RelationshipResponse
import riven.core.projection.entity.toEntityLink
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.RelationshipDefinitionRepository
import riven.core.service.activity.ActivityService
import riven.core.service.activity.log
import riven.core.service.auth.AuthTokenService
import riven.core.service.entity.type.EntityTypeRelationshipService
import riven.core.util.ServiceUtil
import java.util.*

/**
 * Service for managing entity relationship instances.
 *
 * Handles creating/removing individual relationship links between entities,
 * with write-time cardinality enforcement and target type validation.
 * No inverse row storage — bidirectional visibility is resolved at query time.
 */
@Service
class EntityRelationshipService(
    private val entityRelationshipRepository: EntityRelationshipRepository,
    private val entityRepository: EntityRepository,
    private val definitionRepository: RelationshipDefinitionRepository,
    private val entityTypeRelationshipService: EntityTypeRelationshipService,
    private val authTokenService: AuthTokenService,
    private val activityService: ActivityService,
    private val logger: KLogger,
) {

    // ------ Save relationships ------

    /**
     * Saves relationships for a single definition on a source entity.
     *
     * Diffs the current links against the requested targets:
     * additions are validated and inserted, removals are deleted.
     * Cardinality is enforced at write time based on the definition and matching target rules.
     *
     * @param id Source entity ID
     * @param workspaceId Workspace ID
     * @param definitionId The relationship definition ID
     * @param definition Pre-loaded relationship definition with target rules
     * @param targetIds Requested target entity IDs (the desired end state)
     */
    @Transactional
    fun saveRelationships(
        id: UUID,
        workspaceId: UUID,
        definitionId: UUID,
        definition: RelationshipDefinition,
        targetIds: List<UUID>,
    ) {
        val existingRels = entityRelationshipRepository.findAllBySourceIdAndDefinitionIdForUpdate(id, definitionId)
        val existingTargetIds = existingRels.map { it.targetId }.toSet()
        val requestedTargetIds = targetIds.toSet()

        val toAdd = requestedTargetIds - existingTargetIds
        val toRemove = existingTargetIds - requestedTargetIds

        // Remove stale links
        if (toRemove.isNotEmpty()) {
            entityRelationshipRepository.deleteAllBySourceIdAndDefinitionIdAndTargetIdIn(id, definitionId, toRemove)
        }

        // Nothing to add — done
        if (toAdd.isEmpty()) return

        // Load ALL final-state targets (retained + new) for cardinality checks
        val finalTargetEntities = entityRepository.findAllById(requestedTargetIds).associateBy { requireNotNull(it.id) }

        val missingIds = toAdd - finalTargetEntities.keys
        require(missingIds.isEmpty()) {
            "Target entities not found: $missingIds"
        }

        val finalTypesByEntityId = finalTargetEntities.mapValues { it.value.typeId }
        val newTargetTypesByEntityId = finalTypesByEntityId.filterKeys { it in toAdd }

        validateTargets(definition, newTargetTypesByEntityId)
        enforceCardinality(definition, definitionId, finalTypesByEntityId, newTargetTypesByEntityId)

        val newRelationships = toAdd.map { targetId ->
            EntityRelationshipEntity(
                workspaceId = workspaceId,
                sourceId = id,
                targetId = targetId,
                definitionId = definitionId,
            )
        }

        entityRelationshipRepository.saveAll(newRelationships)
    }

    /**
     * System-side replace: drives all rows for `(sourceId, definitionId, targetKind)` toward
     * the desired `targetIds` set, hard-deleting absent targets and inserting new ones.
     *
     * Used by the knowledge ingestion layer ([riven.core.service.knowledge.AbstractKnowledgeEntityIngestionService])
     * to reconcile system relationships (`ATTACHMENT`, `MENTION`, `DEFINES`) without going
     * through the JWT-bound CRUD path. New rows carry the supplied [linkSource] so downstream
     * consumers can distinguish user-authored vs integration-sourced edges.
     *
     * `targetKind` distinguishes ENTITY edges (default) from glossary-style `DEFINES` edges
     * pointing at an entity type, attribute, or relationship definition. Persisted on the
     * `entity_relationships.target_kind` column so projectors can split DEFINES rows by
     * target shape.
     *
     * For sub-reference target_kinds (ATTRIBUTE, RELATIONSHIP), [targetParentId] is the owning
     * entity_type id and is required by the entity_relationships CHECK constraint. NULL for
     * ENTITY / ENTITY_TYPE.
     */
    @Transactional
    internal fun replaceForDefinitionInternal(
        workspaceId: UUID,
        sourceId: UUID,
        definitionId: UUID,
        targetIds: Set<UUID>,
        linkSource: SourceType,
        targetKind: RelationshipTargetKind = RelationshipTargetKind.ENTITY,
        targetParentId: UUID? = null,
    ) {
        // Defense-in-depth: assert the source entity belongs to the supplied workspace before any
        // read or delete. Without this, a system-bus caller passing a foreign sourceId could sweep
        // that workspace's relationship rows.
        val source = ServiceUtil.findOrThrow { entityRepository.findById(sourceId) }
        require(source.workspaceId == workspaceId) {
            "Source entity $sourceId not found in workspace $workspaceId"
        }

        val parentRequired = targetKind == RelationshipTargetKind.ATTRIBUTE ||
            targetKind == RelationshipTargetKind.RELATIONSHIP
        if (parentRequired) {
            require(targetParentId != null) {
                "targetParentId must be non-null for ATTRIBUTE/RELATIONSHIP reconciliation " +
                    "(got targetKind=$targetKind)"
            }
        }
        if (!parentRequired) {
            require(targetParentId == null) {
                "targetParentId must be null for ENTITY/ENTITY_TYPE target kinds " +
                    "(got targetKind=$targetKind, targetParentId=$targetParentId)"
            }
        }

        val existing = entityRelationshipRepository.findAllBySourceIdAndDefinitionIdForUpdate(sourceId, definitionId)
            .filter { it.targetKind == targetKind }
            .filter { !parentRequired || it.targetParentId == targetParentId }
        val existingTargetIds = existing.map { it.targetId }.toSet()

        val toRemove = existingTargetIds - targetIds
        if (toRemove.isNotEmpty()) {
            if (parentRequired) {
                entityRelationshipRepository.deleteAllBySourceIdAndDefinitionIdAndTargetKindAndTargetParentIdAndTargetIdIn(
                    sourceId, definitionId, targetKind, requireNotNull(targetParentId), toRemove,
                )
            } else {
                entityRelationshipRepository.deleteAllBySourceIdAndDefinitionIdAndTargetKindAndTargetIdIn(
                    sourceId, definitionId, targetKind, toRemove,
                )
            }
        }

        val toAdd = targetIds - existingTargetIds
        if (toAdd.isEmpty()) return

        val newRelationships = toAdd.map { targetId ->
            EntityRelationshipEntity(
                workspaceId = workspaceId,
                sourceId = sourceId,
                targetId = targetId,
                targetParentId = targetParentId,
                definitionId = definitionId,
                linkSource = linkSource,
                targetKind = targetKind,
            )
        }
        entityRelationshipRepository.saveAll(newRelationships)
    }

    /**
     * Hard-delete every relationship row matching `(sourceId, definitionId, targetKind)` for
     * the given source. Used by the knowledge ingestion path to reconcile parent-scoped kinds
     * (ATTRIBUTE / RELATIONSHIP) when the input carries no refs of that kind, since
     * [replaceForDefinitionInternal] requires a non-null `targetParentId` for those kinds.
     */
    @Transactional
    fun clearAllOfKindForDefinition(
        workspaceId: UUID,
        sourceId: UUID,
        definitionId: UUID,
        targetKind: RelationshipTargetKind,
    ) {
        // Defense-in-depth: assert workspace ownership before sweeping rows. Mirrors the guard on
        // replaceForDefinitionInternal so the system-bus surface keeps tenancy invariants explicit.
        val source = ServiceUtil.findOrThrow { entityRepository.findById(sourceId) }
        require(source.workspaceId == workspaceId) {
            "Source entity $sourceId not found in workspace $workspaceId"
        }

        entityRelationshipRepository.deleteAllBySourceIdAndDefinitionIdAndTargetKind(
            sourceId, definitionId, targetKind,
        )
    }

    // ------ Read ------

    /**
     * Finds all entity links touching the given entity, as a flat list.
     *
     * Includes:
     *  - **Forward** edges: rows where the entity is the source (any system_type, any definition).
     *  - **Inverse** edges: rows where the entity is the target, admitted when either a
     *    relationship_target_rule applies, the definition's system_type is
     *    `SYSTEM_CONNECTION`, or the definition's system_type is one of
     *    `ATTACHMENT` / `MENTION` / `DEFINES` *and* the source entity's type carries
     *    `surface_role = 'KNOWLEDGE'`. Knowledge inverse edges are how
     *    "notes attached to me" / "glossary terms defining me" surface on a target entity.
     *
     * Each [EntityLink] carries `direction` and `systemType`, so callers
     * (e.g. `EntityEntity.toModel`) can partition the flat list into the
     * regular relationships map vs. the knowledge-refs projection.
     */
    fun findRelatedEntities(entityId: UUID, workspaceId: UUID): List<EntityLink> {
        val forward = entityRelationshipRepository.findEntityLinksBySourceId(entityId, workspaceId)
        val inverse = entityRelationshipRepository.findInverseEntityLinksByTargetId(entityId, workspaceId)

        return (forward + inverse).map { it.toEntityLink() }
    }

    /**
     * Batch variant: flat list per source entity id. Same predicate semantics as the
     * single-entity overload.
     */
    fun findRelatedEntities(entityIds: Set<UUID>, workspaceId: UUID): Map<UUID, List<EntityLink>> {
        val ids = entityIds.toTypedArray()
        val forward = entityRelationshipRepository.findEntityLinksBySourceIdIn(ids, workspaceId)
        val inverse = entityRelationshipRepository.findInverseEntityLinksByTargetIdIn(ids, workspaceId)

        return (forward + inverse)
            .groupBy { it.getSourceEntityId() }
            .mapValues { (_, projections) -> projections.map { it.toEntityLink() } }
    }

    /**
     * Finds all relationships where the given entities are targets.
     */
    fun findByTargetIdIn(ids: List<UUID>): Map<UUID, List<EntityRelationshipEntity>> {
        return entityRelationshipRepository.findByTargetIdIn(ids).groupBy { it.targetId }
    }

    /**
     * Soft-deletes all relationships involving the given entities (as source or target).
     */
    fun archiveEntities(ids: Collection<UUID>, workspaceId: UUID): List<EntityRelationshipEntity> {
        return entityRelationshipRepository.deleteEntities(ids.toTypedArray(), workspaceId)
    }

    // ------ Relationship CRUD ------

    /**
     * Adds a single relationship between two entities.
     *
     * When `definitionId` is provided, creates a typed relationship with target type
     * validation and cardinality enforcement. When omitted, creates a
     * SYSTEM_CONNECTION relationship with bidirectional duplicate detection.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun addRelationship(
        workspaceId: UUID,
        sourceEntityId: UUID,
        request: AddRelationshipRequest,
    ): RelationshipResponse {
        val userId = authTokenService.getUserId()
        val sourceEntity = ServiceUtil.findOrThrow { entityRepository.findByIdAndWorkspaceId(sourceEntityId, workspaceId) }
        val targetEntity = ServiceUtil.findOrThrow { entityRepository.findByIdAndWorkspaceId(request.targetEntityId, workspaceId) }

        val (resolvedDefinitionId, definitionName) = resolveDefinition(
            workspaceId, sourceEntity.typeId, sourceEntityId, request, targetEntity.typeId,
        )

        val entity = EntityRelationshipEntity(
            workspaceId = workspaceId,
            sourceId = sourceEntityId,
            targetId = request.targetEntityId,
            definitionId = resolvedDefinitionId,
            semanticContext = request.semanticContext,
            linkSource = request.linkSource,
        )
        val saved = entityRelationshipRepository.save(entity)

        activityService.log(
            activity = Activity.ENTITY_RELATIONSHIP,
            operation = OperationType.CREATE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.ENTITY,
            entityId = sourceEntityId,
            "relationshipId" to requireNotNull(saved.id).toString(),
            "targetEntityId" to request.targetEntityId.toString(),
            "definitionId" to resolvedDefinitionId.toString(),
            "semanticContext" to request.semanticContext,
        )

        logger.info { "Created relationship ${saved.id} from $sourceEntityId to ${request.targetEntityId} under definition $resolvedDefinitionId" }
        return saved.toRelationshipResponse(definitionName)
    }

    /**
     * Returns all relationships for an entity (forward + inverse), optionally filtered
     * by a specific definition ID.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getRelationships(workspaceId: UUID, entityId: UUID, definitionId: UUID? = null): List<RelationshipResponse> {
        ServiceUtil.findOrThrow { entityRepository.findByIdAndWorkspaceId(entityId, workspaceId) }

        val relationships = if (definitionId != null) {
            entityRelationshipRepository.findByEntityIdAndDefinitionId(entityId, definitionId, workspaceId)
        } else {
            entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)
        }

        if (relationships.isEmpty()) return emptyList()

        val definitionNames = definitionRepository
            .findAllById(relationships.map { it.definitionId }.distinct())
            .associate { requireNotNull(it.id) to it.name }

        return relationships.map { rel ->
            rel.toRelationshipResponse(definitionNames[rel.definitionId] ?: "Unknown")
        }
    }

    /**
     * Updates the semantic context of an existing relationship.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun updateRelationship(
        workspaceId: UUID,
        relationshipId: UUID,
        request: UpdateRelationshipRequest,
    ): RelationshipResponse {
        val userId = authTokenService.getUserId()
        val relationship = ServiceUtil.findOrThrow {
            entityRelationshipRepository.findByIdAndWorkspaceId(relationshipId, workspaceId)
        }

        val updated = entityRelationshipRepository.save(
            relationship.copy(semanticContext = request.semanticContext)
        )

        val definitionName = definitionRepository.findById(relationship.definitionId)
            .map { it.name }.orElse("Unknown")

        activityService.log(
            activity = Activity.ENTITY_RELATIONSHIP,
            operation = OperationType.UPDATE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.ENTITY,
            entityId = relationship.sourceId,
            "relationshipId" to relationshipId.toString(),
            "semanticContext" to request.semanticContext,
        )

        logger.info { "Updated relationship $relationshipId semantic context" }
        return updated.toRelationshipResponse(definitionName)
    }

    /**
     * Soft-deletes a relationship.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun removeRelationship(workspaceId: UUID, relationshipId: UUID) {
        val userId = authTokenService.getUserId()
        val relationship = ServiceUtil.findOrThrow {
            entityRelationshipRepository.findByIdAndWorkspaceId(relationshipId, workspaceId)
        }

        relationship.markDeleted()
        entityRelationshipRepository.save(relationship)

        activityService.log(
            activity = Activity.ENTITY_RELATIONSHIP,
            operation = OperationType.DELETE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.ENTITY,
            entityId = relationship.sourceId,
            "relationshipId" to relationshipId.toString(),
        )

        logger.info { "Deleted relationship $relationshipId" }
    }

    // ------ Relationship CRUD helpers ------

    /**
     * Resolves the definition for a new relationship, validates duplicates, and enforces
     * cardinality for typed definitions. Returns the resolved definition ID and name.
     */
    private fun resolveDefinition(
        workspaceId: UUID,
        sourceEntityTypeId: UUID,
        sourceEntityId: UUID,
        request: AddRelationshipRequest,
        targetTypeId: UUID,
    ): Pair<UUID, String> {
        if (request.definitionId != null) {
            val definition = entityTypeRelationshipService.getDefinitionById(workspaceId, request.definitionId)
            checkDirectionalDuplicate(sourceEntityId, request.targetEntityId, request.definitionId)
            validateTypedRelationship(sourceEntityId, request.targetEntityId, targetTypeId, request.definitionId, definition)
            return request.definitionId to definition.name
        }

        val systemConnectionDef = entityTypeRelationshipService.getOrCreateSystemConnectionDefinition(workspaceId, sourceEntityTypeId)
        val defId = requireNotNull(systemConnectionDef.id)
        checkBidirectionalDuplicate(sourceEntityId, request.targetEntityId, defId)
        return defId to systemConnectionDef.name
    }

    private fun checkDirectionalDuplicate(sourceId: UUID, targetId: UUID, definitionId: UUID) {
        val existing = entityRelationshipRepository.findBySourceIdAndTargetIdAndDefinitionId(
            sourceId, targetId, definitionId,
        )
        if (existing.isNotEmpty()) {
            throw ConflictException("Relationship already exists from $sourceId to $targetId under definition $definitionId")
        }
    }

    private fun checkBidirectionalDuplicate(sourceId: UUID, targetId: UUID, definitionId: UUID) {
        val existingForward = entityRelationshipRepository.findBySourceIdAndTargetIdAndDefinitionId(
            sourceId, targetId, definitionId,
        )
        val existingReverse = entityRelationshipRepository.findBySourceIdAndTargetIdAndDefinitionId(
            targetId, sourceId, definitionId,
        )
        if (existingForward.isNotEmpty() || existingReverse.isNotEmpty()) {
            throw ConflictException("Relationship already exists between $sourceId and $targetId")
        }
    }

    /**
     * Validates target type and enforces cardinality for typed (non-system-connection) definitions.
     */
    private fun validateTypedRelationship(
        sourceEntityId: UUID,
        targetEntityId: UUID,
        targetTypeId: UUID,
        definitionId: UUID,
        definition: RelationshipDefinition,
    ) {
        val newTargetTypesByEntityId = mapOf(targetEntityId to targetTypeId)

        val existingTargets = entityRelationshipRepository.findAllBySourceIdAndDefinitionId(sourceEntityId, definitionId)
        val existingTargetIds = existingTargets.map { it.targetId }.toSet()
        val existingTypesByEntityId = if (existingTargetIds.isNotEmpty()) {
            entityRepository.findAllById(existingTargetIds).associate { requireNotNull(it.id) to it.typeId }
        } else {
            emptyMap()
        }
        val finalTypesByEntityId = existingTypesByEntityId + newTargetTypesByEntityId

        validateTargets(definition, newTargetTypesByEntityId)
        enforceCardinality(definition, definitionId, finalTypesByEntityId, newTargetTypesByEntityId)
    }

    private fun EntityRelationshipEntity.toRelationshipResponse(definitionName: String): RelationshipResponse {
        return RelationshipResponse(
            id = requireNotNull(this.id),
            sourceEntityId = this.sourceId,
            targetEntityId = this.targetId,
            definitionId = this.definitionId,
            definitionName = definitionName,
            semanticContext = this.semanticContext,
            linkSource = this.linkSource,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
        )
    }

    // ------ Private validation helpers ------

    /**
     * Validates that all target entity types are allowed by the definition's target rules.
     * Each target must match a rule by explicit type ID.
     */
    private fun validateTargets(
        definition: RelationshipDefinition,
        targetTypesByEntityId: Map<UUID, UUID>,
    ) {
        val rules = definition.targetRules
        targetTypesByEntityId.forEach { (entityId, typeId) ->
            val matchingRule = findMatchingRule(rules, typeId)
            if (matchingRule == null) {
                throw IllegalArgumentException(
                    "Target entity $entityId has type $typeId which is not allowed by relationship definition '${definition.name}' (${definition.id})"
                )
            }
        }
    }

    /**
     * Dispatches cardinality enforcement across both axes:
     * source-side (how many targets of each type a source can have) and
     * target-side (how many sources can link to each target).
     */
    private fun enforceCardinality(
        definition: RelationshipDefinition,
        definitionId: UUID,
        finalTypesByEntityId: Map<UUID, UUID>,
        newTargetTypesByEntityId: Map<UUID, UUID>,
    ) {
        enforceSourceSideCardinality(definition, finalTypesByEntityId)
        enforceTargetSideCardinality(definition, definitionId, newTargetTypesByEntityId)
    }

    /**
     * Enforces source-side cardinality: checks per-type limits on how many targets
     * of each type a source entity can have.
     *
     * Groups ALL final targets (retained + new) by type and checks each group
     * against the effective cardinality (rule override or definition default).
     */
    private fun enforceSourceSideCardinality(
        definition: RelationshipDefinition,
        finalTypesByEntityId: Map<UUID, UUID>,
    ) {
        val finalByType = finalTypesByEntityId.values.groupingBy { it }.eachCount()

        finalByType.forEach { (typeId, count) ->
            val effective = resolveCardinality(definition, typeId)
            val max = effective.maxSourceTargets()
            if (max != null && count > max) {
                throw InvalidRelationshipException(
                    "Having $count target(s) of type $typeId would exceed the per-type source-side limit of $max " +
                        "for relationship '${definition.name}' (${definition.id})."
                )
            }
        }
    }

    /**
     * Enforces target-side cardinality: for each NEW target, checks that the target
     * is not already linked by another source under this definition (when cardinality
     * requires target exclusivity).
     *
     * Applies to ONE_TO_ONE and ONE_TO_MANY (each target linked by at most one source).
     */
    private fun enforceTargetSideCardinality(
        definition: RelationshipDefinition,
        definitionId: UUID,
        newTargetTypesByEntityId: Map<UUID, UUID>,
    ) {
        val targetsNeedingCheck = newTargetTypesByEntityId.filter { (_, typeId) ->
            val effective = resolveCardinality(definition, typeId)
            effective.maxTargetSources() != null
        }
        if (targetsNeedingCheck.isEmpty()) return

        val existingLinksByTargetId = entityRelationshipRepository
            .findByTargetIdInAndDefinitionIdForUpdate(targetsNeedingCheck.keys, definitionId)
            .groupBy { it.targetId }

        targetsNeedingCheck.forEach { (targetId, typeId) ->
            val effective = resolveCardinality(definition, typeId)
            val existingLinks = existingLinksByTargetId[targetId] ?: emptyList()
            if (existingLinks.isNotEmpty()) {
                throw InvalidRelationshipException(
                    "Target entity $targetId is already linked by source ${existingLinks.first().sourceId} " +
                        "under ${effective.name} relationship '${definition.name}' (${definition.id})."
                )
            }
        }
    }

    /**
     * Resolves the effective cardinality for a target type: uses the matching rule's
     * override if present, otherwise falls back to the definition default.
     */
    private fun resolveCardinality(
        definition: RelationshipDefinition,
        targetTypeId: UUID,
    ): EntityRelationshipCardinality {
        val rule = findMatchingRule(definition.targetRules, targetTypeId)
        return rule?.cardinalityOverride ?: definition.cardinalityDefault
    }

    /**
     * Finds a matching target rule for a given target entity type by exact type ID match.
     */
    private fun findMatchingRule(
        rules: List<RelationshipTargetRule>,
        targetTypeId: UUID,
    ): RelationshipTargetRule? {
        return rules.find { it.targetEntityTypeId == targetTypeId }
    }
}

/**
 * Returns the maximum number of targets of a given type that a source entity can have.
 * Returns null for unlimited.
 *
 * ONE_TO_ONE / MANY_TO_ONE → 1 (source can have at most 1 target per type)
 * ONE_TO_MANY / MANY_TO_MANY → null (unlimited targets per type)
 */
private fun EntityRelationshipCardinality.maxSourceTargets(): Int? = when (this) {
    EntityRelationshipCardinality.ONE_TO_ONE -> 1
    EntityRelationshipCardinality.MANY_TO_ONE -> 1
    EntityRelationshipCardinality.ONE_TO_MANY -> null
    EntityRelationshipCardinality.MANY_TO_MANY -> null
}

/**
 * Returns the maximum number of sources that can link to a given target.
 * Returns null for unlimited.
 *
 * ONE_TO_ONE / ONE_TO_MANY → 1 (each target linked by at most 1 source)
 * MANY_TO_ONE / MANY_TO_MANY → null (unlimited sources per target)
 */
private fun EntityRelationshipCardinality.maxTargetSources(): Int? = when (this) {
    EntityRelationshipCardinality.ONE_TO_ONE -> 1
    EntityRelationshipCardinality.ONE_TO_MANY -> 1
    EntityRelationshipCardinality.MANY_TO_ONE -> null
    EntityRelationshipCardinality.MANY_TO_MANY -> null
}
