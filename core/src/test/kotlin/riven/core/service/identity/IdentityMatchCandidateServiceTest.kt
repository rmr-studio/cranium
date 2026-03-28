package riven.core.service.identity

import io.github.oshai.kotlinlogging.KLogger
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.identity.MatchSignalType
import riven.core.service.util.factory.identity.IdentityFactory
import java.util.UUID

@SpringBootTest(classes = [IdentityMatchCandidateService::class])
class IdentityMatchCandidateServiceTest {

    @MockitoBean
    private lateinit var entityManager: EntityManager

    @MockitoBean
    private lateinit var logger: KLogger

    @Autowired
    private lateinit var identityMatchCandidateService: IdentityMatchCandidateService

    private val triggerEntityId = UUID.fromString("11111111-0000-0000-0000-000000000001")
    private val workspaceId = UUID.fromString("22222222-0000-0000-0000-000000000002")
    private val candidateEntityId = UUID.fromString("33333333-0000-0000-0000-000000000003")
    private val candidateAttributeId = UUID.fromString("44444444-0000-0000-0000-000000000004")

    // ------ Shared mock helpers ------

    /**
     * Creates a mock Query that returns an empty list — represents a trigger entity with no IDENTIFIER attributes.
     */
    private fun emptyQuery(): Query = mock<Query>().also {
        whenever(it.setParameter(any<String>(), any())).thenReturn(it)
        whenever(it.resultList).thenReturn(emptyList<Any>())
    }

    /**
     * Creates a mock Query for trigger identifier attribute lookup.
     *
     * Returns rows for the private queryTriggerIdentifierAttributes method.
     * Each row is Array<Any>: [attributeId, attrValue, schemaType]
     */
    private fun triggerAttributeQuery(rows: List<Array<Any>>): Query = mock<Query>().also {
        whenever(it.setParameter(any<String>(), any())).thenReturn(it)
        whenever(it.resultList).thenReturn(rows)
    }

    /**
     * Creates a mock Query for the candidate similarity search.
     *
     * Each row is Array<Any>: [candidateEntityId, candidateAttributeId, candidateValue, simScore]
     */
    private fun candidateQuery(rows: List<Array<Any>>): Query = mock<Query>().also {
        whenever(it.setParameter(any<String>(), any())).thenReturn(it)
        whenever(it.resultList).thenReturn(rows)
    }

    // ------ findCandidates tests ------

    @Nested
    inner class FindCandidatesTests {

        @Test
        fun `findCandidates returns empty list when trigger entity has no IDENTIFIER attributes`() {
            val emptyTriggerQuery = emptyQuery()
            whenever(entityManager.createNativeQuery(any())).thenReturn(emptyTriggerQuery)

            val result = identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            assertTrue(result.isEmpty(), "Expected empty result when no IDENTIFIER attributes")
            // Only the trigger attribute query should be called — no candidate queries
            verify(entityManager, times(1)).createNativeQuery(any())
        }

        @Test
        fun `findCandidates calls candidate native query once per IDENTIFIER attribute`() {
            val attrId1 = UUID.randomUUID()
            val attrId2 = UUID.randomUUID()

            val triggerRows = listOf(
                arrayOf<Any>(attrId1, "test@example.com", SchemaType.EMAIL.name),
                arrayOf<Any>(attrId2, "555-1234", SchemaType.PHONE.name),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)
            val candidateQ1 = candidateQuery(emptyList())
            val candidateQ2 = candidateQuery(emptyList())

            whenever(entityManager.createNativeQuery(any()))
                .thenReturn(triggerQuery)   // first call: trigger identifier lookup
                .thenReturn(candidateQ1)    // second call: candidate scan for EMAIL
                .thenReturn(candidateQ2)    // third call: candidate scan for PHONE

            identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            // 1 trigger lookup + 2 candidate queries (one per IDENTIFIER attribute)
            verify(entityManager, times(3)).createNativeQuery(any())
        }

        @Test
        fun `findCandidates merges results by (candidateEntityId, signalType) keeping max similarity`() {
            val attrId = UUID.randomUUID()
            val triggerRows = listOf(
                arrayOf<Any>(attrId, "test@example.com", SchemaType.EMAIL.name),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)

            // Two rows for the same candidateEntityId — different attributes, both EMAIL-like
            val candidateAttrId1 = UUID.randomUUID()
            val candidateAttrId2 = UUID.randomUUID()
            val candidateRows = listOf(
                arrayOf<Any>(candidateEntityId.toString(), candidateAttrId1.toString(), "test@example.com", 0.95),
                arrayOf<Any>(candidateEntityId.toString(), candidateAttrId2.toString(), "test+alias@example.com", 0.70),
            )
            val candidateQMock = candidateQuery(candidateRows)

            whenever(entityManager.createNativeQuery(any()))
                .thenReturn(triggerQuery)
                .thenReturn(candidateQMock)

            val result = identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            // Should be merged to one entry for candidateEntityId + EMAIL, keeping max similarity (0.95)
            assertEquals(1, result.size, "Dedup should collapse two rows for same (entityId, signalType)")
            assertEquals(candidateEntityId, result[0].candidateEntityId)
            assertEquals(MatchSignalType.EMAIL, result[0].signalType)
            assertEquals(0.95, result[0].similarityScore, 1e-6)
        }

        @Test
        fun `findCandidates passes workspace ID and excludes trigger entity in query parameters`() {
            val attrId = UUID.randomUUID()
            val triggerRows = listOf(
                arrayOf<Any>(attrId, "test@example.com", SchemaType.EMAIL.name),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)
            val candidateQMock = candidateQuery(emptyList())

            whenever(entityManager.createNativeQuery(any()))
                .thenReturn(triggerQuery)
                .thenReturn(candidateQMock)

            identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            // Verify workspaceId and triggerEntityId params are set on the candidate query mock
            verify(candidateQMock).setParameter("workspaceId", workspaceId)
            verify(candidateQMock).setParameter("triggerEntityId", triggerEntityId)
        }
    }

