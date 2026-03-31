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
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.identity.MatchSignalType
import riven.core.enums.identity.MatchSource
import riven.core.service.util.factory.identity.IdentityFactory
import java.util.UUID

@SpringBootTest(classes = [IdentityMatchCandidateService::class, IdentityNormalizationService::class])
class IdentityMatchCandidateServiceTest {

    @MockitoBean
    private lateinit var entityManager: EntityManager

    @MockitoBean
    private lateinit var normalizationService: IdentityNormalizationService

    @MockitoBean
    private lateinit var logger: KLogger

    @Autowired
    private lateinit var identityMatchCandidateService: IdentityMatchCandidateService

    private val triggerEntityId = UUID.fromString("11111111-0000-0000-0000-000000000001")
    private val workspaceId = UUID.fromString("22222222-0000-0000-0000-000000000002")
    private val candidateEntityId = UUID.fromString("33333333-0000-0000-0000-000000000003")
    private val candidateAttributeId = UUID.fromString("44444444-0000-0000-0000-000000000004")

    @BeforeEach
    fun setupNormalizationDefault() {
        // By default, normalization returns trimmed lowercase — preserves existing test behavior
        whenever(normalizationService.normalize(any(), any())).thenAnswer { invocation ->
            invocation.arguments[0].toString().trim().lowercase()
        }
    }

    // ------ Shared mock helpers ------

    /**
     * Creates a mock Query that returns an empty list — represents a trigger entity with no IDENTIFIER attributes.
     */
    private fun emptyQuery(): Query = mock<Query>().also {
        whenever(it.setParameter(any<String>(), any())).thenReturn(it)
        whenever(it.resultList).thenReturn(emptyList<Any>())
    }

    /**
     * Creates a mock Query for a dmetaphone scalar call (`SELECT dmetaphone(:token)`).
     *
     * Returns [phoneticCode] from singleResult. Pass an empty string to simulate a token
     * that produces no phonetic code (triggers early return in computePhoneticCodes).
     */
    private fun dmetaphoneScalarQuery(phoneticCode: String): Query = mock<Query>().also {
        whenever(it.setParameter(any<String>(), any())).thenReturn(it)
        whenever(it.singleResult).thenReturn(phoneticCode)
    }

    /**
     * Creates a mock Query for trigger identifier attribute lookup.
     *
     * Returns rows for the private queryTriggerIdentifierAttributes method.
     * Each row is Array<Any?>: [attributeId, attrValue, schemaType, signalType?]
     * signalType defaults to null when not provided (backward compat with older test data).
     */
    private fun triggerAttributeQuery(rows: List<Array<Any?>>): Query = mock<Query>().also {
        whenever(it.setParameter(any<String>(), any())).thenReturn(it)
        whenever(it.resultList).thenReturn(rows)
    }

