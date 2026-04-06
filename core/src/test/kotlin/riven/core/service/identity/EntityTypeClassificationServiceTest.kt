package riven.core.service.identity

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticMetadataTargetType
import riven.core.enums.identity.MatchSignalType
import riven.core.repository.entity.EntityTypeSemanticMetadataRepository
import riven.core.service.util.factory.identity.IdentityFactory
import java.util.UUID

/**
 * Unit tests for [EntityTypeClassificationService].
 *
 * Verifies that IDENTIFIER attribute classification is correctly cached per entity type,
 * that signal types are resolved from the signal_type column with correct fallback,
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

        @Test
        fun `getIdentifierAttributeIds delegates to getIdentifierSignalTypes key set`() {
            val attrId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001")
            whenever(
                semanticMetadataRepository.findByEntityTypeIdAndTargetType(entityTypeId, SemanticMetadataTargetType.ATTRIBUTE)
            ).thenReturn(listOf(
                IdentityFactory.createEntityTypeSemanticMetadataEntity(
                    workspaceId = workspaceId,
                    entityTypeId = entityTypeId,
                    targetId = attrId,
                    classification = SemanticAttributeClassification.IDENTIFIER,
                    signalType = MatchSignalType.EMAIL,
                )
            ))

            val attributeIds = service.getIdentifierAttributeIds(entityTypeId)
            val signalTypes = service.getIdentifierSignalTypes(entityTypeId)

            assertEquals(signalTypes.keys, attributeIds)
        }
    }

    // ------ getIdentifierSignalTypes ------

    @Nested
    inner class GetIdentifierSignalTypesTests {

        @Test
        fun `returns EMAIL signal type when signal_type column is EMAIL`() {
            val attrId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001")
            whenever(
                semanticMetadataRepository.findByEntityTypeIdAndTargetType(entityTypeId, SemanticMetadataTargetType.ATTRIBUTE)
            ).thenReturn(listOf(
                IdentityFactory.createEntityTypeSemanticMetadataEntity(
                    workspaceId = workspaceId,
                    entityTypeId = entityTypeId,
                    targetId = attrId,
                    classification = SemanticAttributeClassification.IDENTIFIER,
                    signalType = MatchSignalType.EMAIL,
                )
            ))

            val result = service.getIdentifierSignalTypes(entityTypeId)

            assertEquals(mapOf(attrId to MatchSignalType.EMAIL), result)
        }

        @Test
        fun `returns PHONE signal type when signal_type column is PHONE`() {
            val attrId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001")
            whenever(
                semanticMetadataRepository.findByEntityTypeIdAndTargetType(entityTypeId, SemanticMetadataTargetType.ATTRIBUTE)
            ).thenReturn(listOf(
                IdentityFactory.createEntityTypeSemanticMetadataEntity(
                    workspaceId = workspaceId,
                    entityTypeId = entityTypeId,
                    targetId = attrId,
                    classification = SemanticAttributeClassification.IDENTIFIER,
                    signalType = MatchSignalType.PHONE,
                )
            ))

            val result = service.getIdentifierSignalTypes(entityTypeId)

            assertEquals(mapOf(attrId to MatchSignalType.PHONE), result)
        }

        @Test
        fun `maps CUSTOM column value to CUSTOM_IDENTIFIER enum`() {
            val attrId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001")
            whenever(
                semanticMetadataRepository.findByEntityTypeIdAndTargetType(entityTypeId, SemanticMetadataTargetType.ATTRIBUTE)
            ).thenReturn(listOf(
                IdentityFactory.createEntityTypeSemanticMetadataEntity(
                    workspaceId = workspaceId,
                    entityTypeId = entityTypeId,
                    targetId = attrId,
                    classification = SemanticAttributeClassification.IDENTIFIER,
                    signalType = MatchSignalType.CUSTOM_IDENTIFIER,
                )
            ))

            val result = service.getIdentifierSignalTypes(entityTypeId)

            // "CUSTOM" column value must map to CUSTOM_IDENTIFIER enum (not valueOf("CUSTOM") which throws)
            assertEquals(mapOf(attrId to MatchSignalType.CUSTOM_IDENTIFIER), result)
        }

        @Test
        fun `falls back to CUSTOM_IDENTIFIER when signal_type column is null`() {
            val attrId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001")
            whenever(
                semanticMetadataRepository.findByEntityTypeIdAndTargetType(entityTypeId, SemanticMetadataTargetType.ATTRIBUTE)
            ).thenReturn(listOf(
                IdentityFactory.createEntityTypeSemanticMetadataEntity(
                    workspaceId = workspaceId,
                    entityTypeId = entityTypeId,
                    targetId = attrId,
                    classification = SemanticAttributeClassification.IDENTIFIER,
                    signalType = null,
                )
            ))

            val result = service.getIdentifierSignalTypes(entityTypeId)

            // Null signal_type (pre-existing rows) must fall back to CUSTOM_IDENTIFIER, not throw
            assertEquals(mapOf(attrId to MatchSignalType.CUSTOM_IDENTIFIER), result)
        }

        @Test
        fun `returns map with multiple attributes and correct signal types`() {
            val emailAttrId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001")
            val phoneAttrId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002")
            val customAttrId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003")
            whenever(
                semanticMetadataRepository.findByEntityTypeIdAndTargetType(entityTypeId, SemanticMetadataTargetType.ATTRIBUTE)
            ).thenReturn(listOf(
                IdentityFactory.createEntityTypeSemanticMetadataEntity(
                    workspaceId = workspaceId,
                    entityTypeId = entityTypeId,
                    targetId = emailAttrId,
                    classification = SemanticAttributeClassification.IDENTIFIER,
                    signalType = MatchSignalType.EMAIL,
                ),
                IdentityFactory.createEntityTypeSemanticMetadataEntity(
                    workspaceId = workspaceId,
                    entityTypeId = entityTypeId,
                    targetId = phoneAttrId,
                    classification = SemanticAttributeClassification.IDENTIFIER,
                    signalType = MatchSignalType.PHONE,
                ),
                IdentityFactory.createEntityTypeSemanticMetadataEntity(
                    workspaceId = workspaceId,
                    entityTypeId = entityTypeId,
                    targetId = customAttrId,
                    classification = SemanticAttributeClassification.IDENTIFIER,
                    signalType = MatchSignalType.CUSTOM_IDENTIFIER,
                ),
            ))

            val result = service.getIdentifierSignalTypes(entityTypeId)

            assertEquals(3, result.size)
            assertEquals(MatchSignalType.EMAIL, result[emailAttrId])
            assertEquals(MatchSignalType.PHONE, result[phoneAttrId])
            assertEquals(MatchSignalType.CUSTOM_IDENTIFIER, result[customAttrId])
        }

        @Test
        fun `excludes non-IDENTIFIER attributes from signal types map`() {
            val identifierAttrId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001")
            val categoricalAttrId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002")
            whenever(
                semanticMetadataRepository.findByEntityTypeIdAndTargetType(entityTypeId, SemanticMetadataTargetType.ATTRIBUTE)
            ).thenReturn(listOf(
                IdentityFactory.createEntityTypeSemanticMetadataEntity(
                    workspaceId = workspaceId,
                    entityTypeId = entityTypeId,
                    targetId = identifierAttrId,
                    classification = SemanticAttributeClassification.IDENTIFIER,
                    signalType = MatchSignalType.EMAIL,
                ),
                IdentityFactory.createEntityTypeSemanticMetadataEntity(
                    workspaceId = workspaceId,
                    entityTypeId = entityTypeId,
                    targetId = categoricalAttrId,
                    classification = SemanticAttributeClassification.CATEGORICAL,
                    signalType = null,
                ),
            ))

            val result = service.getIdentifierSignalTypes(entityTypeId)

            assertEquals(1, result.size)
            assertTrue(result.containsKey(identifierAttrId))
            assertFalse(result.containsKey(categoricalAttrId))
        }

        @Test
        fun `cache hit skips repository on second getIdentifierSignalTypes call`() {
            val attrId = UUID.randomUUID()
            whenever(
                semanticMetadataRepository.findByEntityTypeIdAndTargetType(entityTypeId, SemanticMetadataTargetType.ATTRIBUTE)
            ).thenReturn(listOf(
                IdentityFactory.createEntityTypeSemanticMetadataEntity(
                    workspaceId = workspaceId,
                    entityTypeId = entityTypeId,
                    targetId = attrId,
                    classification = SemanticAttributeClassification.IDENTIFIER,
                    signalType = MatchSignalType.EMAIL,
                )
            ))

            // First call — populates cache
            service.getIdentifierSignalTypes(entityTypeId)
            // Second call — should use cache
            service.getIdentifierSignalTypes(entityTypeId)

            verify(semanticMetadataRepository, times(1))
                .findByEntityTypeIdAndTargetType(entityTypeId, SemanticMetadataTargetType.ATTRIBUTE)
        }
    }

    // ------ MatchSignalType.fromColumnValue ------

    @Nested
    inner class FromColumnValueTests {

        @Test
        fun `fromColumnValue maps EMAIL to EMAIL`() {
            assertEquals(MatchSignalType.EMAIL, MatchSignalType.fromColumnValue("EMAIL"))
        }

        @Test
        fun `fromColumnValue maps PHONE to PHONE`() {
            assertEquals(MatchSignalType.PHONE, MatchSignalType.fromColumnValue("PHONE"))
        }

        @Test
        fun `fromColumnValue maps NAME to NAME`() {
            assertEquals(MatchSignalType.NAME, MatchSignalType.fromColumnValue("NAME"))
        }

        @Test
        fun `fromColumnValue maps COMPANY to COMPANY`() {
            assertEquals(MatchSignalType.COMPANY, MatchSignalType.fromColumnValue("COMPANY"))
        }

        @Test
        fun `fromColumnValue maps CUSTOM to CUSTOM_IDENTIFIER`() {
            assertEquals(MatchSignalType.CUSTOM_IDENTIFIER, MatchSignalType.fromColumnValue("CUSTOM"))
        }

        @Test
        fun `fromColumnValue returns null for null input`() {
            assertNull(MatchSignalType.fromColumnValue(null))
        }

        @Test
        fun `fromColumnValue throws for unrecognized non-null value`() {
            assertThrows<IllegalArgumentException> {
                MatchSignalType.fromColumnValue("EMAILL")
            }
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
