package cranium.core.models.core

import cranium.core.enums.entity.EntityRelationshipCardinality
import cranium.core.models.catalog.NormalizedRelationship
import cranium.core.models.catalog.NormalizedTargetRule
import cranium.core.models.catalog.ResolvedRelationshipSemantics

/**
 * Relationship definition on a core model. Source is the declaring model,
 * target is another CoreModelDefinition referenced by compile-time object reference.
 */
data class CoreModelRelationship(
    val key: String,
    val name: String,
    val sourceModelKey: String,
    val targetModelKey: String,
    val cardinality: EntityRelationshipCardinality = EntityRelationshipCardinality.ONE_TO_MANY,
    val inverseName: String? = null,
    val semantics: cranium.core.models.core.RelationshipSemantics? = null,
) {
    /** Converts to the pipeline's NormalizedRelationship format. */
    fun toNormalized(): NormalizedRelationship = NormalizedRelationship(
        key = key,
        sourceEntityTypeKey = sourceModelKey,
        name = name,
        cardinalityDefault = cardinality,
        `protected` = true,
        targetRules = listOf(
            NormalizedTargetRule(
                targetEntityTypeKey = targetModelKey,
                cardinalityOverride = cardinality,
                inverseName = inverseName?.takeIf { it.isNotEmpty() },
            )
        ),
        semantics = semantics?.let { s ->
            ResolvedRelationshipSemantics(definition = s.definition, tags = s.tags)
        },
    )
}

data class RelationshipSemantics(
    val definition: String,
    val tags: List<String> = emptyList(),
)
