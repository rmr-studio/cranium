package cranium.core.service.workflow

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
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
import cranium.core.entity.workflow.WorkflowEdgeEntity
import cranium.core.entity.workflow.WorkflowNodeEntity
import cranium.core.enums.activity.Activity
import cranium.core.enums.util.OperationType
import cranium.core.enums.workflow.WorkflowDefinitionStatus
import cranium.core.enums.workflow.WorkflowNodeType
import cranium.core.exceptions.NotFoundException
import cranium.core.models.request.workflow.CreateWorkflowEdgeRequest
import cranium.core.models.request.workflow.CreateWorkflowNodeRequest
import cranium.core.models.request.workflow.UpdateWorkflowNodeRequest
import cranium.core.models.workflow.WorkflowGraphReference
import cranium.core.models.workflow.node.config.WorkflowNodeConfig
import cranium.core.models.workflow.node.config.actions.WorkflowCreateEntityActionConfig
import cranium.core.models.workflow.node.config.validation.ConfigValidationResult
import cranium.core.repository.workflow.WorkflowDefinitionRepository
import cranium.core.repository.workflow.WorkflowDefinitionVersionRepository
import cranium.core.repository.workflow.WorkflowEdgeRepository
import cranium.core.repository.workflow.WorkflowNodeRepository
import cranium.core.service.activity.ActivityService
import cranium.core.service.auth.AuthTokenService
import cranium.core.service.util.BaseServiceTest
import cranium.core.service.util.SecurityTestConfig
import cranium.core.service.workflow.state.WorkflowNodeConfigValidationService
import cranium.core.service.workflow.state.WorkflowNodeExpressionParserService
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        WorkflowGraphService::class
    ]
)
class WorkflowGraphServiceTest : BaseServiceTest() {

    @MockitoBean
    private lateinit var workflowNodeRepository: WorkflowNodeRepository

    @MockitoBean
    private lateinit var workflowEdgeRepository: WorkflowEdgeRepository

    @MockitoBean
    private lateinit var workflowNodeServiceInjectionProvider: WorkflowNodeServiceInjectionProvider

    @MockitoBean
    private lateinit var workflowDefinitionRepository: WorkflowDefinitionRepository

    @MockitoBean
    private lateinit var workflowDefinitionVersionRepository: WorkflowDefinitionVersionRepository

    @MockitoBean
    private lateinit var activityService: ActivityService

    @MockitoBean
    private lateinit var workflowNodeConfigValidationService: WorkflowNodeConfigValidationService

    @MockitoBean
    private lateinit var workflowNodeExpressionParserService: WorkflowNodeExpressionParserService

    @Autowired
    private lateinit var workflowGraphService: WorkflowGraphService

    @BeforeEach
    fun setUp() {
        // Wire up the service provider to return the mocked validation service
        whenever(workflowNodeServiceInjectionProvider.get(WorkflowNodeConfigValidationService::class))
            .thenReturn(workflowNodeConfigValidationService)

        // Mock validation service to return valid result for all configs
        whenever(workflowNodeConfigValidationService.validateTemplateOrUuid(any(), any()))
            .thenReturn(emptyList())
        whenever(workflowNodeConfigValidationService.validateTemplateMap(any(), any()))
            .thenReturn(emptyList())
        whenever(workflowNodeConfigValidationService.validateOptionalDuration(any(), any()))
            .thenReturn(emptyList())
        whenever(workflowNodeConfigValidationService.combine(any(), any(), any()))
            .thenReturn(ConfigValidationResult.valid())
    }

    /**
     * Creates a test WorkflowNodeConfig using a real implementation.
     */
    private fun createTestConfig(): WorkflowNodeConfig = WorkflowCreateEntityActionConfig(
        version = 1,
        entityTypeId = "550e8400-e29b-41d4-a716-446655440000",
        payload = emptyMap()
    )

    // ------------------------------------------------------------------
    // createNode tests
    // ------------------------------------------------------------------

