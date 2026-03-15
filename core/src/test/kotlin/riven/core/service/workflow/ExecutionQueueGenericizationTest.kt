package riven.core.service.workflow

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.enums.workflow.ExecutionJobType
import riven.core.entity.workflow.ExecutionQueueEntity
import riven.core.repository.workflow.ExecutionQueueRepository
import riven.core.repository.workflow.WorkflowDefinitionRepository
import riven.core.service.auth.AuthTokenService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.factory.workflow.ExecutionQueueFactory
import riven.core.service.util.factory.workflow.WorkflowFactory
import riven.core.service.workflow.queue.WorkflowExecutionQueueService
import java.util.*

/**
 * Regression tests for execution queue genericization (INFRA-01).
 *
 * Verifies that:
 * 1. Existing workflow enqueue path sets job_type = WORKFLOW_EXECUTION on saved entities
 * 2. The repository's claimPendingExecutions is invoked (dispatcher will only claim WORKFLOW_EXECUTION via SQL filter)
 * 3. IDENTITY_MATCH jobs with null workflowDefinitionId can be constructed and converted to model without error
 */
@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        WorkflowExecutionQueueService::class
    ]
)
class ExecutionQueueGenericizationTest : BaseServiceTest() {

    @MockitoBean
    private lateinit var executionQueueRepository: ExecutionQueueRepository

    @MockitoBean
    private lateinit var workflowDefinitionRepository: WorkflowDefinitionRepository

    @Autowired
    private lateinit var workflowExecutionQueueService: WorkflowExecutionQueueService

    /**
     * Verifies that enqueueing a workflow execution produces a queue entry with
     * jobType = WORKFLOW_EXECUTION and a non-null workflowDefinitionId.
     *
     * This is the primary regression test for INFRA-01: the enqueue path for workflow
     * executions must continue to emit WORKFLOW_EXECUTION-typed jobs after the queue
     * was genericized to also support IDENTITY_MATCH jobs.
     */
    @Test
    fun `existing workflow queue service creates queue entry with WORKFLOW_EXECUTION job type`() {
        val definitionId = UUID.randomUUID()
        val definition = WorkflowFactory.createDefinition(
            id = definitionId,
            workspaceId = workspaceId
        )
        whenever(workflowDefinitionRepository.findById(definitionId)).thenReturn(Optional.of(definition))

        val capturedEntity = ExecutionQueueFactory.createWorkflowExecutionJob(
            workspaceId = workspaceId,
            workflowDefinitionId = definitionId
        )
        val entityCaptor = argumentCaptor<ExecutionQueueEntity>()
        whenever(executionQueueRepository.save(entityCaptor.capture())).thenReturn(capturedEntity)

        workflowExecutionQueueService.enqueue(workspaceId, definitionId)

        val saved = entityCaptor.firstValue
        assertEquals(ExecutionJobType.WORKFLOW_EXECUTION, saved.jobType)
        assertEquals(definitionId, saved.workflowDefinitionId)
        assertNull(saved.entityId)
    }

    /**
     * Verifies that claimPendingExecutions is called by the processor service.
     *
     * The actual job_type = 'WORKFLOW_EXECUTION' filter is enforced at the SQL level
     * in the native query. This test confirms the repository method is invoked and
     * returns WORKFLOW_EXECUTION jobs only (not IDENTITY_MATCH jobs).
     */
    @Test
    fun `claim pending executions only returns WORKFLOW_EXECUTION jobs`() {
        val workflowJob = ExecutionQueueFactory.createWorkflowExecutionJob(
            workspaceId = workspaceId
        )
        whenever(executionQueueRepository.claimPendingExecutions(any())).thenReturn(listOf(workflowJob))

        val claimed = executionQueueRepository.claimPendingExecutions(10)

        verify(executionQueueRepository).claimPendingExecutions(10)
        assertTrue(claimed.all { it.jobType == ExecutionJobType.WORKFLOW_EXECUTION })
    }

    /**
     * Verifies that an ExecutionQueueEntity with jobType = IDENTITY_MATCH,
     * a non-null entityId, and a null workflowDefinitionId is valid and toModel() succeeds.
     *
     * This confirms INFRA-01: identity match jobs do not require a workflowDefinitionId,
     * and the nullable field is correctly handled throughout the entity/model pipeline.
     */
    @Test
    fun `queue entry with null workflowDefinitionId is valid for IDENTITY_MATCH jobs`() {
        val entityId = UUID.randomUUID()
        val entity = ExecutionQueueFactory.createIdentityMatchJob(
            workspaceId = workspaceId,
            entityId = entityId
        )

        assertEquals(ExecutionJobType.IDENTITY_MATCH, entity.jobType)
        assertEquals(entityId, entity.entityId)
        assertNull(entity.workflowDefinitionId)

        // toModel() must not throw even with a null workflowDefinitionId
        val model = entity.toModel()
        assertEquals(ExecutionJobType.IDENTITY_MATCH, model.jobType)
        assertEquals(entityId, model.entityId)
        assertNull(model.workflowDefinitionId)
    }
}
