package cranium.core.models.response.entity.type

import cranium.core.models.entity.EntityType
import java.util.*

/**
 * Result of an impact check when a destructive relationship operation has existing instance data.
 *
 * @param deletesDefinition When true, the entire relationship definition will be removed
 *     (e.g. removing the last target rule). When false, only the target rule and its
 *     associated links are removed but the definition survives.
 */
data class DeleteDefinitionImpact(
    val definitionId: UUID,
    val definitionName: String,
    val impactedLinkCount: Long,
    val deletesDefinition: Boolean = true,
)

data class EntityTypeImpactResponse(
    // Return the updated entity type(s) after the update operation
    val error: String? = null,
    val updatedEntityTypes: Map<String, EntityType>? = null,
    val impact: DeleteDefinitionImpact? = null
)
