package cranium.core.models.connector

import cranium.core.enums.entity.LifecycleDomain
import cranium.core.enums.entity.semantics.SemanticGroup
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Response DTO for a custom-source table mapping (Phase 3 plan 03-01).
 *
 * One row per `(workspaceId, connectionId, tableName)`. Holds table-level
 * mapping configuration consumed by Phase 4 (sync orchestration) and
 * Phase 7 (mapping UI).
 */
data class DataConnectorTableMapping(
    val id: UUID,
    val workspaceId: UUID,
    val connectionId: UUID,
    val tableName: String,
    val lifecycleDomain: LifecycleDomain,
    val semanticGroup: SemanticGroup,
    val entityTypeId: UUID?,
    val schemaHash: String,
    val lastIntrospectedAt: ZonedDateTime,
    val published: Boolean,
    val createdAt: ZonedDateTime?,
    val updatedAt: ZonedDateTime?,
)
