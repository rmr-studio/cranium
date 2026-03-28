package riven.core.service.identity

import io.github.oshai.kotlinlogging.KLogger
import io.temporal.api.common.v1.WorkflowExecution
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowExecutionAlreadyStarted
import io.temporal.client.WorkflowOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.repository.workflow.ExecutionQueueRepository
import riven.core.service.util.factory.workflow.ExecutionQueueFactory
import riven.core.service.workflow.identity.IdentityMatchWorkflow
import riven.core.service.workflow.queue.WorkflowExecutionQueueService
import java.util.UUID

/**
 * Unit tests for [IdentityMatchQueueProcessorService].
 *
 * Verifies that items are claimed then dispatched to Temporal, that
 * WorkflowExecutionAlreadyStartedException is treated as a successful dispatch (idempotency),
 * and that failed items are either released for retry or permanently failed depending on
 * attempt count.
 */
@SpringBootTest(classes = [IdentityMatchQueueProcessorService::class])
class IdentityMatchQueueProcessorServiceTest {

    @MockitoBean
    private lateinit var workflowExecutionQueueService: WorkflowExecutionQueueService

    @MockitoBean
    private lateinit var executionQueueRepository: ExecutionQueueRepository

    @MockitoBean
    private lateinit var workflowClient: WorkflowClient

    @MockitoBean
    private lateinit var logger: KLogger

    @Autowired
    private lateinit var service: IdentityMatchQueueProcessorService

    private val workspaceId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001")
    private val entityId = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002")

    @Test
    fun `claimBatch delegates to executionQueueRepository`() {
        val items = listOf(ExecutionQueueFactory.createIdentityMatchJob(workspaceId = workspaceId, entityId = entityId))
        whenever(executionQueueRepository.claimPendingIdentityMatchJobs(10)).thenReturn(items)

        val result = service.claimBatch(10)

        assertEquals(items, result)
        verify(executionQueueRepository).claimPendingIdentityMatchJobs(10)
    }

    @Test
    fun `processItem marks claimed then starts workflow then marks dispatched`() {
        val item = ExecutionQueueFactory.createIdentityMatchJob(workspaceId = workspaceId, entityId = entityId)
        val workflowStub = mock<IdentityMatchWorkflow>()

        whenever(workflowExecutionQueueService.markClaimed(item)).thenReturn(item)
        whenever(workflowClient.newWorkflowStub(eq(IdentityMatchWorkflow::class.java), any<WorkflowOptions>()))
            .thenReturn(workflowStub)

        service.processItem(item)

        verify(workflowExecutionQueueService).markClaimed(item)
        verify(workflowClient).newWorkflowStub(eq(IdentityMatchWorkflow::class.java), any<WorkflowOptions>())
        verify(workflowExecutionQueueService).markDispatched(item)
        verify(workflowExecutionQueueService, never()).markFailed(any(), any())
        verify(workflowExecutionQueueService, never()).releaseToPending(any())
    }

    @Test
    fun `processItem handles WorkflowExecutionAlreadyStartedException by marking dispatched`() {
        val item = ExecutionQueueFactory.createIdentityMatchJob(workspaceId = workspaceId, entityId = entityId)
        val workflowStub = mock<IdentityMatchWorkflow>()

        whenever(workflowExecutionQueueService.markClaimed(item)).thenReturn(item)
        whenever(workflowClient.newWorkflowStub(eq(IdentityMatchWorkflow::class.java), any<WorkflowOptions>()))
            .thenReturn(workflowStub)

        // Simulate WorkflowExecutionAlreadyStarted via static WorkflowClient.start
        // We mock it by making the stub throw when matchEntity is called
        whenever(workflowStub.matchEntity(any(), any(), any()))
            .thenAnswer {
                throw WorkflowExecutionAlreadyStarted(
                    WorkflowExecution.newBuilder().setWorkflowId("identity-match-$entityId").setRunId("run-1").build(),
                    "IdentityMatchWorkflow",
                    null
                )
            }

        service.processItem(item)

        verify(workflowExecutionQueueService).markClaimed(item)
        verify(workflowExecutionQueueService).markDispatched(item)
        verify(workflowExecutionQueueService, never()).markFailed(any(), any())
        verify(workflowExecutionQueueService, never()).releaseToPending(any())
    }

    @Test
    fun `processItem releases to pending on failure when attempts below max`() {
        val item = ExecutionQueueFactory.createIdentityMatchJob(
            workspaceId = workspaceId,
            entityId = entityId,
            attemptCount = 1
        )

        whenever(workflowExecutionQueueService.markClaimed(item))
            .thenThrow(RuntimeException("Simulated DB error"))

        service.processItem(item)

        verify(workflowExecutionQueueService).releaseToPending(item)
        verify(workflowExecutionQueueService, never()).markDispatched(any())
        verify(workflowExecutionQueueService, never()).markFailed(any(), any())
    }

    @Test
    fun `processItem marks failed when attempts reach max`() {
        val item = ExecutionQueueFactory.createIdentityMatchJob(
            workspaceId = workspaceId,
            entityId = entityId,
            attemptCount = IdentityMatchQueueProcessorService.MAX_ATTEMPTS
        )

        whenever(workflowExecutionQueueService.markClaimed(item))
            .thenThrow(RuntimeException("Simulated DB error"))

        service.processItem(item)

        verify(workflowExecutionQueueService).markFailed(eq(item), any())
        verify(workflowExecutionQueueService, never()).releaseToPending(any())
        verify(workflowExecutionQueueService, never()).markDispatched(any())
    }
}
