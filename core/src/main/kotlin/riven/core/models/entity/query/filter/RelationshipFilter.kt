package riven.core.models.entity.query.filter

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import tools.jackson.databind.annotation.JsonTypeIdResolver
import io.swagger.v3.oas.annotations.media.Schema
import riven.core.configuration.util.CaseInsensitiveTypeIdResolver
import riven.core.enums.entity.query.FilterOperator


/**
 * Conditions for relationship filtering.
 *
 * Determines how to evaluate a relationship when filtering entities.
 */
@Schema(
    description = "Condition for evaluating relationships in filters.",
    oneOf = [
        RelationshipFilter.Exists::class,
        RelationshipFilter.NotExists::class,
        RelationshipFilter.TargetEquals::class,
        RelationshipFilter.TargetMatches::class,
        RelationshipFilter.TargetTypeMatches::class,
        RelationshipFilter.CountMatches::class
    ]
)
@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonTypeIdResolver(CaseInsensitiveTypeIdResolver::class)
@JsonSubTypes(
    JsonSubTypes.Type(RelationshipFilter.Exists::class, name = "EXISTS"),
    JsonSubTypes.Type(RelationshipFilter.NotExists::class, name = "NOT_EXISTS"),
    JsonSubTypes.Type(RelationshipFilter.TargetEquals::class, name = "TARGET_EQUALS"),
    JsonSubTypes.Type(RelationshipFilter.TargetMatches::class, name = "TARGET_MATCHES"),
    JsonSubTypes.Type(RelationshipFilter.TargetTypeMatches::class, name = "TARGET_TYPE_MATCHES"),
    JsonSubTypes.Type(RelationshipFilter.CountMatches::class, name = "COUNT_MATCHES")
)
sealed interface RelationshipFilter {

    /**
     * Entity has at least one related entity via this relationship.
     */
    @Schema(description = "Entity has at least one related entity.")
    @JsonTypeName("EXISTS")
    data object Exists : RelationshipFilter

    /**
     * Entity has no related entities via this relationship.
     */
    @Schema(description = "Entity has no related entities.")
    @JsonTypeName("NOT_EXISTS")
    data object NotExists : RelationshipFilter

    /**
     * Entity is related to one of the specified entity IDs.
     *
     * Supports template expressions for dynamic entity ID resolution.
     *
     * @property entityIds List of entity IDs (UUIDs or template expressions)
     */
    @Schema(description = "Entity is related to one of the specified entities.")
    @JsonTypeName("TARGET_EQUALS")
    data class TargetEquals(
        @param:Schema(
            description = "List of entity IDs to match. Supports template expressions.",
            example = "[\"550e8400-e29b-41d4-a716-446655440000\", \"{{ steps.lookup.output.entityId }}\"]"
        )
        val entityIds: List<String>
    ) : RelationshipFilter

    /**
     * Related entity satisfies the nested filter criteria.
     *
     * Enables "related to X that has Y" queries by recursively applying
     * filters to the related entities.
     *
     * @property filter Nested filter to apply on related entities
     */
    @Schema(description = "Related entity satisfies nested filter criteria.")
    @JsonTypeName("TARGET_MATCHES")
    data class TargetMatches(
        @param:Schema(description = "Filter to apply on related entities.")
        val filter: QueryFilter
    ) : RelationshipFilter

    /**
     * Type-aware filtering for polymorphic relationships.
     *
     * Matches if the related entity's type matches any branch AND
     * satisfies that branch's optional filter (OR semantics across branches).
     *
     * @property branches List of type-specific filter branches (at least one required)
     */
    @Schema(description = "Type-aware filtering for polymorphic relationships.")
    @JsonTypeName("TARGET_TYPE_MATCHES")
    data class TargetTypeMatches(
        @param:Schema(description = "Type-specific filter branches. At least one required.")
        val branches: List<TypeBranch>
    ) : RelationshipFilter {
        init {
            require(branches.isNotEmpty()) { "TargetTypeMatches requires at least one branch" }
        }
    }

    /**
     * Relationship count satisfies the specified condition.
     *
     * Examples:
     * - Has more than 5 related entities
     * - Has exactly 1 related entity
     *
     * @property operator Comparison operator for count
     * @property count Count value to compare against
     */
    @Schema(description = "Relationship count satisfies condition.")
    @JsonTypeName("COUNT_MATCHES")
    data class CountMatches(
        @param:Schema(description = "Comparison operator for count.")
        val operator: FilterOperator,

        @param:Schema(description = "Count value to compare against.")
        val count: Int
    ) : RelationshipFilter
}
