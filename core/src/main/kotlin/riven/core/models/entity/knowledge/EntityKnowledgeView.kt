package riven.core.models.entity.knowledge

import java.util.*

/**
 * Canonical per-entity knowledge artifact produced by the assembler in Plan 02-02 and consumed by:
 * - [riven.core.service.enrichment.EntityKnowledgeViewProjector] (Phase 3 truncation)
 * - Future synthesis activities
 * - Future JSONB projection consumers
 *
 * Identifying scalars ([queueItemId], [entityId], [workspaceId], [entityTypeId], [schemaVersion])
 * are duplicated outside [KnowledgeSections] so embedding and persistence layers do not need to
 * drill into [KnowledgeSections.identity] to obtain queue or entity identifiers. The [sections]
 * field carries the full structured knowledge payload.
 *
 * This is a Temporal-serializable, pure-Kotlin data class. All field types must be Jackson-safe;
 * complex types (dates, enums) are serialized via the project's Jackson configuration.
 *
 * @property queueItemId The enrichment queue item that triggered this pipeline run.
 * @property entityId The entity being embedded.
 * @property workspaceId The workspace the entity belongs to.
 * @property entityTypeId The entity type UUID — used to determine schema version at embedding time.
 * @property schemaVersion Schema version at assembly time — stored on the embedding record for staleness detection.
 * @property sections The 8-section knowledge payload assembled by [riven.core.service.enrichment.EnrichmentContextAssembler].
 */
data class EntityKnowledgeView(
    val queueItemId: UUID,
    val entityId: UUID,
    val workspaceId: UUID,
    val entityTypeId: UUID,
    val schemaVersion: Int,
    val sections: KnowledgeSections,
)
