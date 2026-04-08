package riven.core.service.util.factory.enrichment

import riven.core.entity.enrichment.EntityEmbeddingEntity
import riven.core.entity.enrichment.EnrichmentQueueEntity
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.enrichment.EnrichmentQueuePriority
import riven.core.enums.enrichment.EnrichmentQueueStatus
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.integration.SourceType
import riven.core.models.enrichment.EnrichedTextResult
import riven.core.models.enrichment.EnrichmentAttributeContext
import riven.core.models.enrichment.EnrichmentClusterMemberContext
import riven.core.models.enrichment.EnrichmentContext
import riven.core.models.enrichment.EnrichmentRelationshipDefinitionContext
import riven.core.models.enrichment.EnrichmentRelationshipSummary
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
     * Creates an [EnrichmentQueueEntity] with sensible defaults.
     */
    fun enrichmentQueueEntity(
        id: UUID = UUID.randomUUID(),
        workspaceId: UUID = UUID.randomUUID(),
        entityId: UUID = UUID.randomUUID(),
        priority: EnrichmentQueuePriority = EnrichmentQueuePriority.NORMAL,
        status: EnrichmentQueueStatus = EnrichmentQueueStatus.PENDING,
        createdAt: ZonedDateTime = ZonedDateTime.now(),
        claimedAt: ZonedDateTime? = null,
        dispatchedAt: ZonedDateTime? = null,
        attempts: Int = 0,
        lastError: String? = null
    ): EnrichmentQueueEntity = EnrichmentQueueEntity(
        id = id,
        workspaceId = workspaceId,
        entityId = entityId,
        priority = priority,
        status = status,
        createdAt = createdAt,
        claimedAt = claimedAt,
        dispatchedAt = dispatchedAt,
        attempts = attempts,
        lastError = lastError
    )

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