    /**
     * Creates a mock Query for the candidate similarity search.
     *
     * Each row is Array<Any?>: [candidateEntityId, candidateAttributeId, candidateValue, simScore, candidateSignalType]
     * candidateSignalType defaults to null (no signal_type stored on metadata row).
     */
    private fun candidateQuery(rows: List<Array<Any?>>): Query = mock<Query>().also {
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
        fun `findCandidates calls candidate native query once per non-phone IDENTIFIER attribute`() {
            val attrId1 = UUID.randomUUID()
            val triggerRows = listOf(
                arrayOf<Any?>(attrId1, "test@example.com", SchemaType.EMAIL.name, null),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)
            val candidateQ1 = candidateQuery(emptyList())
            val emailDomainQ = candidateQuery(emptyList())

            whenever(entityManager.createNativeQuery(any()))
                .thenReturn(triggerQuery)   // first call: trigger identifier lookup
                .thenReturn(candidateQ1)    // second call: trigram candidate scan for EMAIL
                .thenReturn(emailDomainQ)   // third call: email domain candidate scan for example.com

            identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            // 1 trigger lookup + 1 trigram query + 1 email domain query for corporate EMAIL
            verify(entityManager, times(3)).createNativeQuery(any())
        }

        @Test
        fun `findCandidates calls both trigram and exact-digits queries for PHONE signals`() {
            val attrId = UUID.randomUUID()
            val triggerRows = listOf(
                arrayOf<Any?>(attrId, "555-1234", SchemaType.PHONE.name, null),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)
            val trigramQ = candidateQuery(emptyList())
            val exactDigitsQ = candidateQuery(emptyList())

            whenever(entityManager.createNativeQuery(any()))
                .thenReturn(triggerQuery)   // first call: trigger identifier lookup
                .thenReturn(trigramQ)       // second call: trigram candidate scan for PHONE
                .thenReturn(exactDigitsQ)   // third call: exact-digits candidate scan for PHONE

            identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            // 1 trigger lookup + 1 trigram query + 1 exact-digits query for PHONE
            verify(entityManager, times(3)).createNativeQuery(any())
        }

        @Test
        fun `findCandidates deduplicates trigram and exact-digits results for same entityId and attributeId keeping higher score`() {
            /**
             * Bug: trigram and exact-digits queries for PHONE may return the same (entity, attribute) pair.
             * Fix: mergeCandidates groups by (entityId, attributeId) and keeps the highest score.
             * This test verifies that exact-digits (score=1.0) wins over trigram (score=0.7) for overlapping results.
             */
            val attrId = UUID.randomUUID()
            val triggerRows = listOf(
                arrayOf<Any?>(attrId, "5551234567", SchemaType.PHONE.name, null),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)

            // Trigram query returns a candidate at score 0.7
            val trigramRows = listOf(
                arrayOf<Any?>(candidateEntityId.toString(), candidateAttributeId.toString(), "(555) 123-4567", 0.7, null),
            )
            val trigramQ = candidateQuery(trigramRows)

            // Exact-digits query returns the SAME candidate at score 1.0
            val exactDigitsRows = listOf(
                arrayOf<Any?>(candidateEntityId.toString(), candidateAttributeId.toString(), "(555) 123-4567", 1.0, null),
            )
            val exactDigitsQ = candidateQuery(exactDigitsRows)

            whenever(entityManager.createNativeQuery(any()))
                .thenReturn(triggerQuery)
                .thenReturn(trigramQ)
                .thenReturn(exactDigitsQ)

            val result = identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            // Dedup: overlapping (entityId, attributeId) collapses to one row, keeping score=1.0
            assertEquals(1, result.size, "Overlapping trigram+exact-digits should collapse to one row")
            assertEquals(candidateEntityId, result[0].candidateEntityId)
            assertEquals(1.0, result[0].similarityScore, 1e-6, "Exact-digits score (1.0) should win over trigram (0.7)")
        }

        @Test
        fun `findCandidates phone union includes non-overlapping results from both queries`() {
            val attrId = UUID.randomUUID()
            val triggerRows = listOf(
                arrayOf<Any?>(attrId, "5551234567", SchemaType.PHONE.name, null),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)

            val entityFromTrigram = UUID.randomUUID()
            val attrFromTrigram = UUID.randomUUID()
            val entityFromExactDigits = UUID.randomUUID()
            val attrFromExactDigits = UUID.randomUUID()

            // Trigram returns one entity, exact-digits returns a DIFFERENT entity
            val trigramRows = listOf(
                arrayOf<Any?>(entityFromTrigram.toString(), attrFromTrigram.toString(), "555-1234567", 0.85, null),
            )
            val exactDigitsRows = listOf(
                arrayOf<Any?>(entityFromExactDigits.toString(), attrFromExactDigits.toString(), "(555) 123-4567", 1.0, null),
            )

            whenever(entityManager.createNativeQuery(any()))
                .thenReturn(triggerQuery)
                .thenReturn(candidateQuery(trigramRows))
                .thenReturn(candidateQuery(exactDigitsRows))

            val result = identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            // Both non-overlapping results should be present
            assertEquals(2, result.size, "Non-overlapping results from trigram and exact-digits should both appear")
            val resultEntityIds = result.map { it.candidateEntityId }.toSet()
            assertTrue(resultEntityIds.contains(entityFromTrigram))
            assertTrue(resultEntityIds.contains(entityFromExactDigits))
        }

        @Test
        fun `findCandidates preserves multi-attribute rows for same candidate entity`() {
            /**
             * Bug: original DISTINCT ON sql collapsed all attributes for the same candidate entity to one row.
             * Fix: DISTINCT ON removed from SQL; mergeCandidates groups by (entityId, attributeId) not (entityId, signalType).
             * This test verifies that a candidate with two matching IDENTIFIER attributes produces two result rows.
             */
            val attrId = UUID.randomUUID()
            val triggerRows = listOf(
                arrayOf<Any?>(attrId, "test@example.com", SchemaType.EMAIL.name, null),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)

            // Two rows for the same candidateEntityId — DIFFERENT attribute IDs
            val candidateAttrId1 = UUID.randomUUID()
            val candidateAttrId2 = UUID.randomUUID()
            val candidateRows = listOf(
                arrayOf<Any?>(candidateEntityId.toString(), candidateAttrId1.toString(), "test@example.com", 0.95, null),
                arrayOf<Any?>(candidateEntityId.toString(), candidateAttrId2.toString(), "test+alias@example.com", 0.70, null),
            )
            val candidateQMock = candidateQuery(candidateRows)
            val emailDomainQ = candidateQuery(emptyList())

            whenever(entityManager.createNativeQuery(any()))
                .thenReturn(triggerQuery)
                .thenReturn(candidateQMock)
                .thenReturn(emailDomainQ)   // email domain scan for example.com (corporate domain)

            val result = identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            // Two different attributes on same entity: should produce 2 rows (not collapsed)
            assertEquals(2, result.size, "Different attributeIds for the same entity should produce two candidate rows")
            val scores = result.map { it.similarityScore }.toSet()
            assertTrue(scores.contains(0.95), "Row with score 0.95 should be present")
            assertTrue(scores.contains(0.70), "Row with score 0.70 should be present")
        }

        @Test
        fun `findCandidates deduplicates rows with same entityId AND same attributeId keeping max similarity`() {
            val attrId = UUID.randomUUID()
            val triggerRows = listOf(
                arrayOf<Any?>(attrId, "test@example.com", SchemaType.EMAIL.name, null),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)

            // Two rows for the same (entityId, attributeId) pair — same attribute, different scores
            val candidateRows = listOf(
                arrayOf<Any?>(candidateEntityId.toString(), candidateAttributeId.toString(), "test@example.com", 0.95, null),
                arrayOf<Any?>(candidateEntityId.toString(), candidateAttributeId.toString(), "test@example.com", 0.70, null),
            )
            val candidateQMock = candidateQuery(candidateRows)
            val emailDomainQ = candidateQuery(emptyList())

            whenever(entityManager.createNativeQuery(any()))
                .thenReturn(triggerQuery)
                .thenReturn(candidateQMock)
                .thenReturn(emailDomainQ)   // email domain scan for example.com (corporate domain)

            val result = identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            // Same (entityId, attributeId): should collapse to one row keeping max score
            assertEquals(1, result.size, "Same (entityId, attributeId) should deduplicate to one row")
            assertEquals(0.95, result[0].similarityScore, 1e-6, "Should keep max similarity score")
        }

        @Test
        fun `findCandidates passes workspace ID and excludes trigger entity in query parameters`() {
            val attrId = UUID.randomUUID()
            val triggerRows = listOf(
                arrayOf<Any?>(attrId, "test@example.com", SchemaType.EMAIL.name, null),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)
            val candidateQMock = candidateQuery(emptyList())
            val emailDomainQ = candidateQuery(emptyList())

            whenever(entityManager.createNativeQuery(any()))
                .thenReturn(triggerQuery)
                .thenReturn(candidateQMock)
                .thenReturn(emailDomainQ)   // email domain scan for example.com (corporate domain)

            identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            // Verify workspaceId and triggerEntityId params are set on the candidate query mock
            verify(candidateQMock).setParameter("workspaceId", workspaceId)
            verify(candidateQMock).setParameter("triggerEntityId", triggerEntityId)
        }

        @Test
        fun `findCandidates uses normalizationService for all value normalization`() {
            val attrId = UUID.randomUUID()
            val rawEmail = "TEST@EXAMPLE.COM"
            val triggerRows = listOf(
                arrayOf<Any?>(attrId, rawEmail, SchemaType.EMAIL.name, null),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)
            val candidateQMock = candidateQuery(emptyList())
            val emailDomainQ = candidateQuery(emptyList())

            whenever(entityManager.createNativeQuery(any()))
                .thenReturn(triggerQuery)
                .thenReturn(candidateQMock)
                .thenReturn(emailDomainQ)   // email domain scan for example.com (corporate domain)

            identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            // normalizationService.normalize should have been called with the raw value and EMAIL signal type
            verify(normalizationService).normalize(rawEmail, MatchSignalType.EMAIL)
        }

        @Test
        fun `findCandidates calls trigram query AND nickname query for NAME signals`() {
            /**
             * For NAME signals, findCandidates should run both the trigram candidate query and
             * the nickname expansion query. The trigger value "william smith" has known nickname
             * variants (bill, will, etc.) so findNicknameCandidates should invoke createNativeQuery.
             * After Phase 5, NAME signals also invoke computePhoneticCodes (2 scalar dmetaphone queries
             * for "william" and "smith"). Both scalar queries return null (no phonetic code mocked),
             * so phoneticCodes is empty and findPhoneticCandidates returns early — no phonetic SQL query.
             * Total calls: 1 trigger lookup + 1 trigram + 1 nickname + 2 dmetaphone scalars = 5.
             */
            val attrId = UUID.randomUUID()
            // signal_type="NAME" in semantic metadata drives NAME signal dispatch (SchemaType.TEXT has no NAME mapping)
            val triggerRows = listOf(
                arrayOf<Any?>(attrId, "william smith", SchemaType.TEXT.name, "NAME"),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)
            val trigramQ = candidateQuery(emptyList())
            val nicknameQ = candidateQuery(emptyList())
            // dmetaphone scalars — no return needed for singleResult (Mockito returns null by default),
            // so phoneticCodes is empty and no phonetic SQL query fires.
            val dmetaphoneQ1 = dmetaphoneScalarQuery("")
            val dmetaphoneQ2 = dmetaphoneScalarQuery("")

            whenever(normalizationService.normalize(any(), any())).thenReturn("william smith")
            whenever(entityManager.createNativeQuery(any()))
                .thenReturn(triggerQuery)   // first: trigger identifier lookup
                .thenReturn(trigramQ)       // second: trigram candidate scan for NAME
                .thenReturn(nicknameQ)      // third: nickname expansion scan for NAME
                .thenReturn(dmetaphoneQ1)   // fourth: dmetaphone scalar for "william"
                .thenReturn(dmetaphoneQ2)   // fifth: dmetaphone scalar for "smith"

            identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            // 1 trigger lookup + 1 trigram + 1 nickname + 2 dmetaphone scalars (no phonetic SQL — empty codes)
            verify(entityManager, times(5)).createNativeQuery(any())
        }

        @Test
        fun `findCandidates does NOT call nickname query for EMAIL signals`() {
            /**
             * Nickname expansion only applies to NAME signals. EMAIL signals trigger the trigram
             * query and the email domain query (for corporate domains), but never the nickname query.
             * Total: 1 trigger lookup + 1 trigram + 1 email domain = 3 createNativeQuery calls.
             * Using a corporate domain (example.com) to exercise the email domain path.
             */
            val attrId = UUID.randomUUID()
            val triggerRows = listOf(
                arrayOf<Any?>(attrId, "test@example.com", SchemaType.EMAIL.name, null),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)
            val trigramQ = candidateQuery(emptyList())
            val emailDomainQ = candidateQuery(emptyList())

            whenever(entityManager.createNativeQuery(any()))
                .thenReturn(triggerQuery)
                .thenReturn(trigramQ)
                .thenReturn(emailDomainQ)   // email domain scan for example.com (corporate domain)

            identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            // 3 calls: trigger lookup + trigram + email domain. No nickname query (that's NAME-only).
            verify(entityManager, times(3)).createNativeQuery(any())
        }

        @Test
        fun `findCandidates does NOT call email domain query for free email domains`() {
            /**
             * The free-domain guard in the EMAIL branch must skip findEmailDomainCandidates when
             * the extracted domain is in the FREE_EMAIL_DOMAINS set (e.g. gmail.com).
             * Total calls: 1 trigger lookup + 1 trigram = 2 createNativeQuery calls (no email domain query).
             */
            val attrId = UUID.randomUUID()
            val triggerRows = listOf(
                arrayOf<Any?>(attrId, "test@gmail.com", SchemaType.EMAIL.name, null),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)
            val trigramQ = candidateQuery(emptyList())

            whenever(normalizationService.normalize(any(), any())).thenReturn("test@gmail.com")
            whenever(entityManager.createNativeQuery(any()))
                .thenReturn(triggerQuery)
                .thenReturn(trigramQ)

            identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            // Only 2: trigger lookup + trigram. No email domain query for free domain.
            verify(entityManager, times(2)).createNativeQuery(any())
        }

        @Test
        fun `findCandidates calls email domain query for corporate EMAIL signals`() {
            /**
             * For EMAIL signals with a corporate domain (not in the free-domain set), findCandidates
             * must also invoke findEmailDomainCandidates. Total: 1 trigger + 1 trigram + 1 email domain = 3.
             */
            val attrId = UUID.randomUUID()
            val triggerRows = listOf(
                arrayOf<Any?>(attrId, "alice@acme.com", SchemaType.EMAIL.name, null),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)
            val trigramQ = candidateQuery(emptyList())
            val emailDomainQ = candidateQuery(emptyList())

            whenever(normalizationService.normalize(any(), any())).thenReturn("alice@acme.com")
            whenever(entityManager.createNativeQuery(any()))
                .thenReturn(triggerQuery)
                .thenReturn(trigramQ)
                .thenReturn(emailDomainQ)   // email domain scan for acme.com

            identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            // 3 calls: trigger lookup + trigram + email domain scan.
            verify(entityManager, times(3)).createNativeQuery(any())
        }

        @Test
        fun `findCandidates skips nickname query when trigger name has no known nickname variants`() {
            /**
             * For NAME signals where no token maps to a known nickname group (e.g. a unique
             * custom name), findNicknameCandidates should return early without calling createNativeQuery.
             * "zzunknownname" is not in any nickname group, so variants = {"zzunknownname"} only,
             * but because NicknameExpander.expand returns empty for unknown names, tokens
             * expand to just the original token. The IN-clause will still run (original token is included).
             *
             * This test verifies that for a single-token name "zzunknownname", the nickname SQL
             * IS still executed because variants = {"zzunknownname"} (non-empty set).
             *
             * After Phase 5, NAME signals also invoke computePhoneticCodes (1 scalar dmetaphone query
             * for "zzunknownname"). The scalar returns empty string so phoneticCodes is empty and
             * findPhoneticCandidates returns early — no phonetic SQL query.
             * Expected calls: 1 trigger + 1 trigram + 1 nickname + 1 dmetaphone scalar = 4 total.
             */
            val attrId = UUID.randomUUID()
            val triggerRows = listOf(
                arrayOf<Any?>(attrId, "zzunknownname", SchemaType.TEXT.name, "NAME"),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)
            val trigramQ = candidateQuery(emptyList())
            val nicknameQ = candidateQuery(emptyList())
            val dmetaphoneQ = dmetaphoneScalarQuery("")  // single token, returns empty code

            whenever(normalizationService.normalize(any(), any())).thenReturn("zzunknownname")
            whenever(entityManager.createNativeQuery(any()))
                .thenReturn(triggerQuery)
                .thenReturn(trigramQ)
                .thenReturn(nicknameQ)
                .thenReturn(dmetaphoneQ)   // dmetaphone scalar for "zzunknownname"

            identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            // 4 calls: trigger + trigram + nickname + 1 dmetaphone scalar (no phonetic SQL — empty code)
            verify(entityManager, times(4)).createNativeQuery(any())
        }

        @Test
        fun `findCandidates re-scores NAME trigram candidates using token overlap when higher`() {
            /**
             * For NAME signals, trigram candidate scores are re-scored with token overlap.
             * When token overlap is higher than the trigram score, the final score is updated.
             * "john smith" vs "john smith" → token overlap = 1.0, should win over trigram score 0.7.
             */
            val attrId = UUID.randomUUID()
            val triggerRows = listOf(
                arrayOf<Any?>(attrId, "john smith", SchemaType.TEXT.name, "NAME"),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)

            // Trigram returns a candidate at score 0.7, but token overlap with "john smith" is 1.0
            val trigramRows = listOf(
                arrayOf<Any?>(candidateEntityId.toString(), candidateAttributeId.toString(), "john smith", 0.7, null),
            )
            val trigramQ = candidateQuery(trigramRows)
            val nicknameQ = candidateQuery(emptyList())
            // dmetaphone scalar queries for "john" and "smith" — empty codes → no phonetic SQL query
            val dmetaphoneQ1 = dmetaphoneScalarQuery("")
            val dmetaphoneQ2 = dmetaphoneScalarQuery("")

            whenever(normalizationService.normalize(any(), any())).thenReturn("john smith")
            whenever(entityManager.createNativeQuery(any()))
                .thenReturn(triggerQuery)
                .thenReturn(trigramQ)
                .thenReturn(nicknameQ)
                .thenReturn(dmetaphoneQ1)
                .thenReturn(dmetaphoneQ2)

            val result = identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            assertEquals(1, result.size)
            // Token overlap = 1.0 > trigram 0.7 — final score should be 1.0
            assertEquals(1.0, result[0].similarityScore, 1e-6, "Token overlap (1.0) should win over trigram score (0.7)")
        }

        @Test
        fun `findCandidates keeps trigram score when it is higher than token overlap`() {
            /**
             * When trigram score exceeds token overlap, the original score is preserved.
             * Trigram may have high score for partial character matches where token overlap is lower.
             */
            val attrId = UUID.randomUUID()
            val triggerRows = listOf(
                arrayOf<Any?>(attrId, "jon", SchemaType.TEXT.name, "NAME"),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)

            // Candidate "jonathan" has trigram score 0.8, but token overlap with "jon" is 0.0 (no shared token)
            val trigramRows = listOf(
                arrayOf<Any?>(candidateEntityId.toString(), candidateAttributeId.toString(), "jonathan", 0.8, null),
            )
            val trigramQ = candidateQuery(trigramRows)
            val nicknameQ = candidateQuery(emptyList())
            // dmetaphone scalar for "jon" — empty code → no phonetic SQL query
            val dmetaphoneQ = dmetaphoneScalarQuery("")

            whenever(normalizationService.normalize(any(), any())).thenReturn("jon")
            whenever(entityManager.createNativeQuery(any()))
                .thenReturn(triggerQuery)
                .thenReturn(trigramQ)
                .thenReturn(nicknameQ)
                .thenReturn(dmetaphoneQ)

            val result = identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            assertEquals(1, result.size)
            // Token overlap = 0.0 (no shared token "jon" != "jonathan") — trigram 0.8 should win
            assertEquals(0.8, result[0].similarityScore, 1e-6, "Trigram score (0.8) should be preserved when higher than token overlap")
        }
    }

