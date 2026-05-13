package cranium.core.service.entity.query

import org.springframework.stereotype.Component
import cranium.core.enums.entity.query.QueryDirection
import cranium.core.exceptions.query.FilterNestingDepthExceededException
import cranium.core.exceptions.query.RelationshipDepthExceededException
import cranium.core.models.entity.query.filter.FilterValue
import cranium.core.models.entity.query.filter.QueryFilter
import java.util.*

/**
 * Visitor that traverses [QueryFilter] trees and produces [SqlFragment] output.
 *
 * This visitor handles the conversion of filter expressions into executable SQL:
 * - AND/OR logical combinations with arbitrary nesting
 * - ATTRIBUTE filters delegated to [AttributeSqlGenerator]
 * - RELATIONSHIP filters delegated to [RelationshipSqlGenerator]
 *
 * ## Depth Limit Enforcement
 *
 * The visitor enforces two independent depth limits to prevent excessive SQL complexity:
 *
 * 1. **AND/OR nesting depth** (`depth` / [maxNestingDepth]): Tracks how deeply AND/OR
 *    combinations are nested. Resets to 0 when entering a nested relationship subquery,
 *    giving each subquery context its own AND/OR depth budget.
 *
 * 2. **Relationship traversal depth** (`relationshipDepth` / [maxRelationshipDepth]):
 *    Tracks how many relationship EXISTS subqueries are nested. Incremented only when
 *    entering a relationship filter, never by AND/OR combinations.
 *
 * Filters exceeding either limit throw the corresponding exception:
 * [FilterNestingDepthExceededException] or
 * [cranium.core.exceptions.query.RelationshipDepthExceededException].
 *
 * ## Entity Alias Propagation
 *
 * The [entityAlias] parameter controls which table alias is referenced in generated SQL.
 * At the root level this defaults to `"e"` (the main entity table). When processing
 * nested filters inside relationship subqueries, the [RelationshipSqlGenerator] provides
 * a target entity alias (e.g., `"t_0"`) that is propagated through all nested visit calls.
 *
 * ## Template Resolution
 *
 * Template expressions ([FilterValue.Template]) must be resolved by the caller
 * before invoking the visitor. Unresolved templates throw [IllegalStateException].
 * This separation of concerns keeps the query layer focused on SQL generation
 * while template resolution is handled by the workflow layer.
 *
 * ## Example Usage
 *
 * ```kotlin
 * val visitor = AttributeFilterVisitor(attributeSqlGenerator, relationshipSqlGenerator)
 * val paramGen = ParameterNameGenerator()
 *
 * // Pure attribute filter
 * val filter = QueryFilter.And(listOf(
 *     QueryFilter.Attribute(statusId, EQUALS, FilterValue.Literal("Active")),
 *     QueryFilter.Attribute(tierId, EQUALS, FilterValue.Literal("Premium"))
 * ))
 * val fragment = visitor.visit(filter, paramGen)
 * // fragment.sql = "(e.payload @> :eq_0::jsonb) AND (e.payload @> :eq_1::jsonb)"
 *
 * // Mixed attribute + relationship filter
 * val mixed = QueryFilter.And(listOf(
 *     QueryFilter.Attribute(statusId, EQUALS, FilterValue.Literal("Active")),
 *     QueryFilter.Relationship(clientRelId, RelationshipCondition.Exists)
 * ))
 * val mixedFragment = visitor.visit(mixed, paramGen)
 * ```
 *
 * @property attributeSqlGenerator Generator for ATTRIBUTE filter SQL fragments
 * @property relationshipSqlGenerator Generator for RELATIONSHIP filter SQL fragments
 * @property maxNestingDepth Maximum allowed nesting depth for AND/OR combinations
 * @property maxRelationshipDepth Maximum allowed relationship traversal depth
 */