    @Test
    fun `createNode_success_createsNodeWithVersion1`() {
        // Arrange
        val nodeId = UUID.randomUUID()
        val request = CreateWorkflowNodeRequest(
            key = "test-node",
            name = "Test Node",
            description = "A test node",
            config = createTestConfig()
        )

        val savedNode = createNodeEntity(
            id = nodeId,
            workspaceId = workspaceId,
            key = request.key,
            name = request.name,
            description = request.description,
            config = request.config,
            version = 1
        )

        whenever(workflowNodeRepository.save(any<WorkflowNodeEntity>())).thenReturn(savedNode)

        // Act
        val result = workflowGraphService.createNode(workspaceId, request)

        // Assert
        assertEquals(nodeId, result.id)
        assertEquals(request.key, result.key)
        assertEquals(request.name, result.name)
        assertEquals(request.description, result.description)
        assertEquals(WorkflowNodeType.ACTION, result.type)
        assertEquals(1, result.version)

        // Verify repository call
        verify(workflowNodeRepository).save(argThat<WorkflowNodeEntity> {
            this.key == request.key &&
                    this.name == request.name &&
                    this.version == 1 &&
                    !this.system &&
                    !this.deleted
        })

        // Verify activity logging
        verify(activityService).logActivity(
            activity = eq(Activity.WORKFLOW_NODE),
            operation = eq(OperationType.CREATE),
            userId = any(),
            workspaceId = eq(workspaceId),
            entityType = any(),
            entityId = eq(nodeId),
            timestamp = any(),
            details = any()
        )
    }

    // ------------------------------------------------------------------
    // updateNode tests
    // ------------------------------------------------------------------

    @Test
    fun `updateNode_metadataOnly_updatesInPlace`() {
        // Arrange
        val nodeId = UUID.randomUUID()
        val existingNode = createNodeEntity(
            id = nodeId,
            workspaceId = workspaceId,
            key = "test-node",
            name = "Original Name",
            description = "Original Description",
            config = createTestConfig(),
            version = 1
        )

        val request = UpdateWorkflowNodeRequest(
            name = "Updated Name",
            description = "Updated Description",
            config = null // No config change
        )

        whenever(workflowNodeRepository.findByWorkspaceIdAndId(workspaceId, nodeId)).thenReturn(existingNode)
        whenever(workflowNodeRepository.save(any<WorkflowNodeEntity>())).thenAnswer { invocation ->
            invocation.arguments[0] as WorkflowNodeEntity
        }

        // Act
        val result = workflowGraphService.updateNode(nodeId, workspaceId, request)

        // Assert
        assertEquals("Updated Name", result.name)
        assertEquals("Updated Description", result.description)
        assertEquals(1, result.version) // Version unchanged for metadata-only update

        // Verify save was called (in-place update)
        verify(workflowNodeRepository).save(argThat<WorkflowNodeEntity> {
            this.name == "Updated Name" &&
                    this.description == "Updated Description" &&
                    this.version == 1 // No version increment
        })
    }

