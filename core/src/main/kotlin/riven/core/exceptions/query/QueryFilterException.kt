package cranium.core.exceptions.query

import cranium.core.enums.core.DataType
import cranium.core.enums.entity.query.FilterOperator
import java.util.*

/**
 * Base exception for query filter errors.
 *
 * All query filter exceptions extend this sealed class, enabling
 * exhaustive when-expression handling in error processing code.
 */
sealed class QueryFilterException(message: String) : RuntimeException(message)

/**
 * Thrown when an attribute reference in a filter is invalid.
 *
 * This typically indicates the attribute UUID does not exist in the
 * entity type schema being queried.
 *
 * @property attributeId UUID of the referenced attribute
 * @property reason Description of why the reference is invalid
 */
class InvalidAttributeReferenceException(
    val attributeId: UUID,
    val reason: String
) : QueryFilterException("Attribute $attributeId: $reason")

/**
 * Thrown when a filter operator is not supported for an attribute type.
 *
 * For example, GREATER_THAN is not valid for boolean attributes,
 * or CONTAINS is not valid for numeric attributes.
 *
 * @property operator The filter operator that was attempted
 * @property attributeLabel Human-readable label of the attribute (may be null)
 * @property attributeId UUID of the attribute
 * @property attributeType The data type of the attribute
 */
class UnsupportedOperatorException(
    val operator: FilterOperator,
    val attributeLabel: String?,
    val attributeId: UUID,
    val attributeType: DataType
) : QueryFilterException(
    "Operator $operator not supported for $attributeType attribute '${attributeLabel ?: attributeId}' (id: $attributeId)"
)

/**
 * Thrown when filter AND/OR nesting depth exceeds the configured maximum.
 *
 * Deep nesting can cause performance issues and excessive SQL complexity.
 * This exception prevents runaway filter structures.
 *
 * @property depth The nesting depth that was attempted
 * @property maxDepth The configured maximum depth
 */
class FilterNestingDepthExceededException(
    val depth: Int,
    val maxDepth: Int
) : QueryFilterException("Filter nesting depth $depth exceeds maximum $maxDepth")

/**
 * Thrown when a relationship reference in a filter is invalid.
 *
 * This typically indicates the relationship UUID does not exist in the
 * entity type's relationship definitions.
 *
 * @property relationshipId UUID of the referenced relationship
 * @property reason Description of why the reference is invalid
 */
class InvalidRelationshipReferenceException(
    val relationshipId: UUID,
    val reason: String
) : QueryFilterException("Relationship $relationshipId: $reason")

/**
 * Thrown when relationship traversal depth exceeds the configured maximum.
 *
 * Deep relationship nesting can cause expensive SQL subqueries.
 * This exception prevents runaway traversal depth.
 *
 * @property depth The traversal depth that was attempted
 * @property maxDepth The configured maximum depth
 */
class RelationshipDepthExceededException(
    val depth: Int,
    val maxDepth: Int
) : QueryFilterException("Relationship traversal depth $depth exceeds maximum $maxDepth")

/**
 * Thrown when a type branch in a TargetTypeMatches condition references
 * an invalid entity type for the relationship.
 *
 * @property entityTypeId UUID of the entity type in the branch
 * @property relationshipId UUID of the relationship definition
 * @property reason Description of why the type branch is invalid
 */
class InvalidTypeBranchException(
    val entityTypeId: UUID,
    val relationshipId: UUID,
    val reason: String
) : QueryFilterException("Type branch $entityTypeId for relationship $relationshipId: $reason")

/**
 * Thrown when an unsupported condition type is used with IsRelatedTo.
 *
 * IsRelatedTo only supports Exists and NotExists conditions.
 * Conditions like TargetEquals, TargetMatches, TargetTypeMatches, and
 * CountMatches are not valid for cross-definition relationship checks.
 *
 * @property conditionType The name of the unsupported condition
 */
class UnsupportedIsRelatedToConditionException(
    val conditionType: String,
) : QueryFilterException(
    "IsRelatedTo only supports Exists and NotExists conditions, got: $conditionType"
)

/**
 * Wrapper exception containing all validation errors collected during
 * a single filter tree validation pass.
 *
 * Rather than failing on the first error, the validator collects all
 * errors across the entire tree and reports them together.
 *
 * @property validationErrors List of all collected filter validation errors
 */
class QueryValidationException(
    val validationErrors: List<QueryFilterException>
) : QueryFilterException(
    "Query validation failed with ${validationErrors.size} error(s): " +
            validationErrors.joinToString("; ") { it.message ?: "unknown" }
) {
    init {
        require(validationErrors.isNotEmpty()) { "validationErrors must not be empty" }
    }
}
