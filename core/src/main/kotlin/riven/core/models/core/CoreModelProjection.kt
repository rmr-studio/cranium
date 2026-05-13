package cranium.core.models.core

import cranium.core.enums.entity.LifecycleDomain
import cranium.core.enums.entity.semantics.SemanticGroup

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
    val aggregation: cranium.core.models.core.AggregationType,
    val sourceRelationshipKey: String,
    val targetAttributeKey: String? = null,
    val filter: cranium.core.models.core.AggregationFilter? = null,
)

enum class AggregationType {
    COUNT, SUM, LATEST, STATUS,
}

data class AggregationFilter(
    val attributeKey: String,
    val operator: cranium.core.models.core.FilterOperator,
    val values: List<String>,
)

enum class FilterOperator {
    IN, EQUALS, NOT_EQUALS,
}