    @Test
    fun `updateNode_configChange_createsNewVersion`() {
        // Arrange
        val nodeId = UUID.randomUUID()
        val newNodeId = UUID.randomUUID()
        val existingNode = createNodeEntity(
            id = nodeId,
            workspaceId = workspaceId,
            key = "test-node",
            name = "Original Name",
            config = createTestConfig(),
            version = 1
        )

        val newConfig = createTestConfig()
        val request = UpdateWorkflowNodeRequest(
            name = "Updated Name",
            config = newConfig // Config change triggers new version
        )

        whenever(workflowNodeRepository.findByWorkspaceIdAndId(workspaceId, nodeId)).thenReturn(existingNode)

        // Mock save to handle both calls: return the entity passed in, but assign newNodeId to new version
        whenever(workflowNodeRepository.save(any<WorkflowNodeEntity>())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as WorkflowNodeEntity
            if (entity.deleted) {
                // First call: soft delete old node - return as-is
                entity
            } else {
                // Second call: new version - assign new ID but preserve all other fields including version
                entity.copy(id = newNodeId)
            }
        }

        // Act
        val result = workflowGraphService.updateNode(nodeId, workspaceId, request)

        // Assert
        assertEquals("Updated Name", result.name)
        // Note: result.version is the config schema version (1), not entity version
        assertEquals(newNodeId, result.id) // Should have new ID

        // Verify save was called twice
        verify(workflowNodeRepository, times(2)).save(any<WorkflowNodeEntity>())

        // Verify using argument captor to check both calls
        val nodeCaptor = argumentCaptor<WorkflowNodeEntity>()
        verify(workflowNodeRepository, times(2)).save(nodeCaptor.capture())

        // First call should be soft delete of old node
        val deletedNode = nodeCaptor.allValues.find { it.deleted }
        assertNotNull(deletedNode)
        assertEquals(nodeId, deletedNode!!.id)

        // Second call should be new version with incremented entity version
        val newVersionNode = nodeCaptor.allValues.find { !it.deleted }
        assertNotNull(newVersionNode)
        assertEquals(2, newVersionNode!!.version) // Entity version incremented
        assertEquals(nodeId, newVersionNode.sourceId) // Links to original
        assertNull(newVersionNode.id) // ID is null before persistence (JPA generates)
    }

    @Test
    fun `updateNode_notFound_throwsNotFoundException`() {
        // Arrange
        val nodeId = UUID.randomUUID()
        val request = UpdateWorkflowNodeRequest(name = "Updated")

        whenever(workflowNodeRepository.findByWorkspaceIdAndId(workspaceId, nodeId)).thenReturn(null)

        // Act & Assert
        assertThrows<NotFoundException> {
            workflowGraphService.updateNode(nodeId, workspaceId, request)
        }
    }

    // ------------------------------------------------------------------
    // deleteNode tests (CASCADE DELETION)
    // ------------------------------------------------------------------

    @Test
    fun `deleteNode_withEdges_cascadesDelete`() {
        // Arrange
        val nodeId = UUID.randomUUID()
        val edge1Id = UUID.randomUUID()
        val edge2Id = UUID.randomUUID()
        val otherNodeId = UUID.randomUUID()

        val existingNode = createNodeEntity(
            id = nodeId,
            workspaceId = workspaceId,
            key = "test-node",
            name = "Node to Delete",
            config = createTestConfig()
        )

        val edge1 = createEdgeEntity(
            id = edge1Id,
            workspaceId = workspaceId,
            sourceNodeId = nodeId,
            targetNodeId = otherNodeId
        )

        val edge2 = createEdgeEntity(
            id = edge2Id,
            workspaceId = workspaceId,
            sourceNodeId = otherNodeId,
            targetNodeId = nodeId
        )

        whenever(workflowNodeRepository.findByWorkspaceIdAndId(workspaceId, nodeId)).thenReturn(existingNode)
        whenever(workflowEdgeRepository.findByWorkspaceIdAndNodeId(workspaceId, nodeId)).thenReturn(
            listOf(
                edge1,
                edge2
            )
        )
        whenever(workflowEdgeRepository.save(any<WorkflowEdgeEntity>())).thenAnswer { invocation ->
            invocation.arguments[0] as WorkflowEdgeEntity
        }
        whenever(workflowNodeRepository.save(any<WorkflowNodeEntity>())).thenAnswer { invocation ->
            invocation.arguments[0] as WorkflowNodeEntity
        }

        // Act
        workflowGraphService.deleteNode(nodeId, workspaceId)

        // Assert - verify edges were soft deleted
        val edgeCaptor = argumentCaptor<WorkflowEdgeEntity>()
        verify(workflowEdgeRepository, times(2)).save(edgeCaptor.capture())
        assertTrue(edgeCaptor.allValues.all { it.deleted })

        // Assert - verify node was soft deleted
        val nodeCaptor = argumentCaptor<WorkflowNodeEntity>()
        verify(workflowNodeRepository).save(nodeCaptor.capture())
        assertTrue(nodeCaptor.firstValue.deleted)
        assertNotNull(nodeCaptor.firstValue.deletedAt)

        // Assert - verify activity logs (1 node + 2 edges = 3 total)
        verify(activityService, times(3)).logActivity(
            activity = any(),
            operation = eq(OperationType.DELETE),
            userId = any(),
            workspaceId = eq(workspaceId),
            entityType = any(),
            entityId = any(),
            timestamp = any(),
            details = any()
        )
    }

