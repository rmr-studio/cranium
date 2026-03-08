package riven.core.service.entity.query

import org.springframework.stereotype.Service
import riven.core.exceptions.SchemaValidationException
import riven.core.models.entity.query.filter.QueryFilter
import riven.core.models.entity.query.pagination.QueryPagination
import riven.core.service.entity.query.EntityQueryAssembler.Companion.MAX_LIMIT
import riven.core.enums.entity.query.QueryDirection
import java.util.*

/**
 * Assembles complete parameterized SELECT and COUNT queries from filter visitor output.
 *
 * The assembler is the bridge between filter SQL generation (Phases 2-3) and query execution
 * (Phase 5). It wraps the visitor-produced WHERE clause fragments in complete SQL statements
 * with workspace isolation, entity type filtering, soft-delete exclusion, default ordering,
 * and pagination.
 *
 * ## Query Structure
 *
 * Two queries are produced for each request:
 * 1. **Data query:** `SELECT e.id FROM entities e WHERE ... ORDER BY e.created_at DESC, e.id ASC LIMIT :limit OFFSET :offset`
 * 2. **Count query:** `SELECT COUNT(*) FROM entities e WHERE ...` (same WHERE, no ORDER BY or LIMIT/OFFSET)
 *
 * Both queries share identical WHERE clause conditions assembled from:
 * - Base conditions: `workspace_id`, `type_id`, and `deleted = false`
 * - Optional filter conditions: produced by [AttributeFilterVisitor] from the user's [QueryFilter]
 *
 * ## Parameter Name Uniqueness
 *
 * A single [ParameterNameGenerator] is passed through both the base condition generation
 * and the filter visitor. This ensures all parameter names across the entire query tree
 * are unique, preventing binding collisions when the executor runs the SQL.
 *
 * ## Workspace Isolation
 *
 * The assembler adds `e.workspace_id = :ws_N` to the base WHERE clause. This is the ONLY
 * place workspace_id filtering occurs in the query tree -- relationship subqueries
 * intentionally omit it, relying on FK constraints and RLS for isolation.
 *
 * @property filterVisitor Visitor for traversing filter trees and producing SQL fragments
 */
@Service
class EntityQueryAssembler(
    private val filterVisitor: AttributeFilterVisitor,
) {

    /**
     * Assembles paired data and count queries from the given parameters.
     *
     * @param entityTypeId UUID of the entity type to query
     * @param workspaceId UUID of the workspace for isolation filtering
     * @param filter Optional filter criteria (null means no user filter)
     * @param pagination Pagination configuration with limit and offset
     * @param paramGen Shared parameter name generator for the entire query tree.
     *   Created by the caller and passed through to ensure unique parameter names.
     * @param includeCount When false, skips count query assembly and fetches limit+1 rows
     *   for hasNextPage detection instead.
     * @return [AssembledQuery] with separate data and count [SqlFragment]s
     * @throws SchemaValidationException if pagination parameters are invalid
     */
    fun assemble(
        entityTypeId: UUID,
        workspaceId: UUID,
        filter: QueryFilter?,
        pagination: QueryPagination,
        paramGen: ParameterNameGenerator,
        relationshipDirections: Map<UUID, QueryDirection> = emptyMap(),
        includeCount: Boolean = true,
    ): AssembledQuery {
        validatePagination(pagination)

        val baseFragment = buildBaseWhereClause(entityTypeId, workspaceId, paramGen)

        val filterFragment = filter?.let { filterVisitor.visit(it, paramGen, relationshipDirections = relationshipDirections) }

        val whereFragment = if (filterFragment != null) {
            baseFragment.and(filterFragment)
        } else {
            baseFragment
        }

        val effectiveLimit = if (includeCount) pagination.limit else pagination.limit + 1
        val dataQuery = buildDataQuery(whereFragment, pagination.copy(limit = effectiveLimit), paramGen)
        val countQuery = if (includeCount) buildCountQuery(whereFragment) else null

        return AssembledQuery(dataQuery, countQuery)
    }

    /**
     * Validates pagination parameters before query assembly.
     *
     * @throws SchemaValidationException if limit < 1, limit > [MAX_LIMIT], or offset < 0
     */
    private fun validatePagination(pagination: QueryPagination) {
        if (pagination.limit < 1) {
            throw SchemaValidationException(listOf("Limit must be at least 1, was: ${pagination.limit}"))
        }
        if (pagination.limit > MAX_LIMIT) {
            throw SchemaValidationException(listOf("Limit must not exceed $MAX_LIMIT, was: ${pagination.limit}"))
        }
        if (pagination.offset < 0) {
            throw SchemaValidationException(listOf("Offset must be non-negative, was: ${pagination.offset}"))
        }
    }

    /**
     * Builds the base WHERE clause with workspace isolation, entity type filter,
     * and soft-delete exclusion.
     *
     * Produces: `e.workspace_id = :ws_N AND e.type_id = :type_N AND e.deleted = false`
     *
     * The `deleted = false` condition is a literal (not a parameter) because it is
     * always false and benefits from partial index matching.
     */
    private fun buildBaseWhereClause(
        entityTypeId: UUID,
        workspaceId: UUID,
        paramGen: ParameterNameGenerator,
    ): SqlFragment {
        val wsParam = paramGen.next("ws")
        val typeParam = paramGen.next("type")

        return SqlFragment(
            sql = "e.workspace_id = :$wsParam AND e.type_id = :$typeParam AND e.deleted = false",
            parameters = mapOf(
                wsParam to workspaceId,
                typeParam to entityTypeId,
            ),
        )
    }

    /**
     * Builds the data query with SELECT, ORDER BY, LIMIT, and OFFSET.
     *
     * Default ordering is `created_at DESC, id ASC` for newest-first with
     * deterministic tiebreaking on UUID to ensure stable pagination.
     */
    private fun buildDataQuery(
        whereFragment: SqlFragment,
        pagination: QueryPagination,
        paramGen: ParameterNameGenerator,
    ): SqlFragment {
        val limitParam = paramGen.next("limit")
        val offsetParam = paramGen.next("offset")

        val sql = buildString {
            append("SELECT e.id\n")
            append("FROM entities e\n")
            append("WHERE ${whereFragment.sql}\n")
            append("ORDER BY e.created_at DESC, e.id ASC\n")
            append("LIMIT :$limitParam OFFSET :$offsetParam")
        }

        return SqlFragment(
            sql = sql,
            parameters = whereFragment.parameters + mapOf(
                limitParam to pagination.limit,
                offsetParam to pagination.offset,
            ),
        )
    }

    /**
     * Builds the count query with only SELECT COUNT(*) and the WHERE clause.
     *
     * No ORDER BY or LIMIT/OFFSET -- the count query returns the total number
     * of matching entities regardless of pagination position.
     */
    private fun buildCountQuery(
        whereFragment: SqlFragment,
    ): SqlFragment {
        val sql = buildString {
            append("SELECT COUNT(*)\n")
            append("FROM entities e\n")
            append("WHERE ${whereFragment.sql}")
        }

        return SqlFragment(
            sql = sql,
            parameters = whereFragment.parameters,
        )
    }

    companion object {
        /** Maximum allowed limit for pagination. Requests exceeding this throw validation error. */
        const val MAX_LIMIT = 500
    }
}
