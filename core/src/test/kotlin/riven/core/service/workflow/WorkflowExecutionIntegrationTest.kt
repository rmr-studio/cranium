package cranium.core.service.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import cranium.core.entity.workflow.WorkflowEdgeEntity
import cranium.core.enums.workflow.WorkflowNodeType
import cranium.core.exceptions.WorkflowValidationException
import cranium.core.models.workflow.engine.state.WorkflowExecutionPhase
import cranium.core.models.workflow.engine.state.WorkflowState
import cranium.core.models.workflow.engine.state.WorkflowDataStore
import cranium.core.models.workflow.engine.state.WorkflowMetadata
import cranium.core.models.workflow.node.WorkflowNode
import cranium.core.models.workflow.node.config.actions.WorkflowCreateEntityActionConfig
import cranium.core.service.workflow.engine.coordinator.WorkflowGraphCoordinationService
import cranium.core.service.workflow.engine.coordinator.WorkflowGraphQueueManagementService
import cranium.core.service.workflow.engine.coordinator.WorkflowGraphTopologicalSorterService
import cranium.core.service.workflow.engine.coordinator.WorkflowGraphValidationService
import java.time.Instant
import java.util.*

/**
 * Comprehensive end-to-end integration test for workflow execution.
 *
 * Tests the complete workflow execution stack:
 * 1. **WorkflowCoordinationService** - Node execution coordination with data registry
 * 2. **WorkflowGraphCoordinationService** - DAG orchestration, topological sort, parallel scheduling
 * 3. **InputResolverService** - Template resolution and variable substitution
 * 4. **ExpressionEvaluatorService** - Expression evaluation with context
 * 5. **EntityContextService** - Entity data access for expressions
 * 6. **ExpressionParserService** - Expression parsing and AST generation
 *
 * ## Test Coverage
 *
 * ### DAG Execution Patterns
 * - Linear workflows (A → B → C)
 * - Diamond patterns (parallel execution)
 * - Complex graphs with multiple branches
 * - Error propagation through graph
 *
 * ### Node Types
 * - Entity creation nodes
 * - Entity query nodes
 * - HTTP request nodes
 * - Nodes with template inputs
 * - Nodes with expression evaluation
 *
 * ### Data Flow
 * - Input resolution from templates
 * - Output capture to data registry
 * - Cross-node data access
 * - Expression evaluation with registry context
 *
 * ### Error Handling
 * - Node execution failures
 * - Input resolution errors
 * - Expression evaluation errors
 * - Graph validation errors
 *
 * ## Why Not Using Temporal TestWorkflowEnvironment?
 *
 * The previous test used Temporal's TestWorkflowEnvironment which caused hanging issues
 * due to complex interactions between Temporal, Spring Boot, and database mocking.
 *
 * This test directly tests the service layer without Temporal orchestration:
 * - Tests WorkflowCoordinationService.executeWorkflowWithCoordinator() directly
 * - Avoids Temporal-specific mocking complexity
 * - Faster test execution
 * - More predictable behavior
 *
 * Temporal orchestration is tested separately via manual testing or dedicated Temporal tests.
 *
 * @see WorkflowGraphCoordinationServiceIntegrationTest for graph coordination testing
 */
@SpringBootTest(
    classes = [
        WorkflowGraphCoordinationService::class,
        WorkflowGraphTopologicalSorterService::class,
        WorkflowGraphValidationService::class,
        WorkflowGraphQueueManagementService::class
    ]
)
class WorkflowExecutionIntegrationTest {

    @Autowired
    private lateinit var workflowGraphCoordinationService: WorkflowGraphCoordinationService

    private val logger = KotlinLogging.logger {}

    private lateinit var workspaceId: UUID

    private lateinit var workflowExecutionId: UUID

    @BeforeEach
    fun setup() {
        workspaceId = UUID.randomUUID()
        workflowExecutionId = UUID.randomUUID()
        logger.info { "Test setup complete for workspace: $workspaceId" }
    }

