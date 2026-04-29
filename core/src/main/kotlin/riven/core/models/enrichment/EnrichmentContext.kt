package riven.core.models.enrichment

import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.common.validation.SchemaType
import java.util.*

/**
 * Transient merge view assembled by
 * [riven.core.service.enrichment.EnrichmentService.analyzeSemantics] and consumed by
 * [riven.core.service.enrichment.SemanticTextBuilderService] and downstream activities.
 *
 * Combines the persisted polymorphic envelope axes (RELATIONAL summaries / cluster /
 * STRUCTURAL type metadata, also written to `entity_connotation`) with live entity payload
 * values (FREETEXT, CATEGORICAL, TEMPORAL attribute values) read fresh from `entities` each
 * enrichment cycle. Conceptually:
 * - Section 1 (Type) / Section 2 (Identity) / Section 6 (Relationship Definitions) → STRUCTURAL axis.
 * - Section 4 (Relationship Summaries) / Section 5 (Cluster) → RELATIONAL axis.
 * - Section 3 (Attributes) — semantic labels + classifications come from the STRUCTURAL axis;
 *   attribute *values* are live from `entities.payload`, NOT from the envelope.
 *
 * All fields use Temporal-serializable types (no Any?, no JsonNode, no raw object types).
 * Attribute values are pre-converted to String? before context creation.
 *
 * This is a purpose-built data class, not a reuse of the full Entity domain model.
 */
data class EnrichmentContext(
    /** The enrichment queue item that triggered this pipeline run. */
    val queueItemId: UUID,
    /** The entity being embedded. */
    val entityId: UUID,
    /** The workspace the entity belongs to. */
    val workspaceId: UUID,
    /** The entity type ID — used to determine schema version at embedding time. */
    val entityTypeId: UUID,
    /** Schema version at fetch time — stored on the embedding record for staleness detection. */
    val schemaVersion: Int,
    /** Human-readable entity type name (e.g. "Customer", "Support Ticket"). */
    val entityTypeName: String,
    /** Optional structured type definition string for richer text context. */
    val entityTypeDefinition: String?,
    /** Semantic group classification (e.g. CUSTOMER, PRODUCT, SUPPORT). */
    val semanticGroup: SemanticGroup,
    /** Lifecycle domain classification (e.g. ACQUISITION, BILLING, UNCATEGORIZED). */
    val lifecycleDomain: LifecycleDomain,
    /** Attribute values for this entity, with semantic labels from EntityTypeSemanticMetadata. */
    val attributes: List<EnrichmentAttributeContext>,
    /** Relationship counts grouped by relationship type. */
    val relationshipSummaries: List<EnrichmentRelationshipSummary>,
    /** Entity types that share the same cluster as this entity's type. Empty when entity has no cluster. */
    val clusterMembers: List<EnrichmentClusterMemberContext> = emptyList(),
    /** Maps referenced entity UUID to a display string for RELATIONAL_REFERENCE attribute resolution. */
    val referencedEntityIdentifiers: Map<UUID, String> = emptyMap(),
    /** Semantic definitions for each relationship type — used in Section 6 of enriched text. */
    val relationshipDefinitions: List<EnrichmentRelationshipDefinitionContext> = emptyList(),
    /**
     * Snapshot of the SENTIMENT axis written for this enrichment cycle. Null when
     * the workspace has not opted in or the entity type has no manifest connotation
     * signals (envelope axis remains at NOT_APPLICABLE in that case). Forwarded only
     * when the analyzed status is ANALYZED — FAILED/NOT_APPLICABLE axes are not
     * surfaced to the text builder.
     */
    val sentiment: riven.core.models.connotation.SentimentAxis? = null,
)

/**
 * A single attribute value snapshot for inclusion in the enriched text.
 *
 * The [value] field is always String? to ensure Temporal Jackson serialization safety —
 * complex types (dates, numbers, selects) are toString'd at context-building time.
 */
data class EnrichmentAttributeContext(
    /** The attribute schema key (UUID of the attribute definition). */
    val attributeId: UUID,
    /** Human-readable label from EntityTypeSemanticMetadata (e.g. "Acquisition Channel"). */
    val semanticLabel: String,
    /** Attribute value as a string, or null if not set. */
    val value: String?,
    /** Schema type of this attribute — used for type-aware formatting in Phase 3. */
    val schemaType: SchemaType,
    /** Semantic classification from EntityTypeSemanticMetadata, or null if not annotated. */
    val classification: SemanticAttributeClassification? = null,
)

/**
 * Count-based summary of relationships of a single type.
 *
 * Phase 2 uses count-only summaries. Phase 3 may add categorical breakdowns,
 * temporal recency, and quantitative aggregates.
 */
data class EnrichmentRelationshipSummary(
    /** The relationship definition ID. */
    val definitionId: UUID,
    /** Human-readable relationship name (e.g. "Support Tickets"). */
    val relationshipName: String,
    /** Total number of related entities of this type. */
    val count: Int,
    /** Top categorical values for CATEGORICAL attributes in related entities (Phase 3). */
    val topCategories: List<String> = emptyList(),
    /** ISO-8601 timestamp of the most recent activity on related entities, or null if unavailable. */
    val latestActivityAt: String? = null,
)
