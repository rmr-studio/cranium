package riven.core.service.entity.query

import org.springframework.stereotype.Component
import riven.core.enums.entity.query.QueryDirection
import riven.core.models.entity.query.filter.QueryFilter
import riven.core.models.entity.query.filter.RelationshipFilter
import riven.core.models.entity.query.filter.TypeBranch
import java.util.*

/**
 * Generates parameterized EXISTS/NOT EXISTS subqueries for filtering entities by their relationships.
 *
 * This generator converts [RelationshipFilter] variants into PostgreSQL EXISTS subqueries
 * correlated to the outer entity query. Each subquery targets the `entity_relationships` table
 * and, for conditions that inspect the related entity (TargetMatches, TargetTypeMatches),
 * JOINs to the `entities` table.
 *
 * ## EXISTS Subquery Pattern
 *
 * EXISTS subqueries are preferred over JOINs for relationship filtering because they:
 * - Short-circuit on the first matching row (semi-join optimization)
 * - Avoid row duplication that JOINs cause when an entity has multiple relationships
 * - Compose cleanly with AND/OR logic in the outer WHERE clause
 * - Work well with the existing composite index on `(workspace_id, source_entity_id)`
 *
 * ## Nested Filter Visitor Callback
 *
 * For [RelationshipFilter.TargetMatches] and [RelationshipFilter.TargetTypeMatches],
 * nested filters must be applied to the target entity. Rather than holding a direct reference
 * to the filter visitor (which would create circular dependencies), the [generate] method
 * accepts an optional `nestedFilterVisitor` lambda. The caller (typically the query filter
 * visitor in Plan 03) provides this lambda to enable recursive filter processing.
 *
 * ## Alias Generation Strategy
 *
 * Each subquery generates unique table aliases via [ParameterNameGenerator] to prevent
 * SQL ambiguity at any nesting depth:
 * - Relationship table: `r_{counter}` (e.g., `r_0`, `r_3`, `r_7`)
 * - Target entity table: `t_{counter}` (e.g., `t_1`, `t_4`, `t_8`)
 *
 * Since the counter is shared across the entire query tree, aliases are guaranteed unique
 * even in deeply nested TargetMatches scenarios.
 *
 * ## Workspace Isolation
 *
 * Subqueries do NOT include workspace_id filtering. Workspace isolation is enforced
 * on the root query only. The entity_relationships table's FK constraints guarantee
 * that relationships reference entities within the same workspace, and RLS provides
 * an additional safety net at the database level.
 */
@Component
class RelationshipSqlGenerator {

    /**
     * Generates a SQL fragment for filtering by a relationship condition.
     *
     * @param relationshipId UUID of the relationship definition (maps to `relationship_definition_id`)
     * @param condition The relationship condition to evaluate
     * @param paramGen Generator for unique parameter names and table aliases (shared across query)
     * @param entityAlias Table alias for the entity being filtered. Defaults to `"e"` (root entity).
     *   For nested subqueries, this will be the target entity alias from the parent subquery.
     * @param direction Query direction — FORWARD correlates on `source_entity_id`, INVERSE on `target_id`.
     *   The caller resolves direction based on whether the queried entity type is the source or a target.
     * @param nestedFilterVisitor Optional lambda for processing nested filters inside TargetMatches
     *   and TargetTypeMatches conditions. Signature: `(filter, paramGen, entityAlias) -> SqlFragment`.
     *   Required when the condition contains nested filters; throws [UnsupportedOperationException]
     *   if null and a nested filter is encountered.
     * @return SqlFragment with parameterized EXISTS/NOT EXISTS subquery SQL and bound values
     * @throws UnsupportedOperationException if [RelationshipFilter.CountMatches] is encountered
     *   or if a nested filter is encountered without a [nestedFilterVisitor]
     */
    fun generate(
        relationshipId: UUID,
        condition: RelationshipFilter,
        paramGen: ParameterNameGenerator,
        entityAlias: String = "e",
        direction: QueryDirection = QueryDirection.FORWARD,
        nestedFilterVisitor: ((QueryFilter, ParameterNameGenerator, String) -> SqlFragment)? = null
    ): SqlFragment = when (condition) {
        is RelationshipFilter.Exists ->
            generateExists(relationshipId, paramGen, entityAlias, direction)

        is RelationshipFilter.NotExists ->
            generateNotExists(relationshipId, paramGen, entityAlias, direction)

        is RelationshipFilter.TargetEquals ->
            generateTargetEquals(relationshipId, condition.entityIds, paramGen, entityAlias, direction)

        is RelationshipFilter.TargetMatches ->
            generateTargetMatches(
                relationshipId, condition.filter, paramGen, entityAlias, direction,
                nestedFilterVisitor ?: throw UnsupportedOperationException(
                    "Nested filter visitor required for TargetMatches conditions"
                )
            )

        is RelationshipFilter.TargetTypeMatches ->
            generateTargetTypeMatches(
                relationshipId, condition.branches, paramGen, entityAlias, direction,
                nestedFilterVisitor ?: throw UnsupportedOperationException(
                    "Nested filter visitor required for TargetTypeMatches conditions"
                )
            )

        is RelationshipFilter.CountMatches ->
            throw UnsupportedOperationException(
                "CountMatches is not supported in this version. See v2 requirements REL-09."
            )
    }

