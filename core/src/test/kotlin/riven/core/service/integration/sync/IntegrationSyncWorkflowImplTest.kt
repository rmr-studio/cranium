package cranium.core.service.integration.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import cranium.core.configuration.workflow.TemporalWorkerConfiguration
import cranium.core.configuration.workflow.WorkflowRetryConfigurationProperties
import cranium.core.models.integration.sync.IntegrationSyncWorkflowInput
import cranium.core.models.integration.sync.SyncProcessingResult
import java.util.UUID

/**
 * Unit tests for [IntegrationSyncWorkflowImpl].
 *
 * Verifies the activity stub configuration (retry policy, timeouts) and infrastructure
 * constants that the sync webhook dispatch depends on.
 *
 * Note on Temporal execution tests: [TestWorkflowEnvironment] has known hanging issues
 * in this project when running alongside Spring Boot test infrastructure (documented in
 * WorkflowExecutionIntegrationTest). Activity orchestration sequencing is verified via
 * mock-based unit tests below.
 */
class IntegrationSyncWorkflowImplTest {

    // ------ Queue and Retry Configuration Tests ------

    @Nested
    inner class QueueAndRetryConfigTests {

        /**
         * Contract test: NangoWebhookService dispatch and TemporalWorkerConfiguration
         * registration both reference this constant. If it changes, both must be updated.
         */
        @Test
        fun `INTEGRATION_SYNC_QUEUE constant equals integration dot sync`() {
            assertEquals("integration.sync", TemporalWorkerConfiguration.INTEGRATION_SYNC_QUEUE)
        }

        /**
         * Verifies the integration-sync retry config has correct production defaults.
         * The 30s initial interval and 3 attempts match the plan spec (SYNC-08).
         */
        @Test
        fun `retryProperties integrationSync defaults — 3 attempts 30s initial 2x backoff 120s max`() {
            val props = WorkflowRetryConfigurationProperties()

            assertEquals(3, props.integrationSync.maxAttempts)
            assertEquals(30L, props.integrationSync.initialIntervalSeconds)
            assertEquals(2.0, props.integrationSync.backoffCoefficient)
            assertEquals(120L, props.integrationSync.maxIntervalSeconds)
        }
    }

    // ------ Workflow Interface Contract Tests ------

    @Nested
    inner class WorkflowContractTests {

        /**
         * Verifies that IntegrationSyncWorkflowImpl can be instantiated with a RetryConfig.
         * This is the factory pattern used by TemporalWorkerConfiguration — if the constructor
         * signature changes, the worker registration in TemporalWorkerConfiguration must also change.
         */
        @Test
        fun `IntegrationSyncWorkflowImpl can be instantiated with RetryConfig`() {
            val retryConfig = WorkflowRetryConfigurationProperties().integrationSync
            // If this compiles and runs, the constructor contract is satisfied.
            // The worker factory pattern in TemporalWorkerConfiguration calls this exact lambda.
            IntegrationSyncWorkflowImpl(retryConfig)
        }
    }

    // ------ Activity Orchestration Contract Tests ------

