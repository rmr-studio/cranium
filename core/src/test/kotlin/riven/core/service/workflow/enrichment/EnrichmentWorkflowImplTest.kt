package riven.core.service.workflow.enrichment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import riven.core.configuration.workflow.TemporalWorkerConfiguration
import riven.core.models.enrichment.EnrichmentContext
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
        private val context: EnrichmentContext = EnrichmentFactory.createEnrichmentContext(queueItemId = queueItemId)

        /**
         * Creates a testable workflow subclass with mock-controlled stubs and consumers.
         */
        private fun createTestableWorkflow(
            activities: EnrichmentActivities,
            consumers: List<ConsumerActivity>,
        ): EnrichmentWorkflowImpl {
            return object : EnrichmentWorkflowImpl() {
                override fun createActivitiesStub(): EnrichmentActivities = activities
                override fun buildConsumers(stub: EnrichmentActivities): List<ConsumerActivity> = consumers
            }
        }

        /**
         * Verifies the core contract: analyzeSemantics is called, then each consumer's run()
         * is called with the returned context and queueItemId in order.
         *
         * Also protects the Phase B connotation hook: analyzeSemantics is the activity that
         * calls EnrichmentAnalysisService.persistConnotationSnapshot. Removing the call here
         * silently disables connotation analysis.
         */
        @Test
        fun `embed calls analyzeSemantics then iterates consumer list`() {
            val activities = mock<EnrichmentActivities>()
            val consumer1 = mock<ConsumerActivity>()
            val consumer2 = mock<ConsumerActivity>()
            whenever(activities.analyzeSemantics(queueItemId)).thenReturn(context)

            val workflow = createTestableWorkflow(activities, listOf(consumer1, consumer2))
            workflow.embed(queueItemId)

            verify(activities).analyzeSemantics(queueItemId)
            verify(consumer1).run(context, queueItemId)
            verify(consumer2).run(context, queueItemId)
        }

        /**
         * Verifies the runCatching semantics: when one consumer throws, the workflow logs
         * a warning and continues to the next consumer — it does NOT propagate the failure.
         * This is the ENRICH-05 contract: one consumer's terminal failure must not block siblings.
         */
        @Test
        fun `embed continues to next consumer when one consumer throws`() {
            val activities = mock<EnrichmentActivities>()
            val failingConsumer = mock<ConsumerActivity>()
            val successConsumer = mock<ConsumerActivity>()
            whenever(activities.analyzeSemantics(queueItemId)).thenReturn(context)
            whenever(failingConsumer.run(context, queueItemId)).thenThrow(RuntimeException("provider outage"))

            val workflow = createTestableWorkflow(activities, listOf(failingConsumer, successConsumer))

            // Should not throw — runCatching swallows the failure at workflow level
            workflow.embed(queueItemId)

            verify(failingConsumer).run(context, queueItemId)
            verify(successConsumer).run(context, queueItemId)
        }

        /**
         * Verifies that when all consumers succeed, analyzeSemantics is called exactly once
         * (no retry or duplication at the orchestration level).
         */
        @Test
        fun `embed calls analyzeSemantics exactly once`() {
            val activities = mock<EnrichmentActivities>()
            val consumer = mock<ConsumerActivity>()
            whenever(activities.analyzeSemantics(queueItemId)).thenReturn(context)

            val workflow = createTestableWorkflow(activities, listOf(consumer))
            workflow.embed(queueItemId)

            verify(activities, times(1)).analyzeSemantics(queueItemId)
        }

        /**
         * Verifies that when the consumer list is empty, analyzeSemantics is still called
         * (analysis phase is never skipped even with no consumers).
         */
        @Test
        fun `embed calls analyzeSemantics even with empty consumer list`() {
            val activities = mock<EnrichmentActivities>()
            whenever(activities.analyzeSemantics(queueItemId)).thenReturn(context)

            val workflow = createTestableWorkflow(activities, emptyList())
            workflow.embed(queueItemId)

            verify(activities).analyzeSemantics(queueItemId)
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
        private val context: EnrichmentContext = EnrichmentFactory.createEnrichmentContext(queueItemId = queueItemId)

        @Test
        fun `EmbeddingConsumer run delegates to activities embedAndStore`() {
            val activities = mock<EnrichmentActivities>()
            val consumer = EmbeddingConsumer(activities)

            consumer.run(context, queueItemId)

            verify(activities).embedAndStore(context, queueItemId)
        }

        @Test
        fun `EmbeddingConsumer run propagates exceptions to caller`() {
            val activities = mock<EnrichmentActivities>()
            whenever(activities.embedAndStore(context, queueItemId)).thenThrow(RuntimeException("embedding failed"))
            val consumer = EmbeddingConsumer(activities)

            val result = runCatching { consumer.run(context, queueItemId) }

            assert(result.isFailure)
            assert(result.exceptionOrNull() is RuntimeException)
        }

        @Test
        fun `EmbeddingConsumer run calls embedAndStore exactly once`() {
            val activities = mock<EnrichmentActivities>()
            val consumer = EmbeddingConsumer(activities)

            consumer.run(context, queueItemId)

            verify(activities, times(1)).embedAndStore(context, queueItemId)
            verify(activities, never()).analyzeSemantics(queueItemId)
        }
    }
}