    /**
     * Generates an EXISTS subquery matching entities with at least one related entity.
     *
     * ```sql
     * EXISTS (
     *     SELECT 1 FROM entity_relationships r_0
     *     WHERE r_0.source_entity_id = e.id
     *       AND r_0.relationship_definition_id = :rel_1
     *       AND r_0.deleted = false
     * )
     * ```
     */
    private fun generateExists(
        relationshipId: UUID,
        paramGen: ParameterNameGenerator,
        entityAlias: String,
        direction: QueryDirection,
    ): SqlFragment = generateExistsFragment(relationshipId, paramGen, entityAlias, direction, negated = false)

    /**
     * Generates a NOT EXISTS subquery matching entities with no related entities.
     *
     * Same structure as [generateExists] but with NOT EXISTS prefix.
     */
    private fun generateNotExists(
        relationshipId: UUID,
        paramGen: ParameterNameGenerator,
        entityAlias: String,
        direction: QueryDirection,
    ): SqlFragment = generateExistsFragment(relationshipId, paramGen, entityAlias, direction, negated = true)

    private fun generateExistsFragment(
        relationshipId: UUID,
        paramGen: ParameterNameGenerator,
        entityAlias: String,
        direction: QueryDirection,
        negated: Boolean,
    ): SqlFragment {
        val relAlias = "r_${paramGen.next("a")}"
        val relParam = paramGen.next("rel")
        val prefix = if (negated) "NOT " else ""
        val correlationColumn = direction.correlationColumn()

        val sql = buildString {
            append("${prefix}EXISTS (\n")
            append("    SELECT 1 FROM entity_relationships $relAlias\n")
            append("    WHERE $relAlias.$correlationColumn = $entityAlias.id\n")
            append("      AND $relAlias.relationship_definition_id = :$relParam\n")
            append("      AND $relAlias.deleted = false\n")
            append(")")
        }

        return SqlFragment(
            sql = sql,
            parameters = mapOf(relParam to relationshipId)
        )
    }

    /**
     * Generates an EXISTS subquery matching entities related to specific entity IDs.
     *
     * Converts string entity IDs to UUIDs and uses an IN predicate on the opposite entity column.
     *
     * ```sql
     * EXISTS (
     *     SELECT 1 FROM entity_relationships r_0
     *     WHERE r_0.source_entity_id = e.id
     *       AND r_0.relationship_definition_id = :rel_1
     *       AND r_0.target_id IN (:te_2)
     *       AND r_0.deleted = false
     * )
     * ```
     */
    private fun generateTargetEquals(
        relationshipId: UUID,
        entityIds: List<String>,
        paramGen: ParameterNameGenerator,
        entityAlias: String,
        direction: QueryDirection,
    ): SqlFragment {
        if (entityIds.isEmpty()) {
            return SqlFragment(sql = "1 = 0", parameters = emptyMap())
        }

        val invalidIds = entityIds.filter { id ->
            runCatching { UUID.fromString(id) }.isFailure
        }
        if (invalidIds.isNotEmpty()) {
            throw IllegalArgumentException(
                "generateTargetEquals: invalid entity ID(s): $invalidIds"
            )
        }

        val relAlias = "r_${paramGen.next("a")}"
        val relParam = paramGen.next("rel")
        val targetParam = paramGen.next("te")
        val uuidList = entityIds.map { UUID.fromString(it) }
        val correlationColumn = direction.correlationColumn()
        val oppositeColumn = direction.oppositeColumn()

        val sql = buildString {
            append("EXISTS (\n")
            append("    SELECT 1 FROM entity_relationships $relAlias\n")
            append("    WHERE $relAlias.$correlationColumn = $entityAlias.id\n")
            append("      AND $relAlias.relationship_definition_id = :$relParam\n")
            append("      AND $relAlias.$oppositeColumn IN (:$targetParam)\n")
            append("      AND $relAlias.deleted = false\n")
            append(")")
        }

        return SqlFragment(
            sql = sql,
            parameters = mapOf(
                relParam to relationshipId,
                targetParam to uuidList
            )
        )
    }

