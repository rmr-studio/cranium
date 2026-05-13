package cranium.core.service.entity.query

import org.springframework.stereotype.Component
import cranium.core.exceptions.query.InvalidRelationshipReferenceException
import cranium.core.exceptions.query.QueryFilterException
import cranium.core.exceptions.query.RelationshipDepthExceededException
import cranium.core.exceptions.query.UnsupportedIsRelatedToConditionException
import cranium.core.models.entity.RelationshipDefinition
import cranium.core.models.entity.query.filter.QueryFilter
import cranium.core.models.entity.query.filter.RelationshipFilter
import java.util.*

/**
 * Eager pre-validation pass over the [QueryFilter] tree before SQL generation.
 *
 * Walks the entire filter tree to collect ALL relationship validation errors
 * in a single pass rather than surfacing them one at a time during SQL generation.
 * This gives callers complete diagnostic context upfront.
 *
 * The validator does NOT generate SQL. It only checks structural correctness
 * of relationship references and depth constraints. Errors are collected into
 * a list and returned -- callers decide whether and how to throw.
 *
 * Validations performed:
 * - Relationship IDs exist in the provided definitions map
 * - Relationship traversal depth does not exceed `maxDepth`
 * - TargetTypeMatches branches are recursively validated
 *
 * Usage:
 * ```kotlin
 * val errors = QueryFilterValidator().validate(filter, relationshipDefs, maxDepth = 3)
 * if (errors.isNotEmpty()) {
 *     throw QueryValidationException(errors)
 * }
 * ```
 */
@Component
class QueryFilterValidator {

    /**
     * Validates the filter tree for relationship correctness.
     *
     * Walks the entire tree collecting all validation errors. Returns an empty
     * list if the filter is valid. Callers should wrap non-empty results in
     * [cranium.core.exceptions.query.QueryValidationException] if they wish to throw.
     *
     * @param filter The root filter to validate
     * @param relationshipDefinitions Pre-loaded map of relationship ID to definition.
     *   Callers build this from `entityType.relationships?.associateBy { it.id }`.
     * @param maxDepth Maximum allowed relationship traversal depth
     * @return List of validation errors (empty if valid)
     */
    fun validate(
        filter: QueryFilter,
        relationshipDefinitions: Map<UUID, RelationshipDefinition>,
        maxDepth: Int,
    ): List<QueryFilterException> {
        val context = ValidationContext(
            relationshipDefinitions = relationshipDefinitions,
            maxDepth = maxDepth,
            errors = mutableListOf(),
        )
        walkFilter(filter, context, relationshipDepth = 0)
        return context.errors
    }

    /**
     * Recursively walks the filter tree dispatching on filter type.
     *
     * @param filter Current filter node
     * @param context Validation context accumulating errors
     * @param relationshipDepth Current relationship traversal depth (increments only on Relationship nodes)
     */
    private fun walkFilter(filter: QueryFilter, context: ValidationContext, relationshipDepth: Int) {
        when (filter) {
            is QueryFilter.Attribute -> {
                // No relationship validation needed for attribute filters
            }

            is QueryFilter.And -> {
                // AND does not increment relationship depth
                for (condition in filter.conditions) {
                    walkFilter(condition, context, relationshipDepth)
                }
            }

            is QueryFilter.Or -> {
                // OR does not increment relationship depth
                for (condition in filter.conditions) {
                    walkFilter(condition, context, relationshipDepth)
                }
            }

            is QueryFilter.IsRelatedTo -> {
                when (filter.condition) {
                    is RelationshipFilter.Exists, is RelationshipFilter.NotExists -> {
                        // Supported conditions for IsRelatedTo — no additional validation
                    }
                    else -> {
                        context.errors.add(
                            UnsupportedIsRelatedToConditionException(
                                conditionType = filter.condition::class.simpleName ?: "unknown",
                            )
                        )
                    }
                }
            }

            is QueryFilter.Relationship -> {
                // Check depth constraint
                if (relationshipDepth >= context.maxDepth) {
                    context.errors.add(
                        RelationshipDepthExceededException(
                            depth = relationshipDepth + 1,
                            maxDepth = context.maxDepth,
                        )
                    )
                }

                // Look up relationship definition only at the root level (depth 0).
                // Nested relationship IDs reference target entity type definitions,
                // which aren't available without loading the target entity type schema.
                val definition = if (relationshipDepth == 0) {
                    val def = context.relationshipDefinitions[filter.relationshipId]
                    if (def == null) {
                        context.errors.add(
                            InvalidRelationshipReferenceException(
                                relationshipId = filter.relationshipId,
                                reason = "not found in entity type relationship definitions",
                            )
                        )
                    }
                    def
                } else {
                    null
                }

                // Validate the condition (continue even if definition is null to collect all errors)
                validateCondition(
                    condition = filter.condition,
                    definition = definition,
                    relationshipId = filter.relationshipId,
                    context = context,
                    relationshipDepth = relationshipDepth + 1,
                )
            }
        }
    }

    /**
     * Validates a relationship condition, recursing into nested filters.
     *
     * @param condition The relationship condition to validate
     * @param definition The relationship definition (may be null if lookup failed)
     * @param relationshipId The relationship ID for error context
     * @param context Validation context accumulating errors
     * @param relationshipDepth New relationship depth after entering this relationship
     */
    private fun validateCondition(
        condition: RelationshipFilter,
        definition: RelationshipDefinition?,
        relationshipId: UUID,
        context: ValidationContext,
        relationshipDepth: Int,
    ) {
        when (condition) {
            is RelationshipFilter.Exists -> {
                // No additional validation
            }

            is RelationshipFilter.NotExists -> {
                // No additional validation
            }

            is RelationshipFilter.TargetEquals -> {
                // entityIds validated at model level
            }

            is RelationshipFilter.TargetMatches -> {
                walkFilter(condition.filter, context, relationshipDepth)
            }

            is RelationshipFilter.TargetTypeMatches -> {
                // TODO: Cross-reference branch entityTypeId (UUID) against definition.entityTypeKeys (String keys)
                //  once entity type key-to-ID mapping is available (Phase 5's EntityQueryService will provide this).
                //  For now, we only recurse into branch filters for depth/reference validation.
                for (branch in condition.branches) {
                    if (branch.filter != null) {
                        walkFilter(branch.filter, context, relationshipDepth)
                    }
                }
            }

            is RelationshipFilter.CountMatches -> {
                // Out of scope for Phase 3 -- handled as unsupported at generation time
            }
        }
    }

    /**
     * Internal context for accumulating validation state during tree walk.
     */
    private data class ValidationContext(
        val relationshipDefinitions: Map<UUID, RelationshipDefinition>,
        val maxDepth: Int,
        val errors: MutableList<QueryFilterException>,
    )
}
