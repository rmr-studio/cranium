package cranium.core.models.integration.projection

import java.util.UUID

/**
 * Domain model for a projection rule — maps a source entity type to a target entity type.
 */
data class ProjectionRule(
    val id: UUID,
    val workspaceId: UUID?,
    val sourceEntityTypeId: UUID,
    val targetEntityTypeId: UUID,
    val relationshipDefId: UUID?,
    val autoCreate: Boolean,
)
