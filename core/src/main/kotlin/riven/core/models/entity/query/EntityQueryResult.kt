package riven.core.models.entity.query

import io.swagger.v3.oas.annotations.media.Schema
import riven.core.models.entity.Entity

/**
 * Result of an entity query execution.
 *
 * Contains the matching entities for the current page along with pagination metadata.
 * The [hasNextPage] flag is computed as `(offset + limit) < totalCount` by the executor
 * when constructing this result.
 *
 * @property entities Matching entities for the current page (may be empty)
 * @property totalCount Total number of matching entities across all pages. Null when count was skipped.
 * @property hasNextPage Whether more results exist beyond the current page
 * @property projection The projection used for the query, passed through so callers
 *   can see what field selection hints were applied
 */
@Schema(description = "Result of an entity query execution with pagination metadata.")
data class EntityQueryResult(
    @Schema(description = "Matching entities for the current page.")
    val entities: List<Entity>,

    @Schema(description = "Total number of matching entities across all pages. Null when count was skipped.", nullable = true)
    val totalCount: Long?,

    @Schema(description = "Whether more results exist beyond the current page.")
    val hasNextPage: Boolean,

    @Schema(description = "The projection used for the query.", nullable = true)
    val projection: QueryProjection?,
)
