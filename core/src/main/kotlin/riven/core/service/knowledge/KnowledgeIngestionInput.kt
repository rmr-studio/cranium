package cranium.core.service.knowledge

import cranium.core.enums.entity.RelationshipTargetKind
import cranium.core.enums.entity.SystemRelationshipType
import cranium.core.enums.integration.SourceType
import java.util.UUID

/**
 * Cross-cutting fields every knowledge ingestion input carries. Domain-specific
 * payload + relationship targets live on subclasses (Note, Glossary, future
 * Memo / SOP / Policy / Decision / Meeting / Incident).
 */
interface KnowledgeIngestionInput {
    val workspaceId: UUID
    val sourceType: SourceType
    val sourceIntegrationId: UUID?
    val sourceExternalId: String?
    val linkSource: SourceType

    /**
     * Caller-provided entity id to update in place. When set, the upsert path uses it
     * directly and skips [sourceExternalId]-based lookup. Required for USER_CREATED
     * inputs where `sourceExternalId` is null and the abstract idempotency keys can
     * therefore not resolve an existing row.
     */
    val existingId: UUID?
        get() = null
}

/**
 * One outbound relationship the ingestion call should reconcile against the saved
 * knowledge entity. `targetIds` is the desired exact set — existing rows under
 * the same `(source, definition, linkSource)` triple that aren't in this set are
 * deleted.
 *
 * `targetKind` defaults to [RelationshipTargetKind.ENTITY]. Knowledge subclasses
 * (e.g. glossary `DEFINES`) may emit batches with [RelationshipTargetKind.ENTITY_TYPE],
 * [RelationshipTargetKind.ATTRIBUTE], or [RelationshipTargetKind.RELATIONSHIP]; the
 * kind is materialised on the `entity_relationships.target_kind` column so projectors
 * can route DEFINES edges by the shape of their target.
 *
 * For sub-reference target kinds (ATTRIBUTE, RELATIONSHIP) the batch must carry
 * `targetParentId` — the owning entity_type id — so the persisted row satisfies the
 * `target_parent_id` CHECK constraint. NULL when [targetKind] is ENTITY or ENTITY_TYPE.
 */
data class KnowledgeRelationshipBatch(
    val systemType: SystemRelationshipType,
    val targetIds: Set<UUID>,
    val targetKind: RelationshipTargetKind = RelationshipTargetKind.ENTITY,
    val targetParentId: UUID? = null,
) {
    init {
        val parentRequired = targetKind == RelationshipTargetKind.ATTRIBUTE ||
            targetKind == RelationshipTargetKind.RELATIONSHIP
        if (parentRequired && targetIds.isNotEmpty()) {
            require(targetParentId != null) {
                "targetParentId must be non-null when emitting ATTRIBUTE/RELATIONSHIP rows " +
                    "(got targetKind=$targetKind)"
            }
        }
        if (!parentRequired) {
            require(targetParentId == null) {
                "targetParentId must be null for ENTITY/ENTITY_TYPE target kinds " +
                    "(got targetKind=$targetKind, targetParentId=$targetParentId)"
            }
        }
    }
}