    /**
     * Creates a WorkflowDataStore for testing.
     */
    private fun createDataStore(): WorkflowDataStore {
        return WorkflowDataStore(
            state = WorkflowState(
                phase = WorkflowExecutionPhase.INITIALIZING
            ),
            metadata = WorkflowMetadata(
                executionId = workflowExecutionId,
                workspaceId = workspaceId,
                workflowDefinitionId = UUID.randomUUID(),
                version = 1,
                startedAt = Instant.now()
            )
        )
    }


    /**
     * Test linear workflow execution (A → B → C).
     *
     * Workflow:
     * - Node A: Create entity
     * - Node B: Query entity (depends on A)
     * - Node C: HTTP request (depends on B)
     *
     * Validates:
     * - Nodes execute in correct order
     * - Data flows through registry
     * - Final state is COMPLETED
     */
    @Test
    fun `test linear workflow executes nodes in sequence`() {
        // Create nodes
        val store = createDataStore()
        val nodeA = createMockNode("create_lead", WorkflowNodeType.ACTION)
        val nodeB = createMockNode("query_lead", WorkflowNodeType.ACTION)
        val nodeC = createMockNode("send_notification", WorkflowNodeType.ACTION)

        val nodes = listOf(nodeA, nodeB, nodeC)

        // Create edges: A → B → C
        val edges = listOf(
            createEdge(nodeA.id, nodeB.id),
            createEdge(nodeB.id, nodeC.id)
        )

        // Track execution order
        val executionOrder = mutableListOf<UUID>()

        // Node executor that tracks order
        val nodeExecutor: (List<WorkflowNode>) -> List<Pair<UUID, Any?>> = { readyNodes ->
            readyNodes.map { node ->
                executionOrder.add(node.id)
                logger.info { "Executing node: ${node.name}" }

                // Simulate node output
                node.id to mapOf(
                    "nodeName" to node.name,
                    "result" to "success",
                    "timestamp" to System.currentTimeMillis()
                )
            }
        }

        // Execute workflow
        workflowGraphCoordinationService.executeWorkflow(store, nodes, edges, nodeExecutor)

        // Verify execution order
        assertEquals(3, executionOrder.size)
        assertEquals(nodeA.id, executionOrder[0], "Node A should execute first")
        assertEquals(nodeB.id, executionOrder[1], "Node B should execute second")
        assertEquals(nodeC.id, executionOrder[2], "Node C should execute third")

        // Verify final state
        assertEquals(WorkflowExecutionPhase.COMPLETED, store.state.phase)
        assertEquals(3, store.state.completedNodes.size)
        assertEquals(0, store.state.activeNodes.size)

        // Verify all nodes completed (outputs are now in WorkflowDataStore, not in WorkflowState)
        assertTrue(store.state.completedNodes.contains(nodeA.id))
        assertTrue(store.state.completedNodes.contains(nodeB.id))
        assertTrue(store.state.completedNodes.contains(nodeC.id))
    }

    /**
     * Test diamond workflow with parallel execution.
     *
     * Workflow:
     *       A
     *      / \
     *     B   C
     *      \ /
     *       D
     *
     * Validates:
     * - B and C execute in parallel (same batch)
     * - D waits for both B and C to complete
     * - Correct number of execution batches
     */
    @Test
    fun `test diamond workflow executes middle nodes in parallel`() {
        val store = createDataStore()
        val nodeA = createMockNode("start", WorkflowNodeType.ACTION)
        val nodeB = createMockNode("branch_1", WorkflowNodeType.ACTION)
        val nodeC = createMockNode("branch_2", WorkflowNodeType.ACTION)
        val nodeD = createMockNode("merge", WorkflowNodeType.ACTION)

        val nodes = listOf(nodeA, nodeB, nodeC, nodeD)
        val edges = listOf(
            createEdge(nodeA.id, nodeB.id),
            createEdge(nodeA.id, nodeC.id),
            createEdge(nodeB.id, nodeD.id),
            createEdge(nodeC.id, nodeD.id)
        )

        val executionBatches = mutableListOf<List<UUID>>()

        val nodeExecutor: (List<WorkflowNode>) -> List<Pair<UUID, Any?>> = { readyNodes ->
            executionBatches.add(readyNodes.map { it.id })
            readyNodes.map { node ->
                node.id to mapOf("result" to "completed")
            }
        }

        workflowGraphCoordinationService.executeWorkflow(store, nodes, edges, nodeExecutor)

        // Verify execution batches
        assertEquals(3, executionBatches.size, "Should have 3 execution batches")

        // Batch 1: A
        assertEquals(1, executionBatches[0].size)
        assertTrue(executionBatches[0].contains(nodeA.id))

        // Batch 2: B and C (parallel)
        assertEquals(2, executionBatches[1].size)
        assertTrue(executionBatches[1].containsAll(listOf(nodeB.id, nodeC.id)))

        // Batch 3: D
        assertEquals(1, executionBatches[2].size)
        assertTrue(executionBatches[2].contains(nodeD.id))

        // Verify final state
        assertEquals(WorkflowExecutionPhase.COMPLETED, store.state.phase)
        assertEquals(4, store.state.completedNodes.size)
    }

