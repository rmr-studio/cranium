package riven.core.models.request.entity

import io.swagger.v3.oas.annotations.media.Schema
import riven.core.models.entity.query.QueryProjection
import riven.core.models.entity.query.filter.QueryFilter
import riven.core.models.entity.query.pagination.QueryPagination

@Schema(description = "Request body for querying entities with filtering, pagination, and sorting")
data class EntityQueryRequest(
    @Schema(description = "Filter tree for narrowing results. Null returns all entities.")
    val filter: QueryFilter? = null,

    @Schema(description = "Pagination and sorting options. Defaults to limit=100, offset=0.")
    val pagination: QueryPagination = QueryPagination(),

    @Schema(description = "Projection for selecting which attributes/relationships to include.")
    val projection: QueryProjection? = null,

    @Schema(
        description = "Whether to include totalCount in the response. Set to false for scroll-pagination " +
                "requests where the frontend already has the count from the initial load.",
        defaultValue = "true"
    )
    val includeCount: Boolean = true,

    @Schema(description = "Maximum relationship traversal depth for nested filters (1-10).", example = "3")
    val maxDepth: Int = 3,
) {
    init {
        require(maxDepth in 1..10) { "maxDepth must be between 1 and 10, was: $maxDepth" }
    }
}
