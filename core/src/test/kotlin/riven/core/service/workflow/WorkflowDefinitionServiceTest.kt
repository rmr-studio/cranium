package cranium.core.service.workflow

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.context.bean.override.mockito.MockitoBean
import cranium.core.configuration.auth.WorkspaceSecurity
import cranium.core.entity.workflow.WorkflowDefinitionEntity
import cranium.core.entity.workflow.WorkflowDefinitionVersionEntity
import cranium.core.enums.activity.Activity
import cranium.core.enums.common.icon.IconColour
import cranium.core.enums.common.icon.IconType
import cranium.core.enums.util.OperationType
import cranium.core.enums.workflow.WorkflowDefinitionStatus
import cranium.core.exceptions.NotFoundException
import cranium.core.models.request.workflow.CreateWorkflowDefinitionRequest
import cranium.core.models.request.workflow.UpdateWorkflowDefinitionRequest
import cranium.core.repository.workflow.WorkflowDefinitionRepository
import cranium.core.repository.workflow.WorkflowDefinitionVersionRepository
import cranium.core.service.activity.ActivityService
import cranium.core.service.auth.AuthTokenService
import cranium.core.service.util.BaseServiceTest
import cranium.core.service.util.SecurityTestConfig
import cranium.core.service.util.factory.workflow.WorkflowFactory
import cranium.core.repository.workflow.WorkflowEdgeRepository
import cranium.core.repository.workflow.WorkflowNodeRepository
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        WorkflowDefinitionService::class
    ]
)
class WorkflowDefinitionServiceTest : BaseServiceTest() {

    @MockitoBean
    private lateinit var workflowDefinitionRepository: WorkflowDefinitionRepository

    @MockitoBean
    private lateinit var workflowDefinitionVersionRepository: WorkflowDefinitionVersionRepository

    @MockitoBean
    private lateinit var activityService: ActivityService

    @MockitoBean
    private lateinit var workflowNodeRepository: WorkflowNodeRepository

    @MockitoBean
    private lateinit var workflowEdgeRepository: WorkflowEdgeRepository

    @Autowired
    private lateinit var workflowDefinitionService: WorkflowDefinitionService

    // ------------------------------------------------------------------
    // createWorkflow tests
    // ------------------------------------------------------------------

    @Test
    fun `createWorkflow_success_createsDefinitionAndVersion`() {
        // Arrange
        val definitionId = UUID.randomUUID()
        val versionId = UUID.randomUUID()

        val request = CreateWorkflowDefinitionRequest(
            name = "Test Workflow",
            description = "A test workflow",
            iconColour = IconColour.BLUE,
            iconType = IconType.WORKFLOW,
            tags = listOf("test", "automation")
        )

        val savedDefinition = WorkflowFactory.createDefinition(
            id = definitionId,
            workspaceId = workspaceId,
            name = request.name,
            description = request.description,
            versionNumber = 1,
            status = WorkflowDefinitionStatus.DRAFT,
            iconColour = request.iconColour,
            iconType = request.iconType,
            tags = request.tags
        )

        val savedVersion = WorkflowFactory.createVersion(
            id = versionId,
            workspaceId = workspaceId,
            workflowDefinitionId = definitionId,
            versionNumber = 1
        )

        whenever(workflowDefinitionRepository.save(any<WorkflowDefinitionEntity>())).thenReturn(savedDefinition)
        whenever(workflowDefinitionVersionRepository.save(any<WorkflowDefinitionVersionEntity>())).thenReturn(
            savedVersion
        )

        // Act
        val result = workflowDefinitionService.createWorkflow(workspaceId, request)

        // Assert
        assertEquals(definitionId, result.id)
        assertEquals(request.name, result.name)
        assertEquals(request.description, result.description)
        assertEquals(1, result.definition.version)
        assertEquals(WorkflowDefinitionStatus.DRAFT, result.status)

        // Verify repository calls
        verify(workflowDefinitionRepository, times(1)).save(any<WorkflowDefinitionEntity>())
        verify(workflowDefinitionVersionRepository, times(1)).save(any<WorkflowDefinitionVersionEntity>())

        // Verify activity logging
        verify(activityService).logActivity(
            activity = eq(Activity.WORKFLOW_DEFINITION),
            operation = eq(OperationType.CREATE),
            userId = any(),
            workspaceId = eq(workspaceId),
            entityType = any(),
            entityId = eq(definitionId),
            timestamp = any(),
            details = any()
        )
    }

