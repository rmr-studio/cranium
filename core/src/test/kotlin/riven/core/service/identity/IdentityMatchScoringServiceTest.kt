package riven.core.service.identity

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.enums.identity.MatchSignalType
import riven.core.service.util.factory.identity.IdentityFactory
import java.util.UUID

@SpringBootTest(classes = [IdentityMatchScoringService::class])
class IdentityMatchScoringServiceTest {

    @MockitoBean
    private lateinit var logger: KLogger

    @Autowired
    private lateinit var identityMatchScoringService: IdentityMatchScoringService

    private val triggerEntityId = UUID.fromString("11111111-0000-0000-0000-000000000001")
    private val candidateEntityId = UUID.fromString("33333333-0000-0000-0000-000000000003")

    // ------ Single-signal scoring ------

    @Nested
    inner class SingleSignalTests {

        @Test
        fun `single EMAIL match at 0_95 similarity produces composite score equal to 0_95`() {
            // With one signal, composite = (0.95 * 0.9) / 0.9 = 0.95
            val triggerAttributes = mapOf(MatchSignalType.EMAIL to "john@example.com")
            val candidates = listOf(
                IdentityFactory.createCandidateMatch(
                    candidateEntityId = candidateEntityId,
                    signalType = MatchSignalType.EMAIL,
                    candidateValue = "john@example.com",
                    similarityScore = 0.95,
                )
            )

            val results = identityMatchScoringService.scoreCandidates(triggerEntityId, triggerAttributes, candidates)

            assertEquals(1, results.size)
            assertEquals(0.95, results[0].compositeScore, 1e-4)
        }

        @Test
        fun `candidate below 0_5 threshold is excluded from results`() {
            val triggerAttributes = mapOf(MatchSignalType.EMAIL to "john@example.com")
            val candidates = listOf(
                IdentityFactory.createCandidateMatch(
                    candidateEntityId = candidateEntityId,
                    signalType = MatchSignalType.EMAIL,
                    candidateValue = "other@example.com",
                    similarityScore = 0.45,
                )
            )

            val results = identityMatchScoringService.scoreCandidates(triggerEntityId, triggerAttributes, candidates)

            assertTrue(results.isEmpty(), "Score 0.45 is below threshold 0.5 — should be filtered out")
        }

        @Test
        fun `candidate with composite score exactly 0_5 is included`() {
            // Need a similarity value that results in composite score exactly 0.5
            // With EMAIL weight=0.9: composite = (0.5 * 0.9) / 0.9 = 0.5
            val triggerAttributes = mapOf(MatchSignalType.EMAIL to "john@example.com")
            val candidates = listOf(
                IdentityFactory.createCandidateMatch(
                    candidateEntityId = candidateEntityId,
                    signalType = MatchSignalType.EMAIL,
                    candidateValue = "john@example.org",
                    similarityScore = 0.5,
                )
            )

            val results = identityMatchScoringService.scoreCandidates(triggerEntityId, triggerAttributes, candidates)

            assertEquals(1, results.size, "Score exactly 0.5 should be included (>= threshold)")
            assertEquals(0.5, results[0].compositeScore, 1e-4)
        }
    }

    // ------ Multi-signal scoring ------

