package riven.core.service.ingestion

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.enums.integration.SourceType
import riven.core.models.ingestion.MatchType
import riven.core.models.ingestion.ResolutionResult
import riven.core.repository.entity.EntityAttributeRepository
import riven.core.repository.entity.EntityRepository
import riven.core.service.identity.EntityTypeClassificationService
import riven.core.service.util.factory.entity.EntityFactory
import java.util.UUID

/**
 * Unit tests for [IdentityResolutionService].
 *
 * Verifies the two-query batch resolution strategy:
 * 1. sourceExternalId match (Check 1)
 * 2. IDENTIFIER attribute value match (Check 2)
 * and edge cases like no-match, ambiguous matches, and batch query efficiency.
 */
@SpringBootTest(classes = [IdentityResolutionService::class, IdentityResolutionServiceTest.TestConfig::class])
class IdentityResolutionServiceTest {

    @Configuration
    class TestConfig

    @MockitoBean
    private lateinit var entityRepository: EntityRepository

    @MockitoBean
    private lateinit var entityAttributeRepository: EntityAttributeRepository

    @MockitoBean
    private lateinit var classificationService: EntityTypeClassificationService

    @MockitoBean
    private lateinit var logger: KLogger

    @Autowired
    private lateinit var service: IdentityResolutionService

    private val objectMapper = ObjectMapper()
    private val workspaceId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001")
    private val integrationTypeId = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001")
    private val targetEntityTypeId = UUID.fromString("cccccccc-0000-0000-0000-000000000001")

