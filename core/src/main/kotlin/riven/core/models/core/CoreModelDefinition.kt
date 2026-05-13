package cranium.core.models.core

import cranium.core.enums.common.icon.IconColour
import cranium.core.enums.common.icon.IconType
import cranium.core.enums.entity.EntityTypeRole
import cranium.core.enums.entity.LifecycleDomain
import cranium.core.enums.entity.semantics.SemanticGroup
import cranium.core.models.catalog.NormalizedRelationship
import cranium.core.models.catalog.ResolvedEntityType
import cranium.core.models.catalog.ResolvedSemantics
import cranium.core.models.common.validation.DefaultValue

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
    val role: EntityTypeRole = EntityTypeRole.CATALOG,
    val displayNameSingular: String,
    val displayNamePlural: String,
    val iconType: IconType = IconType.CIRCLE_DASHED,
    val iconColour: IconColour = IconColour.NEUTRAL,
    val semanticGroup: SemanticGroup = SemanticGroup.UNCATEGORIZED,
    val lifecycleDomain: LifecycleDomain = LifecycleDomain.UNCATEGORIZED,
    val identifierKey: String,
    val attributes: Map<String, cranium.core.models.core.CoreModelAttribute>,
    val relationships: List<cranium.core.models.core.CoreModelRelationship> = emptyList(),
    val projectionAccepts: List<cranium.core.models.core.ProjectionAcceptRule> = emptyList(),
    val aggregations: List<cranium.core.models.core.AggregationColumnDefinition> = emptyList(),
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
            lifecycleDomain = lifecycleDomain,
            role = role,
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
        return relationships.map { it.toNormalized() }
    }

    private fun buildAttributeMap(attr: cranium.core.models.core.CoreModelAttribute): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "key" to attr.schemaType.name,
            "type" to attr.dataType.jsonValue,
        )
        map["label"] = attr.label
        if (attr.required) map["required"] = true
        if (attr.unique) map["unique"] = true
        attr.format?.let { map["format"] = it }
        attr.options?.let { opts ->
            val optsMap = mutableMapOf<String, Any>()
            opts.defaultValue?.let { dv ->
                optsMap["defaultValue"] = when (dv) {
                    is DefaultValue.Static -> mapOf("type" to "static", "value" to dv.value)
                    is DefaultValue.Dynamic -> mapOf("type" to "dynamic", "function" to dv.function.name)
                }
            }
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