    // ------------------------------------------------------------------
    // getWorkflowById tests
    // ------------------------------------------------------------------

    @Test
    fun `getWorkflowById_success_returnsWorkflow`() {
        // Arrange
        val definitionId = UUID.randomUUID()
        val definition = WorkflowFactory.createDefinition(
            id = definitionId,
            workspaceId = workspaceId,
            name = "Test Workflow",
            versionNumber = 1
        )
        val version = WorkflowFactory.createVersion(
            workspaceId = workspaceId,
            workflowDefinitionId = definitionId,
            versionNumber = 1
        )

        whenever(workflowDefinitionRepository.findById(definitionId)).thenReturn(Optional.of(definition))
        whenever(workflowDefinitionVersionRepository.findByWorkflowDefinitionIdAndVersionNumber(definitionId, 1))
            .thenReturn(version)

        // Act
        val result = workflowDefinitionService.getWorkflowById(definitionId, workspaceId)

        // Assert
        assertEquals(definitionId, result.id)
        assertEquals("Test Workflow", result.name)
        assertEquals(workspaceId, result.workspaceId)
    }

    @Test
    fun `getWorkflowById_notFound_throwsNotFoundException`() {
        // Arrange
        val definitionId = UUID.randomUUID()
        whenever(workflowDefinitionRepository.findById(definitionId)).thenReturn(Optional.empty())

        // Act & Assert
        assertThrows<NotFoundException> {
            workflowDefinitionService.getWorkflowById(definitionId, workspaceId)
        }
    }

    @Test
    fun `getWorkflowById_wrongWorkspace_throwsAccessDeniedException`() {
        // Arrange
        val definitionId = UUID.randomUUID()
        val differentWorkspaceId = UUID.randomUUID()
        val definition = WorkflowFactory.createDefinition(
            id = definitionId,
            workspaceId = differentWorkspaceId,
            name = "Test Workflow"
        )

        whenever(workflowDefinitionRepository.findById(definitionId)).thenReturn(Optional.of(definition))

        // Act & Assert
        assertThrows<AccessDeniedException> {
            workflowDefinitionService.getWorkflowById(definitionId, workspaceId)
        }
    }

    // ------------------------------------------------------------------
    // listWorkflowsForWorkspace tests
    // ------------------------------------------------------------------

    @Test
    fun `listWorkflowsForWorkspace_success_returnsList`() {
        // Arrange
        val definitions = (1..3).map { i ->
            val defId = UUID.randomUUID()
            WorkflowFactory.createDefinition(
                id = defId,
                workspaceId = workspaceId,
                name = "Workflow $i",
                versionNumber = 1
            ) to WorkflowFactory.createVersion(
                workspaceId = workspaceId,
                workflowDefinitionId = defId,
                versionNumber = 1
            )
        }

        whenever(workflowDefinitionRepository.findByWorkspaceId(workspaceId))
            .thenReturn(definitions.map { it.first })

        definitions.forEach { (def, version) ->
            whenever(workflowDefinitionVersionRepository.findByWorkflowDefinitionIdAndVersionNumber(def.id!!, 1))
                .thenReturn(version)
        }

        // Act
        val result = workflowDefinitionService.listWorkflowsForWorkspace(workspaceId)

        // Assert
        assertEquals(3, result.size)
        assertTrue(result.all { it.workspaceId == workspaceId })
    }

    // ------------------------------------------------------------------
    // updateWorkflow tests
    // ------------------------------------------------------------------

