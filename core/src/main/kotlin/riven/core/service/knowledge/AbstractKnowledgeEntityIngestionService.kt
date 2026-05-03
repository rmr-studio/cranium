package riven.core.service.knowledge

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.entity.EntityEntity
import riven.core.entity.entity.EntityTypeEntity
import riven.core.enums.entity.RelationshipTargetKind
import riven.core.enums.entity.SystemRelationshipType
import riven.core.enums.integration.SourceType
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.service.entity.EntityIngestionService
import riven.core.service.entity.type.EntityTypeRelationshipService
import java.util.UUID

/**
 * Base for all knowledge-domain ingestion services (Note, Glossary, future Memo/SOP/
 * Policy/Decision/Meeting/Incident).
 *
 * Subclasses provide:
 *   - the workspace entity-type key (e.g. "note", "glossary")
 *   - an attribute resolver that maps a typed input to a `Map<attributeUuid, value>`
 *   - the list of relationship batches the input should reconcile
 *
 * The base owns:
 *   - workspace entity-type lookup (with onboarding-incomplete error)
 *   - idempotent upsert via `(workspaceId, sourceIntegrationId, sourceExternalId)`
 *   - the [EntityIngestionService.saveEntityInternal] call (no JWT-bound auth check)
 *   - relationship reconciliation against [EntityTypeRelationshipService]
 */
