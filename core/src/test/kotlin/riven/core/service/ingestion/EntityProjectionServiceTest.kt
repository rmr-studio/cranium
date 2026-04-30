package riven.core.service.ingestion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.entity.entity.EntityAttributeEntity
import riven.core.entity.entity.EntityEntity
import riven.core.entity.entity.EntityRelationshipEntity
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.integration.SourceType
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.models.ingestion.MatchType
import riven.core.models.ingestion.ProjectionOutcome
import riven.core.models.ingestion.ResolutionResult
import riven.core.repository.entity.EntityAttributeRepository
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.integration.ProjectionRuleRepository
import riven.core.service.entity.EntityAttributeService
import riven.core.service.identity.IdentityClusterService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.factory.entity.EntityFactory
import riven.core.service.util.factory.integration.IntegrationFactory
import tools.jackson.databind.node.JsonNodeFactory
import org.mockito.Mockito.reset
import org.springframework.beans.factory.annotation.Autowired
import java.util.Optional
import java.util.UUID

/**
 * Unit tests for [EntityProjectionService].
 *
 * No @WithUserPersona needed — this service runs in Temporal activity context without JWT.
 * Workspace isolation is enforced by parameter, not auth context.
 */
@SpringBootTest(
    classes = [
        EntityProjectionService::class,
        EntityProjectionServiceTest.TestConfig::class,
    ]
)
class EntityProjectionServiceTest : BaseServiceTest() {

    @Configuration
    class TestConfig

    @MockitoBean
    private lateinit var entityRepository: EntityRepository

    @MockitoBean
    private lateinit var entityTypeRepository: EntityTypeRepository

    @MockitoBean
    private lateinit var entityAttributeRepository: EntityAttributeRepository

    @MockitoBean
    private lateinit var entityAttributeService: EntityAttributeService

    @MockitoBean
    private lateinit var entityRelationshipRepository: EntityRelationshipRepository

    @MockitoBean
    private lateinit var projectionRuleRepository: ProjectionRuleRepository

    @MockitoBean
    private lateinit var identityResolutionService: IdentityResolutionService

    @MockitoBean
    private lateinit var identityClusterService: IdentityClusterService

    @Autowired
    private lateinit var service: EntityProjectionService

    private val sourceEntityTypeId = UUID.randomUUID()
    private val targetEntityTypeId = UUID.randomUUID()
    private val relationshipDefId = UUID.randomUUID()

    private fun createProjectionRuleEntity(
        autoCreate: Boolean = true,
        relDefId: UUID? = relationshipDefId,
    ) = IntegrationFactory.createProjectionRule(
        id = UUID.randomUUID(),
        workspaceId = workspaceId,
        sourceEntityTypeId = sourceEntityTypeId,
        targetEntityTypeId = targetEntityTypeId,
        relationshipDefId = relDefId,
        autoCreate = autoCreate,
    )

    private fun createIntegrationEntity(
        id: UUID = UUID.randomUUID(),
        syncVersion: Long = 1,
    ): EntityEntity = EntityFactory.createEntityEntity(
        id = id,
        workspaceId = workspaceId,
        typeId = sourceEntityTypeId,
        sourceType = SourceType.INTEGRATION,
        sourceExternalId = "ext-${id}",
    ).also { it.syncVersion = syncVersion }

    private fun createCoreEntity(
        id: UUID = UUID.randomUUID(),
        syncVersion: Long = 0,
        deleted: Boolean = false,
    ): EntityEntity = EntityFactory.createEntityEntity(
        id = id,
        workspaceId = workspaceId,
        typeId = targetEntityTypeId,
        sourceType = SourceType.PROJECTED,
    ).also {
        it.syncVersion = syncVersion
        it.deleted = deleted
    }

    private fun createAttributeEntity(
        entityId: UUID,
        attributeId: UUID = UUID.randomUUID(),
        value: String = "test-value",
    ): EntityAttributeEntity = EntityFactory.createEntityAttributeEntity(
        entityId = entityId,
        workspaceId = workspaceId,
        typeId = targetEntityTypeId,
        attributeId = attributeId,
        value = JsonNodeFactory.instance.stringNode(value),
    )

    @BeforeEach
    fun setUp() {
        reset(
            entityRepository,
            entityTypeRepository,
            entityAttributeRepository,
            entityAttributeService,
            entityRelationshipRepository,
            projectionRuleRepository,
            identityResolutionService,
            identityClusterService,
        )
    }