    /**
     * Generates an EXISTS subquery with a JOIN to the related entities table,
     * applying a nested filter on the related entity's payload.
     *
     * The nested filter is processed via the [visitor] callback, which receives
     * the related entity's table alias so that attribute references (e.g., `t_0.payload`)
     * point to the correct table.
     *
     * ```sql
     * EXISTS (
     *     SELECT 1 FROM entity_relationships r_0
     *     JOIN entities t_1 ON r_0.target_id = t_1.id AND t_1.deleted = false
     *     WHERE r_0.source_entity_id = e.id
     *       AND r_0.relationship_definition_id = :rel_2
     *       AND r_0.deleted = false
     *       AND {nested filter SQL referencing t_1}
     * )
     * ```
     */
    private fun generateTargetMatches(
        relationshipId: UUID,
        nestedFilter: QueryFilter,
        paramGen: ParameterNameGenerator,
        entityAlias: String,
        direction: QueryDirection,
        visitor: (QueryFilter, ParameterNameGenerator, String) -> SqlFragment
    ): SqlFragment {
        val relAlias = "r_${paramGen.next("a")}"
        val targetAlias = "t_${paramGen.next("a")}"
        val relParam = paramGen.next("rel")
        val correlationColumn = direction.correlationColumn()
        val joinColumn = direction.oppositeColumn()

        val nestedFragment = visitor(nestedFilter, paramGen, targetAlias)

        val sql = buildString {
            append("EXISTS (\n")
            append("    SELECT 1 FROM entity_relationships $relAlias\n")
            append("    JOIN entities $targetAlias ON $relAlias.$joinColumn = $targetAlias.id AND $targetAlias.deleted = false\n")
            append("    WHERE $relAlias.$correlationColumn = $entityAlias.id\n")
            append("      AND $relAlias.relationship_definition_id = :$relParam\n")
            append("      AND $relAlias.deleted = false\n")
            append("      AND ${nestedFragment.sql}\n")
            append(")")
        }

        return SqlFragment(
            sql = sql,
            parameters = mapOf(relParam to relationshipId) + nestedFragment.parameters
        )
    }

