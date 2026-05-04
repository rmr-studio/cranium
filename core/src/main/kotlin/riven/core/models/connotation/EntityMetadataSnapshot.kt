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
 * - SENTIMENT — text sentiment score + themes (populated by DETERMINISTIC tier in Phase B; placeholder in Phase A).
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
data class EntityMetadataSnapshot(
    @param:JsonProperty("snapshotVersion")
    val snapshotVersion: String = "v1",

    @param:JsonProperty("metadata")
    val metadata: EntityMetadata,

    @param:JsonProperty("embeddedAt")
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
data class EntityMetadata(
    @param:JsonProperty("SENTIMENT")
    val sentiment: SentimentMetadata? = null,

    @param:JsonProperty("RELATIONAL")
    val relational: RelationalMetadata? = null,

    @param:JsonProperty("STRUCTURAL")
    val structural: StructuralMetadata? = null,
)

/**
 * SENTIMENT metadata — text sentiment score, label, themes, and analysis provenance.
 *
 * In Phase A all sentiment fields are null and [status] is `NOT_APPLICABLE`. Phase B's
 * DETERMINISTIC mapper populates this category when an entity type's manifest declares
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
    @param:JsonProperty("sentiment")
    val sentiment: Double? = null,

    @param:JsonProperty("sentimentLabel")
    val sentimentLabel: SentimentLabel? = null,

    @param:JsonProperty("themes")
    val themes: List<String> = emptyList(),

    @param:JsonProperty("analysisVersion")
    val analysisVersion: String? = null,

    @param:JsonProperty("analysisModel")
    val analysisModel: String? = null,

    @param:JsonProperty("analysisTier")
    val analysisTier: AnalysisTier? = null,

    @param:JsonProperty("status")
    val status: ConnotationStatus = ConnotationStatus.NOT_APPLICABLE,

    @param:JsonProperty("stalenessModel")
    val stalenessModel: MetadataStalenessModel = MetadataStalenessModel.ON_SOURCE_TEXT_CHANGE,

    @param:JsonProperty("analyzedAt")
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
    @param:JsonProperty("relationshipSummaries")
    val relationshipSummaries: List<RelationshipSummarySnapshot> = emptyList(),

    @param:JsonProperty("clusterMembers")
    val clusterMembers: List<ClusterMemberSnapshot> = emptyList(),

    @param:JsonProperty("relationalReferenceResolutions")
    val relationalReferenceResolutions: List<RelationalReferenceResolution> = emptyList(),

    @param:JsonProperty("stalenessModel")
    val stalenessModel: MetadataStalenessModel = MetadataStalenessModel.ON_NEIGHBOR_CHANGE,

    @param:JsonProperty("snapshotAt")
    val snapshotAt: ZonedDateTime,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RelationshipSummarySnapshot(
    @param:JsonProperty("definitionId")
    val definitionId: String,

    @param:JsonProperty("definitionName")
    val definitionName: String,

    @param:JsonProperty("count")
    val count: Int,

    @param:JsonProperty("topCategories")
    val topCategories: List<String> = emptyList(),

    @param:JsonProperty("latestActivityAt")
    val latestActivityAt: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ClusterMemberSnapshot(
    @param:JsonProperty("sourceType")
    val sourceType: SourceType,

    @param:JsonProperty("entityTypeName")
    val entityTypeName: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RelationalReferenceResolution(
    @param:JsonProperty("attributeId")
    val attributeId: String,

    @param:JsonProperty("targetEntityId")
    val targetEntityId: String,

    @param:JsonProperty("targetIdentifierValue")
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
    @param:JsonProperty("entityTypeName")
    val entityTypeName: String,

    @param:JsonProperty("semanticGroup")
    val semanticGroup: SemanticGroup,

    @param:JsonProperty("lifecycleDomain")
    val lifecycleDomain: LifecycleDomain,

    @param:JsonProperty("entityTypeDefinition")
    val entityTypeDefinition: String? = null,

    @param:JsonProperty("schemaVersion")
    val schemaVersion: Int,

    @param:JsonProperty("attributeClassifications")
    val attributeClassifications: List<AttributeClassificationSnapshot> = emptyList(),

    @param:JsonProperty("relationshipSemanticDefinitions")
    val relationshipSemanticDefinitions: List<RelationshipSemanticDefinitionSnapshot> = emptyList(),

    @param:JsonProperty("stalenessModel")
    val stalenessModel: MetadataStalenessModel = MetadataStalenessModel.ON_TYPE_METADATA_CHANGE,

    @param:JsonProperty("snapshotAt")
    val snapshotAt: ZonedDateTime,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AttributeClassificationSnapshot(
    @param:JsonProperty("attributeId")
    val attributeId: String,

    @param:JsonProperty("semanticLabel")
    val semanticLabel: String,

    @param:JsonProperty("classification")
    val classification: SemanticAttributeClassification? = null,

    @param:JsonProperty("schemaType")
    val schemaType: SchemaType,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RelationshipSemanticDefinitionSnapshot(
    @param:JsonProperty("definitionName")
    val definitionName: String,

    @param:JsonProperty("definitionText")
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