    // ------ mergeCandidates tie-breaking tests ------

    @Nested
    inner class MergeCandidatesTiebreakerTests {

        @Test
        fun `mergeCandidates prefers NICKNAME matchSource over TRIGRAM on equal similarity score`() {
            /**
             * When two candidates have the same (entityId, attributeId) and equal similarity scores,
             * NICKNAME matchSource should be preferred over TRIGRAM for audit trail quality.
             * This is the thenBy tie-breaker: NICKNAME (value=1) > TRIGRAM (value=0).
             */
            val attrId = UUID.randomUUID()
            val triggerRows = listOf(
                arrayOf<Any?>(attrId, "william smith", SchemaType.TEXT.name, "NAME"),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)

            // Trigram returns a candidate at score 0.95
            val trigramRows = listOf(
                arrayOf<Any?>(candidateEntityId.toString(), candidateAttributeId.toString(), "bill smith", 0.95, null),
            )
            val trigramQ = candidateQuery(trigramRows)

            // Nickname query returns the SAME (entityId, attributeId) at score 0.95 — same score, NICKNAME source
            val nicknameRows = listOf(
                arrayOf<Any?>(candidateEntityId.toString(), candidateAttributeId.toString(), "bill smith", 0.95, null),
            )
            val nicknameQ = candidateQuery(nicknameRows)
            // dmetaphone scalars for "william" and "smith" — empty codes → no phonetic SQL query
            val dmetaphoneQ1 = dmetaphoneScalarQuery("")
            val dmetaphoneQ2 = dmetaphoneScalarQuery("")

            whenever(normalizationService.normalize(any(), any())).thenReturn("william smith")
            whenever(entityManager.createNativeQuery(any()))
                .thenReturn(triggerQuery)
                .thenReturn(trigramQ)
                .thenReturn(nicknameQ)
                .thenReturn(dmetaphoneQ1)
                .thenReturn(dmetaphoneQ2)

            val result = identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            // Should deduplicate to one row — NICKNAME source preferred
            assertEquals(1, result.size, "Equal-score candidates should deduplicate to one row")
            assertEquals(MatchSource.NICKNAME, result[0].matchSource, "NICKNAME should be preferred over TRIGRAM on tie")
        }

        @Test
        fun `mergeCandidates prefers EMAIL_DOMAIN matchSource over TRIGRAM on equal similarity score`() {
            /**
             * EMAIL_DOMAIN matchSource should be preferred over TRIGRAM when deduplicating the same
             * (entityId, attributeId) pair on equal similarity score — same tiebreaker logic as NICKNAME.
             * This ensures the audit trail reflects the domain-aware strategy over a raw trigram match.
             *
             * The trigger "a.smith@acme.com" and candidate "alice.smith@acme.com" share domain "acme.com"
             * and local-part tokens ["smith"] — overlap coefficient = 1/2 = 0.5, at the threshold.
             * The trigram query also returns the same candidate at the same score (0.5), triggering
             * the EMAIL_DOMAIN vs TRIGRAM tiebreaker.
             *
             * Note: findEmailDomainCandidates returns 4-column rows [entityId, attributeId, value, signalType]
             * (no SQL sim_score column — similarity is computed Kotlin-side from local-part overlap).
             */
            val attrId = UUID.randomUUID()
            // "a.smith@acme.com" tokens: ["a", "smith"] vs "alice.smith@acme.com" tokens: ["alice", "smith"]
            // overlap = |{"smith"}| / min(2, 2) = 0.5 — exactly at threshold, so candidate is included
            val triggerEmail = "a.smith@acme.com"
            val candidateEmail = "alice.smith@acme.com"
            val triggerRows = listOf(
                arrayOf<Any?>(attrId, triggerEmail, SchemaType.EMAIL.name, null),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)

            // Trigram returns the candidate at score 0.5 (same as email domain overlap score below)
            val trigramRows = listOf(
                arrayOf<Any?>(candidateEntityId.toString(), candidateAttributeId.toString(), candidateEmail, 0.5, null),
            )
            val trigramQ = candidateQuery(trigramRows)

            // Email domain query returns 4-column rows [entityId, attributeId, value, signalType]
            // The Kotlin-side localPartSimilarity("a.smith", "alice.smith") = 0.5 (shared token "smith")
            val emailDomainRows: List<Array<Any?>> = listOf(
                arrayOf(candidateEntityId.toString(), candidateAttributeId.toString(), candidateEmail, null),
            )
            val emailDomainQ = mock<Query>().also {
                whenever(it.setParameter(any<String>(), any())).thenReturn(it)
                whenever(it.resultList).thenReturn(emailDomainRows)
            }

            whenever(normalizationService.normalize(any(), any())).thenReturn(triggerEmail)
            whenever(entityManager.createNativeQuery(any()))
                .thenReturn(triggerQuery)
                .thenReturn(trigramQ)
                .thenReturn(emailDomainQ)

            val result = identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            // Should deduplicate to one row — EMAIL_DOMAIN source preferred over TRIGRAM on tie
            assertEquals(1, result.size, "Equal-score candidates should deduplicate to one row")
            assertEquals(MatchSource.EMAIL_DOMAIN, result[0].matchSource, "EMAIL_DOMAIN should be preferred over TRIGRAM on tie")
        }

        @Test
        fun `mergeCandidates keeps higher score regardless of matchSource`() {
            /**
             * When a TRIGRAM candidate has a higher score than a NICKNAME candidate for the
             * same (entityId, attributeId), the TRIGRAM candidate should win despite the tie-breaker.
             * Score comparison takes precedence over matchSource preference.
             */
            val attrId = UUID.randomUUID()
            val triggerRows = listOf(
                arrayOf<Any?>(attrId, "william smith", SchemaType.TEXT.name, "NAME"),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)

            // Trigram returns a candidate at score 0.99 (higher)
            val trigramRows = listOf(
                arrayOf<Any?>(candidateEntityId.toString(), candidateAttributeId.toString(), "bill smith", 0.99, null),
            )
            val trigramQ = candidateQuery(trigramRows)

            // Nickname query returns SAME candidate at score 0.95 (lower)
            val nicknameRows = listOf(
                arrayOf<Any?>(candidateEntityId.toString(), candidateAttributeId.toString(), "bill smith", 0.95, null),
            )
            val nicknameQ = candidateQuery(nicknameRows)
            // dmetaphone scalars for "william" and "smith" — empty codes → no phonetic SQL query
            val dmetaphoneQ1 = dmetaphoneScalarQuery("")
            val dmetaphoneQ2 = dmetaphoneScalarQuery("")

            whenever(normalizationService.normalize(any(), any())).thenReturn("william smith")
            whenever(entityManager.createNativeQuery(any()))
                .thenReturn(triggerQuery)
                .thenReturn(trigramQ)
                .thenReturn(nicknameQ)
                .thenReturn(dmetaphoneQ1)
                .thenReturn(dmetaphoneQ2)

            val result = identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            assertEquals(1, result.size, "Should deduplicate to one row")
            assertEquals(0.99, result[0].similarityScore, 1e-6, "Higher score (0.99 TRIGRAM) should win")
        }
    }