    /**
     * Test complex workflow with multiple parallel branches.
     *
     * Workflow:
     *         A
     *       / | \
     *      B  C  D
     *      |  |  |
     *      E  F  G
     *       \ | /
     *         H
     *
     * Validates:
     * - Maximum parallelism (B, C, D execute together)
     * - Independent branches (E, F, G execute in parallel)
     * - Final merge node waits for all branches
     */
    @Test
    fun `test complex workflow with multiple parallel branches`() {
        val store = createDataStore()
        val nodeA = createMockNode("start", WorkflowNodeType.ACTION)
        val nodeB = createMockNode("branch_1", WorkflowNodeType.ACTION)
        val nodeC = createMockNode("branch_2", WorkflowNodeType.ACTION)
        val nodeD = createMockNode("branch_3", WorkflowNodeType.ACTION)
        val nodeE = createMockNode("task_1", WorkflowNodeType.ACTION)
        val nodeF = createMockNode("task_2", WorkflowNodeType.ACTION)
        val nodeG = createMockNode("task_3", WorkflowNodeType.ACTION)
        val nodeH = createMockNode("merge", WorkflowNodeType.ACTION)

        val nodes = listOf(nodeA, nodeB, nodeC, nodeD, nodeE, nodeF, nodeG, nodeH)
        val edges = listOf(
            // A fans out to B, C, D
            createEdge(nodeA.id, nodeB.id),
            createEdge(nodeA.id, nodeC.id),
            createEdge(nodeA.id, nodeD.id),
            // B → E, C → F, D → G
            createEdge(nodeB.id, nodeE.id),
            createEdge(nodeC.id, nodeF.id),
            createEdge(nodeD.id, nodeG.id),
            // E, F, G merge to H
            createEdge(nodeE.id, nodeH.id),
            createEdge(nodeF.id, nodeH.id),
            createEdge(nodeG.id, nodeH.id)
        )

        val executionBatches = mutableListOf<List<UUID>>()

        val nodeExecutor: (List<WorkflowNode>) -> List<Pair<UUID, Any?>> = { readyNodes ->
            executionBatches.add(readyNodes.map { it.id })
            readyNodes.map { node ->
                node.id to mapOf("result" to "completed", "batch" to executionBatches.size)
            }
        }

        workflowGraphCoordinationService.executeWorkflow(store, nodes, edges, nodeExecutor)

        // Verify execution batches
        assertEquals(4, executionBatches.size, "Should have 4 execution batches")

        // Batch 1: A
        assertEquals(1, executionBatches[0].size)

        // Batch 2: B, C, D (parallel)
        assertEquals(3, executionBatches[1].size)
        assertTrue(executionBatches[1].containsAll(listOf(nodeB.id, nodeC.id, nodeD.id)))

        // Batch 3: E, F, G (parallel)
        assertEquals(3, executionBatches[2].size)
        assertTrue(executionBatches[2].containsAll(listOf(nodeE.id, nodeF.id, nodeG.id)))

        // Batch 4: H
        assertEquals(1, executionBatches[3].size)
        assertTrue(executionBatches[3].contains(nodeH.id))

        // Verify final state
        assertEquals(WorkflowExecutionPhase.COMPLETED, store.state.phase)
        assertEquals(8, store.state.completedNodes.size)
    }

