package cranium.core.service.identity

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.bean.override.mockito.MockitoBean
import cranium.core.enums.workflow.ExecutionJobType
import cranium.core.enums.workflow.ExecutionQueueStatus
import cranium.core.repository.workflow.ExecutionQueueRepository
import cranium.core.service.util.factory.workflow.ExecutionQueueFactory
import java.util.UUID

/**
 * Unit tests for [IdentityMatchQueueService].
 *
 * Verifies that IDENTITY_MATCH jobs are created with correct fields and that
 * duplicate enqueue attempts (signaled by DataIntegrityViolationException from the
 * dedup partial unique index) are silently swallowed.
 */
@SpringBootTest(classes = [IdentityMatchQueueService::class])
class IdentityMatchQueueServiceTest {

    @MockitoBean
    private lateinit var executionQueueRepository: ExecutionQueueRepository

    @MockitoBean
    private lateinit var logger: KLogger

    @Autowired
    private lateinit var service: IdentityMatchQueueService

    private val entityId = UUID.fromString("11111111-0000-0000-0000-000000000001")
    private val workspaceId = UUID.fromString("22222222-0000-0000-0000-000000000002")

    @Test
    fun `enqueueIfNotPending saves IDENTITY_MATCH job with correct jobType and status`() {
        val savedEntity = ExecutionQueueFactory.createIdentityMatchJob(
            workspaceId = workspaceId,
            entityId = entityId,
            status = ExecutionQueueStatus.PENDING,
        )
        whenever(executionQueueRepository.save(any())).thenReturn(savedEntity)

        service.enqueueIfNotPending(entityId, workspaceId)

        val captor = argumentCaptor<cranium.core.entity.workflow.ExecutionQueueEntity>()
        verify(executionQueueRepository).save(captor.capture())

        val saved = captor.firstValue
        assertEquals(ExecutionJobType.IDENTITY_MATCH, saved.jobType)
        assertEquals(ExecutionQueueStatus.PENDING, saved.status)
        assertEquals(entityId, saved.entityId)
        assertEquals(workspaceId, saved.workspaceId)
    }

    @Test
    fun `enqueueIfNotPending silently returns when DataIntegrityViolationException is thrown for dedup constraint`() {
        whenever(executionQueueRepository.save(any()))
            .thenThrow(DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uq_execution_queue_pending_identity_match\""
            ))

        // Should not throw — dedup exception is caught and swallowed
        service.enqueueIfNotPending(entityId, workspaceId)

        // Repository was called (attempt was made)
        verify(executionQueueRepository).save(any())
    }

    @Test
    fun `enqueueIfNotPending re-throws DataIntegrityViolationException for unrelated constraint`() {
        whenever(executionQueueRepository.save(any()))
            .thenThrow(DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"some_other_constraint\""
            ))

        org.junit.jupiter.api.assertThrows<DataIntegrityViolationException> {
            service.enqueueIfNotPending(entityId, workspaceId)
        }
    }

    @Test
    fun `enqueueIfNotPending does not set workflowDefinitionId on IDENTITY_MATCH job`() {
        val savedEntity = ExecutionQueueFactory.createIdentityMatchJob(
            workspaceId = workspaceId,
            entityId = entityId,
        )
        whenever(executionQueueRepository.save(any())).thenReturn(savedEntity)

        service.enqueueIfNotPending(entityId, workspaceId)

        val captor = argumentCaptor<cranium.core.entity.workflow.ExecutionQueueEntity>()
        verify(executionQueueRepository).save(captor.capture())
        assertEquals(null, captor.firstValue.workflowDefinitionId)
    }
}
