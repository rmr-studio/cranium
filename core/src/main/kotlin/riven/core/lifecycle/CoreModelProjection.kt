package riven.core.lifecycle

import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticGroup

/**
 * Declares which integration entities project into this core model.
 * Routing is by (LifecycleDomain, SemanticGroup) pair — source-agnostic.
 *
 * Not installed until PR2 — declared here for co-location with core model definitions.
 */
data class ProjectionAcceptRule(
    val domain: LifecycleDomain,
    val semanticGroup: SemanticGroup,
    val relationshipName: String,
    val autoCreate: Boolean = true,
) {
    companion object {
        /** Standard relationship name for integration-to-core projection links. */
        const val SOURCE_DATA_RELATIONSHIP = "source-data"
    }
}

/**
 * Aggregation column definition. Computed at query time from relationships.
 *
 * Not installed until PR2 — declared here for co-location with core model definitions.
 */
data class AggregationColumnDefinition(
    val name: String,
    val aggregation: AggregationType,
    val sourceRelationshipKey: String,
    val targetAttributeKey: String? = null,
    val filter: AggregationFilter? = null,
)

enum class AggregationType {
    COUNT, SUM, LATEST, STATUS,
}

data class AggregationFilter(
    val attributeKey: String,
    val operator: FilterOperator,
    val values: List<String>,
)

enum class FilterOperator {
    IN, EQUALS, NOT_EQUALS,
}