    @Test
    fun `updateWorkflow_success_updatesMetadata`() {
        // Arrange
        val definitionId = UUID.randomUUID()
        val definition = WorkflowFactory.createDefinition(
            id = definitionId,
            workspaceId = workspaceId,
            name = "Original Name",
            description = "Original Description",
            iconColour = IconColour.NEUTRAL,
            iconType = IconType.FILE,
            tags = listOf("original")
        )
        val version = WorkflowFactory.createVersion(
            workspaceId = workspaceId,
            workflowDefinitionId = definitionId,
            versionNumber = 1
        )

        val request = UpdateWorkflowDefinitionRequest(
            name = "Updated Name",
            description = "Updated Description",
            iconColour = IconColour.GREEN,
            iconType = IconType.WORKFLOW,
            tags = listOf("updated", "new")
        )

        whenever(workflowDefinitionRepository.findById(definitionId)).thenReturn(Optional.of(definition))
        whenever(workflowDefinitionRepository.save(any<WorkflowDefinitionEntity>())).thenAnswer { invocation ->
            invocation.arguments[0] as WorkflowDefinitionEntity
        }
        whenever(workflowDefinitionVersionRepository.findByWorkflowDefinitionIdAndVersionNumber(definitionId, 1))
            .thenReturn(version)

        // Act
        val result = workflowDefinitionService.updateWorkflow(definitionId, workspaceId, request)

        // Assert
        assertEquals("Updated Name", result.name)
        assertEquals("Updated Description", result.description)
        assertEquals(IconColour.GREEN, result.icon.colour)
        assertEquals(IconType.WORKFLOW, result.icon.type)
        assertEquals(listOf("updated", "new"), result.tags)

        // Verify repository save was called
        verify(workflowDefinitionRepository).save(any<WorkflowDefinitionEntity>())

        // Verify activity logging
        verify(activityService).logActivity(
            activity = eq(Activity.WORKFLOW_DEFINITION),
            operation = eq(OperationType.UPDATE),
            userId = any(),
            workspaceId = eq(workspaceId),
            entityType = any(),
            entityId = eq(definitionId),
            timestamp = any(),
            details = any()
        )
    }

    // ------------------------------------------------------------------
    // deleteWorkflow tests
    // ------------------------------------------------------------------

    @Test
    fun `deleteWorkflow_success_softDeletes`() {
        // Arrange
        val definitionId = UUID.randomUUID()
        val definition = WorkflowFactory.createDefinition(
            id = definitionId,
            workspaceId = workspaceId,
            name = "Workflow to Delete",
            deleted = false
        )

        whenever(workflowDefinitionRepository.findById(definitionId)).thenReturn(Optional.of(definition))
        whenever(workflowDefinitionRepository.save(any<WorkflowDefinitionEntity>())).thenAnswer { invocation ->
            invocation.arguments[0] as WorkflowDefinitionEntity
        }

        // Act
        workflowDefinitionService.deleteWorkflow(definitionId, workspaceId)

        // Assert - verify save was called with deleted=true
        val captor = argumentCaptor<WorkflowDefinitionEntity>()
        verify(workflowDefinitionRepository).save(captor.capture())
        assertTrue(captor.firstValue.deleted)
        assertNotNull(captor.firstValue.deletedAt)

        // Verify activity logging
        verify(activityService).logActivity(
            activity = eq(Activity.WORKFLOW_DEFINITION),
            operation = eq(OperationType.DELETE),
            userId = any(),
            workspaceId = eq(workspaceId),
            entityType = any(),
            entityId = eq(definitionId),
            timestamp = any(),
            details = any()
        )
    }

    @Test
    fun `deleteWorkflow_notFound_throwsNotFoundException`() {
        // Arrange
        val definitionId = UUID.randomUUID()
        whenever(workflowDefinitionRepository.findById(definitionId)).thenReturn(Optional.empty())

        // Act & Assert
        assertThrows<NotFoundException> {
            workflowDefinitionService.deleteWorkflow(definitionId, workspaceId)
        }
    }

    @Test
    fun `deleteWorkflow_alreadyDeleted_throwsNotFoundException`() {
        // Arrange
        val definitionId = UUID.randomUUID()
        val definition = WorkflowFactory.createDefinition(
            id = definitionId,
            workspaceId = workspaceId,
            name = "Already Deleted Workflow",
            deleted = true
        )

        whenever(workflowDefinitionRepository.findById(definitionId)).thenReturn(Optional.of(definition))

        // Act & Assert
        assertThrows<NotFoundException> {
            workflowDefinitionService.deleteWorkflow(definitionId, workspaceId)
        }

        // Verify save was NOT called
        verify(workflowDefinitionRepository, never()).save(any<WorkflowDefinitionEntity>())
    }
}
