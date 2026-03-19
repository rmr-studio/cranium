package riven.core.service.identity

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticMetadataTargetType
import riven.core.repository.entity.EntityTypeSemanticMetadataRepository
import riven.core.service.util.factory.identity.IdentityFactory
import java.util.UUID

/**
 * Unit tests for [EntityTypeClassificationService].
 *
 * Verifies that IDENTIFIER attribute classification is correctly cached per entity type
 * and that cache invalidation forces a fresh repository lookup.
 */
@SpringBootTest(classes = [EntityTypeClassificationService::class])
class EntityTypeClassificationServiceTest {

    @MockitoBean
    private lateinit var semanticMetadataRepository: EntityTypeSemanticMetadataRepository

    @MockitoBean
    private lateinit var logger: KLogger

    @Autowired
    private lateinit var service: EntityTypeClassificationService

    private val entityTypeId = UUID.fromString("11111111-0000-0000-0000-000000000001")
    private val workspaceId = UUID.fromString("22222222-0000-0000-0000-000000000002")

    @BeforeEach
    fun clearCache() {
        // Invalidate before each test to ensure a clean cache state
        service.invalidate(entityTypeId)
    }

    // ------ hasIdentifierAttributes ------

    @Nested
    inner class HasIdentifierAttributesTests {

        @Test
        fun `returns true when entity type has at least one IDENTIFIER attribute`() {
            val attrId = UUID.randomUUID()
            whenever(
                semanticMetadataRepository.findByEntityTypeIdAndTargetType(entityTypeId, SemanticMetadataTargetType.ATTRIBUTE)
            ).thenReturn(listOf(IdentityFactory.createEntityTypeSemanticMetadataEntity(
                    workspaceId = workspaceId,
                    entityTypeId = entityTypeId,
                    targetId = attrId,
                    classification = SemanticAttributeClassification.IDENTIFIER,
                )))

            val result = service.hasIdentifierAttributes(entityTypeId)

            assertTrue(result)
        }

        @Test
        fun `returns false when entity type has no IDENTIFIER attributes`() {
            whenever(
                semanticMetadataRepository.findByEntityTypeIdAndTargetType(entityTypeId, SemanticMetadataTargetType.ATTRIBUTE)
            ).thenReturn(listOf(IdentityFactory.createEntityTypeSemanticMetadataEntity(
                    workspaceId = workspaceId,
                    entityTypeId = entityTypeId,
                    targetId = UUID.randomUUID(),
                    classification = SemanticAttributeClassification.CATEGORICAL,
                )))

            val result = service.hasIdentifierAttributes(entityTypeId)

            assertFalse(result)
        }

        @Test
        fun `returns false when entity type has no attributes at all`() {
            whenever(
                semanticMetadataRepository.findByEntityTypeIdAndTargetType(entityTypeId, SemanticMetadataTargetType.ATTRIBUTE)
            ).thenReturn(emptyList())

            val result = service.hasIdentifierAttributes(entityTypeId)

            assertFalse(result)
        }

        @Test
        fun `cache hit skips repository on second call`() {
            val attrId = UUID.randomUUID()
            whenever(
                semanticMetadataRepository.findByEntityTypeIdAndTargetType(entityTypeId, SemanticMetadataTargetType.ATTRIBUTE)
            ).thenReturn(listOf(IdentityFactory.createEntityTypeSemanticMetadataEntity(
                    workspaceId = workspaceId,
                    entityTypeId = entityTypeId,
                    targetId = attrId,
                    classification = SemanticAttributeClassification.IDENTIFIER,
                )))

            // First call — populates cache
            service.hasIdentifierAttributes(entityTypeId)
            // Second call — should use cache
            service.hasIdentifierAttributes(entityTypeId)

            // Repository queried only once despite two calls
            verify(semanticMetadataRepository, times(1))
                .findByEntityTypeIdAndTargetType(entityTypeId, SemanticMetadataTargetType.ATTRIBUTE)
        }
    }

    // ------ getIdentifierAttributeIds ------

    @Nested
    inner class GetIdentifierAttributeIdsTests {

        @Test
        fun `returns set of IDENTIFIER attribute IDs`() {
            val attrId1 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001")
            val attrId2 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002")
            val nonIdentifierAttrId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003")
            whenever(
                semanticMetadataRepository.findByEntityTypeIdAndTargetType(entityTypeId, SemanticMetadataTargetType.ATTRIBUTE)
            ).thenReturn(
                listOf(
                    IdentityFactory.createEntityTypeSemanticMetadataEntity(
                        workspaceId = workspaceId,
                        entityTypeId = entityTypeId,
                        targetId = attrId1,
                        classification = SemanticAttributeClassification.IDENTIFIER,
                    ),
                    IdentityFactory.createEntityTypeSemanticMetadataEntity(
                        workspaceId = workspaceId,
                        entityTypeId = entityTypeId,
                        targetId = attrId2,
                        classification = SemanticAttributeClassification.IDENTIFIER,
                    ),
                    IdentityFactory.createEntityTypeSemanticMetadataEntity(
                        workspaceId = workspaceId,
                        entityTypeId = entityTypeId,
                        targetId = nonIdentifierAttrId,
                        classification = SemanticAttributeClassification.FREETEXT,
                    ),
                )
            )

            val result = service.getIdentifierAttributeIds(entityTypeId)

            assertEquals(setOf(attrId1, attrId2), result)
        }

        @Test
        fun `returns empty set when no IDENTIFIER attributes exist`() {
            whenever(
                semanticMetadataRepository.findByEntityTypeIdAndTargetType(entityTypeId, SemanticMetadataTargetType.ATTRIBUTE)
            ).thenReturn(
                listOf(IdentityFactory.createEntityTypeSemanticMetadataEntity(
                    workspaceId = workspaceId,
                    entityTypeId = entityTypeId,
                    targetId = UUID.randomUUID(),
                    classification = SemanticAttributeClassification.QUANTITATIVE,
                ))
            )

            val result = service.getIdentifierAttributeIds(entityTypeId)

            assertTrue(result.isEmpty())
        }
    }

    // ------ invalidate ------

    @Nested
    inner class InvalidateTests {

        @Test
        fun `invalidate clears cache so next call re-queries repository`() {
            val attrId = UUID.randomUUID()
            whenever(
                semanticMetadataRepository.findByEntityTypeIdAndTargetType(entityTypeId, SemanticMetadataTargetType.ATTRIBUTE)
            ).thenReturn(listOf(IdentityFactory.createEntityTypeSemanticMetadataEntity(
                    workspaceId = workspaceId,
                    entityTypeId = entityTypeId,
                    targetId = attrId,
                    classification = SemanticAttributeClassification.IDENTIFIER,
                )))

            // First call — populates cache
            service.hasIdentifierAttributes(entityTypeId)
            // Invalidate cache
            service.invalidate(entityTypeId)
            // Second call — must re-query
            service.hasIdentifierAttributes(entityTypeId)

            verify(semanticMetadataRepository, times(2))
                .findByEntityTypeIdAndTargetType(entityTypeId, SemanticMetadataTargetType.ATTRIBUTE)
        }
    }

}
