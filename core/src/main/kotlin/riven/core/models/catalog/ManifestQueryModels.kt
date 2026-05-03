package riven.core.models.catalog

import riven.core.enums.catalog.ManifestType
import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.entity.semantics.SemanticMetadataTargetType
import java.util.*

/**
 * Read-only domain models for manifest catalog query responses.
 *
 * These are separate from pipeline models (ManifestPipelineModels.kt) because
 * pipeline models carry persistence concerns like stale flags and mutable state
 * that don't belong in query responses.
 */

/** Lightweight summary for list endpoints (getAvailableTemplates). */
data class ManifestSummary(
    val id: UUID,
    val key: String,
    val name: String,
    val description: String?,
    val manifestVersion: String?,
    val entityTypeCount: Int
)

/** Fully hydrated manifest for detail endpoint (getManifestByKey). */
data class ManifestDetail(
    val id: UUID,
    val key: String,
    val name: String,
    val description: String?,
    val manifestType: ManifestType,
    val manifestVersion: String?,
    val entityTypes: List<CatalogEntityTypeModel>,
    val relationships: List<CatalogRelationshipModel>,
    val fieldMappings: List<CatalogFieldMappingModel>
)

/** Entity type with schema, columns, and semantic metadata. */
data class CatalogEntityTypeModel(
    val id: UUID,
    val manifestId: UUID,
    val key: String,
    val displayNameSingular: String,
    val displayNamePlural: String,
    val iconType: IconType,
    val iconColour: IconColour,
    val semanticGroup: SemanticGroup,
    val lifecycleDomain: LifecycleDomain = LifecycleDomain.UNCATEGORIZED,
    val identifierKey: String?,
    val readonly: Boolean,
    val schema: Map<String, Any>,
    val columns: List<Map<String, Any>>?,
    val connotationSignals: ConnotationSignals? = null,
    val schemaHash: String?,
    val semanticMetadata: List<CatalogSemanticMetadataModel>
)

/** Relationship definition with target rules. */
data class CatalogRelationshipModel(
    val id: UUID,
    val key: String,
    val sourceEntityTypeKey: String,
    val name: String,
    val iconType: IconType,
    val iconColour: IconColour,
    val cardinalityDefault: EntityRelationshipCardinality,
    val `protected`: Boolean,
    val targetRules: List<CatalogRelationshipTargetRuleModel>
)

/** Target rule for a relationship. */
data class CatalogRelationshipTargetRuleModel(
    val id: UUID,
    val targetEntityTypeKey: String,
    val cardinalityOverride: EntityRelationshipCardinality?,
    val inverseVisible: Boolean,
    val inverseName: String?
)

/** Semantic metadata entry for an entity type, attribute, or relationship. */
data class CatalogSemanticMetadataModel(
    val id: UUID,
    val targetType: SemanticMetadataTargetType,
    val targetId: String,
    val definition: String?,
    val classification: SemanticAttributeClassification?,
    val tags: List<String>
)

/** Field mapping for an entity type within a manifest. */
data class CatalogFieldMappingModel(
    val id: UUID,
    val entityTypeKey: String,
    val mappings: Map<String, Any>
)

