package cranium.core.models.entity.query

import io.swagger.v3.oas.annotations.media.Schema
import cranium.core.models.entity.query.filter.QueryFilter
import java.util.*

/**
 * Core query definition targeting an entity type with optional filters.
 *
 * @property entityTypeId UUID of the entity type/collection to query
 * @property filter Optional filter criteria for narrowing results
 * @property maxDepth Maximum relationship traversal depth (1-10, default 3)
 */
@Schema(description = "Core query definition with entity type and optional filter criteria.")
data class EntityQuery(
    @param:Schema(description = "UUID of the entity type to query.")
    val entityTypeId: UUID,

    @param:Schema(description = "Optional filter criteria.", nullable = true)
    val filter: QueryFilter? = null,

    @param:Schema(
        description = "Maximum depth for nested relationship traversal. Applies to entire query tree.",
        defaultValue = "3",
        minimum = "1",
        maximum = "10"
    )
    val maxDepth: Int = 3
) {
    init {
        require(maxDepth in 1..10) { "maxDepth must be between 1 and 10, was: $maxDepth" }
    }
}