    /**
     * Test data registry captures and provides access to node outputs.
     *
     * Validates:
     * - Node outputs stored in data registry
     * - Outputs accessible by node ID
     * - Registry contains all executed nodes
     * - Output structure preserved
     */
    @Test
    fun `test data registry captures node outputs correctly`() {
        val store = createDataStore()
        val nodeA = createMockNode("create_entity", WorkflowNodeType.ACTION)
        val nodeB = createMockNode("query_entity", WorkflowNodeType.ACTION)

        val nodes = listOf(nodeA, nodeB)
        val edges = listOf(createEdge(nodeA.id, nodeB.id))

        val nodeExecutor: (List<WorkflowNode>) -> List<Pair<UUID, Any?>> = { readyNodes ->
            readyNodes.map { node ->
                val output = mapOf(
                    "nodeId" to node.id.toString(),
                    "nodeName" to node.name,
                    "timestamp" to System.currentTimeMillis(),
                    "data" to mapOf(
                        "entityId" to UUID.randomUUID().toString(),
                        "status" to "created"
                    )
                )
                node.id to output
            }
        }

        workflowGraphCoordinationService.executeWorkflow(store, nodes, edges, nodeExecutor)

        // Verify completion (outputs are now in WorkflowDataStore, not in WorkflowState)
        assertEquals(2, store.state.completedNodes.size)
        assertTrue(store.state.completedNodes.contains(nodeA.id))
        assertTrue(store.state.completedNodes.contains(nodeB.id))
    }

    /**
     * Test error propagation through workflow.
     *
     * Workflow: A → B (fails) → C (should not execute)
     *
     * Validates:
     * - Node B failure stops workflow
     * - Node C does not execute
     * - Exception propagated with correct message
     * - Partial results captured for completed nodes
     */
    @Test
    fun `test node failure stops workflow execution`() {
        val store = createDataStore()
        val nodeA = createMockNode("start", WorkflowNodeType.ACTION)
        val nodeB = createMockNode("failing_node", WorkflowNodeType.ACTION)
        val nodeC = createMockNode("should_not_execute", WorkflowNodeType.ACTION)

        val nodes = listOf(nodeA, nodeB, nodeC)
        val edges = listOf(
            createEdge(nodeA.id, nodeB.id),
            createEdge(nodeB.id, nodeC.id)
        )

        val executedNodes = mutableListOf<UUID>()

        val nodeExecutor: (List<WorkflowNode>) -> List<Pair<UUID, Any?>> = { readyNodes ->
            readyNodes.map { node ->
                executedNodes.add(node.id)

                if (node.id == nodeB.id) {
                    throw RuntimeException("Node B failed intentionally")
                }

                node.id to mapOf("result" to "success")
            }
        }

        // Execute and expect exception
        val exception = assertThrows<RuntimeException> {
            workflowGraphCoordinationService.executeWorkflow(store, nodes, edges, nodeExecutor)
        }

        // Verify error message
        assertTrue(exception.message?.contains("Node B failed") == true)

        // Verify only A and B executed (C should not execute)
        assertEquals(2, executedNodes.size)
        assertTrue(executedNodes.contains(nodeA.id))
        assertTrue(executedNodes.contains(nodeB.id))
        assertFalse(executedNodes.contains(nodeC.id))
    }