    @Test
    fun `deleteNode_noEdges_deletesOnlyNode`() {
        // Arrange
        val nodeId = UUID.randomUUID()
        val existingNode = createNodeEntity(
            id = nodeId,
            workspaceId = workspaceId,
            key = "isolated-node",
            name = "Isolated Node",
            config = createTestConfig()
        )

        whenever(workflowNodeRepository.findByWorkspaceIdAndId(workspaceId, nodeId)).thenReturn(existingNode)
        whenever(workflowEdgeRepository.findByWorkspaceIdAndNodeId(workspaceId, nodeId)).thenReturn(emptyList())
        whenever(workflowNodeRepository.save(any<WorkflowNodeEntity>())).thenAnswer { invocation ->
            invocation.arguments[0] as WorkflowNodeEntity
        }

        // Act
        workflowGraphService.deleteNode(nodeId, workspaceId)

        // Assert - verify no edges were saved
        verify(workflowEdgeRepository, never()).save(any<WorkflowEdgeEntity>())

        // Assert - verify node was deleted
        verify(workflowNodeRepository).save(argThat<WorkflowNodeEntity> { deleted })

        // Assert - only 1 activity log (node only)
        verify(activityService, times(1)).logActivity(
            activity = eq(Activity.WORKFLOW_NODE),
            operation = eq(OperationType.DELETE),
            userId = any(),
            workspaceId = eq(workspaceId),
            entityType = any(),
            entityId = eq(nodeId),
            timestamp = any(),
            details = any()
        )
    }

    // ------------------------------------------------------------------
    // createEdge tests
    // ------------------------------------------------------------------

    @Test
    fun `createEdge_success_validatesNodesExist`() {
        // Arrange
        val sourceNodeId = UUID.randomUUID()
        val targetNodeId = UUID.randomUUID()
        val edgeId = UUID.randomUUID()

        val sourceNode = createNodeEntity(
            id = sourceNodeId,
            workspaceId = workspaceId,
            key = "source-node",
            name = "Source Node",
            config = createTestConfig()
        )

        val targetNode = createNodeEntity(
            id = targetNodeId,
            workspaceId = workspaceId,
            key = "target-node",
            name = "Target Node",
            config = createTestConfig()
        )

        val request = CreateWorkflowEdgeRequest(
            sourceNodeId = sourceNodeId,
            targetNodeId = targetNodeId,
            label = "condition"
        )

        whenever(workflowNodeRepository.findByWorkspaceIdAndId(workspaceId, sourceNodeId)).thenReturn(sourceNode)
        whenever(workflowNodeRepository.findByWorkspaceIdAndId(workspaceId, targetNodeId)).thenReturn(targetNode)
        whenever(workflowEdgeRepository.save(any<WorkflowEdgeEntity>())).thenAnswer { invocation ->
            (invocation.arguments[0] as WorkflowEdgeEntity).copy(id = edgeId)
        }

        // Act
        val result = workflowGraphService.createEdge(workspaceId, request)

        // Assert
        assertEquals(edgeId, result.id)
        assertEquals("condition", result.label)
        assertEquals(sourceNodeId, result.source.id)
        assertEquals(targetNodeId, result.target.id)

        // Verify edge was saved
        verify(workflowEdgeRepository).save(argThat<WorkflowEdgeEntity> {
            this.sourceNodeId == sourceNodeId &&
                    this.targetNodeId == targetNodeId &&
                    this.label == "condition" &&
                    !this.deleted
        })

        // Verify activity logging
        verify(activityService).logActivity(
            activity = eq(Activity.WORKFLOW_EDGE),
            operation = eq(OperationType.CREATE),
            userId = any(),
            workspaceId = eq(workspaceId),
            entityType = any(),
            entityId = eq(edgeId),
            timestamp = any(),
            details = any()
        )
    }

