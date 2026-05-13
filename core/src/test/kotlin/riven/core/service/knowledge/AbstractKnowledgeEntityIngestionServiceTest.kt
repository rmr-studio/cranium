package cranium.core.service.knowledge

import io.github.oshai.kotlinlogging.KLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import cranium.core.entity.entity.EntityEntity
import cranium.core.entity.entity.EntityTypeEntity
import cranium.core.enums.entity.EntityRelationshipCardinality
import cranium.core.enums.entity.RelationshipTargetKind
import cranium.core.enums.entity.SystemRelationshipType
import cranium.core.enums.integration.SourceType
import cranium.core.repository.entity.EntityRelationshipRepository
import cranium.core.repository.entity.EntityRepository
import cranium.core.repository.entity.EntityTypeRepository
import cranium.core.service.entity.EntityIngestionService
import cranium.core.service.entity.type.EntityTypeRelationshipService
import cranium.core.service.util.factory.entity.EntityFactory
import java.util.Optional
import java.util.UUID

/**
 * Unit-level coverage of the abstract ingestion base. Uses a fake subclass so the
 * base's lifecycle (entity-type lookup, idempotent upsert via sourceExternalId,
 * relationship batch reconciliation) is exercised without Spring wiring.
 */
class AbstractKnowledgeEntityIngestionServiceTest {

    private val workspaceId: UUID = UUID.randomUUID()
    private val entityTypeId: UUID = UUID.randomUUID()
    private val labelAttributeId: UUID = UUID.randomUUID()
    private val systemDefinitionId: UUID = UUID.randomUUID()

    private val entityIngestionService: EntityIngestionService = mock()
    private val entityTypeRepository: EntityTypeRepository = mock()
    private val entityRepository: EntityRepository = mock()
    private val entityRelationshipRepository: EntityRelationshipRepository = mock()
    private val entityTypeRelationshipService: EntityTypeRelationshipService = mock()
    private val logger: KLogger = mock()

    private val service = FakeKnowledgeService(
        entityIngestionService, entityTypeRepository, entityRepository, entityRelationshipRepository, entityTypeRelationshipService, logger,
    )

    private fun stubEntityType(): EntityTypeEntity {
        val entityType = EntityFactory.createEntityType(
            id = entityTypeId,
            key = "fake",
            workspaceId = workspaceId,
            attributeKeyMapping = mapOf("label" to labelAttributeId.toString()),
        )
        whenever(entityTypeRepository.findByworkspaceIdAndKey(workspaceId, "fake"))
            .thenReturn(Optional.of(entityType))
        return entityType
    }

    private fun stubSystemDefinition() {
        val def = EntityFactory.createRelationshipDefinitionEntity(
            id = systemDefinitionId,
            workspaceId = workspaceId,
            sourceEntityTypeId = entityTypeId,
            name = "Attachments",
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
            protected = true,
            systemType = SystemRelationshipType.ATTACHMENT,
        )
        whenever(
            entityTypeRelationshipService.getOrCreateSystemDefinition(
                workspaceId, entityTypeId, SystemRelationshipType.ATTACHMENT,
            )
        ).thenReturn(def)
    }

    private fun stubSavedEntity(): EntityEntity {
        val saved = EntityFactory.createEntityEntity(
            id = UUID.randomUUID(),
            workspaceId = workspaceId,
            typeId = entityTypeId,
            typeKey = "fake",
        )
        whenever(
            entityIngestionService.saveEntityInternal(
                workspaceId = any(),
                entityTypeId = any(),
                existingId = anyOrNull(),
                attributePayload = any(),
                sourceType = any(),
                sourceIntegrationId = anyOrNull(),
                sourceExternalId = anyOrNull(),
            )
        ).thenReturn(saved)
        return saved
    }