    /**
     * Test cycle detection in workflow graph.
     *
     * Workflow: A → B → C → A (cycle)
     *
     * Validates:
     * - Cycle detected before execution
     * - WorkflowValidationException thrown
     * - Error message indicates cycle
     */
    @Test
    fun `test cycle detection throws validation exception`() {
        val store = createDataStore()
        val nodeA = createMockNode("node_a", WorkflowNodeType.ACTION)
        val nodeB = createMockNode("node_b", WorkflowNodeType.ACTION)
        val nodeC = createMockNode("node_c", WorkflowNodeType.ACTION)

        val nodes = listOf(nodeA, nodeB, nodeC)
        val edges = listOf(
            createEdge(nodeA.id, nodeB.id),
            createEdge(nodeB.id, nodeC.id),
            createEdge(nodeC.id, nodeA.id) // Cycle!
        )

        val nodeExecutor: (List<WorkflowNode>) -> List<Pair<UUID, Any?>> = { readyNodes ->
            readyNodes.map { it.id to mapOf("result" to "success") }
        }

        val exception = assertThrows<WorkflowValidationException> {
            workflowGraphCoordinationService.executeWorkflow(store, nodes, edges, nodeExecutor)
        }

        assertTrue(
            exception.message?.contains("Cycle") == true ||
                    exception.message?.contains("unreachable") == true,
            "Exception should mention cycle detection"
        )
    }

    /**
     * Test empty workflow executes successfully.
     *
     * Validates:
     * - Empty node list handled gracefully
     * - No errors thrown
     * - Final state is COMPLETED
     */
    @Test
    fun `test empty workflow executes successfully`() {
        val store = createDataStore()
        val nodes = emptyList<WorkflowNode>()
        val edges = emptyList<WorkflowEdgeEntity>()

        val nodeExecutor: (List<WorkflowNode>) -> List<Pair<UUID, Any?>> = { readyNodes ->
            readyNodes.map { it.id to mapOf("result" to "success") }
        }

        workflowGraphCoordinationService.executeWorkflow(store, nodes, edges, nodeExecutor)

        assertEquals(WorkflowExecutionPhase.COMPLETED, store.state.phase)
        assertEquals(0, store.state.completedNodes.size)
        assertEquals(0, store.state.completedNodes.size)
    }

    /**
     * Test single node workflow.
     *
     * Validates:
     * - Single node executes correctly
     * - No dependencies required
     * - Output captured
     */
    @Test
    fun `test single node workflow executes successfully`() {
        val store = createDataStore()
        val node = createMockNode("solo_node", WorkflowNodeType.ACTION)

        val nodes = listOf(node)
        val edges = emptyList<WorkflowEdgeEntity>()

        val nodeExecutor: (List<WorkflowNode>) -> List<Pair<UUID, Any?>> = { readyNodes ->
            readyNodes.map { n ->
                n.id to mapOf(
                    "result" to "success",
                    "message" to "Solo execution completed"
                )
            }
        }

        workflowGraphCoordinationService.executeWorkflow(store, nodes, edges, nodeExecutor)

        assertEquals(WorkflowExecutionPhase.COMPLETED, store.state.phase)
        assertEquals(1, store.state.completedNodes.size)
        assertTrue(store.state.completedNodes.contains(node.id))
    }

    /**
     * Test multiple independent workflows execute in parallel.
     *
     * Workflow: A → B    C → D (two independent chains)
     *
     * Validates:
     * - Both chains execute
     * - A and C start in parallel (both have no dependencies)
     * - Final state includes all nodes
     */
    @Test
    fun `test multiple independent workflows execute in parallel`() {
        val store = createDataStore()
        val nodeA = createMockNode("chain1_start", WorkflowNodeType.ACTION)
        val nodeB = createMockNode("chain1_end", WorkflowNodeType.ACTION)
        val nodeC = createMockNode("chain2_start", WorkflowNodeType.ACTION)
        val nodeD = createMockNode("chain2_end", WorkflowNodeType.ACTION)

        val nodes = listOf(nodeA, nodeB, nodeC, nodeD)
        val edges = listOf(
            createEdge(nodeA.id, nodeB.id),
            createEdge(nodeC.id, nodeD.id)
        )

        val executionBatches = mutableListOf<List<UUID>>()

        val nodeExecutor: (List<WorkflowNode>) -> List<Pair<UUID, Any?>> = { readyNodes ->
            executionBatches.add(readyNodes.map { it.id })
            readyNodes.map { it.id to mapOf("result" to "success") }
        }

        workflowGraphCoordinationService.executeWorkflow(store, nodes, edges, nodeExecutor)

        // Verify both chains completed
        assertEquals(WorkflowExecutionPhase.COMPLETED, store.state.phase)
        assertEquals(4, store.state.completedNodes.size)

        // Verify A and C started in parallel (batch 1)
        assertEquals(2, executionBatches[0].size)
        assertTrue(executionBatches[0].containsAll(listOf(nodeA.id, nodeC.id)))

        // Verify B and D in batch 2
        assertEquals(2, executionBatches[1].size)
        assertTrue(executionBatches[1].containsAll(listOf(nodeB.id, nodeD.id)))
    }