@Component
class AttributeFilterVisitor(
    private val attributeSqlGenerator: AttributeSqlGenerator,
    private val relationshipSqlGenerator: RelationshipSqlGenerator,
    private val maxNestingDepth: Int = DEFAULT_MAX_NESTING_DEPTH,
    private val maxRelationshipDepth: Int = DEFAULT_MAX_RELATIONSHIP_DEPTH
) {

    /**
     * Visits a filter tree and produces a SQL fragment.
     *
     * Entry point for filter tree traversal. Creates internal depth tracking
     * and delegates to the recursive visitor implementation.
     *
     * @param filter Root filter expression to visit
     * @param paramGen Generator for unique parameter names (shared across the query)
     * @param entityAlias Table alias for the entity being filtered. Defaults to `"e"` (root entity).
     *   Pass a different alias when generating SQL for nested relationship filter contexts.
     * @return SqlFragment with parameterized SQL and bound values
     * @throws FilterNestingDepthExceededException if AND/OR nesting exceeds [maxNestingDepth]
     * @throws cranium.core.exceptions.query.RelationshipDepthExceededException if relationship depth exceeds [maxRelationshipDepth]
     * @throws IllegalStateException if unresolved template expressions are encountered
     */
    fun visit(
        filter: QueryFilter,
        paramGen: ParameterNameGenerator,
        entityAlias: String = "e",
        relationshipDirections: Map<UUID, QueryDirection> = emptyMap(),
    ): SqlFragment {
        return visitInternal(filter, depth = 0, relationshipDepth = 0, paramGen, entityAlias, relationshipDirections)
    }

    /**
     * Internal recursive visitor with depth tracking.
     *
     * Dispatches to type-specific handlers based on the filter variant.
     * AND/OR depth is incremented when descending into AND/OR conditions.
     * Relationship depth is incremented when descending into relationship filters.
     */
    private fun visitInternal(
        filter: QueryFilter,
        depth: Int,
        relationshipDepth: Int,
        paramGen: ParameterNameGenerator,
        entityAlias: String,
        relationshipDirections: Map<UUID, QueryDirection> = emptyMap(),
    ): SqlFragment {
        if (depth > maxNestingDepth) {
            throw FilterNestingDepthExceededException(depth, maxNestingDepth)
        }

        return when (filter) {
            is QueryFilter.Attribute -> visitAttribute(filter, paramGen, entityAlias)
            is QueryFilter.Relationship -> visitRelationship(filter, relationshipDepth, paramGen, entityAlias, relationshipDirections)
            is QueryFilter.IsRelatedTo -> visitIsRelatedTo(filter, paramGen, entityAlias)
            is QueryFilter.And -> visitAnd(filter.conditions, depth, relationshipDepth, paramGen, entityAlias, relationshipDirections)
            is QueryFilter.Or -> visitOr(filter.conditions, depth, relationshipDepth, paramGen, entityAlias, relationshipDirections)
        }
    }

    /**
     * Visits an AND combination of filters.
     *
     * All conditions must match for the combined fragment to match.
     * Empty AND returns [SqlFragment.ALWAYS_TRUE] (vacuous truth).
     *
     * @param conditions List of filters to combine with AND logic
     * @param depth Current AND/OR nesting depth
     * @param relationshipDepth Current relationship traversal depth (passed through unchanged)
     * @param paramGen Parameter name generator
     * @param entityAlias Table alias for the entity being filtered
     * @return Combined SQL fragment with AND logic
     */
    private fun visitAnd(
        conditions: List<QueryFilter>,
        depth: Int,
        relationshipDepth: Int,
        paramGen: ParameterNameGenerator,
        entityAlias: String,
        relationshipDirections: Map<UUID, QueryDirection>,
    ): SqlFragment {
        if (conditions.isEmpty()) {
            return SqlFragment.ALWAYS_TRUE
        }

        return conditions
            .map { visitInternal(it, depth + 1, relationshipDepth, paramGen, entityAlias, relationshipDirections) }
            .reduce { acc, fragment -> acc.and(fragment) }
    }

    /**
     * Visits an OR combination of filters.
     *
     * Any matching condition causes the combined fragment to match.
     * Empty OR returns [SqlFragment.ALWAYS_FALSE] (no match possible).
     *
     * @param conditions List of filters to combine with OR logic
     * @param depth Current AND/OR nesting depth
     * @param relationshipDepth Current relationship traversal depth (passed through unchanged)
     * @param paramGen Parameter name generator
     * @param entityAlias Table alias for the entity being filtered
     * @return Combined SQL fragment with OR logic
     */
    private fun visitOr(
        conditions: List<QueryFilter>,
        depth: Int,
        relationshipDepth: Int,
        paramGen: ParameterNameGenerator,
        entityAlias: String,
        relationshipDirections: Map<UUID, QueryDirection>,
    ): SqlFragment {
        if (conditions.isEmpty()) {
            return SqlFragment.ALWAYS_FALSE
        }

        return conditions
            .map { visitInternal(it, depth + 1, relationshipDepth, paramGen, entityAlias, relationshipDirections) }
            .reduce { acc, fragment -> acc.or(fragment) }
    }

    /**
     * Visits an ATTRIBUTE filter.
     *
     * Extracts the literal value from [FilterValue] and delegates to
     * [AttributeSqlGenerator] for SQL generation.
     *
     * @param filter Attribute filter with attribute ID, operator, and value
     * @param paramGen Parameter name generator
     * @param entityAlias Table alias for the entity being filtered
     * @return SQL fragment for the attribute comparison
     * @throws IllegalStateException if value is an unresolved template
     */
    private fun visitAttribute(
        filter: QueryFilter.Attribute,
        paramGen: ParameterNameGenerator,
        entityAlias: String
    ): SqlFragment {
        val value = extractValue(filter.value)
        return attributeSqlGenerator.generate(
            attributeId = filter.attributeId,
            operator = filter.operator,
            value = value,
            paramGen = paramGen,
            entityAlias = entityAlias
        )
    }

    /**
     * Visits a RELATIONSHIP filter.
     *
     * Delegates to [RelationshipSqlGenerator] with a nested filter visitor callback
     * that enables recursive processing of filters on related entities. Relationship
     * depth is checked before delegation as a safety net (the eager [QueryFilterValidator]
     * catches this first, but the visitor enforces it too).
     *
     * The nested visitor callback:
     * - Increments relationship depth by 1 for the next level
     * - Resets AND/OR depth to 0 (each subquery gets its own AND/OR depth budget)
     * - Uses the target entity alias provided by [RelationshipSqlGenerator]
     * - Shares the same [ParameterNameGenerator] for parameter uniqueness across the query tree
     *
     * @param filter Relationship filter with relationship ID and condition
     * @param relationshipDepth Current relationship traversal depth
     * @param paramGen Parameter name generator
     * @param entityAlias Table alias for the entity being filtered
     * @return SQL fragment with EXISTS/NOT EXISTS subquery
     * @throws cranium.core.exceptions.query.RelationshipDepthExceededException if depth exceeds [maxRelationshipDepth]
     */
    private fun visitRelationship(
        filter: QueryFilter.Relationship,
        relationshipDepth: Int,
        paramGen: ParameterNameGenerator,
        entityAlias: String,
        relationshipDirections: Map<UUID, QueryDirection>,
    ): SqlFragment {
        if (relationshipDepth >= maxRelationshipDepth) {
            throw RelationshipDepthExceededException(
                relationshipDepth + 1,
                maxRelationshipDepth
            )
        }

        val direction = relationshipDirections[filter.relationshipId] ?: QueryDirection.FORWARD

        val nestedVisitor: (QueryFilter, ParameterNameGenerator, String) -> SqlFragment =
            { nestedFilter, nestedParamGen, nestedEntityAlias ->
                visitInternal(
                    nestedFilter,
                    depth = 0,
                    relationshipDepth = relationshipDepth + 1,
                    nestedParamGen,
                    nestedEntityAlias
                )
            }

        return relationshipSqlGenerator.generate(
            relationshipId = filter.relationshipId,
            condition = filter.condition,
            paramGen = paramGen,
            entityAlias = entityAlias,
            direction = direction,
            nestedFilterVisitor = nestedVisitor
        )
    }

    /**
     * Visits an IS_RELATED_TO filter.
     *
     * Delegates to [RelationshipSqlGenerator.generateIsRelatedTo] for bidirectional
     * existence checks across all relationship definitions.
     */
    private fun visitIsRelatedTo(
        filter: QueryFilter.IsRelatedTo,
        paramGen: ParameterNameGenerator,
        entityAlias: String,
    ): SqlFragment {
        return relationshipSqlGenerator.generateIsRelatedTo(
            condition = filter.condition,
            paramGen = paramGen,
            entityAlias = entityAlias,
        )
    }

    /**
     * Extracts the actual value from a [FilterValue].
     *
     * Literal values are returned directly. Template values throw an error
     * because templates must be resolved by the caller (workflow layer) before
     * reaching the query service.
     *
     * @param filterValue The filter value to extract
     * @return The extracted literal value (may be null)
     * @throws IllegalStateException if value is an unresolved template
     */
    private fun extractValue(filterValue: FilterValue): Any? {
        return when (filterValue) {
            is FilterValue.Literal -> filterValue.value
            is FilterValue.Template -> {
                throw IllegalStateException(
                    "Template expression '${filterValue.expression}' was not resolved before query execution. " +
                            "Resolve templates before calling the query service."
                )
            }
        }
    }

    companion object {
        /**
         * Default maximum nesting depth for AND/OR filter combinations.
         *
         * This limit prevents excessive SQL complexity from deeply nested filters.
         * Can be overridden via constructor parameter if needed.
         */
        const val DEFAULT_MAX_NESTING_DEPTH = 10

        /**
         * Default maximum relationship traversal depth.
         *
         * This limit prevents expensive deeply nested EXISTS subqueries.
         * Can be overridden via constructor parameter if needed.
         */
        const val DEFAULT_MAX_RELATIONSHIP_DEPTH = 3
    }
}
