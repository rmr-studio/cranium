package riven.core.service.knowledge

import riven.core.enums.entity.RelationshipTargetKind
import riven.core.enums.entity.SystemRelationshipType
import riven.core.enums.integration.SourceType
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
    val readonly: Boolean
    val linkSource: SourceType
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
)
