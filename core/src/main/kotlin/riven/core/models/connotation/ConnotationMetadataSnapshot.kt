package riven.core.models.connotation

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.connotation.ConnotationStatus
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.integration.SourceType
import java.time.ZonedDateTime

/**
 * Polymorphic semantic snapshot persisted in `entity_connotation.connotation_metadata`.
 *
 * Captures three orthogonal categories computed at last enrichment time:
 * - SENTIMENT — text sentiment score + themes (populated by Tier 1 in Phase B; placeholder in Phase A).
 * - RELATIONAL — relationship summaries, cluster membership, RELATIONAL_REFERENCE resolutions.
 * - STRUCTURAL — entity type metadata, attribute classifications, relationship semantic definitions.
 *
 * The snapshot is "as of last enrichment", not a live view. Consumers needing live state must query
 * the underlying tables. Last-write-wins on concurrent writes (queue dedup minimises this in
 * practice).
 *
 * @property snapshotVersion Forward-compat insurance. v0 (missing) means "treat as v1".
 * @property metadata Container of the three metadata categories. SENTIMENT, RELATIONAL, STRUCTURAL (Phase A).
 * @property embeddedAt Timestamp the snapshot was assembled and persisted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ConnotationMetadataSnapshot(
    @JsonProperty("snapshotVersion")
    val snapshotVersion: String = "v1",

    @JsonProperty("metadata")
    val metadata: ConnotationMetadata,

    @JsonProperty("embeddedAt")
    val embeddedAt: ZonedDateTime,
)

/**
 * Container for the three metadata categories of a connotation snapshot.
 *
 * Each category is independently populated; a missing category means "not computed yet".
 * SENTIMENT is the only category whose population is gated on a workspace flag and tier
 * configuration; RELATIONAL and STRUCTURAL are deterministic and always populated post-Phase-A.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ConnotationMetadata(
    @JsonProperty("SENTIMENT")
    val sentiment: SentimentMetadata? = null,

    @JsonProperty("RELATIONAL")
    val relational: RelationalMetadata? = null,

    @JsonProperty("STRUCTURAL")
    val structural: StructuralMetadata? = null,
)

/**
 * SENTIMENT metadata — text sentiment score, label, themes, and analysis provenance.
 *
 * In Phase A all sentiment fields are null and [status] is `NOT_APPLICABLE`. Phase B's
 * Tier 1 mapper populates this category when an entity type's manifest declares
 * connotationSignals.
 *
 * @property sentiment Normalized sentiment score in `[-1.0, 1.0]`. Null pre-Phase-B.
 * @property sentimentLabel Coarse-grained label derived from [sentiment]. Null pre-Phase-B.
 * @property themes Free-form thematic keywords extracted from the source text or attributes.
 * @property analysisVersion Version identifier of the active sentiment analysis configuration.
 * @property analysisModel Model identifier (e.g. manifest mapper name, Ollama model, LLM model).
 * @property analysisTier Tier classification. Null pre-Phase-B.
 * @property status Lifecycle status of this category. Drives Phase B retry semantics.
 * @property stalenessModel Documented invalidation trigger class. Metadata only — not enforced in Phase A.
 * @property analyzedAt Timestamp the sentiment fields were computed. Null pre-Phase-B.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SentimentMetadata(
    @JsonProperty("sentiment")
    val sentiment: Double? = null,

    @JsonProperty("sentimentLabel")
    val sentimentLabel: SentimentLabel? = null,

    @JsonProperty("themes")
    val themes: List<String> = emptyList(),

    @JsonProperty("analysisVersion")
    val analysisVersion: String? = null,

    @JsonProperty("analysisModel")
    val analysisModel: String? = null,

    @JsonProperty("analysisTier")
    val analysisTier: AnalysisTier? = null,

    @JsonProperty("status")
    val status: ConnotationStatus = ConnotationStatus.NOT_APPLICABLE,

    @JsonProperty("stalenessModel")
    val stalenessModel: MetadataStalenessModel = MetadataStalenessModel.ON_SOURCE_TEXT_CHANGE,

    @JsonProperty("analyzedAt")
    val analyzedAt: ZonedDateTime? = null,
)

/**
 * RELATIONAL metadata — neighbor summary information already computed during enrichment context fetch.
 *
 * Persisted as a snapshot so non-enrichment consumers (frontend display, debug tooling, future
 * Layer 4 categories) can read the relational shape without re-querying. Note: the underlying data
 * is also queryable live from `entity_relationships` — this category is the snapshot-as-of-embed view.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class RelationalMetadata(
    @JsonProperty("relationshipSummaries")
    val relationshipSummaries: List<RelationshipSummarySnapshot> = emptyList(),

    @JsonProperty("clusterMembers")
    val clusterMembers: List<ClusterMemberSnapshot> = emptyList(),

    @JsonProperty("relationalReferenceResolutions")
    val relationalReferenceResolutions: List<RelationalReferenceResolution> = emptyList(),

    @JsonProperty("stalenessModel")
    val stalenessModel: MetadataStalenessModel = MetadataStalenessModel.ON_NEIGHBOR_CHANGE,

    @JsonProperty("snapshotAt")
    val snapshotAt: ZonedDateTime,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RelationshipSummarySnapshot(
    @JsonProperty("definitionId")
    val definitionId: String,

    @JsonProperty("definitionName")
    val definitionName: String,

    @JsonProperty("count")
    val count: Int,

    @JsonProperty("topCategories")
    val topCategories: List<String> = emptyList(),

    @JsonProperty("latestActivityAt")
    val latestActivityAt: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ClusterMemberSnapshot(
    @JsonProperty("sourceType")
    val sourceType: SourceType,

    @JsonProperty("entityTypeName")
    val entityTypeName: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RelationalReferenceResolution(
    @JsonProperty("attributeId")
    val attributeId: String,

    @JsonProperty("targetEntityId")
    val targetEntityId: String,

    @JsonProperty("targetIdentifierValue")
    val targetIdentifierValue: String,
)

/**
 * STRUCTURAL metadata — entity type, lifecycle, and semantic definition snapshot at embed time.
 *
 * Mirrors `EntityTypeSemanticMetadataEntity` / `RelationshipDefinitionEntity` /
 * `EntityTypeEntity` columns, but captures the per-entity view of which definitions
 * applied at embed time. Stored so a manifest-driven re-embed can detect drift via
 * the schema reconciliation hook.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class StructuralMetadata(
    @JsonProperty("entityTypeName")
    val entityTypeName: String,

    @JsonProperty("semanticGroup")
    val semanticGroup: SemanticGroup,

    @JsonProperty("lifecycleDomain")
    val lifecycleDomain: LifecycleDomain,

    @JsonProperty("entityTypeDefinition")
    val entityTypeDefinition: String? = null,

    @JsonProperty("schemaVersion")
    val schemaVersion: Int,

    @JsonProperty("attributeClassifications")
    val attributeClassifications: List<AttributeClassificationSnapshot> = emptyList(),

    @JsonProperty("relationshipSemanticDefinitions")
    val relationshipSemanticDefinitions: List<RelationshipSemanticDefinitionSnapshot> = emptyList(),

    @JsonProperty("stalenessModel")
    val stalenessModel: MetadataStalenessModel = MetadataStalenessModel.ON_TYPE_METADATA_CHANGE,

    @JsonProperty("snapshotAt")
    val snapshotAt: ZonedDateTime,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AttributeClassificationSnapshot(
    @JsonProperty("attributeId")
    val attributeId: String,

    @JsonProperty("semanticLabel")
    val semanticLabel: String,

    @JsonProperty("classification")
    val classification: SemanticAttributeClassification? = null,

    @JsonProperty("schemaType")
    val schemaType: SchemaType,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RelationshipSemanticDefinitionSnapshot(
    @JsonProperty("definitionName")
    val definitionName: String,

    @JsonProperty("definitionText")
    val definitionText: String? = null,
)

/**
 * Trigger class describing when a metadata category's snapshot becomes stale.
 *
 * Documentation only — not enforced by any code path in Phase A. Future invalidation tooling
 * uses these values to decide which snapshots to refresh.
 */
enum class MetadataStalenessModel {
    @JsonProperty("ON_SOURCE_TEXT_CHANGE")
    ON_SOURCE_TEXT_CHANGE,

    @JsonProperty("ON_NEIGHBOR_CHANGE")
    ON_NEIGHBOR_CHANGE,

    @JsonProperty("ON_TYPE_METADATA_CHANGE")
    ON_TYPE_METADATA_CHANGE,

    @JsonProperty("PERIODIC_REBUILD")
    PERIODIC_REBUILD,
}
