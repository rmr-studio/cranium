package riven.core.service.entity.query

/**
 * Holds paired data and count SQL queries assembled from filter visitor output.
 *
 * Both queries share the same WHERE clause conditions (workspace isolation, entity type,
 * soft-delete filter, and optional user filters). The [dataQuery] adds ORDER BY and
 * LIMIT/OFFSET for pagination, while the [countQuery] returns only the total matching
 * row count without ordering or pagination.
 *
 * The executor (Phase 5) runs these queries separately -- potentially in parallel --
 * and combines the results into an [riven.core.models.entity.query.EntityQueryResult].
 *
 * @property dataQuery SELECT e.id query with ORDER BY, LIMIT, and OFFSET clauses
 * @property countQuery SELECT COUNT(*) query with the same WHERE clause but no ORDER BY or LIMIT/OFFSET.
 *   Null when includeCount is false (count is skipped for scroll-pagination).
 */
data class AssembledQuery(
    val dataQuery: SqlFragment,
    val countQuery: SqlFragment?,
)