    @Test
    fun `createEdge_sourceNotFound_throwsNotFoundException`() {
        // Arrange
        val sourceNodeId = UUID.randomUUID()
        val targetNodeId = UUID.randomUUID()

        val request = CreateWorkflowEdgeRequest(
            sourceNodeId = sourceNodeId,
            targetNodeId = targetNodeId
        )

        whenever(workflowNodeRepository.findByWorkspaceIdAndId(workspaceId, sourceNodeId)).thenReturn(null)

        // Act & Assert
        val exception = assertThrows<NotFoundException> {
            workflowGraphService.createEdge(workspaceId, request)
        }
        assertTrue(exception.message?.contains("Source") ?: false)
    }

    @Test
    fun `createEdge_targetNotFound_throwsNotFoundException`() {
        // Arrange
        val sourceNodeId = UUID.randomUUID()
        val targetNodeId = UUID.randomUUID()

        val sourceNode = createNodeEntity(
            id = sourceNodeId,
            workspaceId = workspaceId,
            key = "source-node",
            name = "Source Node",
            config = createTestConfig()
        )

        val request = CreateWorkflowEdgeRequest(
            sourceNodeId = sourceNodeId,
            targetNodeId = targetNodeId
        )

        whenever(workflowNodeRepository.findByWorkspaceIdAndId(workspaceId, sourceNodeId)).thenReturn(sourceNode)
        whenever(workflowNodeRepository.findByWorkspaceIdAndId(workspaceId, targetNodeId)).thenReturn(null)

        // Act & Assert
        val exception = assertThrows<NotFoundException> {
            workflowGraphService.createEdge(workspaceId, request)
        }
        assertTrue(exception.message?.contains("Target") ?: false)
    }

    // ------------------------------------------------------------------
    // deleteEdge tests
    // ------------------------------------------------------------------

    @Test
    fun `deleteEdge_success_softDeletes`() {
        // Arrange
        val edgeId = UUID.randomUUID()
        val existingEdge = createEdgeEntity(
            id = edgeId,
            workspaceId = workspaceId,
            sourceNodeId = UUID.randomUUID(),
            targetNodeId = UUID.randomUUID()
        )

        whenever(workflowEdgeRepository.findById(edgeId)).thenReturn(Optional.of(existingEdge))
        whenever(workflowEdgeRepository.save(any<WorkflowEdgeEntity>())).thenAnswer { invocation ->
            invocation.arguments[0] as WorkflowEdgeEntity
        }

        // Act
        workflowGraphService.deleteEdge(edgeId, workspaceId)

        // Assert - verify edge was soft deleted
        val captor = argumentCaptor<WorkflowEdgeEntity>()
        verify(workflowEdgeRepository).save(captor.capture())
        assertTrue(captor.firstValue.deleted)
        assertNotNull(captor.firstValue.deletedAt)

        // Verify activity logging
        verify(activityService).logActivity(
            activity = eq(Activity.WORKFLOW_EDGE),
            operation = eq(OperationType.DELETE),
            userId = any(),
            workspaceId = eq(workspaceId),
            entityType = any(),
            entityId = eq(edgeId),
            timestamp = any(),
            details = any()
        )
    }

