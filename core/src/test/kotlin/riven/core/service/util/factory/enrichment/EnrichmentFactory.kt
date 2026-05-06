package riven.core.service.util.factory.enrichment

import riven.core.entity.connotation.EntityConnotationEntity
import riven.core.entity.enrichment.EntityEmbeddingEntity
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.integration.SourceType
import riven.core.models.connotation.EntityMetadata
import riven.core.models.connotation.EntityMetadataSnapshot
import riven.core.models.connotation.SentimentMetadata
import riven.core.models.enrichment.EnrichedTextResult
import riven.core.models.entity.knowledge.AttributeSection
import riven.core.models.entity.knowledge.CatalogBacklinkSection
import riven.core.models.entity.knowledge.ClusterSiblingSection
import riven.core.models.entity.knowledge.EntityKnowledgeView
import riven.core.models.entity.knowledge.EntityMetadataSection
import riven.core.models.entity.knowledge.IdentitySection
import riven.core.models.entity.knowledge.KnowledgeBacklinkSection
import riven.core.models.entity.knowledge.KnowledgeSections
import riven.core.models.entity.knowledge.RelationalReferenceSection
import riven.core.models.entity.knowledge.TypeNarrativeSection
import java.time.ZonedDateTime
import java.util.*

/**
 * Test factories for the enrichment domain.
 *
 * Provides pre-built instances of enrichment entities and models with
 * sensible defaults for unit and integration tests.
 *
 * Plan 02-03: [enrichmentContext] now returns [EntityKnowledgeView] with the same parameter
 * surface as before — tests that previously tested [SemanticTextBuilderService] against the
 * old [riven.core.models.enrichment.EnrichmentContext] shape now feed the same data through
 * the new section types without requiring test-body changes beyond the factory update.
 */
object EnrichmentFactory {

    /**
     * Creates an [EntityKnowledgeView] with the same parameter surface as the former
     * [riven.core.models.enrichment.EnrichmentContext] factory, enabling
     * [riven.core.service.enrichment.SemanticTextBuilderServiceTest] to compile without
     * per-test changes.
     *
     * Parameter mapping:
     * - [attributes] → [KnowledgeSections.attributes]
     * - [relationshipSummaries] (formerly [riven.core.models.enrichment.EnrichmentRelationshipSummary]) →
     *   [KnowledgeSections.catalogBacklinks] (topCategories field was dropped in Phase 2)
     * - [clusterMembers] → [KnowledgeSections.clusterSiblings]
     * - [referencedEntityIdentifiers] → [KnowledgeSections.relationalReferences]
     * - [relationshipDefinitions] → dropped; no equivalent in Phase 2 text output
     * - [sentiment] → [EntityMetadataSection.sentiment]
     */
    fun enrichmentContext(
        queueItemId: UUID = UUID.randomUUID(),
        entityId: UUID = UUID.randomUUID(),
        workspaceId: UUID = UUID.randomUUID(),
        entityTypeId: UUID = UUID.randomUUID(),
        schemaVersion: Int = 1,
        entityTypeName: String = "Customer",
        entityTypeDefinition: String? = "A person or organization that purchases products or services.",
        semanticGroup: SemanticGroup = SemanticGroup.CUSTOMER,
        lifecycleDomain: LifecycleDomain = LifecycleDomain.UNCATEGORIZED,
        attributes: List<AttributeSection> = listOf(
            enrichmentAttributeContext(semanticLabel = "Name", value = "Acme Corp", schemaType = SchemaType.TEXT),
            enrichmentAttributeContext(semanticLabel = "Industry", value = "Technology", schemaType = SchemaType.SELECT),
        ),
        relationshipSummaries: List<CatalogBacklinkSection> = listOf(
            enrichmentRelationshipSummary(relationshipName = "Support Tickets", count = 5),
        ),
        clusterMembers: List<ClusterSiblingSection> = emptyList(),
        referencedEntityIdentifiers: Map<UUID, String> = emptyMap(),
        relationshipDefinitions: List<RelationshipDefinitionContext> = emptyList(),
        sentiment: SentimentMetadata? = null,
    ): EntityKnowledgeView {
        val relationalReferences = referencedEntityIdentifiers.map { (id, display) ->
            RelationalReferenceSection(referencedEntityId = id, displayValue = display)
        }
        return EntityKnowledgeView(
            queueItemId = queueItemId,
            entityId = entityId,
            workspaceId = workspaceId,
            entityTypeId = entityTypeId,
            schemaVersion = schemaVersion,
            sections = KnowledgeSections(
                identity = IdentitySection(
                    entityId = entityId,
                    entityTypeId = entityTypeId,
                    identifierValue = null,
                    displayLabel = entityId.toString(),
                ),
                typeNarrative = TypeNarrativeSection(
                    entityTypeName = entityTypeName,
                    semanticGroup = semanticGroup,
                    lifecycleDomain = lifecycleDomain,
                    metadataDefinition = entityTypeDefinition,
                    glossaryDefinitions = emptyList(),
                ),
                attributes = attributes,
                catalogBacklinks = relationshipSummaries,
                knowledgeBacklinks = emptyList(),
                entityMetadata = EntityMetadataSection(
                    schemaVersion = schemaVersion,
                    composedAt = ZonedDateTime.now(),
                    sentiment = sentiment,
                ),
                clusterSiblings = clusterMembers,
                relationalReferences = relationalReferences,
            ),
        )
    }