    // ------ getTriggerAttributes tests ------

    @Nested
    inner class GetTriggerAttributesTests {

        @Test
        fun `getTriggerAttributes returns empty map when entity has no IDENTIFIER attributes`() {
            val emptyTriggerQuery = emptyQuery()
            whenever(entityManager.createNativeQuery(any())).thenReturn(emptyTriggerQuery)

            val result = identityMatchCandidateService.getTriggerAttributes(triggerEntityId, workspaceId)

            assertTrue(result.isEmpty(), "Expected empty map when no IDENTIFIER attributes")
        }

        @Test
        fun `getTriggerAttributes returns correct MatchSignalType to value mapping`() {
            val attrId1 = UUID.randomUUID()
            val attrId2 = UUID.randomUUID()

            val triggerRows = listOf(
                arrayOf<Any>(attrId1, "Test@Example.COM", SchemaType.EMAIL.name),
                arrayOf<Any>(attrId2, "  555-1234  ", SchemaType.PHONE.name),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)
            whenever(entityManager.createNativeQuery(any())).thenReturn(triggerQuery)

            val result = identityMatchCandidateService.getTriggerAttributes(triggerEntityId, workspaceId)

            assertEquals(2, result.size)
            // EMAIL maps from SchemaType.EMAIL, value should be normalized (trim + lowercase)
            assertEquals("test@example.com", result[MatchSignalType.EMAIL])
            assertEquals("555-1234", result[MatchSignalType.PHONE])
        }

        @Test
        fun `getTriggerAttributes normalizes values by trimming and lowercasing`() {
            val attrId = UUID.randomUUID()
            val triggerRows = listOf(
                arrayOf<Any>(attrId, "  JOHN@EXAMPLE.COM  ", SchemaType.EMAIL.name),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)
            whenever(entityManager.createNativeQuery(any())).thenReturn(triggerQuery)

            val result = identityMatchCandidateService.getTriggerAttributes(triggerEntityId, workspaceId)

            assertEquals("john@example.com", result[MatchSignalType.EMAIL])
        }

        @Test
        fun `getTriggerAttributes maps non-EMAIL non-PHONE schema types to CUSTOM_IDENTIFIER`() {
            val attrId = UUID.randomUUID()
            val triggerRows = listOf(
                arrayOf<Any>(attrId, "custom-id-123", SchemaType.TEXT.name),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)
            whenever(entityManager.createNativeQuery(any())).thenReturn(triggerQuery)

            val result = identityMatchCandidateService.getTriggerAttributes(triggerEntityId, workspaceId)

            assertEquals(1, result.size)
            assertEquals("custom-id-123", result[MatchSignalType.CUSTOM_IDENTIFIER])
        }
    }
}
