package cranium.core.models.entity.query

import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

/**
 * Field selection for query results.
 *
 * Controls which attributes and relationships are included in returned entities.
 *
 * @property includeAttributes Specific attribute UUIDs to include (null = all)
 * @property includeRelationships Specific relationship UUIDs to include (null = all)
 * @property expandRelationships Whether to hydrate related entities
 */
@Schema(description = "Field selection for query results.")
data class QueryProjection(
    @param:Schema(
        description = "Specific attribute UUIDs to include. Null includes all.",
        nullable = true
    )
    val includeAttributes: List<UUID>? = null,

    @param:Schema(
        description = "Specific relationship UUIDs to include. Null includes all.",
        nullable = true
    )
    val includeRelationships: List<UUID>? = null,

    @param:Schema(
        description = "Whether to hydrate related entities.",
        defaultValue = "false"
    )
    val expandRelationships: Boolean = false
)
