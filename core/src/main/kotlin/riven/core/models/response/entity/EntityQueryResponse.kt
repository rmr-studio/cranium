package cranium.core.models.response.entity

import io.swagger.v3.oas.annotations.media.Schema
import cranium.core.models.entity.Entity

@Schema(description = "Paginated response from an entity query")
data class EntityQueryResponse(
    @Schema(description = "Entities matching the query, with relationships hydrated")
    val entities: List<Entity>,

    @Schema(description = "Total number of entities matching the filter (ignoring pagination). Null when includeCount is false.")
    val totalCount: Long?,

    @Schema(description = "Whether more results exist beyond the current page")
    val hasNextPage: Boolean,

    @Schema(description = "Number of entities requested per page")
    val limit: Int,

    @Schema(description = "Number of entities skipped before the current page")
    val offset: Int,
)
