package riven.core.service.enrichment

import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowStub
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.MockedStatic
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.support.TransactionSynchronizationManager
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.entity.workflow.ExecutionQueueEntity
import riven.core.enums.integration.SourceType
import riven.core.enums.workflow.ExecutionQueueStatus
import riven.core.repository.entity.EntityRepository
import riven.core.repository.workflow.ExecutionQueueRepository
import riven.core.service.auth.AuthTokenService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import riven.core.service.util.factory.entity.EntityFactory
import riven.core.service.util.factory.workflow.ExecutionQueueFactory
import riven.core.service.workflow.enrichment.EnrichmentWorkflow
import riven.core.enums.workspace.WorkspaceRoles
import java.util.*
import kotlin.reflect.full.primaryConstructor

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        EnrichmentQueueService::class,
    ]
)
@WithUserPersona(
    userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
    email = "test@example.com",
    displayName = "Test User",
    roles = [
        WorkspaceRole(
            workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
            role = WorkspaceRoles.ADMIN
        )
    ]
)
class EnrichmentQueueServiceTest : BaseServiceTest() {

    @MockitoBean
    private lateinit var executionQueueRepository: ExecutionQueueRepository

    @MockitoBean
    private lateinit var entityRepository: EntityRepository

    @MockitoBean
    private lateinit var workflowClient: WorkflowClient

    @Autowired
    private lateinit var enrichmentQueueService: EnrichmentQueueService