    @Test
    fun `deleteEdge_wrongWorkspace_throwsAccessDeniedException`() {
        // Arrange
        val edgeId = UUID.randomUUID()
        val differentWorkspaceId = UUID.randomUUID()
        val existingEdge = createEdgeEntity(
            id = edgeId,
            workspaceId = differentWorkspaceId, // Different workspace
            sourceNodeId = UUID.randomUUID(),
            targetNodeId = UUID.randomUUID()
        )

        whenever(workflowEdgeRepository.findById(edgeId)).thenReturn(Optional.of(existingEdge))

        // Act & Assert
        assertThrows<AccessDeniedException> {
            workflowGraphService.deleteEdge(edgeId, workspaceId)
        }
    }

    // ------------------------------------------------------------------
    // getWorkflowGraph tests
    // ------------------------------------------------------------------

    @Test
    fun `getWorkflowGraph_success_returnsNodesAndEdges`() {
        // Arrange
        val workflowDefinitionId = UUID.randomUUID()
        val node1Id = UUID.randomUUID()
        val node2Id = UUID.randomUUID()
        val node3Id = UUID.randomUUID()
        val edge1Id = UUID.randomUUID()
        val edge2Id = UUID.randomUUID()

        val definition = WorkflowDefinitionEntity(
            id = workflowDefinitionId,
            workspaceId = workspaceId,
            name = "Test Workflow",
            versionNumber = 1,
            status = WorkflowDefinitionStatus.DRAFT,
            tags = emptyList()
        )

        val node1 = createNodeEntity(
            id = node1Id,
            workspaceId = workspaceId,
            key = "node1",
            name = "Node 1",
            config = createTestConfig()
        )
        val node2 = createNodeEntity(
            id = node2Id,
            workspaceId = workspaceId,
            key = "node2",
            name = "Node 2",
            config = createTestConfig()
        )
        val node3 = createNodeEntity(
            id = node3Id,
            workspaceId = workspaceId,
            key = "node3",
            name = "Node 3",
            config = createTestConfig()
        )

        val edge1 =
            createEdgeEntity(id = edge1Id, workspaceId = workspaceId, sourceNodeId = node1Id, targetNodeId = node2Id)
        val edge2 =
            createEdgeEntity(id = edge2Id, workspaceId = workspaceId, sourceNodeId = node2Id, targetNodeId = node3Id)


        val version = WorkflowDefinitionVersionEntity(
            id = UUID.randomUUID(),
            workspaceId = workspaceId,
            workflowDefinitionId = workflowDefinitionId,
            versionNumber = 1,
            workflow = WorkflowGraphReference(
                nodeIds = setOf(node1Id, node2Id, node3Id),
                edgeIds = setOf(edge1Id, edge2Id)
            ),
            canvas = emptyMap<String, Any>()
        )

        whenever(workflowDefinitionRepository.findById(workflowDefinitionId)).thenReturn(Optional.of(definition))
        whenever(
            workflowDefinitionVersionRepository.findByWorkflowDefinitionIdAndVersionNumber(
                workflowDefinitionId,
                1
            )
        )
            .thenReturn(version)
        whenever(workflowNodeRepository.findByWorkspaceIdAndIdIn(eq(workspaceId), any()))
            .thenReturn(listOf(node1, node2, node3))
        whenever(workflowEdgeRepository.findByWorkspaceIdAndNodeIds(eq(workspaceId), any()))
            .thenReturn(listOf(edge1, edge2))

        // Act
        val result = workflowGraphService.getWorkflowGraph(workflowDefinitionId, workspaceId)

        // Assert
        assertEquals(workflowDefinitionId, result.workflowDefinitionId)
        assertEquals(3, result.nodes.size)
        assertEquals(2, result.edges.size)

        val nodeNames = result.nodes.map { it.name }.toSet()
        assertTrue(nodeNames.containsAll(setOf("Node 1", "Node 2", "Node 3")))
    }