    /**
     * Generates an EXISTS subquery with OR-branched type predicates for polymorphic relationships.
     *
     * Each [TypeBranch] produces a type check (`target.type_id = :typeParam`) optionally
     * combined with a nested filter. Branches are combined with OR semantics: the condition
     * matches if the related entity satisfies any branch.
     *
     * ```sql
     * EXISTS (
     *     SELECT 1 FROM entity_relationships r_0
     *     JOIN entities t_1 ON r_0.target_id = t_1.id AND t_1.deleted = false
     *     WHERE r_0.source_entity_id = e.id
     *       AND r_0.relationship_definition_id = :rel_2
     *       AND r_0.deleted = false
     *       AND (
     *           (t_1.type_id = :ttm_type_3 AND {branch filter SQL})
     *           OR
     *           (t_1.type_id = :ttm_type_4)
     *       )
     * )
     * ```
     */
    private fun generateTargetTypeMatches(
        relationshipId: UUID,
        branches: List<TypeBranch>,
        paramGen: ParameterNameGenerator,
        entityAlias: String,
        direction: QueryDirection,
        visitor: (QueryFilter, ParameterNameGenerator, String) -> SqlFragment
    ): SqlFragment {
        if (branches.isEmpty()) {
            return SqlFragment(sql = "1 = 0", parameters = emptyMap())
        }

        val relAlias = "r_${paramGen.next("a")}"
        val targetAlias = "t_${paramGen.next("a")}"
        val relParam = paramGen.next("rel")
        val correlationColumn = direction.correlationColumn()
        val joinColumn = direction.oppositeColumn()

        val branchFragments = branches.map { branch ->
            val typeParam = paramGen.next("ttm_type")
            val typeCondition = SqlFragment(
                "${targetAlias}.type_id = :$typeParam",
                mapOf(typeParam to branch.entityTypeId)
            )

            if (branch.filter != null) {
                val filterFragment = visitor(branch.filter, paramGen, targetAlias)
                typeCondition.and(filterFragment)
            } else {
                typeCondition
            }
        }

        val combinedBranches = branchFragments.reduce { acc, fragment -> acc.or(fragment) }

        val sql = buildString {
            append("EXISTS (\n")
            append("    SELECT 1 FROM entity_relationships $relAlias\n")
            append("    JOIN entities $targetAlias ON $relAlias.$joinColumn = $targetAlias.id AND $targetAlias.deleted = false\n")
            append("    WHERE $relAlias.$correlationColumn = $entityAlias.id\n")
            append("      AND $relAlias.relationship_definition_id = :$relParam\n")
            append("      AND $relAlias.deleted = false\n")
            append("      AND (${combinedBranches.sql})\n")
            append(")")
        }

        return SqlFragment(
            sql = sql,
            parameters = mapOf(relParam to relationshipId) + combinedBranches.parameters
        )
    }
    /**
     * Generates a bidirectional EXISTS filter that matches entities with any relationship
     * (source or target), regardless of definition.
     *
     * Produces two EXISTS subqueries joined by OR — one for forward links and one for
     * inverse links — so each can use its own index.
     *
     * ```sql
     * (EXISTS (SELECT 1 FROM entity_relationships r_0
     *          WHERE r_0.source_entity_id = e.id AND r_0.deleted = false)
     *  OR EXISTS (SELECT 1 FROM entity_relationships r_1
     *             WHERE r_1.target_id = e.id AND r_1.deleted = false))
     * ```
     */
    fun generateIsRelatedTo(
        condition: RelationshipFilter,
        paramGen: ParameterNameGenerator,
        entityAlias: String = "e",
    ): SqlFragment {
        return when (condition) {
            is RelationshipFilter.Exists -> generateBidirectionalExists(paramGen, entityAlias, negated = false)
            is RelationshipFilter.NotExists -> generateBidirectionalExists(paramGen, entityAlias, negated = true)
            else -> throw UnsupportedOperationException(
                "IsRelatedTo only supports Exists and NotExists conditions, got: ${condition::class.simpleName}"
            )
        }
    }

    private fun generateBidirectionalExists(
        paramGen: ParameterNameGenerator,
        entityAlias: String,
        negated: Boolean,
    ): SqlFragment {
        val fwdAlias = "r_${paramGen.next("a")}"
        val invAlias = "r_${paramGen.next("a")}"
        val prefix = if (negated) "NOT " else ""
        val connector = if (negated) " AND " else " OR "

        val sql = buildString {
            append("(${prefix}EXISTS (\n")
            append("    SELECT 1 FROM entity_relationships $fwdAlias\n")
            append("    WHERE $fwdAlias.source_entity_id = $entityAlias.id AND $fwdAlias.deleted = false\n")
            append(")${connector}${prefix}EXISTS (\n")
            append("    SELECT 1 FROM entity_relationships $invAlias\n")
            append("    WHERE $invAlias.target_id = $entityAlias.id AND $invAlias.deleted = false\n")
            append("))")
        }
        return SqlFragment(sql = sql, parameters = emptyMap())
    }
}

/**
 * Returns the column used to correlate with the outer entity alias.
 */
private fun QueryDirection.correlationColumn(): String = when (this) {
    QueryDirection.FORWARD -> "source_entity_id"
    QueryDirection.INVERSE -> "target_id"
}

/**
 * Returns the opposite column (the "related" side).
 */
private fun QueryDirection.oppositeColumn(): String = when (this) {
    QueryDirection.FORWARD -> "target_id"
    QueryDirection.INVERSE -> "source_entity_id"
}