abstract class AbstractKnowledgeEntityIngestionService<TInput : KnowledgeIngestionInput>(
    protected val entityIngestionService: EntityIngestionService,
    protected val entityTypeRepository: EntityTypeRepository,
    protected val entityRepository: EntityRepository,
    protected val entityRelationshipRepository: EntityRelationshipRepository,
    protected val entityTypeRelationshipService: EntityTypeRelationshipService,
    protected val logger: KLogger,
) {

    /** Workspace-scoped key on `entity_types` (matches `CoreModelDefinition.key`). */
    protected abstract val entityTypeKey: String

    /** Resolve the typed input into a flat `attributeUuid -> value` payload. */
    protected abstract fun buildAttributePayload(entityType: EntityTypeEntity, input: TInput): Map<UUID, Any?>

    /** Outbound relationship batches the upsert should reconcile. */
    protected abstract fun relationshipBatches(input: TInput): List<KnowledgeRelationshipBatch>

    /**
     * Parent-scoped relationship kinds (ATTRIBUTE / RELATIONSHIP) the upsert should fully clear
     * for the saved entity. Used when the input carries no refs of that kind and the regular
     * [replaceRelationshipsInternal] path cannot run because it requires a non-null
     * `targetParentId` for parent-scoped kinds. Default: empty.
     */
    protected open fun clearParentScopedKinds(input: TInput): Set<Pair<SystemRelationshipType, RelationshipTargetKind>> = emptySet()

    /**
     * Garbage-collection hook for parent-scoped relationship kinds (ATTRIBUTE / RELATIONSHIP)
     * whose parent has been orphaned by the input — i.e. existing rows reference a
     * `targetParentId` that the new input no longer covers. Default: empty.
     *
     * Subclasses should compare existing rows on the saved entity against the input's parent
     * set and emit one batch per orphan parent with `targetIds = emptySet()` and
     * `targetParentId = orphanParentId`. The base feeds these batches through the same
     * [syncRelationship] path as the desired-state batches; [replaceForDefinitionInternal]
     * sweeps each orphan parent's rows.
     *
     * Decoupled from [relationshipBatches] so that method stays focused on desired state.
     */
    protected open fun cleanupOrphanedParentBatches(saved: EntityEntity, input: TInput): List<KnowledgeRelationshipBatch> = emptyList()

    /** Optional hook — subclasses may persist domain-specific extras (e.g. pendingAssociations). */
    protected open fun postSave(saved: EntityEntity, input: TInput) {}

    @Transactional
    open fun upsert(input: TInput): EntityEntity {
        val entityType = resolveEntityType(input.workspaceId)
        val existingId = input.existingId ?: idempotentLookup(input)?.id
        val payload = buildAttributePayload(entityType, input)

        val saved = entityIngestionService.saveEntityInternal(
            workspaceId = input.workspaceId,
            entityTypeId = requireNotNull(entityType.id) { "$entityTypeKey entity type id must not be null" },
            existingId = existingId,
            attributePayload = payload,
            sourceType = input.sourceType,
            sourceIntegrationId = input.sourceIntegrationId,
            sourceExternalId = input.sourceExternalId,
        )

        relationshipBatches(input).forEach { batch ->
            syncRelationship(input.workspaceId, saved, batch, input.linkSource)
        }

        cleanupOrphanedParentBatches(saved, input).forEach { batch ->
            syncRelationship(input.workspaceId, saved, batch, input.linkSource)
        }

        clearParentScopedKinds(input).forEach { (systemType, targetKind) ->
            clearKind(input.workspaceId, saved, systemType, targetKind)
        }

        postSave(saved, input)
        return saved
    }

    private fun clearKind(
        workspaceId: UUID,
        knowledgeEntity: EntityEntity,
        systemType: SystemRelationshipType,
        targetKind: RelationshipTargetKind,
    ) {
        val sourceId = requireNotNull(knowledgeEntity.id) { "knowledgeEntity.id" }
        val typeId = knowledgeEntity.typeId
        val def = entityTypeRelationshipService.getOrCreateSystemDefinition(workspaceId, typeId, systemType)
        entityIngestionService.clearRelationshipsByKindInternal(
            workspaceId = workspaceId,
            sourceEntityId = sourceId,
            relationshipDefinitionId = requireNotNull(def.id) { "system relationship definition id must not be null" },
            targetKind = targetKind,
        )
    }

    open fun softDelete(workspaceId: UUID, entityId: UUID) {
        entityIngestionService.softDeleteEntityInternal(workspaceId, entityId)
    }

    private fun resolveEntityType(workspaceId: UUID): EntityTypeEntity =
        entityTypeRepository.findByworkspaceIdAndKey(workspaceId, entityTypeKey)
            .orElseThrow {
                IllegalStateException(
                    "$entityTypeKey entity type missing for workspace $workspaceId — onboarding incomplete"
                )
            }

    /**
     * Idempotency lookup keyed on `sourceExternalId`.
     *
     * Two shapes are supported:
     *   - integration-sourced inputs: `(workspaceId, sourceIntegrationId, sourceExternalId)`
     *     uniquely identifies the entity (matches the ingestion path used by
     *     [riven.core.service.note.NoteEmbeddingService]);
     *   - workspace-internal inputs (e.g. glossary backfill, where there is no integration):
     *     `(workspaceId, sourceExternalId)` is used. Subclasses scope their own external-id
     *     namespace (e.g. `legacy:{uuid}`) to avoid cross-type collisions.
     */
    protected open fun idempotentLookup(input: TInput): EntityEntity? {
        val externalId = input.sourceExternalId ?: return null
        val integrationId = input.sourceIntegrationId
        if (integrationId != null) {
            return entityRepository.findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(
                input.workspaceId, integrationId, listOf(externalId),
            ).firstOrNull()
        }
        return entityRepository.findByWorkspaceIdAndSourceExternalId(input.workspaceId, externalId)
            .firstOrNull { it.typeKey == entityTypeKey }
    }

    private fun syncRelationship(
        workspaceId: UUID,
        knowledgeEntity: EntityEntity,
        batch: KnowledgeRelationshipBatch,
        linkSource: SourceType,
    ) {
        val sourceId = requireNotNull(knowledgeEntity.id) { "knowledgeEntity.id" }
        val typeId = knowledgeEntity.typeId
        val def = entityTypeRelationshipService.getOrCreateSystemDefinition(workspaceId, typeId, batch.systemType)
        entityIngestionService.replaceRelationshipsInternal(
            workspaceId = workspaceId,
            sourceEntityId = sourceId,
            relationshipDefinitionId = requireNotNull(def.id) { "system relationship definition id must not be null" },
            targetIds = batch.targetIds,
            linkSource = linkSource,
            targetKind = batch.targetKind,
            targetParentId = batch.targetParentId,
        )
    }

    /** Helper: resolve an attribute UUID from `attributeKeyMapping` by attribute key. */
    protected fun attributeId(entityType: EntityTypeEntity, key: String): UUID {
        val mapping = entityType.attributeKeyMapping
            ?: error("$entityTypeKey entity type has no attributeKeyMapping configured")
        val raw = mapping[key]
            ?: error("$entityTypeKey entity type missing attribute key '$key' in attributeKeyMapping")
        return UUID.fromString(raw)
    }
}