    // ------ enqueueAndProcess tests ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        displayName = "Test User",
        roles = [
            WorkspaceRole(
                workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
                role = WorkspaceRoles.ADMIN
            )
        ]
    )
    inner class EnqueueAndProcess {

        @Test
        fun `skips INTEGRATION entities and returns without saving a queue item or dispatching a workflow`() {
            val entity = EntityFactory.createEntityEntity(
                id = UUID.randomUUID(),
                workspaceId = workspaceId,
                sourceType = SourceType.INTEGRATION,
            )

            whenever(entityRepository.findByIdAndWorkspaceId(entity.id!!, workspaceId))
                .thenReturn(Optional.of(entity))

            enrichmentQueueService.enqueueAndProcess(entity.id!!, workspaceId)

            verify(executionQueueRepository, never()).save(any<ExecutionQueueEntity>())
            verify(workflowClient, never()).newWorkflowStub(any<Class<EnrichmentWorkflow>>(), any<io.temporal.client.WorkflowOptions>())
        }

        @Test
        fun `persists a PENDING queue item and dispatches Temporal IMMEDIATELY when no transaction is active`() {
            val entityId = UUID.randomUUID()
            val queueItemId = UUID.randomUUID()
            val entity = EntityFactory.createEntityEntity(
                id = entityId,
                workspaceId = workspaceId,
                sourceType = SourceType.USER_CREATED,
            )
            val savedQueueItem = ExecutionQueueFactory.createEnrichmentJob(
                id = queueItemId,
                workspaceId = workspaceId,
                entityId = entityId,
                status = ExecutionQueueStatus.PENDING,
            )

            whenever(entityRepository.findByIdAndWorkspaceId(entityId, workspaceId))
                .thenReturn(Optional.of(entity))
            whenever(executionQueueRepository.save(any<ExecutionQueueEntity>()))
                .thenReturn(savedQueueItem)

            // Mock the workflow stub so WorkflowClient.start doesn't fail
            val workflowStub = mock<EnrichmentWorkflow>()
            whenever(workflowClient.newWorkflowStub(eq(EnrichmentWorkflow::class.java), any<io.temporal.client.WorkflowOptions>()))
                .thenReturn(workflowStub)

            // No active transaction in this test — dispatch should happen immediately
            assertFalse(TransactionSynchronizationManager.isSynchronizationActive(),
                "This test assumes no active transaction synchronization")

            enrichmentQueueService.enqueueAndProcess(entityId, workspaceId)

            val captor = argumentCaptor<ExecutionQueueEntity>()
            verify(executionQueueRepository).save(captor.capture())
            assertEquals(ExecutionQueueStatus.PENDING, captor.firstValue.status)
            assertEquals(entityId, captor.firstValue.entityId)

            verify(workflowClient).newWorkflowStub(eq(EnrichmentWorkflow::class.java), any<io.temporal.client.WorkflowOptions>())
        }

        @Test
        fun `persists a PENDING queue item and dispatches AFTER COMMIT when synchronization is active`() {
            val entityId = UUID.randomUUID()
            val queueItemId = UUID.randomUUID()
            val entity = EntityFactory.createEntityEntity(
                id = entityId,
                workspaceId = workspaceId,
                sourceType = SourceType.USER_CREATED,
            )
            val savedQueueItem = ExecutionQueueFactory.createEnrichmentJob(
                id = queueItemId,
                workspaceId = workspaceId,
                entityId = entityId,
                status = ExecutionQueueStatus.PENDING,
            )

            whenever(entityRepository.findByIdAndWorkspaceId(entityId, workspaceId))
                .thenReturn(Optional.of(entity))
            whenever(executionQueueRepository.save(any<ExecutionQueueEntity>()))
                .thenReturn(savedQueueItem)

            val workflowStub = mock<EnrichmentWorkflow>()
            whenever(workflowClient.newWorkflowStub(eq(EnrichmentWorkflow::class.java), any<io.temporal.client.WorkflowOptions>()))
                .thenReturn(workflowStub)

            // Activate transaction synchronization to simulate an active transaction
            TransactionSynchronizationManager.initSynchronization()
            try {
                enrichmentQueueService.enqueueAndProcess(entityId, workspaceId)

                // Within the transaction: queue item saved, but workflow NOT dispatched yet
                verify(executionQueueRepository).save(any<ExecutionQueueEntity>())
                verify(workflowClient, never()).newWorkflowStub(any<Class<EnrichmentWorkflow>>(), any<io.temporal.client.WorkflowOptions>())
            } finally {
                TransactionSynchronizationManager.clearSynchronization()
            }
        }
    }

    // ------ enqueueByEntityType tests ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        displayName = "Test User",
        roles = [
            WorkspaceRole(
                workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
                role = WorkspaceRoles.ADMIN
            )
        ]
    )
    inner class EnqueueByEntityType {

        @Test
        fun `returns the bulk-insert count from repository and does NOT trigger a workflow`() {
            val entityTypeId = UUID.randomUUID()
            whenever(executionQueueRepository.enqueueEnrichmentByEntityType(entityTypeId, workspaceId))
                .thenReturn(7)

            val result = enrichmentQueueService.enqueueByEntityType(entityTypeId, workspaceId)

            assertEquals(7, result)
            verify(workflowClient, never()).newWorkflowStub(any<Class<EnrichmentWorkflow>>(), any<io.temporal.client.WorkflowOptions>())
        }
    }

    // ------ @PreAuthorize security tests ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        displayName = "Test User",
        roles = [
            WorkspaceRole(
                workspaceId = "00000000-0000-0000-0000-000000000099",  // Different workspace
                role = WorkspaceRoles.ADMIN
            )
        ]
    )
    inner class PreAuthorize {

        @Test
        fun `enqueueAndProcess denies access when persona lacks workspace authority`() {
            assertThrows(AccessDeniedException::class.java) {
                enrichmentQueueService.enqueueAndProcess(UUID.randomUUID(), workspaceId)
            }
        }

        @Test
        fun `enqueueByEntityType denies access when persona lacks workspace authority`() {
            assertThrows(AccessDeniedException::class.java) {
                enrichmentQueueService.enqueueByEntityType(UUID.randomUUID(), workspaceId)
            }
        }
    }

    // ------ Constructor-count assertion (ENRICH-02) ------

    /**
     * ENRICH-02: Ensures EnrichmentQueueService does not accumulate dependencies beyond the
     * documented ceiling of 8. Actual target: 4 (executionQueueRepository, entityRepository,
     * workflowClient, logger). Adding deps beyond 8 fails this test as a design gate.
     */
    @Test
    fun `EnrichmentQueueService constructor parameter count is within ceiling of 8`() {
        val paramCount = EnrichmentQueueService::class.primaryConstructor!!.parameters.size
        assertTrue(
            paramCount <= 8,
            "EnrichmentQueueService has $paramCount constructor params; ceiling is 8 (ENRICH-02)"
        )
    }
}
