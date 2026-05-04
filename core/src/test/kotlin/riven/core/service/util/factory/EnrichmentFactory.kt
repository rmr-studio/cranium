package riven.core.service.util.factory

import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.models.entity.knowledge.AttributeSection
import riven.core.models.entity.knowledge.ClusterSiblingSection
import riven.core.models.entity.knowledge.CatalogBacklinkSection
import riven.core.models.entity.knowledge.EntityKnowledgeView
import riven.core.models.entity.knowledge.EntityMetadataSection
import riven.core.models.entity.knowledge.IdentitySection
import riven.core.models.entity.knowledge.KnowledgeBacklinkSection
import riven.core.models.entity.knowledge.KnowledgeSections
import riven.core.models.entity.knowledge.RelationalReferenceSection
import riven.core.models.entity.knowledge.TypeNarrativeSection
import java.time.ZonedDateTime
import java.util.UUID

object EnrichmentFactory {

    /**
     * Creates an [EntityKnowledgeView] with sensible defaults for workflow-level tests.
     *
     * Plan 02-03: replaces the former createEnrichmentContext factory now that [EnrichmentContext]
     * is deleted. All workflow-level tests (EnrichmentWorkflowImplTest, EnrichmentWorkflowIT)
     * use this factory to build deterministic fixture views.
     */
    fun createEntityKnowledgeView(
        queueItemId: UUID = UUID.randomUUID(),
        entityId: UUID = UUID.randomUUID(),
        workspaceId: UUID = UUID.randomUUID(),
        entityTypeId: UUID = UUID.randomUUID(),
        schemaVersion: Int = 1,
        entityTypeName: String = "Customer",
        semanticGroup: SemanticGroup = SemanticGroup.CUSTOMER,
        lifecycleDomain: LifecycleDomain = LifecycleDomain.ACQUISITION,
        attributes: List<AttributeSection> = emptyList(),
        catalogBacklinks: List<CatalogBacklinkSection> = emptyList(),
        knowledgeBacklinks: List<KnowledgeBacklinkSection> = emptyList(),
        clusterSiblings: List<ClusterSiblingSection> = emptyList(),
        relationalReferences: List<RelationalReferenceSection> = emptyList(),
    ): EntityKnowledgeView = EntityKnowledgeView(
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
                metadataDefinition = "A business customer entity",
                glossaryDefinitions = emptyList(),
            ),
            attributes = attributes,
            catalogBacklinks = catalogBacklinks,
            knowledgeBacklinks = knowledgeBacklinks,
            entityMetadata = EntityMetadataSection(
                schemaVersion = schemaVersion,
                composedAt = ZonedDateTime.now(),
                sentiment = null,
            ),
            clusterSiblings = clusterSiblings,
            relationalReferences = relationalReferences,
        ),
    )
}
