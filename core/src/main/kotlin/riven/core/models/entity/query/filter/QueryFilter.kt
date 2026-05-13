package cranium.core.models.entity.query.filter

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import tools.jackson.databind.annotation.JsonTypeIdResolver
import io.swagger.v3.oas.annotations.media.Schema
import cranium.core.configuration.util.CaseInsensitiveTypeIdResolver
import cranium.core.enums.entity.query.FilterOperator
import java.util.*

/**
 * Sealed hierarchy for filter expressions supporting:
 * - Attribute comparisons (Status == "Active", ARR > 100000)
 * - Relationship traversals (has Client, has Client where Client.tier == "Premium")
 * - Logical combinations (AND/OR grouping)
 */
@Schema(
    description = "Filter expression for querying entities.",
    oneOf = [
        QueryFilter.Attribute::class,
        QueryFilter.Relationship::class,
        QueryFilter.IsRelatedTo::class,
        QueryFilter.And::class,
        QueryFilter.Or::class
    ]
)
@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonTypeIdResolver(CaseInsensitiveTypeIdResolver::class)
@JsonSubTypes(
    JsonSubTypes.Type(QueryFilter.Attribute::class, name = "ATTRIBUTE"),
    JsonSubTypes.Type(QueryFilter.Relationship::class, name = "RELATIONSHIP"),
    JsonSubTypes.Type(QueryFilter.IsRelatedTo::class, name = "IS_RELATED_TO"),
    JsonSubTypes.Type(QueryFilter.And::class, name = "AND"),
    JsonSubTypes.Type(QueryFilter.Or::class, name = "OR")
)
sealed interface QueryFilter {

    /**
     * Filter by attribute value comparison.
     *
     * Examples:
     * - Status == "Active"
     * - ARR > 100000
     * - Name CONTAINS "Corp"
     *
     * @property attributeId UUID key of the attribute in the entity schema
     * @property operator Comparison operator to apply
     * @property value Value to compare against (literal or template)
     */
    @Schema(description = "Filter by attribute value comparison.")
    @JsonTypeName("ATTRIBUTE")
    data class Attribute(
        @param:Schema(description = "UUID key of the attribute in the entity schema.")
        val attributeId: UUID,

        @param:Schema(description = "Comparison operator to apply.")
        val operator: FilterOperator,

        @param:Schema(description = "Value to compare against (literal or template expression).")
        val value: FilterValue
    ) : QueryFilter

    /**
     * Filter by relationship existence or with nested conditions on related entities.
     *
     * Examples:
     * - Has any Client relationship
     * - Has Client where Client.tier == "Premium"
     * - Related to specific entity ID
     *
     * @property relationshipId UUID of the relationship definition
     * @property condition How to evaluate the relationship
     */
    @Schema(description = "Filter by relationship to other entities.")
    @JsonTypeName("RELATIONSHIP")
    data class Relationship(
        @param:Schema(description = "UUID of the relationship definition.")
        val relationshipId: UUID,

        @param:Schema(description = "Condition to apply on the relationship.")
        val condition: RelationshipFilter
    ) : QueryFilter

    /**
     * Cross-definition filter: entity has at least one relationship (of any definition)
     * either as source or target.
     *
     * Unlike [Relationship], this does not require a specific relationship definition ID.
     * It checks across ALL relationship definitions in the workspace.
     *
     * @property condition How to evaluate (defaults to Exists)
     */
    @Schema(description = "Filter by any relationship across all definitions.")
    @JsonTypeName("IS_RELATED_TO")
    data class IsRelatedTo(
        @param:Schema(description = "Condition to apply (defaults to EXISTS).")
        val condition: RelationshipFilter = RelationshipFilter.Exists,
    ) : QueryFilter

    /**
     * Logical AND - all conditions must match.
     *
     * @property conditions List of filters that must all evaluate to true
     */
    @Schema(description = "Logical AND: all conditions must match.")
    @JsonTypeName("AND")
    data class And(
        @param:Schema(description = "List of conditions that must all match.")
        val conditions: List<QueryFilter>
    ) : QueryFilter

    /**
     * Logical OR - at least one condition must match.
     *
     * @property conditions List of filters where at least one must evaluate to true
     */
    @Schema(description = "Logical OR: at least one condition must match.")
    @JsonTypeName("OR")
    data class Or(
        @param:Schema(description = "List of conditions where at least one must match.")
        val conditions: List<QueryFilter>
    ) : QueryFilter
}