    // ------ findPhoneticCandidates tests ------

    @Nested
    inner class FindPhoneticCandidatesTests {

        @Test
        fun `findPhoneticCandidates returns PHONETIC candidates when dmetaphone codes match`() {
            /**
             * For a NAME signal with trigger value "smith" (one token, length >= 2), computePhoneticCodes
             * issues one dmetaphone scalar query returning "SM0". findPhoneticCandidates then issues the
             * phonetic SQL query. The result should contain a candidate with matchSource=PHONETIC
             * and similarityScore=0.85 (the fixed phonetic score).
             *
             * Total calls: 1 trigger + 1 trigram + 1 nickname + 1 dmetaphone scalar + 1 phonetic SQL = 5.
             */
            val attrId = UUID.randomUUID()
            val triggerRows = listOf(
                arrayOf<Any?>(attrId, "smith", SchemaType.TEXT.name, "NAME"),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)
            val trigramQ = candidateQuery(emptyList())
            val nicknameQ = candidateQuery(emptyList())
            val dmetaphoneQ = dmetaphoneScalarQuery("SM0")  // "smith" → dmetaphone code "SM0"
            // Phonetic SQL returns one candidate row (e.g. "smythe" — phonetically equivalent to "smith")
            val phoneticRows = listOf(
                arrayOf<Any?>(candidateEntityId.toString(), candidateAttributeId.toString(), "smythe", 0.85, null),
            )
            val phoneticQ = candidateQuery(phoneticRows)

            whenever(normalizationService.normalize(any(), any())).thenReturn("smith")
            whenever(entityManager.createNativeQuery(any()))
                .thenReturn(triggerQuery)
                .thenReturn(trigramQ)
                .thenReturn(nicknameQ)
                .thenReturn(dmetaphoneQ)   // dmetaphone scalar for "smith"
                .thenReturn(phoneticQ)     // phonetic candidate SQL

            val result = identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            assertEquals(1, result.size, "Phonetic query should return one candidate")
            assertEquals(candidateEntityId, result[0].candidateEntityId)
            assertEquals(MatchSource.PHONETIC, result[0].matchSource, "matchSource should be PHONETIC")
            assertEquals(0.85, result[0].similarityScore, 1e-6, "Phonetic candidates have fixed score 0.85")
            verify(entityManager, times(5)).createNativeQuery(any())
        }

        @Test
        fun `findPhoneticCandidates returns empty list when all tokens are shorter than 2 chars`() {
            /**
             * Tokens "a" and "b" both have length 1, which is below the minimum length threshold.
             * computePhoneticCodes filters them out before issuing any dmetaphone scalar queries —
             * phoneticCodes is empty immediately (no JDBC calls at all).
             * findPhoneticCandidates returns early with emptyList().
             *
             * Total calls: 1 trigger + 1 trigram + 1 nickname = 3 (no dmetaphone scalars, no phonetic SQL).
             */
            val attrId = UUID.randomUUID()
            val triggerRows = listOf(
                arrayOf<Any?>(attrId, "a b", SchemaType.TEXT.name, "NAME"),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)
            val trigramQ = candidateQuery(emptyList())
            val nicknameQ = candidateQuery(emptyList())

            whenever(normalizationService.normalize(any(), any())).thenReturn("a b")
            whenever(entityManager.createNativeQuery(any()))
                .thenReturn(triggerQuery)
                .thenReturn(trigramQ)
                .thenReturn(nicknameQ)

            val result = identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            assertTrue(result.isEmpty(), "No results expected for short-token trigger value")
            // Exactly 3 calls: trigger lookup + trigram + nickname. No dmetaphone or phonetic SQL.
            verify(entityManager, times(3)).createNativeQuery(any())
        }

        @Test
        fun `findPhoneticCandidates returns empty list when dmetaphone codes are all empty`() {
            /**
             * Token "zz" has length >= 2 so computePhoneticCodes issues the dmetaphone scalar query,
             * but the query returns "" (empty string). takeIf { it.isNotEmpty() } filters it out,
             * so phoneticCodes is empty. findPhoneticCandidates early-returns without issuing the
             * phonetic SQL query — prevents the empty-collection SQL parameter error.
             *
             * Total calls: 1 trigger + 1 trigram + 1 nickname + 1 dmetaphone scalar = 4.
             * No phonetic SQL query (empty phoneticCodes early return).
             */
            val attrId = UUID.randomUUID()
            val triggerRows = listOf(
                arrayOf<Any?>(attrId, "zz", SchemaType.TEXT.name, "NAME"),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)
            val trigramQ = candidateQuery(emptyList())
            val nicknameQ = candidateQuery(emptyList())
            val dmetaphoneQ = dmetaphoneScalarQuery("")  // empty code → phoneticCodes empty

            whenever(normalizationService.normalize(any(), any())).thenReturn("zz")
            whenever(entityManager.createNativeQuery(any()))
                .thenReturn(triggerQuery)
                .thenReturn(trigramQ)
                .thenReturn(nicknameQ)
                .thenReturn(dmetaphoneQ)

            val result = identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            assertTrue(result.isEmpty(), "No phonetic results expected when dmetaphone code is empty")
            // 4 calls: trigger + trigram + nickname + 1 dmetaphone scalar. No phonetic SQL.
            verify(entityManager, times(4)).createNativeQuery(any())
        }

        @Test
        fun `mergeCandidates tiebreaker prefers PHONETIC over TRIGRAM on equal similarity score`() {
            /**
             * When a candidate matches via both PHONETIC (score=0.85) and TRIGRAM (score=0.85)
             * for the same (entityId, attributeId), PHONETIC should win the tiebreaker.
             * Tiebreaker ordering: PHONETIC (3) > TRIGRAM (0).
             *
             * Setup: trigger "smith", dmetaphone scalar returns "SM0". Trigram query returns "smythe"
             * at score 0.85. Token overlap of "smith" vs "smythe" is 0.0 (no shared token), so
             * re-scoring does not change the trigram score. Phonetic SQL also returns "smythe" at 0.85.
             * Both arrive at dedup with equal score 0.85 — PHONETIC tiebreaker wins.
             */
            val attrId = UUID.randomUUID()
            val triggerRows = listOf(
                arrayOf<Any?>(attrId, "smith", SchemaType.TEXT.name, "NAME"),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)

            // Trigram returns "smythe" at score 0.85 — token overlap with trigger "smith" is 0.0 (no shared token)
            // so re-scoring leaves it at 0.85, matching the phonetic score
            val trigramRows = listOf(
                arrayOf<Any?>(candidateEntityId.toString(), candidateAttributeId.toString(), "smythe", 0.85, null),
            )
            val trigramQ = candidateQuery(trigramRows)
            val nicknameQ = candidateQuery(emptyList())
            val dmetaphoneQ = dmetaphoneScalarQuery("SM0")
            // Phonetic SQL returns same (entityId, attributeId) at the fixed phonetic score 0.85
            val phoneticRows = listOf(
                arrayOf<Any?>(candidateEntityId.toString(), candidateAttributeId.toString(), "smythe", 0.85, null),
            )
            val phoneticQ = candidateQuery(phoneticRows)

            whenever(normalizationService.normalize(any(), any())).thenReturn("smith")
            whenever(entityManager.createNativeQuery(any()))
                .thenReturn(triggerQuery)
                .thenReturn(trigramQ)
                .thenReturn(nicknameQ)
                .thenReturn(dmetaphoneQ)
                .thenReturn(phoneticQ)

            val result = identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            assertEquals(1, result.size, "Equal-score PHONETIC and TRIGRAM should deduplicate to one row")
            assertEquals(MatchSource.PHONETIC, result[0].matchSource, "PHONETIC should be preferred over TRIGRAM on tie")
        }

        @Test
        fun `mergeCandidates tiebreaker prefers NICKNAME over PHONETIC on equal similarity score`() {
            /**
             * When the same (entityId, attributeId) is matched by both NICKNAME (score=0.85) and
             * PHONETIC (score=0.85), NICKNAME should win the tiebreaker.
             * Tiebreaker ordering: NICKNAME (4) > PHONETIC (3).
             *
             * Setup: trigger "smith", nickname returns candidate at 0.85, phonetic also returns same
             * candidate at 0.85. NICKNAME should be retained after dedup.
             *
             * Note: "smith" has no nickname group, but we use a name with a known nickname to exercise
             * the nickname query. Use "william" so nickname query fires and we can mock its result.
             * However, "william" is one token — 1 dmetaphone scalar query.
             */
            val attrId = UUID.randomUUID()
            val triggerRows = listOf(
                arrayOf<Any?>(attrId, "william", SchemaType.TEXT.name, "NAME"),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)
            val trigramQ = candidateQuery(emptyList())
            // Nickname query returns candidate at score 0.85
            val nicknameRows = listOf(
                arrayOf<Any?>(candidateEntityId.toString(), candidateAttributeId.toString(), "bill", 0.85, null),
            )
            val nicknameQ = candidateQuery(nicknameRows)
            val dmetaphoneQ = dmetaphoneScalarQuery("ALM")  // "william" → some phonetic code
            // Phonetic SQL returns SAME (entityId, attributeId) at 0.85
            val phoneticRows = listOf(
                arrayOf<Any?>(candidateEntityId.toString(), candidateAttributeId.toString(), "bill", 0.85, null),
            )
            val phoneticQ = candidateQuery(phoneticRows)

            whenever(normalizationService.normalize(any(), any())).thenReturn("william")
            whenever(entityManager.createNativeQuery(any()))
                .thenReturn(triggerQuery)
                .thenReturn(trigramQ)
                .thenReturn(nicknameQ)
                .thenReturn(dmetaphoneQ)
                .thenReturn(phoneticQ)

            val result = identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            assertEquals(1, result.size, "Equal-score NICKNAME and PHONETIC should deduplicate to one row")
            assertEquals(MatchSource.NICKNAME, result[0].matchSource, "NICKNAME should be preferred over PHONETIC on tie")
        }

        @Test
        fun `NAME signal dispatches to trigram plus nickname plus phonetic strategies`() {
            /**
             * After Phase 5 consolidation, NAME signals run all four strategies:
             * 1. Trigram (runCandidateQuery)
             * 2. Token re-scoring (inline, no extra query)
             * 3. Nickname expansion (findNicknameCandidates)
             * 4. Phonetic (computePhoneticCodes scalar + findPhoneticCandidates SQL)
             *
             * This test uses "smith" as trigger (1 token). Dmetaphone returns "SM0" so the phonetic
             * SQL query is issued. Expected calls: 1 trigger + 1 trigram + 1 nickname + 1 dmetaphone scalar + 1 phonetic SQL = 5.
             *
             * Verifying the call count confirms that the when(signalType) consolidation wired all
             * NAME strategies correctly and none were dropped.
             */
            val attrId = UUID.randomUUID()
            val triggerRows = listOf(
                arrayOf<Any?>(attrId, "smith", SchemaType.TEXT.name, "NAME"),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)
            val trigramQ = candidateQuery(emptyList())
            val nicknameQ = candidateQuery(emptyList())
            val dmetaphoneQ = dmetaphoneScalarQuery("SM0")  // non-empty → phonetic SQL fires
            val phoneticQ = candidateQuery(emptyList())

            whenever(normalizationService.normalize(any(), any())).thenReturn("smith")
            whenever(entityManager.createNativeQuery(any()))
                .thenReturn(triggerQuery)
                .thenReturn(trigramQ)
                .thenReturn(nicknameQ)
                .thenReturn(dmetaphoneQ)
                .thenReturn(phoneticQ)

            identityMatchCandidateService.findCandidates(triggerEntityId, workspaceId)

            // 1 trigger + 1 trigram + 1 nickname + 1 dmetaphone scalar + 1 phonetic SQL = 5 total
            verify(entityManager, times(5)).createNativeQuery(any())
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
                arrayOf<Any?>(attrId1, "Test@Example.COM", SchemaType.EMAIL.name, null),
                arrayOf<Any?>(attrId2, "  555-1234  ", SchemaType.PHONE.name, null),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)
            whenever(entityManager.createNativeQuery(any())).thenReturn(triggerQuery)
            // Normalization default from @BeforeEach: trim + lowercase (not signal-type-aware)
            // This test verifies mapping structure, not normalization strategy

            val result = identityMatchCandidateService.getTriggerAttributes(triggerEntityId, workspaceId)

            assertEquals(2, result.size)
            // Values are whatever normalizationService.normalize returns (mocked as trim+lowercase)
            assertEquals("test@example.com", result[MatchSignalType.EMAIL])
            assertEquals("555-1234", result[MatchSignalType.PHONE])
        }

