package riven.core.models.catalog

import com.fasterxml.jackson.databind.JsonNode
import riven.core.enums.catalog.ManifestType
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.entity.LifecycleDomain

/** Output of ManifestScannerService -- parsed and schema-validated JSON. */
data class ScannedManifest(
    val key: String,
    val type: ManifestType,
    val json: JsonNode
)

/** Output of ManifestResolverService -- fully resolved, ready for persistence. */
data class ResolvedManifest(
    val key: String,
    val name: String,
    val description: String?,
    val type: ManifestType,
    val manifestVersion: String?,
    val entityTypes: List<ResolvedEntityType>,
    val relationships: List<NormalizedRelationship>,
    val fieldMappings: List<ResolvedFieldMapping>,
    val syncModels: Map<String, String> = emptyMap(),
    val stale: Boolean = false
)

data class ResolvedEntityType(
    val key: String,
    val displayNameSingular: String,
    val displayNamePlural: String,
    val iconType: String,
    val iconColour: String,
    val semanticGroup: String,
    val lifecycleDomain: LifecycleDomain? = null,
    val identifierKey: String?,
    val readonly: Boolean,
    val schema: Map<String, Any>,
    val columns: List<Map<String, Any>>?,
    val semantics: ResolvedSemantics?
)

data class ResolvedSemantics(
    val definition: String?,
    val tags: List<String>
)

data class NormalizedRelationship(
    val key: String,
    val sourceEntityTypeKey: String,
    val name: String,
    val iconType: String = "LINK",
    val iconColour: String = "NEUTRAL",
    val cardinalityDefault: EntityRelationshipCardinality,
    val `protected`: Boolean,
    val targetRules: List<NormalizedTargetRule>,
    val semantics: ResolvedRelationshipSemantics? = null
)

data class NormalizedTargetRule(
    val targetEntityTypeKey: String,
    val cardinalityOverride: EntityRelationshipCardinality? = null,
    val inverseVisible: Boolean? = null,
    val inverseName: String? = null
)

data class ResolvedRelationshipSemantics(
    val definition: String?,
    val tags: List<String>
)

data class ResolvedFieldMapping(
    val entityTypeKey: String,
    val mappings: Map<String, Any>
)