    /**
     * Creates an [EntityEmbeddingEntity] with sensible defaults.
     *
     * The [embedding] defaults to a 1536-dimensional zero vector to match the
     * default vectorDimensions in [riven.core.configuration.properties.EnrichmentConfigurationProperties].
     */
    fun entityEmbeddingEntity(
        id: UUID = UUID.randomUUID(),
        workspaceId: UUID = UUID.randomUUID(),
        entityId: UUID = UUID.randomUUID(),
        entityTypeId: UUID = UUID.randomUUID(),
        embedding: FloatArray = FloatArray(1536) { 0.0f },
        embeddedAt: ZonedDateTime = ZonedDateTime.now(),
        embeddingModel: String = "text-embedding-3-small",
        schemaVersion: Int = 1,
        truncated: Boolean = false,
    ): EntityEmbeddingEntity = EntityEmbeddingEntity(
        id = id,
        workspaceId = workspaceId,
        entityId = entityId,
        entityTypeId = entityTypeId,
        embedding = embedding,
        embeddedAt = embeddedAt,
        embeddingModel = embeddingModel,
        schemaVersion = schemaVersion,
        truncated = truncated,
    )

    /**
     * Creates an [EntityConnotationEntity] wrapping a SENTIMENT-only [riven.core.models.connotation.EntityMetadataSnapshot].
     * Wraps the snapshot construction so callers don't repeat the boilerplate when only the
     * sentiment-version + status differ between cases.
     */
    fun entityConnotationEntity(
        entityId: UUID,
        workspaceId: UUID,
        metadata: EntityMetadataSnapshot,
    ): EntityConnotationEntity = EntityConnotationEntity(
        entityId = entityId,
        workspaceId = workspaceId,
        connotationMetadata = metadata,
    )

    fun entityConnotationEntity(
        entityId: UUID,
        workspaceId: UUID,
        metadata: EntityMetadata,
        embeddedAt: ZonedDateTime = ZonedDateTime.now(),
    ): EntityConnotationEntity = EntityConnotationEntity(
        entityId = entityId,
        workspaceId = workspaceId,
        connotationMetadata = EntityMetadataSnapshot(
            metadata = metadata,
            embeddedAt = embeddedAt,
        ),
    )

    /**
     * Creates an [AttributeSection] (formerly [riven.core.models.enrichment.EnrichmentAttributeContext])
     * with sensible defaults.
     */
    fun enrichmentAttributeContext(
        attributeId: UUID = UUID.randomUUID(),
        semanticLabel: String = "Name",
        value: String? = "Test Value",
        schemaType: SchemaType = SchemaType.TEXT,
        classification: SemanticAttributeClassification? = null,
    ): AttributeSection = AttributeSection(
        attributeId = attributeId,
        semanticLabel = semanticLabel,
        value = value,
        schemaType = schemaType,
        classification = classification,
        glossaryNarrative = null,
    )

    /**
     * Creates a [CatalogBacklinkSection] (formerly [riven.core.models.enrichment.EnrichmentRelationshipSummary])
     * with sensible defaults.
     *
     * Note: [topCategories] was present on the old model but is absent from [CatalogBacklinkSection].
     * Callers that pass [topCategories] for legacy compatibility — that parameter is silently dropped;
     * the Phase 2 section model does not carry per-category breakdown.
     */
    fun enrichmentRelationshipSummary(
        definitionId: UUID = UUID.randomUUID(),
        relationshipName: String = "Related Entities",
        count: Int = 3,
        @Suppress("UNUSED_PARAMETER") topCategories: List<String> = emptyList(),
        latestActivityAt: String? = null,
    ): CatalogBacklinkSection = CatalogBacklinkSection(
        definitionId = definitionId,
        relationshipName = relationshipName,
        count = count,
        latestActivityAt = latestActivityAt,
        sampleLabels = emptyList(),
    )

    /**
     * Creates an [EnrichedTextResult] with sensible defaults.
     */
    fun enrichedTextResult(
        text: String = "## Entity Type: Customer\n\nType: Customer",
        truncated: Boolean = false,
        estimatedTokens: Int = text.length / 4,
    ): EnrichedTextResult = EnrichedTextResult(
        text = text,
        truncated = truncated,
        estimatedTokens = estimatedTokens,
    )

    /**
     * Creates a [ClusterSiblingSection] (formerly [riven.core.models.enrichment.EnrichmentClusterMemberContext])
     * with sensible defaults.
     *
     * [sourceType] is a [SourceType] enum for call-site convenience; it is converted to its [name]
     * string for the [ClusterSiblingSection.sourceType] field.
     */
    fun enrichmentClusterMemberContext(
        sourceType: SourceType = SourceType.INTEGRATION,
        entityTypeName: String = "Company",
    ): ClusterSiblingSection = ClusterSiblingSection(
        sourceType = sourceType.name,
        entityTypeName = entityTypeName,
    )

    /**
     * Placeholder holder for the legacy relationship-definition concept; no longer surfaced in
     * Phase 2 text. Kept so [enrichmentContext] callers that pass [relationshipDefinitions] lists
     * still compile — the data is accepted and ignored.
     */
    data class RelationshipDefinitionContext(
        val name: String = "Support Tickets",
        val definition: String? = "Escalation records from the help desk system.",
    )

    /**
     * Creates a [RelationshipDefinitionContext] placeholder with sensible defaults.
     *
     * The returned object is accepted by [enrichmentContext] but not surfaced in Phase 2 text.
     * Relationship definitions are now carried via [KnowledgeSections.typeNarrative.glossaryDefinitions].
     */
    fun enrichmentRelationshipDefinitionContext(
        name: String = "Support Tickets",
        definition: String? = "Escalation records from the help desk system.",
    ): RelationshipDefinitionContext = RelationshipDefinitionContext(
        name = name,
        definition = definition,
    )
}
