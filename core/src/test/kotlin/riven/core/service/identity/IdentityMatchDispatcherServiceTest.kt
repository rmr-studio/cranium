package riven.core.service.identity

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.enums.workflow.ExecutionJobType
import riven.core.entity.workflow.ExecutionQueueEntity
import riven.core.repository.workflow.ExecutionQueueRepository
import riven.core.service.util.factory.workflow.ExecutionQueueFactory
import riven.core.service.workflow.queue.WorkflowExecutionQueueService
import java.util.UUID

/**
 * Unit tests for [IdentityMatchDispatcherService].
 *
 * Verifies that the dispatcher delegates batch processing to the processor service
 * and releases stale items from the execution queue.
 */
@SpringBootTest(classes = [IdentityMatchDispatcherService::class])
class IdentityMatchDispatcherServiceTest {

    @MockitoBean
    private lateinit var processorService: IdentityMatchQueueProcessorService

    @MockitoBean
    private lateinit var executionQueueRepository: ExecutionQueueRepository

    @MockitoBean
    private lateinit var workflowExecutionQueueService: WorkflowExecutionQueueService

    @MockitoBean
    private lateinit var logger: KLogger

    @Autowired
    private lateinit var service: IdentityMatchDispatcherService

    private val workspaceId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001")
    private val entityId = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002")

    @Test
    fun `processQueue delegates each claimed item to processor`() {
        val item1 = ExecutionQueueFactory.createIdentityMatchJob(workspaceId = workspaceId, entityId = entityId)
        val item2 = ExecutionQueueFactory.createIdentityMatchJob(workspaceId = workspaceId, entityId = UUID.randomUUID())
        whenever(processorService.claimBatch(any())).thenReturn(listOf(item1, item2))

        service.processQueue()

        verify(processorService).processItem(item1)
        verify(processorService).processItem(item2)
    }

    @Test
    fun `processQueue returns early when no items are pending`() {
        whenever(processorService.claimBatch(any())).thenReturn(emptyList())

        service.processQueue()

        verify(processorService, never()).processItem(any())
    }

    @Test
    fun `recoverStaleItems releases each stale item back to pending`() {
        val stale1 = ExecutionQueueFactory.createIdentityMatchJob(workspaceId = workspaceId, entityId = entityId)
        val stale2 = ExecutionQueueFactory.createIdentityMatchJob(workspaceId = workspaceId, entityId = UUID.randomUUID())
        whenever(executionQueueRepository.findStaleClaimedByJobType(any(), any())).thenReturn(listOf(stale1, stale2))

        service.recoverStaleItems()

        verify(workflowExecutionQueueService).releaseToPending(stale1)
        verify(workflowExecutionQueueService).releaseToPending(stale2)
    }

    @Test
    fun `recoverStaleItems returns early when no stale items exist`() {
        whenever(executionQueueRepository.findStaleClaimedByJobType(any(), any())).thenReturn(emptyList())

        service.recoverStaleItems()

        verify(workflowExecutionQueueService, never()).releaseToPending(any())
    }
}