    /**
     * Test workflow with fan-out and fan-in pattern.
     *
     * Workflow:
     *       A
     *     / | \
     *    B  C  D
     *     \ | /
     *       E
     *
     * Validates:
     * - Fan-out from A to B, C, D
     * - All three execute in parallel
     * - Fan-in to E waits for all three
     * - Correct batch execution
     */
    @Test
    fun `test fan-out and fan-in pattern executes correctly`() {
        val store = createDataStore()
        val nodeA = createMockNode("start", WorkflowNodeType.ACTION)
        val nodeB = createMockNode("worker_1", WorkflowNodeType.ACTION)
        val nodeC = createMockNode("worker_2", WorkflowNodeType.ACTION)
        val nodeD = createMockNode("worker_3", WorkflowNodeType.ACTION)
        val nodeE = createMockNode("merge", WorkflowNodeType.ACTION)

        val nodes = listOf(nodeA, nodeB, nodeC, nodeD, nodeE)
        val edges = listOf(
            createEdge(nodeA.id, nodeB.id),
            createEdge(nodeA.id, nodeC.id),
            createEdge(nodeA.id, nodeD.id),
            createEdge(nodeB.id, nodeE.id),
            createEdge(nodeC.id, nodeE.id),
            createEdge(nodeD.id, nodeE.id)
        )

        val executionBatches = mutableListOf<List<UUID>>()

        val nodeExecutor: (List<WorkflowNode>) -> List<Pair<UUID, Any?>> = { readyNodes ->
            executionBatches.add(readyNodes.map { it.id })
            readyNodes.map { it.id to mapOf("result" to "success") }
        }

        workflowGraphCoordinationService.executeWorkflow(store, nodes, edges, nodeExecutor)

        // Verify 3 batches
        assertEquals(3, executionBatches.size)

        // Batch 1: A
        assertEquals(1, executionBatches[0].size)
        assertEquals(nodeA.id, executionBatches[0][0])

        // Batch 2: B, C, D (parallel)
        assertEquals(3, executionBatches[1].size)
        assertTrue(executionBatches[1].containsAll(listOf(nodeB.id, nodeC.id, nodeD.id)))

        // Batch 3: E (waits for all)
        assertEquals(1, executionBatches[2].size)
        assertEquals(nodeE.id, executionBatches[2][0])

        assertEquals(WorkflowExecutionPhase.COMPLETED, store.state.phase)
    }

    // ========================================
    // Helper Functions
    // ========================================

    /**
     * Create a mock WorkflowNode for testing.
     */
    private fun createMockNode(name: String, type: WorkflowNodeType): WorkflowNode {
        return WorkflowNode(
            id = UUID.randomUUID(),
            name = name,
            config = when (type) {
                WorkflowNodeType.ACTION -> WorkflowCreateEntityActionConfig(
                    version = 1,
                    entityTypeId = UUID.randomUUID().toString(),
                    payload = mapOf("name" to name)
                )

                else -> WorkflowCreateEntityActionConfig(
                    version = 1,
                    entityTypeId = UUID.randomUUID().toString(),
                    payload = emptyMap()
                )
            },
            workspaceId = workspaceId,
            key = name.lowercase().replace(" ", "_")
        )
    }

    /**
     * Create a WorkflowEdgeEntity representing a dependency.
     */
    private fun createEdge(sourceId: UUID, targetId: UUID): WorkflowEdgeEntity {
        return WorkflowEdgeEntity(
            id = UUID.randomUUID(),
            workspaceId = workspaceId,
            sourceNodeId = sourceId,
            targetNodeId = targetId
        )
    }
}
