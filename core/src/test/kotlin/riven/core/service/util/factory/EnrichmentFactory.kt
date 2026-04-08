package riven.core.service.util.factory

import riven.core.enums.common.validation.SchemaType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.models.enrichment.EnrichmentAttributeContext
import riven.core.models.enrichment.EnrichmentContext
import riven.core.models.enrichment.EnrichmentRelationshipSummary
import java.util.UUID

object EnrichmentFactory {

    fun createEnrichmentContext(
        queueItemId: UUID = UUID.randomUUID(),
        entityId: UUID = UUID.randomUUID(),
        workspaceId: UUID = UUID.randomUUID(),
        entityTypeId: UUID = UUID.randomUUID(),
        schemaVersion: Int = 1,
        entityTypeName: String = "Customer",
        entityTypeDefinition: String? = "A business customer entity",
        semanticGroup: SemanticGroup = SemanticGroup.CUSTOMER,
        lifecycleDomain: LifecycleDomain = LifecycleDomain.ACQUISITION,
        attributes: List<EnrichmentAttributeContext> = emptyList(),
        relationshipSummaries: List<EnrichmentRelationshipSummary> = emptyList(),
    ) = EnrichmentContext(
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
    )

    fun createEnrichmentAttributeContext(
        attributeId: UUID = UUID.randomUUID(),
        semanticLabel: String = "Company Name",
        value: String? = "Acme Corp",
        schemaType: SchemaType = SchemaType.TEXT,
    ) = EnrichmentAttributeContext(
        attributeId = attributeId,
        semanticLabel = semanticLabel,
        value = value,
        schemaType = schemaType,
    )

    fun createEnrichmentRelationshipSummary(
        definitionId: UUID = UUID.randomUUID(),
        relationshipName: String = "Support Tickets",
        count: Int = 3,
    ) = EnrichmentRelationshipSummary(
        definitionId = definitionId,
        relationshipName = relationshipName,
        count = count,
    )
}