    @Nested
    inner class ResolveBatch {

        @Test
        fun `sourceExternalId match returns ExistingEntity with EXTERNAL_ID matchType`() {
            val integrationEntityId = UUID.randomUUID()
            val coreEntityId = UUID.randomUUID()
            val externalId = "ext-123"

            val integrationEntity = EntityFactory.createEntityEntity(
                id = integrationEntityId,
                workspaceId = workspaceId,
                typeId = integrationTypeId,
                sourceExternalId = externalId,
                sourceType = SourceType.INTEGRATION,
            )

            val coreEntity = EntityFactory.createEntityEntity(
                id = coreEntityId,
                workspaceId = workspaceId,
                typeId = targetEntityTypeId,
                sourceExternalId = externalId,
                sourceType = SourceType.USER_CREATED,
            )

            whenever(
                entityRepository.findByTypeIdAndWorkspaceIdAndSourceExternalIdIn(
                    entityTypeId = targetEntityTypeId,
                    workspaceId = workspaceId,
                    sourceExternalIds = setOf(externalId),
                )
            ).thenReturn(listOf(coreEntity))

            val results = service.resolveBatch(
                entities = listOf(integrationEntity),
                workspaceId = workspaceId,
                targetEntityTypeId = targetEntityTypeId,
            )

            assertEquals(1, results.size)
            val result = results[integrationEntityId]
            assertTrue(result is ResolutionResult.ExistingEntity)
            val existing = result as ResolutionResult.ExistingEntity
            assertEquals(coreEntityId, existing.entityId)
            assertEquals(MatchType.EXTERNAL_ID, existing.matchType)
        }

        @Test
        fun `identifier key match returns ExistingEntity with IDENTIFIER_KEY matchType`() {
            val integrationEntityId = UUID.randomUUID()
            val coreEntityId = UUID.randomUUID()
            val identifierAttrId = UUID.randomUUID()
            val emailValue = "john@test.com"
            val valueNode = objectMapper.createObjectNode().put("value", emailValue)

            val integrationEntity = EntityFactory.createEntityEntity(
                id = integrationEntityId,
                workspaceId = workspaceId,
                typeId = integrationTypeId,
                sourceExternalId = null,
                sourceType = SourceType.INTEGRATION,
            )

            // Check 1 short-circuits (no sourceExternalId on integration entity)
            // Classification service returns the identifier attribute ID
            whenever(classificationService.getIdentifierAttributeIds(targetEntityTypeId))
                .thenReturn(setOf(identifierAttrId))

            // Integration entity has an attribute with the identifier value
            val integrationAttr = EntityFactory.createEntityAttributeEntity(
                entityId = integrationEntityId,
                workspaceId = workspaceId,
                typeId = integrationTypeId,
                attributeId = identifierAttrId,
                value = valueNode,
            )
            whenever(entityAttributeRepository.findByEntityIdIn(listOf(integrationEntityId)))
                .thenReturn(listOf(integrationAttr))

            // Core entity attribute matches the identifier value
            val coreAttr = EntityFactory.createEntityAttributeEntity(
                entityId = coreEntityId,
                workspaceId = workspaceId,
                typeId = targetEntityTypeId,
                attributeId = identifierAttrId,
                value = valueNode,
            )
            whenever(
                entityAttributeRepository.findByIdentifierValuesForEntityType(
                    workspaceId = workspaceId,
                    entityTypeId = targetEntityTypeId,
                    attributeIds = setOf(identifierAttrId),
                    textValues = setOf(emailValue),
                )
            ).thenReturn(listOf(coreAttr))

            val results = service.resolveBatch(
                entities = listOf(integrationEntity),
                workspaceId = workspaceId,
                targetEntityTypeId = targetEntityTypeId,
            )

            assertEquals(1, results.size)
            val result = results[integrationEntityId]
            assertTrue(result is ResolutionResult.ExistingEntity)
            val existing = result as ResolutionResult.ExistingEntity
            assertEquals(coreEntityId, existing.entityId)
            assertEquals(MatchType.IDENTIFIER_KEY, existing.matchType)
        }

        @Test
        fun `no match returns NewEntity`() {
            val integrationEntityId = UUID.randomUUID()
            val identifierAttrId = UUID.randomUUID()

            val integrationEntity = EntityFactory.createEntityEntity(
                id = integrationEntityId,
                workspaceId = workspaceId,
                typeId = integrationTypeId,
                sourceExternalId = null,
                sourceType = SourceType.INTEGRATION,
            )

            // Classification service returns identifier attrs but no values match
            whenever(classificationService.getIdentifierAttributeIds(targetEntityTypeId))
                .thenReturn(setOf(identifierAttrId))

            // Integration entity has no attributes at all
            whenever(entityAttributeRepository.findByEntityIdIn(listOf(integrationEntityId)))
                .thenReturn(emptyList())

            val results = service.resolveBatch(
                entities = listOf(integrationEntity),
                workspaceId = workspaceId,
                targetEntityTypeId = targetEntityTypeId,
            )

            assertEquals(1, results.size)
            val result = results[integrationEntityId]
            assertTrue(result is ResolutionResult.NewEntity)
            val newEntity = result as ResolutionResult.NewEntity
            assertTrue(newEntity.warnings.isEmpty())
        }

        @Test
        fun `ambiguous match returns NewEntity with warning`() {
            val integrationEntityId = UUID.randomUUID()
            val coreEntityId1 = UUID.randomUUID()
            val coreEntityId2 = UUID.randomUUID()
            val identifierAttrId = UUID.randomUUID()
            val emailValue = "shared@test.com"
            val valueNode = objectMapper.createObjectNode().put("value", emailValue)

            val integrationEntity = EntityFactory.createEntityEntity(
                id = integrationEntityId,
                workspaceId = workspaceId,
                typeId = integrationTypeId,
                sourceExternalId = null,
                sourceType = SourceType.INTEGRATION,
            )

            whenever(classificationService.getIdentifierAttributeIds(targetEntityTypeId))
                .thenReturn(setOf(identifierAttrId))

            // Integration entity has an identifier attribute
            val integrationAttr = EntityFactory.createEntityAttributeEntity(
                entityId = integrationEntityId,
                workspaceId = workspaceId,
                typeId = integrationTypeId,
                attributeId = identifierAttrId,
                value = valueNode,
            )
            whenever(entityAttributeRepository.findByEntityIdIn(listOf(integrationEntityId)))
                .thenReturn(listOf(integrationAttr))

            // Two different core entities match the same identifier value
            val coreAttr1 = EntityFactory.createEntityAttributeEntity(
                entityId = coreEntityId1,
                workspaceId = workspaceId,
                typeId = targetEntityTypeId,
                attributeId = identifierAttrId,
                value = valueNode,
            )
            val coreAttr2 = EntityFactory.createEntityAttributeEntity(
                entityId = coreEntityId2,
                workspaceId = workspaceId,
                typeId = targetEntityTypeId,
                attributeId = identifierAttrId,
                value = valueNode,
            )
            whenever(
                entityAttributeRepository.findByIdentifierValuesForEntityType(
                    workspaceId = workspaceId,
                    entityTypeId = targetEntityTypeId,
                    attributeIds = setOf(identifierAttrId),
                    textValues = setOf(emailValue),
                )
            ).thenReturn(listOf(coreAttr1, coreAttr2))

            val results = service.resolveBatch(
                entities = listOf(integrationEntity),
                workspaceId = workspaceId,
                targetEntityTypeId = targetEntityTypeId,
            )

            assertEquals(1, results.size)
            val result = results[integrationEntityId]
            assertTrue(result is ResolutionResult.NewEntity)
            val newEntity = result as ResolutionResult.NewEntity
            assertTrue(newEntity.warnings.isNotEmpty())
            assertTrue(newEntity.warnings.any { it.contains("Ambiguous match") })
        }

        @Test
        fun `batch of 100 records issues exactly 2 DB queries`() {
            val identifierAttrId = UUID.randomUUID()

            // Create 100 integration entities, all with sourceExternalId
            val integrationEntities = (1..100).map { i ->
                EntityFactory.createEntityEntity(
                    id = UUID.randomUUID(),
                    workspaceId = workspaceId,
                    typeId = integrationTypeId,
                    sourceExternalId = "ext-$i",
                    sourceType = SourceType.INTEGRATION,
                )
            }

            // 50 match via Check 1 (sourceExternalId)
            val matchedCoreEntities = integrationEntities.take(50).map { integration ->
                EntityFactory.createEntityEntity(
                    id = UUID.randomUUID(),
                    workspaceId = workspaceId,
                    typeId = targetEntityTypeId,
                    sourceExternalId = integration.sourceExternalId,
                    sourceType = SourceType.USER_CREATED,
                )
            }

            val allExternalIds = integrationEntities.mapNotNull { it.sourceExternalId }.toSet()

            whenever(
                entityRepository.findByTypeIdAndWorkspaceIdAndSourceExternalIdIn(
                    entityTypeId = targetEntityTypeId,
                    workspaceId = workspaceId,
                    sourceExternalIds = allExternalIds,
                )
            ).thenReturn(matchedCoreEntities)

            // 50 unmatched entities go to Check 2
            val unmatchedEntityIds = integrationEntities.drop(50).mapNotNull { it.id }

            whenever(classificationService.getIdentifierAttributeIds(targetEntityTypeId))
                .thenReturn(setOf(identifierAttrId))

            // No identifier attributes found for unmatched entities
            whenever(entityAttributeRepository.findByEntityIdIn(unmatchedEntityIds))
                .thenReturn(emptyList())

            val results = service.resolveBatch(
                entities = integrationEntities,
                workspaceId = workspaceId,
                targetEntityTypeId = targetEntityTypeId,
            )

            assertEquals(100, results.size)

            // Verify exactly 1 call to each repository query
            verify(entityRepository).findByTypeIdAndWorkspaceIdAndSourceExternalIdIn(
                entityTypeId = targetEntityTypeId,
                workspaceId = workspaceId,
                sourceExternalIds = allExternalIds,
            )
            verify(entityAttributeRepository).findByEntityIdIn(unmatchedEntityIds)
            verifyNoMoreInteractions(entityRepository, entityAttributeRepository)

            // Verify 50 matched as ExistingEntity, 50 as NewEntity
            val existingCount = results.values.count { it is ResolutionResult.ExistingEntity }
            val newCount = results.values.count { it is ResolutionResult.NewEntity }
            assertEquals(50, existingCount)
            assertEquals(50, newCount)
        }
    }
}
