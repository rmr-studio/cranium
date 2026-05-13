package cranium.core.models.workflow.node.config.actions

import cranium.core.models.entity.query.filter.FilterValue
import cranium.core.models.entity.query.filter.QueryFilter
import cranium.core.models.entity.query.filter.RelationshipFilter
import cranium.core.models.workflow.engine.state.WorkflowDataStore
import cranium.core.models.workflow.node.config.validation.ConfigValidationError
import cranium.core.service.workflow.state.WorkflowNodeConfigValidationService
import cranium.core.service.workflow.state.WorkflowNodeInputResolverService

/**
 * Shared utilities for filter template validation and resolution.
 *
 * Used by action configs that embed entity queries with filterable templates
 * (e.g., QUERY_ENTITY, BULK_UPDATE_ENTITY).
 */
object WorkflowFilterTemplateUtils {

    /**
     * Recursively validates a QueryFilter structure.
     *
     * Walks the filter tree validating FilterValue.Template syntax and
     * RelationshipFilter conditions at each level.
     */
    fun validateFilter(
        filter: QueryFilter,
        path: String,
        validationService: WorkflowNodeConfigValidationService
    ): List<ConfigValidationError> {
        return when (filter) {
            is QueryFilter.Attribute -> {
                validateFilterValue(filter.value, "$path.value", validationService)
            }

            is QueryFilter.Relationship -> {
                validateRelationshipCondition(filter.condition, "$path.condition", validationService)
            }

            is QueryFilter.And -> {
                if (filter.conditions.isEmpty()) {
                    listOf(ConfigValidationError(path, "AND filter must have at least one condition"))
                } else {
                    filter.conditions.flatMapIndexed { index, condition ->
                        validateFilter(condition, "$path.conditions[$index]", validationService)
                    }
                }
            }

            is QueryFilter.Or -> {
                if (filter.conditions.isEmpty()) {
                    listOf(ConfigValidationError(path, "OR filter must have at least one condition"))
                } else {
                    filter.conditions.flatMapIndexed { index, condition ->
                        validateFilter(condition, "$path.conditions[$index]", validationService)
                    }
                }
            }

            is QueryFilter.IsRelatedTo -> {
                // No template expressions in IsRelatedTo — nothing to validate
                emptyList()
            }
        }
    }

    /**
     * Validates a FilterValue (literal or template).
     */
    fun validateFilterValue(
        value: FilterValue,
        path: String,
        validationService: WorkflowNodeConfigValidationService
    ): List<ConfigValidationError> {
        return when (value) {
            is FilterValue.Literal -> emptyList()
            is FilterValue.Template -> validationService.validateTemplateSyntax(value.expression, path)
        }
    }

    /**
     * Validates a RelationshipFilter condition.
     */
    fun validateRelationshipCondition(
        condition: RelationshipFilter,
        path: String,
        validationService: WorkflowNodeConfigValidationService
    ): List<ConfigValidationError> {
        return when (condition) {
            is RelationshipFilter.Exists -> emptyList()
            is RelationshipFilter.NotExists -> emptyList()

            is RelationshipFilter.TargetEquals -> {
                if (condition.entityIds.isEmpty()) {
                    listOf(ConfigValidationError(path, "TARGET_EQUALS must specify at least one entity ID"))
                } else {
                    condition.entityIds.flatMapIndexed { index, id ->
                        validationService.validateTemplateOrUuid(id, "$path.entityIds[$index]")
                    }
                }
            }

            is RelationshipFilter.TargetMatches -> {
                validateFilter(condition.filter, "$path.filter", validationService)
            }

            is RelationshipFilter.TargetTypeMatches -> {
                if (condition.branches.isEmpty()) {
                    listOf(ConfigValidationError(path, "TARGET_TYPE_MATCHES must specify at least one branch"))
                } else {
                    condition.branches.flatMapIndexed { index, branch ->
                        branch.filter?.let { validateFilter(it, "$path.branches[$index].filter", validationService) }
                            ?: emptyList()
                    }
                }
            }

            is RelationshipFilter.CountMatches -> {
                if (condition.count < 0) {
                    listOf(ConfigValidationError(path, "Count must be non-negative"))
                } else {
                    emptyList()
                }
            }
        }
    }

    /**
     * Recursively resolves template FilterValues in the filter tree.
     *
     * Walks the filter structure and replaces any FilterValue.Template with
     * FilterValue.Literal containing the resolved value.
     *
     * @param filter The filter to resolve templates in
     * @param dataStore Workflow datastore for template resolution
     * @param resolver Service to resolve template expressions
     * @return Filter with all templates resolved to literals
     */
    fun resolveFilterTemplates(
        filter: QueryFilter,
        dataStore: WorkflowDataStore,
        resolver: WorkflowNodeInputResolverService
    ): QueryFilter {
        return when (filter) {
            is QueryFilter.Attribute -> {
                val resolvedValue = when (val value = filter.value) {
                    is FilterValue.Literal -> value
                    is FilterValue.Template -> {
                        val resolved = resolver.resolve(value.expression, dataStore)
                        FilterValue.Literal(resolved)
                    }
                }
                filter.copy(value = resolvedValue)
            }

            is QueryFilter.And -> {
                filter.copy(
                    conditions = filter.conditions.map { condition ->
                        resolveFilterTemplates(condition, dataStore, resolver)
                    }
                )
            }

            is QueryFilter.Or -> {
                filter.copy(
                    conditions = filter.conditions.map { condition ->
                        resolveFilterTemplates(condition, dataStore, resolver)
                    }
                )
            }

            is QueryFilter.Relationship -> {
                val resolvedCondition = resolveRelationshipConditionTemplates(
                    filter.condition,
                    dataStore,
                    resolver
                )
                filter.copy(condition = resolvedCondition)
            }

            is QueryFilter.IsRelatedTo -> {
                // No template expressions in IsRelatedTo — return as-is
                filter
            }
        }
    }

    /**
     * Resolves templates in relationship filter conditions.
     *
     * Handles template resolution for different relationship condition types.
     * Throws [IllegalStateException] if a template resolves to null for values
     * expected to be entity IDs (TargetEquals).
     *
     * @param condition The relationship condition to resolve templates in
     * @param dataStore Workflow datastore for template resolution
     * @param resolver Service to resolve template expressions
     * @return Relationship condition with all templates resolved
     */
    fun resolveRelationshipConditionTemplates(
        condition: RelationshipFilter,
        dataStore: WorkflowDataStore,
        resolver: WorkflowNodeInputResolverService
    ): RelationshipFilter {
        return when (condition) {
            is RelationshipFilter.Exists -> condition
            is RelationshipFilter.NotExists -> condition

            is RelationshipFilter.TargetEquals -> {
                val resolvedIds = condition.entityIds.map { id ->
                    resolver.resolve(id, dataStore)?.toString()
                        ?: throw IllegalStateException(
                            "Failed to resolve template '$id' to entity ID in TARGET_EQUALS filter"
                        )
                }
                condition.copy(entityIds = resolvedIds)
            }

            is RelationshipFilter.TargetMatches -> {
                condition.copy(
                    filter = resolveFilterTemplates(condition.filter, dataStore, resolver)
                )
            }

            is RelationshipFilter.TargetTypeMatches -> {
                val resolvedBranches = condition.branches.map { branch ->
                    branch.copy(
                        filter = branch.filter?.let { resolveFilterTemplates(it, dataStore, resolver) }
                    )
                }
                condition.copy(branches = resolvedBranches)
            }

            is RelationshipFilter.CountMatches -> condition
        }
    }
}