    @Test
    fun `upsert creates entity and reconciles ATTACHMENT batch`() {
        stubEntityType()
        stubSystemDefinition()
        val saved = stubSavedEntity()

        val target = UUID.randomUUID()
        val input = FakeInput(
            workspaceId = workspaceId,
            label = "Status",
            targets = setOf(target),
        )

        val returned = service.upsert(input)

        assertThat(returned.id).isEqualTo(saved.id)
        verify(entityIngestionService).replaceRelationshipsInternal(
            workspaceId = eq(workspaceId),
            sourceEntityId = eq(requireNotNull(saved.id)),
            relationshipDefinitionId = eq(systemDefinitionId),
            targetIds = eq(setOf(target)),
            linkSource = eq(SourceType.USER_CREATED),
            targetKind = eq(RelationshipTargetKind.ENTITY),
            targetParentId = eq(null),
        )
    }

    @Test
    fun `upsert with same sourceExternalId is idempotent — looks up existing entity and passes existingId`() {
        stubEntityType()
        stubSystemDefinition()
        val existing = EntityFactory.createEntityEntity(
            id = UUID.randomUUID(),
            workspaceId = workspaceId,
            typeId = entityTypeId,
            typeKey = "fake",
            sourceExternalId = "ext-1",
        )
        val integrationId = UUID.randomUUID()
        whenever(
            entityRepository.findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(
                workspaceId, integrationId, listOf("ext-1"),
            )
        ).thenReturn(listOf(existing))
        stubSavedEntity()

        val input = FakeInput(
            workspaceId = workspaceId,
            label = "Status",
            sourceType = SourceType.INTEGRATION,
            sourceIntegrationId = integrationId,
            sourceExternalId = "ext-1",
            linkSource = SourceType.INTEGRATION,
        )

        service.upsert(input)

        val captor = argumentCaptor<UUID>()
        verify(entityIngestionService).saveEntityInternal(
            workspaceId = eq(workspaceId),
            entityTypeId = eq(entityTypeId),
            existingId = captor.capture(),
            attributePayload = any(),
            sourceType = eq(SourceType.INTEGRATION),
            sourceIntegrationId = eq(integrationId),
            sourceExternalId = eq("ext-1"),
        )
        assertThat(captor.firstValue).isEqualTo(existing.id)
        verify(entityRepository).findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(
            workspaceId, integrationId, listOf("ext-1"),
        )
    }

    @Test
    fun `upsert without provenance skips idempotent lookup`() {
        stubEntityType()
        stubSystemDefinition()
        stubSavedEntity()

        val input = FakeInput(workspaceId = workspaceId, label = "Status")

        service.upsert(input)

        verify(entityRepository, never()).findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(
            any(), any(), any(),
        )
    }

    private class FakeKnowledgeService(
        entityIngestionService: EntityIngestionService,
        entityTypeRepository: EntityTypeRepository,
        entityRepository: EntityRepository,
        entityRelationshipRepository: EntityRelationshipRepository,
        entityTypeRelationshipService: EntityTypeRelationshipService,
        logger: KLogger,
    ) : AbstractKnowledgeEntityIngestionService<FakeInput>(
        entityIngestionService, entityTypeRepository, entityRepository, entityRelationshipRepository, entityTypeRelationshipService, logger,
    ) {
        override val entityTypeKey: String = "fake"
        override fun buildAttributePayload(entityType: EntityTypeEntity, input: FakeInput): Map<UUID, Any?> =
            mapOf(attributeId(entityType, "label") to input.label)

        override fun relationshipBatches(input: FakeInput): List<KnowledgeRelationshipBatch> =
            listOf(KnowledgeRelationshipBatch(SystemRelationshipType.ATTACHMENT, input.targets))
    }

    private data class FakeInput(
        override val workspaceId: UUID,
        val label: String,
        val targets: Set<UUID> = emptySet(),
        override val sourceType: SourceType = SourceType.USER_CREATED,
        override val sourceIntegrationId: UUID? = null,
        override val sourceExternalId: String? = null,
        override val linkSource: SourceType = SourceType.USER_CREATED,
    ) : KnowledgeIngestionInput
}
