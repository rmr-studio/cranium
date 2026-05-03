package riven.core.service.workflow.enrichment

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import riven.core.configuration.workflow.TemporalWorkerConfiguration
import riven.core.models.enrichment.EnrichedTextResult
import riven.core.models.enrichment.EnrichmentContext
import riven.core.service.util.factory.EnrichmentFactory
import riven.core.service.util.factory.enrichment.EnrichmentFactory as EnrichmentModelFactory
import java.util.UUID

/**
 * Unit tests for [EnrichmentWorkflowImpl].
 *
 * Verifies the 4-activity orchestration sequence and data-flow contracts using the
 * testable subclass pattern — overrides createActivitiesStub() to return a mock,
 * avoiding Temporal's TestWorkflowEnvironment which has known hanging issues in this project.
 */
class EnrichmentWorkflowImplTest {

    // ------ Queue Constant Tests ------

    @Nested
    inner class QueueConstantTests {

        /**
         * Contract test: EnrichmentService dispatch and TemporalWorkerConfiguration registration
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
     * These tests verify the workflow's 4-activity sequence using a testable subclass
     * that overrides createActivitiesStub() to return a mock.
     */
    @Nested
    inner class ActivityOrchestrationTests {

        private val queueItemId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        private val context: EnrichmentContext = EnrichmentFactory.createEnrichmentContext(queueItemId = queueItemId)
        private val enrichedTextResult = EnrichmentModelFactory.enrichedTextResult(
            text = "## Entity Type: Customer\n\nType: Customer",
            truncated = false
        )
        private val embedding = floatArrayOf(0.1f, 0.2f, 0.3f)

        /**
         * Creates a testable subclass with the mock injected as the activities stub.
         * This bypasses Temporal's Workflow.newActivityStub() which requires an active workflow context.
         */
        private fun createTestableWorkflow(activities: EnrichmentActivities): EnrichmentWorkflowImpl {
            return object : EnrichmentWorkflowImpl() {
                override fun createActivitiesStub(): EnrichmentActivities = activities
            }
        }

        /**
         * Also protects the Phase B connotation hook: `analyzeSemantics` is the activity
         * that calls `EnrichmentService.persistConnotationSnapshot` (which routes through
         * `ConnotationAnalysisService` for the SENTIMENT category). Removing the call here
         * would silently disable connotation analysis. Snapshot-level assertions live in
         * `EnrichmentServiceTest` and `ConnotationPipelineIntegrationTest`.
         */
        @Test
        fun `embed calls all 4 activities in sequence`() {
            val activities = mock<EnrichmentActivities>()
            whenever(activities.analyzeSemantics(queueItemId)).thenReturn(context)
            whenever(activities.constructEnrichedText(context)).thenReturn(enrichedTextResult)
            whenever(activities.generateEmbedding(enrichedTextResult.text)).thenReturn(embedding)

            val workflow = createTestableWorkflow(activities)
            workflow.embed(queueItemId)

            val order = inOrder(activities)
            order.verify(activities).analyzeSemantics(queueItemId)
            order.verify(activities).constructEnrichedText(context)
            order.verify(activities).generateEmbedding(enrichedTextResult.text)
            order.verify(activities).storeEmbedding(queueItemId, context, embedding, enrichedTextResult.truncated)
        }

        @Test
        fun `embed passes context from analyzeSemantics to constructEnrichedText`() {
            val activities = mock<EnrichmentActivities>()
            val specificContext = EnrichmentFactory.createEnrichmentContext(
                queueItemId = queueItemId,
                entityTypeName = "UniqueTypeName"
            )
            whenever(activities.analyzeSemantics(queueItemId)).thenReturn(specificContext)
            whenever(activities.constructEnrichedText(specificContext)).thenReturn(enrichedTextResult)
            whenever(activities.generateEmbedding(enrichedTextResult.text)).thenReturn(embedding)

            val workflow = createTestableWorkflow(activities)
            workflow.embed(queueItemId)

            verify(activities).constructEnrichedText(specificContext)
        }

        @Test
        fun `embed passes text from constructEnrichedText to generateEmbedding`() {
            val activities = mock<EnrichmentActivities>()
            val specificText = "## Specific enriched text for testing"
            val specificResult = EnrichmentModelFactory.enrichedTextResult(text = specificText, truncated = false)
            whenever(activities.analyzeSemantics(queueItemId)).thenReturn(context)
            whenever(activities.constructEnrichedText(context)).thenReturn(specificResult)
            whenever(activities.generateEmbedding(specificText)).thenReturn(embedding)

            val workflow = createTestableWorkflow(activities)
            workflow.embed(queueItemId)

            verify(activities).generateEmbedding(specificText)
        }

        @Test
        fun `embed passes embedding from generateEmbedding to storeEmbedding`() {
            val activities = mock<EnrichmentActivities>()
            val specificEmbedding = floatArrayOf(0.9f, 0.8f, 0.7f, 0.6f)
            whenever(activities.analyzeSemantics(queueItemId)).thenReturn(context)
            whenever(activities.constructEnrichedText(context)).thenReturn(enrichedTextResult)
            whenever(activities.generateEmbedding(enrichedTextResult.text)).thenReturn(specificEmbedding)

            val workflow = createTestableWorkflow(activities)
            workflow.embed(queueItemId)

            // Capture what storeEmbedding was called with and verify it matches exactly
            verify(activities).storeEmbedding(queueItemId, context, specificEmbedding, enrichedTextResult.truncated)
        }

        @Test
        fun `embed passes truncated flag from constructEnrichedText result to storeEmbedding`() {
            val activities = mock<EnrichmentActivities>()
            val truncatedResult = EnrichmentModelFactory.enrichedTextResult(
                text = "## Entity Type: Customer\n\nType: Customer",
                truncated = true
            )
            whenever(activities.analyzeSemantics(queueItemId)).thenReturn(context)
            whenever(activities.constructEnrichedText(context)).thenReturn(truncatedResult)
            whenever(activities.generateEmbedding(truncatedResult.text)).thenReturn(embedding)

            val workflow = createTestableWorkflow(activities)
            workflow.embed(queueItemId)

            verify(activities).storeEmbedding(queueItemId, context, embedding, true)
        }
    }
}