    @Test
    fun `getWorkflowGraph_emptyWorkflow_returnsEmptyGraph`() {
        // Arrange
        val workflowDefinitionId = UUID.randomUUID()

        val definition = WorkflowDefinitionEntity(
            id = workflowDefinitionId,
            workspaceId = workspaceId,
            name = "Empty Workflow",
            versionNumber = 1,
            status = WorkflowDefinitionStatus.DRAFT,
            tags = emptyList()
        )

        val version = WorkflowDefinitionVersionEntity(
            id = UUID.randomUUID(),
            workspaceId = workspaceId,
            workflowDefinitionId = workflowDefinitionId,
            versionNumber = 1,
            workflow = WorkflowGraphReference(setOf(), setOf()),
            canvas = emptyMap<String, Any>()
        )

        whenever(workflowDefinitionRepository.findById(workflowDefinitionId)).thenReturn(Optional.of(definition))
        whenever(
            workflowDefinitionVersionRepository.findByWorkflowDefinitionIdAndVersionNumber(
                workflowDefinitionId,
                1
            )
        )
            .thenReturn(version)

        // Act
        val result = workflowGraphService.getWorkflowGraph(workflowDefinitionId, workspaceId)

        // Assert
        assertEquals(workflowDefinitionId, result.workflowDefinitionId)
        assertTrue(result.nodes.isEmpty())
        assertTrue(result.edges.isEmpty())
    }

    @Test
    fun `getWorkflowGraph_notFound_throwsNotFoundException`() {
        // Arrange
        val workflowDefinitionId = UUID.randomUUID()
        whenever(workflowDefinitionRepository.findById(workflowDefinitionId)).thenReturn(Optional.empty())

        // Act & Assert
        assertThrows<NotFoundException> {
            workflowGraphService.getWorkflowGraph(workflowDefinitionId, workspaceId)
        }
    }

    @Test
    fun `getWorkflowGraph_wrongWorkspace_throwsAccessDeniedException`() {
        // Arrange
        val workflowDefinitionId = UUID.randomUUID()
        val differentWorkspaceId = UUID.randomUUID()

        val definition = WorkflowDefinitionEntity(
            id = workflowDefinitionId,
            workspaceId = differentWorkspaceId, // Different workspace
            name = "Test Workflow",
            versionNumber = 1,
            status = WorkflowDefinitionStatus.DRAFT,
            tags = emptyList()
        )

        whenever(workflowDefinitionRepository.findById(workflowDefinitionId)).thenReturn(Optional.of(definition))

        // Act & Assert
        assertThrows<AccessDeniedException> {
            workflowGraphService.getWorkflowGraph(workflowDefinitionId, workspaceId)
        }
    }

    // ------------------------------------------------------------------
    // Helper methods
    // ------------------------------------------------------------------

    private fun createNodeEntity(
        id: UUID = UUID.randomUUID(),
        workspaceId: UUID,
        key: String,
        name: String,
        description: String? = null,
        config: WorkflowNodeConfig,
        version: Int = 1,
        sourceId: UUID? = null,
        deleted: Boolean = false
    ): WorkflowNodeEntity {
        return WorkflowNodeEntity(
            id = id,
            workspaceId = workspaceId,
            key = key,
            name = name,
            description = description,
            type = config.type,
            version = version,
            sourceId = sourceId,
            config = config,
            system = false,
        ).also {
            it.deleted = deleted
        }
    }

    private fun createEdgeEntity(
        id: UUID = UUID.randomUUID(),
        workspaceId: UUID,
        sourceNodeId: UUID,
        targetNodeId: UUID,
        label: String? = null,
        deleted: Boolean = false
    ): WorkflowEdgeEntity {
        return WorkflowEdgeEntity(
            id = id,
            workspaceId = workspaceId,
            sourceNodeId = sourceNodeId,
            targetNodeId = targetNodeId,
            label = label,
        ).also {
            it.deleted = deleted
        }
    }
}
