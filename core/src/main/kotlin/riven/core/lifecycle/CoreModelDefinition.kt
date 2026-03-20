package riven.core.lifecycle

import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.models.catalog.NormalizedRelationship
import riven.core.models.catalog.NormalizedTargetRule
import riven.core.models.catalog.ResolvedEntityType
import riven.core.models.catalog.ResolvedRelationshipSemantics
import riven.core.models.catalog.ResolvedSemantics

/**
 * Abstract base for all core lifecycle model definitions.
 *
 * Provides shared defaults for protected core models:
 * - protected = true (non-deletable)
 * - sourceType = TEMPLATE
 * - readonly = false (users can add custom attributes)
 *
 * Subclasses are Kotlin objects (singletons) — one per core entity type.
 * Each object declares its attributes, relationships, projection routing,
 * and aggregation columns. Reading a model object gives the complete picture.
 *
 * Core models produce ResolvedManifest via toResolvedEntityType() for
 * consumption by ManifestUpsertService. The catalog is the runtime registry.
 */
abstract class CoreModelDefinition(
    val key: String,
    val displayNameSingular: String,
    val displayNamePlural: String,
    val iconType: IconType = IconType.CIRCLE_DASHED,
    val iconColour: IconColour = IconColour.NEUTRAL,
    val semanticGroup: SemanticGroup = SemanticGroup.UNCATEGORIZED,
    val lifecycleDomain: LifecycleDomain = LifecycleDomain.UNCATEGORIZED,
    val identifierKey: String,
    val attributes: Map<String, CoreModelAttribute>,
    val relationships: List<CoreModelRelationship> = emptyList(),
    val projectionAccepts: ProjectionAcceptRule? = null,
    val aggregations: List<AggregationColumnDefinition> = emptyList(),
    val semanticDefinition: String? = null,
    val semanticTags: List<String> = emptyList(),
) {

    /** Converts this core model definition to the pipeline's ResolvedEntityType format. */
    fun toResolvedEntityType(): ResolvedEntityType {
        val schema = attributes.mapValues { (_, attr) ->
            buildAttributeMap(attr)
        }

        return ResolvedEntityType(
            key = key,
            displayNameSingular = displayNameSingular,
            displayNamePlural = displayNamePlural,
            iconType = iconType.name,
            iconColour = iconColour.name,
            semanticGroup = semanticGroup.name,
            lifecycleDomain = lifecycleDomain.name,
            identifierKey = identifierKey,
            readonly = false,
            schema = schema,
            columns = null,
            semantics = semanticDefinition?.let { def ->
                ResolvedSemantics(definition = def, tags = semanticTags)
            },
        )
    }

    /** Converts this model's relationships to NormalizedRelationship format. */
    fun toNormalizedRelationships(): List<NormalizedRelationship> {
        return relationships.map { rel ->
            NormalizedRelationship(
                key = rel.key,
                sourceEntityTypeKey = rel.sourceModelKey,
                name = rel.name,
                cardinalityDefault = rel.cardinality,
                `protected` = true,
                targetRules = listOf(
                    NormalizedTargetRule(
                        targetEntityTypeKey = rel.targetModelKey,
                        cardinalityOverride = rel.cardinality,
                        inverseName = rel.inverseName,
                    )
                ),
                semantics = rel.semantics?.let { s ->
                    ResolvedRelationshipSemantics(definition = s.definition, tags = s.tags)
                },
            )
        }
    }

    private fun buildAttributeMap(attr: CoreModelAttribute): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "key" to attr.schemaType,
            "type" to attr.dataType,
        )
        map["label"] = attr.label
        if (attr.required) map["required"] = true
        if (attr.unique) map["unique"] = true
        attr.format?.let { map["format"] = it }
        attr.options?.let { opts ->
            val optsMap = mutableMapOf<String, Any>()
            opts.default?.let { optsMap["default"] = it }
            opts.prefix?.let { optsMap["prefix"] = it }
            opts.regex?.let { optsMap["regex"] = it }
            opts.enum?.let { optsMap["enum"] = it }
            opts.minLength?.let { optsMap["minLength"] = it }
            opts.maxLength?.let { optsMap["maxLength"] = it }
            opts.minimum?.let { optsMap["minimum"] = it }
            opts.maximum?.let { optsMap["maximum"] = it }
            if (optsMap.isNotEmpty()) map["options"] = optsMap
        }
        attr.semantics?.let { sem ->
            val semMap = mutableMapOf<String, Any>("definition" to sem.definition)
            sem.classification?.let { semMap["classification"] = it.name }
            if (sem.tags.isNotEmpty()) semMap["tags"] = sem.tags
            map["semantics"] = semMap
        }
        return map
    }
}