    /**
     * These tests verify the workflow's activity orchestration logic using a testable subclass
     * that overrides createActivitiesStub() to return a mock. This avoids Temporal's
     * TestWorkflowEnvironment which has known hanging issues in this project.
     */
    @Nested
    inner class ActivityOrchestrationTests {

        private val connectionId: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
        private val workspaceId: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
        private val entityTypeId: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")

        private val input = IntegrationSyncWorkflowInput(
            connectionId = connectionId,
            workspaceId = workspaceId,
            integrationId = UUID.randomUUID(),
            nangoConnectionId = "nango-conn-001",
            providerConfigKey = "hubspot",
            model = "hubspot-contact",
            modifiedAfter = null,
        )

        private val syncedEntityIds = listOf(
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
        )

        private val successResult = SyncProcessingResult(
            entityTypeId = entityTypeId,
            cursor = "cursor-final",
            recordsSynced = 5,
            recordsFailed = 0,
            success = true,
            syncedEntityIds = syncedEntityIds,
        )

        /**
         * Creates a testable workflow that uses a mock activities stub instead of Temporal's
         * Workflow.newActivityStub() which requires an active Temporal workflow context.
         */
        private fun createTestableWorkflow(activities: IntegrationSyncActivities): IntegrationSyncWorkflowImpl {
            val retryConfig = WorkflowRetryConfigurationProperties().integrationSync
            return object : IntegrationSyncWorkflowImpl(retryConfig) {
                override fun createActivitiesStub(): IntegrationSyncActivities = activities
            }
        }

        @Test
        fun `workflow calls executeProjections after finalizeSyncState and before evaluateHealth`() {
            val activities = mock<IntegrationSyncActivities>()
            whenever(activities.fetchAndProcessRecords(any())).thenReturn(successResult)

            val workflow = createTestableWorkflow(activities)
            workflow.execute(input)

            val order = inOrder(activities)
            order.verify(activities).transitionToSyncing(connectionId, input.workspaceId)
            order.verify(activities).fetchAndProcessRecords(input)
            order.verify(activities).finalizeSyncState(connectionId, entityTypeId, successResult)
            order.verify(activities).executeProjections(connectionId, input.workspaceId, entityTypeId, syncedEntityIds)
            order.verify(activities).evaluateHealth(connectionId)
        }

        @Test
        fun `workflow skips finalizeSyncState when entityTypeId is null`() {
            val activities = mock<IntegrationSyncActivities>()
            val nullEntityTypeResult = SyncProcessingResult(
                entityTypeId = null,
                cursor = null,
                recordsSynced = 0,
                recordsFailed = 0,
                lastErrorMessage = "Failed to resolve model context",
                success = false,
            )
            whenever(activities.fetchAndProcessRecords(any())).thenReturn(nullEntityTypeResult)

            val workflow = createTestableWorkflow(activities)
            workflow.execute(input)

            verify(activities).transitionToSyncing(connectionId, input.workspaceId)
            verify(activities).fetchAndProcessRecords(input)
            verify(activities, org.mockito.kotlin.never()).finalizeSyncState(any(), any(), any())
            verify(activities).evaluateHealth(connectionId)
        }

        @Test
        fun `workflow completes successfully even when evaluateHealth throws`() {
            val activities = mock<IntegrationSyncActivities>()
            whenever(activities.fetchAndProcessRecords(any())).thenReturn(successResult)
            doThrow(RuntimeException("health evaluation failed")).whenever(activities).evaluateHealth(any())

            val workflow = createTestableWorkflow(activities)

            // Workflow must not propagate the exception from evaluateHealth
            assertDoesNotThrow { workflow.execute(input) }

            // Verify sync state and projections were still executed before health
            verify(activities).finalizeSyncState(connectionId, entityTypeId, successResult)
            verify(activities).executeProjections(connectionId, input.workspaceId, entityTypeId, syncedEntityIds)
        }

        @Test
        fun `workflow skips executeProjections when recordsSynced is zero`() {
            val activities = mock<IntegrationSyncActivities>()
            val zeroRecordsResult = SyncProcessingResult(
                entityTypeId = entityTypeId,
                cursor = null,
                recordsSynced = 0,
                recordsFailed = 0,
                success = true,
            )
            whenever(activities.fetchAndProcessRecords(any())).thenReturn(zeroRecordsResult)

            val workflow = createTestableWorkflow(activities)
            workflow.execute(input)

            verify(activities, org.mockito.kotlin.never()).executeProjections(any(), any(), any(), any())
        }

        @Test
        fun `workflow skips executeProjections when result is not successful`() {
            val activities = mock<IntegrationSyncActivities>()
            val failedResult = SyncProcessingResult(
                entityTypeId = entityTypeId,
                cursor = null,
                recordsSynced = 3,
                recordsFailed = 2,
                success = false,
                lastErrorMessage = "Some error",
            )
            whenever(activities.fetchAndProcessRecords(any())).thenReturn(failedResult)

            val workflow = createTestableWorkflow(activities)
            workflow.execute(input)

            verify(activities, org.mockito.kotlin.never()).executeProjections(any(), any(), any(), any())
        }
    }
}
