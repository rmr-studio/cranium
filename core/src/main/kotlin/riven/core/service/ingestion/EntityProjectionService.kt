package riven.core.service.ingestion

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.entity.EntityEntity
import riven.core.entity.entity.EntityRelationshipEntity
import riven.core.enums.integration.SourceType
import riven.core.models.ingestion.ProjectionDetail
import riven.core.models.ingestion.ProjectionOutcome
import riven.core.models.ingestion.ProjectionResult
import riven.core.models.ingestion.ResolutionResult
import riven.core.models.integration.projection.ProjectionRule
import riven.core.repository.entity.EntityAttributeRepository
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.integration.ProjectionRuleRepository
import riven.core.service.entity.EntityAttributeService
import riven.core.service.identity.IdentityClusterService
import riven.core.util.ServiceUtil
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Core projection pipeline: transforms integration entities into core lifecycle entities.
 *
 * Called from the Temporal sync workflow after integration data is ingested.
 * No @PreAuthorize — runs in Temporal activity context without JWT.
 * Workspace isolation is enforced by parameter, not by auth context.
 */
@Service
class EntityProjectionService(
    private val entityRepository: EntityRepository,
    private val entityTypeRepository: EntityTypeRepository,
    private val entityAttributeRepository: EntityAttributeRepository,
    private val entityAttributeService: EntityAttributeService,
    private val entityRelationshipRepository: EntityRelationshipRepository,
    private val projectionRuleRepository: ProjectionRuleRepository,
    private val identityResolutionService: IdentityResolutionService,
    private val identityClusterService: IdentityClusterService,
    private val logger: KLogger,
) {

    companion object {
        const val CHUNK_SIZE = 100
    }

    /**
     * Project integration entities into core lifecycle entities based on projection rules.
     *
     * @param syncedEntityIds IDs of integration entities synced in this run
     * @param workspaceId the workspace scope
     * @param sourceEntityTypeId the integration entity type ID
     * @return projection result summary
     */
    @Transactional
    fun processProjections(
        syncedEntityIds: List<UUID>,
        workspaceId: UUID,
        sourceEntityTypeId: UUID,
    ): ProjectionResult {
        if (syncedEntityIds.isEmpty()) {
            return ProjectionResult(created = 0, updated = 0, skipped = 0, errors = 0)
        }

        val rules = loadProjectionRules(sourceEntityTypeId, workspaceId)
        if (rules.isEmpty()) {
            logger.debug { "No projection rules for entity type $sourceEntityTypeId — skipping projection" }
            return ProjectionResult(created = 0, updated = 0, skipped = syncedEntityIds.size, errors = 0)
        }

        var totalCreated = 0
        var totalUpdated = 0
        var totalSkipped = 0
        var totalErrors = 0
        val allDetails = mutableListOf<ProjectionDetail>()

        for (rule in rules) {
            val result = processRuleProjections(syncedEntityIds, workspaceId, rule)
            totalCreated += result.created
            totalUpdated += result.updated
            totalSkipped += result.skipped
            totalErrors += result.errors
            allDetails.addAll(result.details)
        }

        logger.info {
            "Projection complete for entity type $sourceEntityTypeId: " +
                "created=$totalCreated, updated=$totalUpdated, skipped=$totalSkipped, errors=$totalErrors"
        }

        return ProjectionResult(
            created = totalCreated,
            updated = totalUpdated,
            skipped = totalSkipped,
            errors = totalErrors,
            details = allDetails,
        )
    }

    // ------ Rule Processing ------

    /**
     * Process projections for a single rule, chunking to prevent OOM on large syncs.
     */
    private fun processRuleProjections(
        syncedEntityIds: List<UUID>,
        workspaceId: UUID,
        rule: ProjectionRule,
    ): ProjectionResult {
        var created = 0
        var updated = 0
        var skipped = 0
        var errors = 0
        val details = mutableListOf<ProjectionDetail>()

        for (chunk in syncedEntityIds.chunked(CHUNK_SIZE)) {
            val entities = entityRepository.findByIdIn(chunk)
            val resolutions = identityResolutionService.resolveBatch(entities, workspaceId, rule.targetEntityTypeId)

            for (entity in entities) {
                val entityId = requireNotNull(entity.id)
                val resolution = resolutions[entityId] ?: ResolutionResult.NewEntity()

                try {
                    val detail = projectSingleEntity(entity, resolution, workspaceId, rule)
                    details.add(detail)
                    when (detail.outcome) {
                        ProjectionOutcome.CREATED -> created++
                        ProjectionOutcome.UPDATED -> updated++
                        ProjectionOutcome.ERROR -> errors++
                        else -> skipped++
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Projection failed for entity $entityId → rule ${rule.id}" }
                    errors++
                    details.add(ProjectionDetail(
                        sourceEntityId = entityId,
                        targetEntityId = null,
                        outcome = ProjectionOutcome.ERROR,
                        message = e.message,
                    ))
                }
            }
        }

        return ProjectionResult(created = created, updated = updated, skipped = skipped, errors = errors, details = details)
    }

    // ------ Single Entity Projection ------

    /**
     * Project a single integration entity into a core entity based on the resolution result.
     */
    private fun projectSingleEntity(
        integrationEntity: EntityEntity,
        resolution: ResolutionResult,
        workspaceId: UUID,
        rule: ProjectionRule,
    ): ProjectionDetail {
        val integrationEntityId = requireNotNull(integrationEntity.id)

        return when (resolution) {
            is ResolutionResult.ExistingEntity -> updateExistingEntity(
                integrationEntity, resolution.entityId, workspaceId, rule
            )
            is ResolutionResult.NewEntity -> {
                if (rule.autoCreate) {
                    createProjectedEntity(integrationEntity, workspaceId, rule)
                } else {
                    logger.debug { "Skipping auto-create for entity $integrationEntityId (autoCreate=false)" }
                    ProjectionDetail(
                        sourceEntityId = integrationEntityId,
                        targetEntityId = null,
                        outcome = ProjectionOutcome.SKIPPED_AUTO_CREATE_DISABLED,
                    )
                }
            }
        }
    }

    /**
     * Update an existing core entity with mapped fields from the integration entity.
     * Source wins — integration data overwrites core entity attributes for mapped fields.
     */
    private fun updateExistingEntity(
        integrationEntity: EntityEntity,
        coreEntityId: UUID,
        workspaceId: UUID,
        rule: ProjectionRule,
    ): ProjectionDetail {
        val integrationEntityId = requireNotNull(integrationEntity.id)
        val coreEntity = entityRepository.findByIdAndWorkspaceId(coreEntityId, workspaceId).orElse(null)

        if (coreEntity == null || coreEntity.deleted) {
            return ProjectionDetail(
                sourceEntityId = integrationEntityId,
                targetEntityId = coreEntityId,
                outcome = ProjectionOutcome.SKIPPED_SOFT_DELETED,
            )
        }

        // syncVersion guard: reject stale writes
        if (integrationEntity.syncVersion < coreEntity.syncVersion) {
            logger.warn { "Stale projection: integration entity $integrationEntityId (v${integrationEntity.syncVersion}) < core entity $coreEntityId (v${coreEntity.syncVersion})" }
            return ProjectionDetail(
                sourceEntityId = integrationEntityId,
                targetEntityId = coreEntityId,
                outcome = ProjectionOutcome.SKIPPED_STALE_VERSION,
            )
        }

        // Transfer mapped attributes from integration → core
        transferAttributes(integrationEntity, coreEntity)

        // Update sync metadata
        coreEntity.lastSyncedAt = ZonedDateTime.now()
        coreEntity.syncVersion = integrationEntity.syncVersion
        entityRepository.save(coreEntity)

        // Ensure relationship link exists (idempotent)
        ensureRelationshipLink(integrationEntityId, coreEntityId, workspaceId, rule)

        return ProjectionDetail(
            sourceEntityId = integrationEntityId,
            targetEntityId = coreEntityId,
            outcome = ProjectionOutcome.UPDATED,
        )
    }

    /**
     * Create a new PROJECTED core entity from integration entity data.
     */
    private fun createProjectedEntity(
        integrationEntity: EntityEntity,
        workspaceId: UUID,
        rule: ProjectionRule,
    ): ProjectionDetail {
        val integrationEntityId = requireNotNull(integrationEntity.id)
        val targetEntityType = ServiceUtil.findOrThrow { entityTypeRepository.findById(rule.targetEntityTypeId) }
        val now = ZonedDateTime.now()

        val coreEntity = entityRepository.save(
            EntityEntity(
                workspaceId = workspaceId,
                typeId = rule.targetEntityTypeId,
                typeKey = targetEntityType.key,
                identifierKey = targetEntityType.identifierKey,
                sourceType = SourceType.PROJECTED,
                sourceExternalId = integrationEntity.sourceExternalId,
                firstSyncedAt = now,
                lastSyncedAt = now,
                syncVersion = integrationEntity.syncVersion,
            )
        )

        val coreEntityId = requireNotNull(coreEntity.id)

        // Copy attributes from integration entity to core entity
        copyAttributes(integrationEntity, coreEntityId, workspaceId, rule.targetEntityTypeId)

        // Create relationship link (integration → core)
        ensureRelationshipLink(integrationEntityId, coreEntityId, workspaceId, rule)

        // Add to identity cluster
        addToIdentityCluster(integrationEntityId, coreEntityId, workspaceId)

        logger.debug { "Created projected entity $coreEntityId from integration entity $integrationEntityId" }

        return ProjectionDetail(
            sourceEntityId = integrationEntityId,
            targetEntityId = coreEntityId,
            outcome = ProjectionOutcome.CREATED,
        )
    }

    // ------ Attribute Transfer ------

    /**
     * Transfer mapped attributes from integration entity to an existing core entity.
     * Uses delete-all + re-insert via EntityAttributeService.
     * User-owned unmapped fields are preserved: only attributes that exist on the
     * integration entity are overwritten.
     */
    private fun transferAttributes(integrationEntity: EntityEntity, coreEntity: EntityEntity) {
        val integrationEntityId = requireNotNull(integrationEntity.id)
        val coreEntityId = requireNotNull(coreEntity.id)

        val sourceAttrs = entityAttributeRepository.findByEntityId(integrationEntityId)
        val existingCoreAttrs = entityAttributeRepository.findByEntityId(coreEntityId)

        // Build merged attribute map: start with existing core attrs, overlay with integration attrs
        val mergedAttrs = existingCoreAttrs.associate { it.attributeId to it.toPrimitivePayload() }.toMutableMap()

        for (attr in sourceAttrs) {
            mergedAttrs[attr.attributeId] = attr.toPrimitivePayload()
        }

        entityAttributeService.saveAttributes(
            entityId = coreEntityId,
            workspaceId = coreEntity.workspaceId,
            typeId = coreEntity.typeId,
            attributes = mergedAttrs,
        )
    }

    /**
     * Copy all attributes from integration entity to a new core entity.
     */
    private fun copyAttributes(
        integrationEntity: EntityEntity,
        coreEntityId: UUID,
        workspaceId: UUID,
        targetEntityTypeId: UUID,
    ) {
        val sourceAttrs = entityAttributeRepository.findByEntityId(requireNotNull(integrationEntity.id))
        val attrMap = sourceAttrs.associate { it.attributeId to it.toPrimitivePayload() }

        entityAttributeService.saveAttributes(
            entityId = coreEntityId,
            workspaceId = workspaceId,
            typeId = targetEntityTypeId,
            attributes = attrMap,
        )
    }

    // ------ Relationship Management ------

    /**
     * Ensure a relationship link exists between integration and core entity. Idempotent.
     */
    private fun ensureRelationshipLink(
        integrationEntityId: UUID,
        coreEntityId: UUID,
        workspaceId: UUID,
        rule: ProjectionRule,
    ) {
        val relationshipDefId = rule.relationshipDefId ?: return

        val existing = entityRelationshipRepository.findBySourceIdAndTargetIdAndDefinitionId(
            sourceId = integrationEntityId,
            targetId = coreEntityId,
            definitionId = relationshipDefId,
        )

        if (existing.isNotEmpty()) return

        entityRelationshipRepository.save(
            EntityRelationshipEntity(
                workspaceId = workspaceId,
                sourceId = integrationEntityId,
                targetId = coreEntityId,
                definitionId = relationshipDefId,
                linkSource = SourceType.PROJECTED,
            )
        )
    }

    // ------ Identity Cluster ------

    /**
     * Add the integration and core entity to the same identity cluster.
     * Fails silently — cluster assignment is best-effort in the projection pipeline.
     */
    private fun addToIdentityCluster(
        integrationEntityId: UUID,
        coreEntityId: UUID,
        workspaceId: UUID,
    ) {
        try {
            identityClusterService.resolveClusterMembership(
                workspaceId = workspaceId,
                sourceEntityId = integrationEntityId,
                targetEntityId = coreEntityId,
                clusterName = null,
                userId = UUID(0, 0), // system-initiated — no user context in Temporal activities
            )
        } catch (e: Exception) {
            logger.warn(e) { "Identity cluster assignment failed for integration=$integrationEntityId, core=$coreEntityId — continuing" }
        }
    }

    // ------ Rule Loading ------

    private fun loadProjectionRules(sourceEntityTypeId: UUID, workspaceId: UUID): List<ProjectionRule> {
        return projectionRuleRepository
            .findBySourceEntityTypeIdAndWorkspace(sourceEntityTypeId, workspaceId)
            .map { it.toModel() }
    }
}
