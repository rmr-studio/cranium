package riven.core.service.workflow.enrichment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import riven.core.configuration.workflow.TemporalWorkerConfiguration
import riven.core.models.entity.knowledge.EntityKnowledgeView
import riven.core.service.enrichment.EmbeddingConsumer
import riven.core.service.util.factory.EnrichmentFactory
import java.util.UUID

/**
 * Unit tests for [EnrichmentWorkflowImpl].
 *
 * Verifies the 2-activity + consumer-fan-out orchestration using the testable subclass pattern —
 * overrides createActivitiesStub() and buildConsumers() to inject mocks, avoiding Temporal's
 * TestWorkflowEnvironment which has known hanging issues in this project.
 *
 * Post Plan 01-03: the 4-activity sequence (analyze → constructText → generateEmbedding → store)
 * is replaced by analyze → List<ConsumerActivity> fan-out. The consumer-list tests below verify
 * both the happy path and the runCatching swallow-and-continue semantics.
 *
 * Plan 02-03: fixture type changed from [EnrichmentContext] to [EntityKnowledgeView].
 */
class EnrichmentWorkflowImplTest {

    // ------ Queue Constant Tests ------

    @Nested
    inner class QueueConstantTests {

        /**
         * Contract test: EnrichmentQueueService dispatch and TemporalWorkerConfiguration registration
         * both reference this constant. Changing it requires updating both sites.
         */
        @Test
        fun `ENRICHMENT_EMBED_QUEUE constant equals enrichment dot embed`() {
            assertEquals("enrichment.embed", TemporalWorkerConfiguration.ENRICHMENT_EMBED_QUEUE)
        }

        /**
         * Verifies the workflow ID helper produces the canonical format used for Temporal deduplication.
         */
        @Test
        fun `workflowId produces correct format`() {
            val uuid = UUID.fromString("11111111-1111-1111-1111-111111111111")
            assertEquals("enrichment-embed-11111111-1111-1111-1111-111111111111", EnrichmentWorkflow.workflowId(uuid))
        }
    }

    // ------ Activity Orchestration Tests ------

