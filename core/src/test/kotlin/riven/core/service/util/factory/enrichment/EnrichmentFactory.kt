package cranium.core.service.util.factory.enrichment

import cranium.core.entity.connotation.EntityConnotationEntity
import cranium.core.entity.enrichment.EntityEmbeddingEntity
import cranium.core.enums.common.validation.SchemaType
import cranium.core.enums.entity.LifecycleDomain
import cranium.core.enums.entity.semantics.SemanticAttributeClassification
import cranium.core.enums.entity.semantics.SemanticGroup
import cranium.core.enums.integration.SourceType
import cranium.core.models.connotation.EntityMetadata
import cranium.core.models.connotation.EntityMetadataSnapshot
import cranium.core.models.connotation.SentimentMetadata
import cranium.core.models.enrichment.EnrichedTextResult
import cranium.core.models.enrichment.EnrichmentAttributeContext
import cranium.core.models.enrichment.EnrichmentClusterMemberContext
import cranium.core.models.enrichment.EnrichmentContext
import cranium.core.models.enrichment.EnrichmentRelationshipDefinitionContext
import cranium.core.models.enrichment.EnrichmentRelationshipSummary
import java.time.ZonedDateTime
import java.util.*

/**
 * Test factories for the enrichment domain.
 *
 * Provides pre-built instances of enrichment entities and models with
 * sensible defaults for unit and integration tests.
 */
object EnrichmentFactory {

    /**
     * Creates an [EnrichmentContext] with sensible defaults including sample
     * attributes and relationship summaries.
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
        attributes: List<EnrichmentAttributeContext> = listOf(
            enrichmentAttributeContext(semanticLabel = "Name", value = "Acme Corp", schemaType = SchemaType.TEXT),
            enrichmentAttributeContext(semanticLabel = "Industry", value = "Technology", schemaType = SchemaType.SELECT)
        ),
        relationshipSummaries: List<EnrichmentRelationshipSummary> = listOf(
            enrichmentRelationshipSummary(relationshipName = "Support Tickets", count = 5)
        ),
        clusterMembers: List<EnrichmentClusterMemberContext> = emptyList(),
        referencedEntityIdentifiers: Map<UUID, String> = emptyMap(),
        relationshipDefinitions: List<EnrichmentRelationshipDefinitionContext> = emptyList(),
        sentiment: SentimentMetadata? = null,
    ): EnrichmentContext = EnrichmentContext(
        queueItemId = queueItemId,
        entityId = entityId,
        workspaceId = workspaceId,
        entityTypeId = entityTypeId,
        schemaVersion = schemaVersion,
        entityTypeName = entityTypeName,
        entityTypeDefinition = entityTypeDefinition,
        semanticGroup = semanticGroup,
        lifecycleDomain = lifecycleDomain,
        attributes = attributes,
        relationshipSummaries = relationshipSummaries,
        clusterMembers = clusterMembers,
        referencedEntityIdentifiers = referencedEntityIdentifiers,
        relationshipDefinitions = relationshipDefinitions,
        sentiment = sentiment,
    )

    /**
     * Creates an [EntityEmbeddingEntity] with sensible defaults.
     *
     * The [embedding] defaults to a 1536-dimensional zero vector to match the
     * default vectorDimensions in [EnrichmentConfigurationProperties].
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
        truncated: Boolean = false
    ): EntityEmbeddingEntity = EntityEmbeddingEntity(
        id = id,
        workspaceId = workspaceId,
        entityId = entityId,
        entityTypeId = entityTypeId,
        embedding = embedding,
        embeddedAt = embeddedAt,
        embeddingModel = embeddingModel,
        schemaVersion = schemaVersion,
        truncated = truncated
    )

    /**
     * Creates an [EntityConnotationEntity] wrapping a SENTIMENT-only [cranium.core.models.connotation.EntityMetadataSnapshot].
     * Wraps the snapshot construction so callers don't repeat the boilerplate when only the
     * sentiment-version + status differ between cases.
     */
    fun entityConnotationEntity(
        entityId: UUID,
        workspaceId: UUID,
        metadata: EntityMetadataSnapshot
    ): EntityConnotationEntity = EntityConnotationEntity(
        entityId = entityId,
        workspaceId = workspaceId,
        connotationMetadata = metadata
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
     * Creates an [EnrichmentAttributeContext] with sensible defaults.
     */
    fun enrichmentAttributeContext(
        attributeId: UUID = UUID.randomUUID(),
        semanticLabel: String = "Name",
        value: String? = "Test Value",
        schemaType: SchemaType = SchemaType.TEXT,
        classification: SemanticAttributeClassification? = null,
    ): EnrichmentAttributeContext = EnrichmentAttributeContext(
        attributeId = attributeId,
        semanticLabel = semanticLabel,
        value = value,
        schemaType = schemaType,
        classification = classification,
    )

    /**
     * Creates an [EnrichmentRelationshipSummary] with sensible defaults.
     */
    fun enrichmentRelationshipSummary(
        definitionId: UUID = UUID.randomUUID(),
        relationshipName: String = "Related Entities",
        count: Int = 3,
        topCategories: List<String> = emptyList(),
        latestActivityAt: String? = null,
    ): EnrichmentRelationshipSummary = EnrichmentRelationshipSummary(
        definitionId = definitionId,
        relationshipName = relationshipName,
        count = count,
        topCategories = topCategories,
        latestActivityAt = latestActivityAt,
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
     * Creates an [EnrichmentClusterMemberContext] with sensible defaults.
     */
    fun enrichmentClusterMemberContext(
        sourceType: SourceType = SourceType.INTEGRATION,
        entityTypeName: String = "Company",
    ): EnrichmentClusterMemberContext = EnrichmentClusterMemberContext(
        sourceType = sourceType,
        entityTypeName = entityTypeName,
    )

    /**
     * Creates an [EnrichmentRelationshipDefinitionContext] with sensible defaults.
     */
    fun enrichmentRelationshipDefinitionContext(
        name: String = "Support Tickets",
        definition: String? = "Escalation records from the help desk system.",
    ): EnrichmentRelationshipDefinitionContext = EnrichmentRelationshipDefinitionContext(
        name = name,
        definition = definition,
    )
}
