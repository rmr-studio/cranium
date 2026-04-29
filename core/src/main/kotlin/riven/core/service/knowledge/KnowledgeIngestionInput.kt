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
 * knowledge entity. `targetEntityIds` is the desired exact set — existing rows under
 * the same `(source, definition, linkSource)` triple that aren't in this set are
 * deleted.
 *
 * `targetKind` defaults to [RelationshipTargetKind.ENTITY]. Knowledge subclasses
 * (e.g. glossary `DEFINES`) may emit batches with [RelationshipTargetKind.ENTITY_TYPE]
 * or [RelationshipTargetKind.ATTRIBUTE]; the kind is materialised on the
 * `entity_relationships.target_kind` column so projectors can route DEFINES edges by
 * the shape of their target.
 */
data class KnowledgeRelationshipBatch(
    val systemType: SystemRelationshipType,
    val targetEntityIds: Set<UUID>,
    val targetKind: RelationshipTargetKind = RelationshipTargetKind.ENTITY,
)