    /**
     * Verifies the 2-activity workflow shape: analyzeSemantics → consumer fan-out.
     * Uses testable subclass pattern to override both createActivitiesStub() and buildConsumers().
     */
    @Nested
    inner class ActivityOrchestrationTests {

        private val queueItemId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        private val view: EntityKnowledgeView = EnrichmentFactory.createEntityKnowledgeView(queueItemId = queueItemId)

        /**
         * Creates a testable workflow subclass with mock-controlled stubs and consumers.
         *
         * @param activities Mock Temporal activity stub used by the workflow body.
         * @param siblings Sibling consumers (run with runCatching, isolated per ENRICH-05).
         * @param primary Optional override for the primary completion consumer. When `null`,
         *   the default [EmbeddingConsumer] backed by [activities] is used — most tests want
         *   this so failures originate from `activities.embedAndStore` mocking.
         */
        private fun createTestableWorkflow(
            activities: EnrichmentActivities,
            siblings: List<ConsumerActivity> = emptyList(),
            primary: ConsumerActivity? = null,
        ): EnrichmentWorkflowImpl {
            return object : EnrichmentWorkflowImpl() {
                override fun createActivitiesStub(): EnrichmentActivities = activities
                override fun buildConsumers(stub: EnrichmentActivities): List<ConsumerActivity> = siblings
                override fun buildPrimaryConsumer(stub: EnrichmentActivities): ConsumerActivity =
                    primary ?: super.buildPrimaryConsumer(stub)
            }
        }

        /**
         * Verifies the core contract: analyzeSemantics is called first, then the primary
         * EmbeddingConsumer (via activities.embedAndStore), then each sibling consumer's run()
         * — all in declared order.
         *
         * Also protects the Phase B connotation hook: analyzeSemantics is the activity that
         * calls EnrichmentAnalysisService.persistConnotationSnapshot. Removing the call here
         * silently disables connotation analysis.
         */
        @Test
        fun `embed calls analyzeSemantics then primary embedAndStore then siblings in order`() {
            val activities = mock<EnrichmentActivities>()
            val sibling1 = mock<ConsumerActivity>()
            val sibling2 = mock<ConsumerActivity>()
            whenever(activities.analyzeSemantics(queueItemId)).thenReturn(view)

            val workflow = createTestableWorkflow(activities, siblings = listOf(sibling1, sibling2))
            workflow.embed(queueItemId)

            // r3180290327: lock the documented sequential contract — analyze → primary embed → siblings.
            inOrder(activities, sibling1, sibling2) {
                verify(activities).analyzeSemantics(queueItemId)
                verify(activities).embedAndStore(view, queueItemId)
                verify(sibling1).run(view, queueItemId)
                verify(sibling2).run(view, queueItemId)
            }
        }

        /**
         * Regression test for r3180290311 — terminal primary-consumer failure transitions the
         * queue row to FAILED via the markQueueItemFailed activity and does NOT propagate as a
         * workflow failure.
         *
         * Pre-fix bug: the embedding consumer was iterated under the same runCatching as
         * sibling consumers, so a terminal embedAndStore failure was silently logged and the
         * workflow returned successfully — leaving the queue row stuck in CLAIMED with no
         * FAILED state and no retry signal.
         *
         * Post-fix: the primary is now run inside its own runCatching that, on failure, invokes
         * stub.markQueueItemFailed(queueItemId, reason). The workflow body still completes
         * normally (Temporal sees a successful workflow + a FAILED queue row).
         */
        @Test
        fun `embed marks queue FAILED when primary embedAndStore throws and does not propagate`() {
            val activities = mock<EnrichmentActivities>()
            whenever(activities.analyzeSemantics(queueItemId)).thenReturn(view)
            whenever(activities.embedAndStore(view, queueItemId)).thenThrow(RuntimeException("embedding provider outage"))

            val workflow = createTestableWorkflow(activities)

            // Workflow body must NOT propagate the primary failure — Temporal sees success.
            workflow.embed(queueItemId)

            inOrder(activities) {
                verify(activities).analyzeSemantics(queueItemId)
                verify(activities).embedAndStore(view, queueItemId)
                verify(activities).markQueueItemFailed(eq(queueItemId), any())
            }
        }

        /**
         * Regression test for r3180290311 — when the primary completion consumer SUCCEEDS,
         * markQueueItemFailed is never invoked. Guards against a regression where every embed
         * call writes a redundant FAILED transition.
         */
        @Test
        fun `embed does not call markQueueItemFailed when primary embedAndStore succeeds`() {
            val activities = mock<EnrichmentActivities>()
            whenever(activities.analyzeSemantics(queueItemId)).thenReturn(view)

            val workflow = createTestableWorkflow(activities)
            workflow.embed(queueItemId)

            verify(activities).embedAndStore(view, queueItemId)
            verify(activities, never()).markQueueItemFailed(any(), any())
        }

        /**
         * Sibling-isolation contract (ENRICH-05): when a sibling throws, the workflow logs and
         * continues to the next sibling — it does NOT propagate the failure and does NOT touch
         * the queue row. (The primary completion semantics are the previous test's job.)
         */
        @Test
        fun `embed continues to next sibling consumer when one sibling throws and does not regress queue`() {
            val activities = mock<EnrichmentActivities>()
            val failingSibling = mock<ConsumerActivity>()
            val successSibling = mock<ConsumerActivity>()
            whenever(activities.analyzeSemantics(queueItemId)).thenReturn(view)
            whenever(failingSibling.run(view, queueItemId)).thenThrow(RuntimeException("synthesis outage"))

            val workflow = createTestableWorkflow(activities, siblings = listOf(failingSibling, successSibling))

            // Should not throw — runCatching isolates the sibling failure at workflow level.
            workflow.embed(queueItemId)

            verify(failingSibling).run(view, queueItemId)
            verify(successSibling).run(view, queueItemId)
            // ENRICH-05 invariant: a sibling failure must NOT mutate queue state.
            verify(activities, never()).markQueueItemFailed(any(), any())
        }

        /**
         * Verifies that when the primary completion consumer succeeds, analyzeSemantics is
         * called exactly once (no retry or duplication at the orchestration level).
         */
        @Test
        fun `embed calls analyzeSemantics exactly once`() {
            val activities = mock<EnrichmentActivities>()
            whenever(activities.analyzeSemantics(queueItemId)).thenReturn(view)

            val workflow = createTestableWorkflow(activities)
            workflow.embed(queueItemId)

            verify(activities, times(1)).analyzeSemantics(queueItemId)
        }

        /**
         * Verifies the analysis phase runs even with no sibling consumers — the primary
         * completion consumer (default EmbeddingConsumer) is always invoked.
         */
        @Test
        fun `embed runs analyzeSemantics and primary embedAndStore even with empty sibling list`() {
            val activities = mock<EnrichmentActivities>()
            whenever(activities.analyzeSemantics(queueItemId)).thenReturn(view)

            val workflow = createTestableWorkflow(activities, siblings = emptyList())
            workflow.embed(queueItemId)

            verify(activities).analyzeSemantics(queueItemId)
            verify(activities).embedAndStore(view, queueItemId)
        }
    }

    // ------ EmbeddingConsumer Tests ------

    /**
     * Unit tests for [EmbeddingConsumer] — verifies it delegates embedAndStore correctly
     * and propagates exceptions (so the workflow's runCatching can catch them).
     */
    @Nested
    inner class EmbeddingConsumerTests {

        private val queueItemId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
        private val view: EntityKnowledgeView = EnrichmentFactory.createEntityKnowledgeView(queueItemId = queueItemId)

        @Test
        fun `EmbeddingConsumer run delegates to activities embedAndStore`() {
            val activities = mock<EnrichmentActivities>()
            val consumer = EmbeddingConsumer(activities)

            consumer.run(view, queueItemId)

            verify(activities).embedAndStore(view, queueItemId)
        }

        @Test
        fun `EmbeddingConsumer run propagates exceptions to caller`() {
            val activities = mock<EnrichmentActivities>()
            whenever(activities.embedAndStore(view, queueItemId)).thenThrow(RuntimeException("embedding failed"))
            val consumer = EmbeddingConsumer(activities)

            val result = runCatching { consumer.run(view, queueItemId) }

            assert(result.isFailure)
            assert(result.exceptionOrNull() is RuntimeException)
        }

        @Test
        fun `EmbeddingConsumer run calls embedAndStore exactly once`() {
            val activities = mock<EnrichmentActivities>()
            val consumer = EmbeddingConsumer(activities)

            consumer.run(view, queueItemId)

            verify(activities, times(1)).embedAndStore(view, queueItemId)
            verify(activities, never()).analyzeSemantics(queueItemId)
        }
    }
}
