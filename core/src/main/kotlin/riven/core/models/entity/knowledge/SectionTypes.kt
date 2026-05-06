package riven.core.models.entity.knowledge

import riven.core.enums.common.validation.SchemaType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.models.connotation.SentimentMetadata
import java.time.ZonedDateTime
import java.util.*

// All 8 section data classes co-located in one file, ordered to match KnowledgeSections property order.
// VIEW-11: KnowledgeSectionsContractTest asserts the exact 8-name set via reflection.

/**
 * Identity information for the entity being embedded.
 *
 * @property entityId The entity UUID.
 * @property entityTypeId The entity type UUID.
 * @property identifierValue Value of the entity type's identifier attribute, if set.
 * @property displayLabel identifierValue if non-null, otherwise entityId.toString().
 */
data class IdentitySection(
    val entityId: UUID,
    val entityTypeId: UUID,
    val identifierValue: String?,
    val displayLabel: String,
)

/**
 * Entity-type narrative context carrying semantic classification and glossary definitions.
 *
 * @property entityTypeName Display name singular of the entity type (e.g. "Customer").
 * @property semanticGroup Semantic group classification.
 * @property lifecycleDomain Lifecycle domain classification.
 * @property metadataDefinition STRUCTURAL metadata.definition — preserved alongside glossary so
 *   PROJ-12 parity IT can prove glossary-primary read path; Phase 3 may remove the fallback.
 * @property glossaryDefinitions Glossary narratives joined from DEFINES edges where
 *   target_kind = ENTITY_TYPE or RELATIONSHIP.
 */
data class TypeNarrativeSection(
    val entityTypeName: String,
    val semanticGroup: SemanticGroup,
    val lifecycleDomain: LifecycleDomain,
    val metadataDefinition: String?,
    val glossaryDefinitions: List<GlossaryNarrative>,
)

/**
 * A single attribute value snapshot for the entity.
 *
 * @property attributeId UUID of the attribute definition.
 * @property semanticLabel Current fallback to metadata.definition; Phase 3 PROJ-12 deletes fallback.
 * @property value Attribute value as a string, or null if not set.
 * @property schemaType Schema type of the attribute — used for type-aware formatting.
 * @property classification Semantic classification from EntityTypeSemanticMetadata, or null if unannotated.
 * @property glossaryNarrative Joined from DEFINES edges where target_kind = ATTRIBUTE and target_id matches.
 */
data class AttributeSection(
    val attributeId: UUID,
    val semanticLabel: String,
    val value: String?,
    val schemaType: SchemaType,
    val classification: SemanticAttributeClassification?,
    val glossaryNarrative: String?,
)

/**
 * Catalog relationship backlink count for a single relationship definition.
 *
 * Preserves the semantics of [riven.core.models.enrichment.EnrichmentRelationshipSummary] within the
 * new view model shape.
 *
 * @property definitionId The relationship definition UUID.
 * @property relationshipName Human-readable relationship name.
 * @property count Total number of related entities of this type.
 * @property latestActivityAt ISO_OFFSET_DATE_TIME of the most recent edge, or null.
 * @property sampleLabels Identifier values of linked entities, capped at [riven.core.configuration.properties.EnrichmentConfigurationProperties.knowledgeBacklinkCap].
 */
data class CatalogBacklinkSection(
    val definitionId: UUID,
    val relationshipName: String,
    val count: Int,
    val latestActivityAt: String?,
    val sampleLabels: List<String>,
)

/**
 * A single KNOWLEDGE-type backlink pointing at the entity being embedded.
 *
 * Drives excerpt extraction in Plan 02-02. Phase 3 trimKnowledgeByRecency ranks by [createdAt].
 *
 * @property sourceEntityId UUID of the source KNOWLEDGE entity (note or glossary).
 * @property sourceTypeKey Entity type key of the source — distinguishes note vs glossary.
 * @property sourceLabel Identifier value of the source entity.
 * @property excerpt Relevant text excerpt extracted from the source entity.
 * @property createdAt Relationship edge createdAt — drives Phase 3 recency trimming.
 */
data class KnowledgeBacklinkSection(
    val sourceEntityId: UUID,
    val sourceTypeKey: String,
    val sourceLabel: String,
    val excerpt: String,
    val createdAt: ZonedDateTime,
)

/**
 * Metadata about the embedded entity snapshot itself.
 *
 * Note: [sentiment] is carried here as a field, NOT as a separate 9th section. This is a
 * deliberate architecture decision (see CONTEXT.md) — sentiment is metadata about the entity,
 * not a structural section parallel to identity or attributes.
 *
 * @property schemaVersion Entity type schema version at snapshot time.
 * @property composedAt Timestamp when the [EntityKnowledgeView] was composed.
 * @property sentiment Sentiment metadata if available; null if not opted in or not analyzed.
 */
data class EntityMetadataSection(
    val schemaVersion: Int,
    val composedAt: ZonedDateTime,
    val sentiment: SentimentMetadata?,
)

/**
 * A sibling entity type that shares the same cluster as the entity being embedded.
 *
 * Mirrors [riven.core.models.enrichment.EnrichmentClusterMemberContext] in the new view model shape.
 *
 * @property sourceType The cluster source type key.
 * @property entityTypeName Display name singular of the sibling entity type.
 */
data class ClusterSiblingSection(
    val sourceType: String,
    val entityTypeName: String,
)

/**
 * A resolved relational reference for a RELATIONAL_REFERENCE attribute.
 *
 * @property referencedEntityId UUID of the referenced entity.
 * @property displayValue Identifier-attribute value of the referenced entity; fallback
 *   "[reference not resolved]" when the entity cannot be found.
 */
data class RelationalReferenceSection(
    val referencedEntityId: UUID,
    val displayValue: String,
)