    @Nested
    inner class MultiSignalTests {

        @Test
        fun `EMAIL 0_95 and PHONE 0_80 produce correct weighted average composite score`() {
            // composite = (0.95*0.9 + 0.80*0.85) / (0.9 + 0.85)
            // = (0.855 + 0.68) / 1.75
            // = 1.535 / 1.75
            // = 0.87714...
            val expectedScore = (0.95 * 0.9 + 0.80 * 0.85) / (0.9 + 0.85)

            val triggerAttributes = mapOf(
                MatchSignalType.EMAIL to "john@example.com",
                MatchSignalType.PHONE to "555-1234",
            )
            val candidates = listOf(
                IdentityFactory.createCandidateMatch(
                    candidateEntityId = candidateEntityId,
                    signalType = MatchSignalType.EMAIL,
                    candidateValue = "john@example.com",
                    similarityScore = 0.95,
                ),
                IdentityFactory.createCandidateMatch(
                    candidateEntityId = candidateEntityId,
                    signalType = MatchSignalType.PHONE,
                    candidateValue = "555-1234",
                    similarityScore = 0.80,
                ),
            )

            val results = identityMatchScoringService.scoreCandidates(triggerEntityId, triggerAttributes, candidates)

            assertEquals(1, results.size)
            assertEquals(expectedScore, results[0].compositeScore, 1e-4)
            assertEquals(2, results[0].signals.size, "Both EMAIL and PHONE signals should be included")
        }

        @Test
        fun `signals with similarity 0 are excluded from score and signal breakdown`() {
            val triggerAttributes = mapOf(
                MatchSignalType.EMAIL to "john@example.com",
                MatchSignalType.PHONE to "555-1234",
            )
            val candidates = listOf(
                IdentityFactory.createCandidateMatch(
                    candidateEntityId = candidateEntityId,
                    signalType = MatchSignalType.EMAIL,
                    candidateValue = "john@example.com",
                    similarityScore = 0.95,
                ),
                IdentityFactory.createCandidateMatch(
                    candidateEntityId = candidateEntityId,
                    signalType = MatchSignalType.PHONE,
                    candidateValue = "999-9999",
                    similarityScore = 0.0,  // zero similarity — should be excluded
                ),
            )

            val results = identityMatchScoringService.scoreCandidates(triggerEntityId, triggerAttributes, candidates)

            assertEquals(1, results.size)
            assertEquals(1, results[0].signals.size, "Zero-similarity PHONE signal must be excluded from breakdown")
            assertEquals(MatchSignalType.EMAIL, results[0].signals[0].type)
            // Composite should be based only on EMAIL signal
            assertEquals(0.95, results[0].compositeScore, 1e-4)
        }

        @Test
        fun `multiple candidates scored independently with correct signals`() {
            val candidate2Id = UUID.fromString("55555555-0000-0000-0000-000000000005")

            val triggerAttributes = mapOf(MatchSignalType.EMAIL to "john@example.com")
            val candidates = listOf(
                IdentityFactory.createCandidateMatch(
                    candidateEntityId = candidateEntityId,
                    signalType = MatchSignalType.EMAIL,
                    candidateValue = "john@example.com",
                    similarityScore = 0.95,
                ),
                IdentityFactory.createCandidateMatch(
                    candidateEntityId = candidate2Id,
                    signalType = MatchSignalType.EMAIL,
                    candidateValue = "johnny@example.com",
                    similarityScore = 0.60,
                ),
            )

            val results = identityMatchScoringService.scoreCandidates(triggerEntityId, triggerAttributes, candidates)

            assertEquals(2, results.size)
            val result1 = results.first { it.targetEntityId == candidateEntityId }
            val result2 = results.first { it.targetEntityId == candidate2Id }

            assertEquals(0.95, result1.compositeScore, 1e-4)
            assertEquals(0.60, result2.compositeScore, 1e-4)
        }

        @Test
        fun `best match per signal type kept when multiple candidates exist for same entity and type`() {
            // Two EMAIL candidates for the same entity — should keep the one with higher similarity
            val triggerAttributes = mapOf(MatchSignalType.EMAIL to "john@example.com")
            val candidates = listOf(
                IdentityFactory.createCandidateMatch(
                    candidateEntityId = candidateEntityId,
                    signalType = MatchSignalType.EMAIL,
                    candidateValue = "john@example.com",
                    similarityScore = 0.95,
                ),
                IdentityFactory.createCandidateMatch(
                    candidateEntityId = candidateEntityId,
                    signalType = MatchSignalType.EMAIL,
                    candidateValue = "j.doe@example.com",
                    similarityScore = 0.60,
                ),
            )

            val results = identityMatchScoringService.scoreCandidates(triggerEntityId, triggerAttributes, candidates)

            assertEquals(1, results.size)
            assertEquals(1, results[0].signals.size, "Only one EMAIL signal should remain after best-match selection")
            assertEquals(0.95, results[0].signals[0].similarity, 1e-4)
        }
    }

    // ------ Signal breakdown content ------

    @Nested
    inner class SignalBreakdownTests {

        @Test
        fun `signal breakdown contains type sourceValue targetValue similarity and weight`() {
            val triggerAttributes = mapOf(MatchSignalType.EMAIL to "john@example.com")
            val candidates = listOf(
                IdentityFactory.createCandidateMatch(
                    candidateEntityId = candidateEntityId,
                    signalType = MatchSignalType.EMAIL,
                    candidateValue = "john@example.com",
                    similarityScore = 0.95,
                )
            )

            val results = identityMatchScoringService.scoreCandidates(triggerEntityId, triggerAttributes, candidates)

            assertEquals(1, results.size)
            val signal = results[0].signals[0]
            assertEquals(MatchSignalType.EMAIL, signal.type)
            assertEquals("john@example.com", signal.sourceValue)
            assertEquals("john@example.com", signal.targetValue)
            assertEquals(0.95, signal.similarity, 1e-4)
            assertEquals(MatchSignalType.DEFAULT_WEIGHTS[MatchSignalType.EMAIL]!!, signal.weight, 1e-4)
        }

        @Test
        fun `scoreCandidates sets sourceEntityId from triggerEntityId parameter`() {
            val triggerAttributes = mapOf(MatchSignalType.EMAIL to "john@example.com")
            val candidates = listOf(
                IdentityFactory.createCandidateMatch(
                    candidateEntityId = candidateEntityId,
                    signalType = MatchSignalType.EMAIL,
                    candidateValue = "john@example.com",
                    similarityScore = 0.95,
                )
            )

            val results = identityMatchScoringService.scoreCandidates(triggerEntityId, triggerAttributes, candidates)

            assertEquals(triggerEntityId, results[0].sourceEntityId)
            assertEquals(candidateEntityId, results[0].targetEntityId)
        }
    }
}
