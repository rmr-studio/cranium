package cranium.core.models.entity.query.pagination

import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

/**
 * Pagination and ordering configuration for query results.
 *
 * @property limit Maximum number of entities to return (default 100)
 * @property offset Number of entities to skip (default 0)
 * @property orderBy Optional list of ordering clauses
 */
@Schema(description = "Pagination and ordering configuration.")
data class QueryPagination(
    @Schema(description = "Maximum number of entities to return.", defaultValue = "100")
    val limit: Int = 100,

    @Schema(description = "Number of entities to skip.", defaultValue = "0")
    val offset: Int = 0,

    @Schema(description = "Optional ordering clauses.", nullable = true)
    val orderBy: List<OrderByClause>? = null
)

/**
 * Single ordering clause for query results.
 *
 * @property attributeId UUID key of the attribute to order by
 * @property direction Sort direction (ASC or DESC)
 */
@Schema(description = "Single ordering clause.")
data class OrderByClause(
    @Schema(description = "UUID key of the attribute to order by.")
    val attributeId: UUID,

    @Schema(description = "Sort direction.", defaultValue = "ASC")
    val direction: SortDirection = SortDirection.ASC
)

/**
 * Sort direction for ordering.
 */
@Schema(description = "Sort direction.")
enum class SortDirection {
    /** Ascending order (A-Z, 0-9, oldest first) */
    ASC,

    /** Descending order (Z-A, 9-0, newest first) */
    DESC
}
