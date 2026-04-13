package riven.core.models.connector.response

import riven.core.models.connector.CursorIndexWarning
import java.util.UUID

/**
 * POST /api/v1/custom-sources/connections/{id}/schema/tables/{tableName}/mapping response.
 *
 * @property entityTypeId The created (or updated) `EntityTypeEntity` ID.
 * @property relationshipsCreated Relationship-definition IDs materialised
 *   during this Save (both FK ends published).
 * @property pendingRelationships FK targets whose table mapping is not yet
 *   `published=true` — stored as metadata on the field row; will be retried
 *   on the target table's future Save.
 * @property compositeFkSkipped FK references we could not materialise because
 *   the underlying Postgres FK spans multiple columns; informational only.
 * @property cursorIndexWarning Populated when the chosen sync-cursor column
 *   has no supporting index (copy from GET /schema for symmetry).
 */
data class CustomSourceMappingSaveResponse(
    val entityTypeId: UUID,
    val relationshipsCreated: List<UUID> = emptyList(),
    val pendingRelationships: List<PendingRelationship> = emptyList(),
    val compositeFkSkipped: List<String> = emptyList(),
    val cursorIndexWarning: CursorIndexWarning? = null,
)

data class PendingRelationship(
    val targetTable: String,
    val targetColumn: String,
)