        @Test
        fun `getTriggerAttributes delegates normalization to normalizationService`() {
            val attrId = UUID.randomUUID()
            val rawValue = "  JOHN@EXAMPLE.COM  "
            val triggerRows = listOf(
                arrayOf<Any?>(attrId, rawValue, SchemaType.EMAIL.name, null),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)
            whenever(entityManager.createNativeQuery(any())).thenReturn(triggerQuery)

            val result = identityMatchCandidateService.getTriggerAttributes(triggerEntityId, workspaceId)

            // Default mock: trim + lowercase
            assertEquals("john@example.com", result[MatchSignalType.EMAIL])
            // Verify the normalization service was called with the correct signal type
            verify(normalizationService).normalize(rawValue, MatchSignalType.EMAIL)
        }

        @Test
        fun `getTriggerAttributes maps non-EMAIL non-PHONE schema types to CUSTOM_IDENTIFIER`() {
            val attrId = UUID.randomUUID()
            val triggerRows = listOf(
                arrayOf<Any?>(attrId, "custom-id-123", SchemaType.TEXT.name, null),
            )
            val triggerQuery = triggerAttributeQuery(triggerRows)
            whenever(entityManager.createNativeQuery(any())).thenReturn(triggerQuery)

            val result = identityMatchCandidateService.getTriggerAttributes(triggerEntityId, workspaceId)

            assertEquals(1, result.size)
            assertEquals("custom-id-123", result[MatchSignalType.CUSTOM_IDENTIFIER])
        }
    }
}