    @Nested
    inner class ProcessProjections {

        @Test
        fun `rule exists + identity match - updates core entity with mapped fields`() {
            val integrationEntityId = UUID.randomUUID()
            val coreEntityId = UUID.randomUUID()
            val integrationEntity = createIntegrationEntity(id = integrationEntityId, syncVersion = 2)
            val coreEntity = createCoreEntity(id = coreEntityId, syncVersion = 1)
            val ruleEntity = createProjectionRuleEntity()

            whenever(projectionRuleRepository.findBySourceEntityTypeIdAndWorkspace(sourceEntityTypeId, workspaceId))
                .thenReturn(listOf(ruleEntity))
            whenever(entityRepository.findByIdIn(listOf(integrationEntityId)))
                .thenReturn(listOf(integrationEntity))
            whenever(identityResolutionService.resolveBatch(any(), eq(workspaceId), eq(targetEntityTypeId)))
                .thenReturn(mapOf(integrationEntityId to ResolutionResult.ExistingEntity(coreEntityId, MatchType.EXTERNAL_ID)))
            whenever(entityRepository.findByIdAndWorkspaceId(coreEntityId, workspaceId))
                .thenReturn(Optional.of(coreEntity))
            whenever(entityAttributeRepository.findByEntityId(integrationEntityId))
                .thenReturn(emptyList())
            whenever(entityAttributeRepository.findByEntityId(coreEntityId))
                .thenReturn(emptyList())
            whenever(entityRelationshipRepository.findBySourceIdAndTargetIdAndDefinitionId(any(), any(), any()))
                .thenReturn(emptyList())

            val result = service.processProjections(listOf(integrationEntityId), workspaceId, sourceEntityTypeId)

            assertEquals(0, result.created)
            assertEquals(1, result.updated)
            assertEquals(0, result.skipped)
            verify(entityRepository).save(coreEntity)
        }

        @Test
        fun `no match + autoCreate - creates PROJECTED entity + link + cluster assignment`() {
            val integrationEntityId = UUID.randomUUID()
            val integrationEntity = createIntegrationEntity(id = integrationEntityId, syncVersion = 1)
            val targetEntityType = EntityFactory.createEntityType(
                id = targetEntityTypeId,
                workspaceId = workspaceId,
                key = "target_type",
            )
            val savedCoreEntity = createCoreEntity(id = UUID.randomUUID())
            val ruleEntity = createProjectionRuleEntity(autoCreate = true)

            whenever(projectionRuleRepository.findBySourceEntityTypeIdAndWorkspace(sourceEntityTypeId, workspaceId))
                .thenReturn(listOf(ruleEntity))
            whenever(entityRepository.findByIdIn(listOf(integrationEntityId)))
                .thenReturn(listOf(integrationEntity))
            whenever(identityResolutionService.resolveBatch(any(), eq(workspaceId), eq(targetEntityTypeId)))
                .thenReturn(mapOf(integrationEntityId to ResolutionResult.NewEntity()))
            whenever(entityTypeRepository.findById(targetEntityTypeId))
                .thenReturn(Optional.of(targetEntityType))
            whenever(entityRepository.save(any<EntityEntity>()))
                .thenReturn(savedCoreEntity)
            whenever(entityAttributeRepository.findByEntityId(integrationEntityId))
                .thenReturn(emptyList())
            whenever(entityRelationshipRepository.findBySourceIdAndTargetIdAndDefinitionId(any(), any(), any()))
                .thenReturn(emptyList())

            val result = service.processProjections(listOf(integrationEntityId), workspaceId, sourceEntityTypeId)

            assertEquals(1, result.created)
            assertEquals(0, result.updated)

            // Verify entity was saved with PROJECTED source type
            val entityCaptor = argumentCaptor<EntityEntity>()
            verify(entityRepository).save(entityCaptor.capture())
            assertEquals(SourceType.PROJECTED, entityCaptor.firstValue.sourceType)

            // Verify relationship link was created
            verify(entityRelationshipRepository).save(any<EntityRelationshipEntity>())

            // Verify cluster membership was resolved
            verify(identityClusterService).resolveClusterMembership(
                eq(workspaceId),
                eq(integrationEntityId),
                eq(requireNotNull(savedCoreEntity.id)),
                eq(null),
                eq(UUID(0, 0)),
            )
        }

        @Test
        fun `no projection rules - returns empty result`() {
            val entityIds = listOf(UUID.randomUUID(), UUID.randomUUID())

            whenever(projectionRuleRepository.findBySourceEntityTypeIdAndWorkspace(sourceEntityTypeId, workspaceId))
                .thenReturn(emptyList())

            val result = service.processProjections(entityIds, workspaceId, sourceEntityTypeId)

            assertEquals(0, result.created)
            assertEquals(0, result.updated)
            assertEquals(entityIds.size, result.skipped)
            assertEquals(0, result.errors)

            verify(entityRepository, never()).findByIdIn(any())
            verify(identityResolutionService, never()).resolveBatch(any(), any(), any())
        }

        @Test
        fun `autoCreate=false + no match - skips entity`() {
            val integrationEntityId = UUID.randomUUID()
            val integrationEntity = createIntegrationEntity(id = integrationEntityId)
            val ruleEntity = createProjectionRuleEntity(autoCreate = false)

            whenever(projectionRuleRepository.findBySourceEntityTypeIdAndWorkspace(sourceEntityTypeId, workspaceId))
                .thenReturn(listOf(ruleEntity))
            whenever(entityRepository.findByIdIn(listOf(integrationEntityId)))
                .thenReturn(listOf(integrationEntity))
            whenever(identityResolutionService.resolveBatch(any(), eq(workspaceId), eq(targetEntityTypeId)))
                .thenReturn(mapOf(integrationEntityId to ResolutionResult.NewEntity()))

            val result = service.processProjections(listOf(integrationEntityId), workspaceId, sourceEntityTypeId)

            assertEquals(0, result.created)
            assertEquals(0, result.updated)
            assertEquals(1, result.skipped)
            assertEquals(ProjectionOutcome.SKIPPED_AUTO_CREATE_DISABLED, result.details.first().outcome)

            verify(entityRepository, never()).save(any<EntityEntity>())
        }

        @Test
        fun `source wins - mapped field overwrite on update`() {
            val integrationEntityId = UUID.randomUUID()
            val coreEntityId = UUID.randomUUID()
            val attrId = UUID.randomUUID()
            val integrationEntity = createIntegrationEntity(id = integrationEntityId, syncVersion = 2)
            val coreEntity = createCoreEntity(id = coreEntityId, syncVersion = 1)
            val ruleEntity = createProjectionRuleEntity()

            val sourceAttr = createAttributeEntity(entityId = integrationEntityId, attributeId = attrId, value = "new-value")
            val existingCoreAttr = createAttributeEntity(entityId = coreEntityId, attributeId = attrId, value = "old-value")

            whenever(projectionRuleRepository.findBySourceEntityTypeIdAndWorkspace(sourceEntityTypeId, workspaceId))
                .thenReturn(listOf(ruleEntity))
            whenever(entityRepository.findByIdIn(listOf(integrationEntityId)))
                .thenReturn(listOf(integrationEntity))
            whenever(identityResolutionService.resolveBatch(any(), eq(workspaceId), eq(targetEntityTypeId)))
                .thenReturn(mapOf(integrationEntityId to ResolutionResult.ExistingEntity(coreEntityId, MatchType.EXTERNAL_ID)))
            whenever(entityRepository.findByIdAndWorkspaceId(coreEntityId, workspaceId))
                .thenReturn(Optional.of(coreEntity))
            whenever(entityAttributeRepository.findByEntityId(integrationEntityId))
                .thenReturn(listOf(sourceAttr))
            whenever(entityAttributeRepository.findByEntityId(coreEntityId))
                .thenReturn(listOf(existingCoreAttr))
            whenever(entityRelationshipRepository.findBySourceIdAndTargetIdAndDefinitionId(any(), any(), any()))
                .thenReturn(emptyList())

            service.processProjections(listOf(integrationEntityId), workspaceId, sourceEntityTypeId)

            val attrsCaptor = argumentCaptor<Map<UUID, EntityAttributePrimitivePayload>>()
            verify(entityAttributeService).saveAttributes(
                entityId = eq(coreEntityId),
                workspaceId = eq(workspaceId),
                typeId = eq(targetEntityTypeId),
                attributes = attrsCaptor.capture(),
            )

            val savedAttrs = attrsCaptor.firstValue
            assertEquals(1, savedAttrs.size)
            // Source value ("new-value") should overwrite core value ("old-value")
            assertEquals("new-value", savedAttrs[attrId]?.value)
        }

        @Test
        fun `user-owned unmapped fields preserved on update`() {
            val integrationEntityId = UUID.randomUUID()
            val coreEntityId = UUID.randomUUID()
            val attrA = UUID.randomUUID()
            val attrB = UUID.randomUUID()
            val integrationEntity = createIntegrationEntity(id = integrationEntityId, syncVersion = 2)
            val coreEntity = createCoreEntity(id = coreEntityId, syncVersion = 1)
            val ruleEntity = createProjectionRuleEntity()

            // Integration entity has only attr A
            val sourceAttrA = createAttributeEntity(entityId = integrationEntityId, attributeId = attrA, value = "updated-A")
            // Core entity has both attr A and attr B
            val existingCoreAttrA = createAttributeEntity(entityId = coreEntityId, attributeId = attrA, value = "old-A")
            val existingCoreAttrB = createAttributeEntity(entityId = coreEntityId, attributeId = attrB, value = "user-B")

            whenever(projectionRuleRepository.findBySourceEntityTypeIdAndWorkspace(sourceEntityTypeId, workspaceId))
                .thenReturn(listOf(ruleEntity))
            whenever(entityRepository.findByIdIn(listOf(integrationEntityId)))
                .thenReturn(listOf(integrationEntity))
            whenever(identityResolutionService.resolveBatch(any(), eq(workspaceId), eq(targetEntityTypeId)))
                .thenReturn(mapOf(integrationEntityId to ResolutionResult.ExistingEntity(coreEntityId, MatchType.EXTERNAL_ID)))
            whenever(entityRepository.findByIdAndWorkspaceId(coreEntityId, workspaceId))
                .thenReturn(Optional.of(coreEntity))
            whenever(entityAttributeRepository.findByEntityId(integrationEntityId))
                .thenReturn(listOf(sourceAttrA))
            whenever(entityAttributeRepository.findByEntityId(coreEntityId))
                .thenReturn(listOf(existingCoreAttrA, existingCoreAttrB))
            whenever(entityRelationshipRepository.findBySourceIdAndTargetIdAndDefinitionId(any(), any(), any()))
                .thenReturn(emptyList())

            service.processProjections(listOf(integrationEntityId), workspaceId, sourceEntityTypeId)

            val attrsCaptor = argumentCaptor<Map<UUID, EntityAttributePrimitivePayload>>()
            verify(entityAttributeService).saveAttributes(
                entityId = eq(coreEntityId),
                workspaceId = eq(workspaceId),
                typeId = eq(targetEntityTypeId),
                attributes = attrsCaptor.capture(),
            )

            val savedAttrs = attrsCaptor.firstValue
            assertEquals(2, savedAttrs.size)
            // Attr A should have the new integration value
            assertEquals("updated-A", savedAttrs[attrA]?.value)
            // Attr B should be preserved from the core entity
            assertEquals("user-B", savedAttrs[attrB]?.value)
        }

        @Test
        fun `relationship link already exists - idempotent, no duplicate`() {
            val integrationEntityId = UUID.randomUUID()
            val coreEntityId = UUID.randomUUID()
            val integrationEntity = createIntegrationEntity(id = integrationEntityId, syncVersion = 2)
            val coreEntity = createCoreEntity(id = coreEntityId, syncVersion = 1)
            val ruleEntity = createProjectionRuleEntity()

            val existingRelationship = EntityFactory.createRelationshipEntity(
                workspaceId = workspaceId,
                sourceId = integrationEntityId,
                targetId = coreEntityId,
                definitionId = relationshipDefId,
                linkSource = SourceType.PROJECTED,
            )

            whenever(projectionRuleRepository.findBySourceEntityTypeIdAndWorkspace(sourceEntityTypeId, workspaceId))
                .thenReturn(listOf(ruleEntity))
            whenever(entityRepository.findByIdIn(listOf(integrationEntityId)))
                .thenReturn(listOf(integrationEntity))
            whenever(identityResolutionService.resolveBatch(any(), eq(workspaceId), eq(targetEntityTypeId)))
                .thenReturn(mapOf(integrationEntityId to ResolutionResult.ExistingEntity(coreEntityId, MatchType.EXTERNAL_ID)))
            whenever(entityRepository.findByIdAndWorkspaceId(coreEntityId, workspaceId))
                .thenReturn(Optional.of(coreEntity))
            whenever(entityAttributeRepository.findByEntityId(any()))
                .thenReturn(emptyList())
            whenever(entityRelationshipRepository.findBySourceIdAndTargetIdAndDefinitionId(
                integrationEntityId, coreEntityId, relationshipDefId
            )).thenReturn(listOf(existingRelationship))

            service.processProjections(listOf(integrationEntityId), workspaceId, sourceEntityTypeId)

            // Relationship save should NOT be called since one already exists
            verify(entityRelationshipRepository, never()).save(any<EntityRelationshipEntity>())
        }

        @Test
        fun `syncVersion guard - stale write rejected`() {
            val integrationEntityId = UUID.randomUUID()
            val coreEntityId = UUID.randomUUID()
            val integrationEntity = createIntegrationEntity(id = integrationEntityId, syncVersion = 1)
            val coreEntity = createCoreEntity(id = coreEntityId, syncVersion = 5)
            val ruleEntity = createProjectionRuleEntity()

            whenever(projectionRuleRepository.findBySourceEntityTypeIdAndWorkspace(sourceEntityTypeId, workspaceId))
                .thenReturn(listOf(ruleEntity))
            whenever(entityRepository.findByIdIn(listOf(integrationEntityId)))
                .thenReturn(listOf(integrationEntity))
            whenever(identityResolutionService.resolveBatch(any(), eq(workspaceId), eq(targetEntityTypeId)))
                .thenReturn(mapOf(integrationEntityId to ResolutionResult.ExistingEntity(coreEntityId, MatchType.EXTERNAL_ID)))
            whenever(entityRepository.findByIdAndWorkspaceId(coreEntityId, workspaceId))
                .thenReturn(Optional.of(coreEntity))

            val result = service.processProjections(listOf(integrationEntityId), workspaceId, sourceEntityTypeId)

            assertEquals(0, result.updated)
            assertEquals(1, result.skipped)
            assertEquals(ProjectionOutcome.SKIPPED_STALE_VERSION, result.details.first().outcome)

            // No attribute transfer or save should happen
            verify(entityAttributeService, never()).saveAttributes(any(), any(), any(), any())
            verify(entityRepository, never()).save(any<EntityEntity>())
        }

        @Test
        fun `150 entities - 2 chunks processed`() {
            val entityIds = (1..150).map { UUID.randomUUID() }
            val chunk1Ids = entityIds.take(100)
            val chunk2Ids = entityIds.drop(100)
            val ruleEntity = createProjectionRuleEntity(autoCreate = false)

            val chunk1Entities = chunk1Ids.map { createIntegrationEntity(id = it) }
            val chunk2Entities = chunk2Ids.map { createIntegrationEntity(id = it) }

            whenever(projectionRuleRepository.findBySourceEntityTypeIdAndWorkspace(sourceEntityTypeId, workspaceId))
                .thenReturn(listOf(ruleEntity))
            whenever(entityRepository.findByIdIn(chunk1Ids))
                .thenReturn(chunk1Entities)
            whenever(entityRepository.findByIdIn(chunk2Ids))
                .thenReturn(chunk2Entities)
            whenever(identityResolutionService.resolveBatch(any(), eq(workspaceId), eq(targetEntityTypeId)))
                .thenReturn(emptyMap()) // All resolve to NewEntity (default)

            val result = service.processProjections(entityIds, workspaceId, sourceEntityTypeId)

            // All should be skipped since autoCreate=false and all resolve to NewEntity
            assertEquals(150, result.skipped)

            // Verify findByIdIn was called twice (once per chunk)
            verify(entityRepository).findByIdIn(chunk1Ids)
            verify(entityRepository).findByIdIn(chunk2Ids)
        }

        @Test
        fun `soft-deleted core entity - skipped`() {
            val integrationEntityId = UUID.randomUUID()
            val coreEntityId = UUID.randomUUID()
            val integrationEntity = createIntegrationEntity(id = integrationEntityId, syncVersion = 2)
            val coreEntity = createCoreEntity(id = coreEntityId, syncVersion = 1, deleted = true)
            val ruleEntity = createProjectionRuleEntity()

            whenever(projectionRuleRepository.findBySourceEntityTypeIdAndWorkspace(sourceEntityTypeId, workspaceId))
                .thenReturn(listOf(ruleEntity))
            whenever(entityRepository.findByIdIn(listOf(integrationEntityId)))
                .thenReturn(listOf(integrationEntity))
            whenever(identityResolutionService.resolveBatch(any(), eq(workspaceId), eq(targetEntityTypeId)))
                .thenReturn(mapOf(integrationEntityId to ResolutionResult.ExistingEntity(coreEntityId, MatchType.EXTERNAL_ID)))
            whenever(entityRepository.findByIdAndWorkspaceId(coreEntityId, workspaceId))
                .thenReturn(Optional.of(coreEntity))

            val result = service.processProjections(listOf(integrationEntityId), workspaceId, sourceEntityTypeId)

            assertEquals(0, result.updated)
            assertEquals(1, result.skipped)
            assertEquals(ProjectionOutcome.SKIPPED_SOFT_DELETED, result.details.first().outcome)

            verify(entityRepository, never()).save(any<EntityEntity>())
            verify(entityAttributeService, never()).saveAttributes(any(), any(), any(), any())
        }
    }
}
