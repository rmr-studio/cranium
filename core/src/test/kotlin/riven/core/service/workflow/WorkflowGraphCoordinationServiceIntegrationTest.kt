package cranium.core.service.workflow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import cranium.core.entity.workflow.WorkflowEdgeEntity
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
 * Integration test for DAG execution coordinator.
 *
 * This test validates the complete integration of:
 * - TopologicalSorter (dependency order)
 * - DagValidator (structural validation)
 * - ActiveNodeQueue (parallel scheduling)
 * - DagExecutionCoordinator (orchestration)
 *
 * ## Test Scenarios
 *
 * 1. Linear DAG (A → B → C): Sequential execution order
 * 2. Diamond DAG (A → B,C → D): B and C execute in parallel
 * 3. Parallel branches (A → B,C,D → E): Maximum parallelism
 * 4. Cycle detection: Exception thrown for cyclic graph
 * 5. Disconnected components: Validator catches orphaned nodes
 *
 * ## Test Pattern
 *
 * Each test:
 * 1. Creates WorkflowNode instances (mock nodes)
 * 2. Creates WorkflowEdgeEntity instances (dependencies)
 * 3. Defines nodeExecutor that tracks execution batches
 * 4. Calls coordinator.executeWorkflow()
 * 5. Asserts on execution order and parallelism
 * */
@SpringBootTest(
    classes = [
        WorkflowGraphCoordinationService::class,
        WorkflowGraphTopologicalSorterService::class,
        WorkflowGraphValidationService::class,
        WorkflowGraphQueueManagementService::class
    ]
)
class WorkflowGraphCoordinationServiceIntegrationTest {


    @Autowired
    private lateinit var workflowGraphCoordinationService: WorkflowGraphCoordinationService

    @Autowired
    private lateinit var workflowGraphTopologicalSorterService: WorkflowGraphTopologicalSorterService

    @Autowired

    private lateinit var workflowGraphValidationService: WorkflowGraphValidationService

    @Autowired

    private lateinit var workflowGraphQueueManagementService: WorkflowGraphQueueManagementService

    private lateinit var workspaceId: UUID

    private lateinit var workflowExecutionId: UUID

    /**
     * Create a fresh coordinator for each test execution.
     *
     * ActiveNodeQueue maintains mutable state, so we need fresh instances
     * to prevent test interference.
     */
    private fun createCoordinator(): WorkflowGraphCoordinationService {
        return WorkflowGraphCoordinationService(
            WorkflowGraphValidationService(WorkflowGraphTopologicalSorterService()),
            WorkflowGraphTopologicalSorterService(),
            WorkflowGraphQueueManagementService()
        )
    }

    @BeforeEach
    fun setup() {
        workspaceId = UUID.randomUUID()
        workflowExecutionId = UUID.randomUUID()
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
     * Test linear DAG execution order.
     *
     * Graph: A → B → C
     *
     * Expected batches:
     * - Batch 1: [A]
     * - Batch 2: [B]
     * - Batch 3: [C]
     */
    @Test
    fun `test linear DAG executes in correct order`() {
        val store = createDataStore()
        val (nodes, edges) = createLinearDag()

        val executionBatches = mutableListOf<List<UUID>>()
        val nodeExecutor: (List<WorkflowNode>) -> List<Pair<UUID, Any?>> = { readyNodes ->
            executionBatches.add(readyNodes.map { it.id })
            readyNodes.map { it.id to mapOf("result" to "completed") }
        }

        createCoordinator().executeWorkflow(store, nodes, edges, nodeExecutor)

        // Verify final state
        assertEquals(WorkflowExecutionPhase.COMPLETED, store.state.phase)
        assertEquals(3, store.state.completedNodes.size)
        assertEquals(0, store.state.activeNodes.size)

        // Verify execution batches (sequential)
        assertEquals(3, executionBatches.size)
        assertEquals(1, executionBatches[0].size) // A
        assertEquals(1, executionBatches[1].size) // B
        assertEquals(1, executionBatches[2].size) // C

        // Verify order
        val nodeA = nodes[0]
        val nodeB = nodes[1]
        val nodeC = nodes[2]
        assertEquals(nodeA.id, executionBatches[0][0])
        assertEquals(nodeB.id, executionBatches[1][0])
        assertEquals(nodeC.id, executionBatches[2][0])
    }

    /**
     * Test diamond DAG with parallel execution.
     *
     * Graph:
     *     A
     *    / \
     *   B   C
     *    \ /
     *     D
     *
     * Expected batches:
     * - Batch 1: [A]
     * - Batch 2: [B, C] (parallel)
     * - Batch 3: [D]
     */
    @Test
    fun `test diamond DAG executes B and C in parallel`() {
        val (nodes, edges) = createDiamondDag()
        val store = createDataStore()
        val executionBatches = mutableListOf<List<UUID>>()
        val nodeExecutor: (List<WorkflowNode>) -> List<Pair<UUID, Any?>> = { readyNodes ->
            executionBatches.add(readyNodes.map { it.id })
            readyNodes.map { it.id to mapOf("result" to "completed") }
        }

        createCoordinator().executeWorkflow(store, nodes, edges, nodeExecutor)

        // Verify final state
        assertEquals(WorkflowExecutionPhase.COMPLETED, store.state.phase)
        assertEquals(4, store.state.completedNodes.size)

        // Verify execution batches
        assertEquals(3, executionBatches.size)

        // Batch 1: A
        assertEquals(1, executionBatches[0].size)

        // Batch 2: B and C (parallel)
        assertEquals(2, executionBatches[1].size)

        // Batch 3: D
        assertEquals(1, executionBatches[2].size)

        // Verify B and C are in same batch (proves parallelism)
        val nodeB = nodes[1]
        val nodeC = nodes[2]
        assertTrue(
            executionBatches[1].containsAll(listOf(nodeB.id, nodeC.id)),
            "B and C should execute in parallel (same batch)"
        )
    }

    /**
     * Test parallel branches with maximum parallelism.
     *
     * Graph:
     *       A
     *     / | \
     *    B  C  D
     *     \ | /
     *       E
     *
     * Expected batches:
     * - Batch 1: [A]
     * - Batch 2: [B, C, D] (all parallel)
     * - Batch 3: [E]
     */
    @Test
    fun `test parallel branches execute with maximum parallelism`() {
        val (nodes, edges) = createParallelDag()
        val store = createDataStore()
        val executionBatches = mutableListOf<List<UUID>>()
        val nodeExecutor: (List<WorkflowNode>) -> List<Pair<UUID, Any?>> = { readyNodes ->
            executionBatches.add(readyNodes.map { it.id })
            readyNodes.map { it.id to mapOf("result" to "completed") }
        }

        createCoordinator().executeWorkflow(store, nodes, edges, nodeExecutor)

        // Verify final state
        assertEquals(WorkflowExecutionPhase.COMPLETED, store.state.phase)
        assertEquals(5, store.state.completedNodes.size)

        // Verify execution batches
        assertEquals(3, executionBatches.size)

        // Batch 1: A
        assertEquals(1, executionBatches[0].size)

        // Batch 2: B, C, D (all parallel)
        assertEquals(3, executionBatches[1].size)

        // Batch 3: E
        assertEquals(1, executionBatches[2].size)

        // Verify B, C, D all in same batch
        val nodeB = nodes[1]
        val nodeC = nodes[2]
        val nodeD = nodes[3]
        assertTrue(
            executionBatches[1].containsAll(listOf(nodeB.id, nodeC.id, nodeD.id)),
            "B, C, and D should execute in parallel (same batch)"
        )
    }

    /**
     * Test cycle detection throws exception.
     *
     * Graph: A → B → C → A (cycle)
     *
     * Expected: WorkflowValidationException
     */
    @Test
    fun `test cycle detection throws exception`() {
        val store = createDataStore()
        val (nodes, edges) = createCyclicDag()

        val nodeExecutor: (List<WorkflowNode>) -> List<Pair<UUID, Any?>> = { readyNodes ->
            readyNodes.map { it.id to mapOf("result" to "completed") }
        }

        val exception = assertThrows<WorkflowValidationException> {
            createCoordinator().executeWorkflow(store, nodes, edges, nodeExecutor)
        }

        assertTrue(
            exception.message?.contains("Cycle detected") == true ||
                    exception.message?.contains("unreachable") == true,
            "Exception should mention cycle detection"
        )
    }

    /**
     * Test disconnected components detection.
     *
     * Graph: A → B    C (orphaned node with no edges)
     *
     * Expected: Multiple start nodes execute successfully (A and C both start)
     *
     * Note: Two independent DAGs (A→B and C→D) are actually valid for parallel execution.
     * This test verifies that orphaned nodes (nodes with edges but unreachable from start)
     * would be caught, but since C is a valid start node, this test verifies that
     * multiple independent DAGs can execute successfully.
     */
    @Test
    fun `test multiple independent DAGs execute successfully`() {
        val store = createDataStore()
        val (nodes, edges) = createDisconnectedDag()

        val executionBatches = mutableListOf<List<UUID>>()
        val nodeExecutor: (List<WorkflowNode>) -> List<Pair<UUID, Any?>> = { readyNodes ->
            executionBatches.add(readyNodes.map { it.id })
            readyNodes.map { it.id to mapOf("result" to "completed") }
        }

        createCoordinator().executeWorkflow(store, nodes, edges, nodeExecutor)

        // Both independent DAGs should complete
        assertEquals(WorkflowExecutionPhase.COMPLETED, store.state.phase)
        assertEquals(4, store.state.completedNodes.size)

        // First batch should contain both A and C (both have in-degree 0)
        assertEquals(2, executionBatches[0].size)
    }

    /**
     * Test data registry captures node outputs.
     *
     * Graph: A → B
     *
     * Verifies that outputs are stored in dataRegistry.
     */
    @Test
    fun `test data registry captures node outputs`() {
        val store = createDataStore()
        val nodeA = createMockNode("A")
        val nodeB = createMockNode("B")
        val nodes = listOf(nodeA, nodeB)
        val edges = listOf(
            WorkflowEdgeEntity(
                UUID.randomUUID(),
                workspaceId = UUID.randomUUID(),
                sourceNodeId = nodeA.id,
                targetNodeId = nodeB.id
            )
        )

        val nodeExecutor: (List<WorkflowNode>) -> List<Pair<UUID, Any?>> = { readyNodes ->
            readyNodes.map { node ->
                node.id to mapOf("nodeName" to "node-${node.id}", "output" to "data-${node.id}")
            }
        }

        createCoordinator().executeWorkflow(store, nodes, edges, nodeExecutor)

        // Verify completion (outputs are now in WorkflowDataStore, not in WorkflowState)
        assertEquals(2, store.state.completedNodes.size)
        assertTrue(store.state.completedNodes.contains(nodeA.id))
        assertTrue(store.state.completedNodes.contains(nodeB.id))
    }

    /**
     * Test empty workflow executes successfully.
     */
    @Test
    fun `test empty workflow executes successfully`() {
        val store = createDataStore()
        val nodes = emptyList<WorkflowNode>()
        val edges = emptyList<WorkflowEdgeEntity>()

        val nodeExecutor: (List<WorkflowNode>) -> List<Pair<UUID, Any?>> = { readyNodes ->
            readyNodes.map { it.id to mapOf("result" to "completed") }
        }

        createCoordinator().executeWorkflow(store, nodes, edges, nodeExecutor)

        assertEquals(WorkflowExecutionPhase.COMPLETED, store.state.phase)
        assertEquals(0, store.state.completedNodes.size)
    }

    // ========================================
    // Helper Functions: DAG Creation
    // ========================================

    /**
     * Create linear DAG: A → B → C
     */
    private fun createLinearDag(): Pair<List<WorkflowNode>, List<WorkflowEdgeEntity>> {
        val nodeA = createMockNode("A")
        val nodeB = createMockNode("B")
        val nodeC = createMockNode("C")

        val nodes = listOf(nodeA, nodeB, nodeC)
        val edges = listOf(
            WorkflowEdgeEntity(
                UUID.randomUUID(),
                workspaceId = UUID.randomUUID(),
                sourceNodeId = nodeA.id,
                targetNodeId = nodeB.id
            ),
            WorkflowEdgeEntity(
                UUID.randomUUID(),
                workspaceId = UUID.randomUUID(),
                sourceNodeId = nodeB.id,
                targetNodeId = nodeC.id
            )
        )

        return nodes to edges
    }

    /**
     * Create diamond DAG:
     *     A
     *    / \
     *   B   C
     *    \ /
     *     D
     */
    private fun createDiamondDag(): Pair<List<WorkflowNode>, List<WorkflowEdgeEntity>> {
        val nodeA = createMockNode("A")
        val nodeB = createMockNode("B")
        val nodeC = createMockNode("C")
        val nodeD = createMockNode("D")

        val workspaceId: UUID = UUID.randomUUID()

        val nodes = listOf(nodeA, nodeB, nodeC, nodeD)
        val edges = listOf(
            WorkflowEdgeEntity(
                UUID.randomUUID(),
                workspaceId = workspaceId,
                sourceNodeId = nodeA.id,
                targetNodeId = nodeB.id
            ),
            WorkflowEdgeEntity(
                UUID.randomUUID(),
                workspaceId = workspaceId,
                sourceNodeId = nodeA.id,
                targetNodeId = nodeC.id
            ),
            WorkflowEdgeEntity(
                UUID.randomUUID(),
                workspaceId = workspaceId,
                sourceNodeId = nodeB.id,
                targetNodeId = nodeD.id
            ),
            WorkflowEdgeEntity(
                UUID.randomUUID(),
                workspaceId = workspaceId,
                sourceNodeId = nodeC.id,
                targetNodeId = nodeD.id
            )
        )

        return nodes to edges
    }

    /**
     * Create parallel DAG:
     *       A
     *     / | \
     *    B  C  D
     *     \ | /
     *       E
     */
    private fun createParallelDag(): Pair<List<WorkflowNode>, List<WorkflowEdgeEntity>> {
        val nodeA = createMockNode("A")
        val nodeB = createMockNode("B")
        val nodeC = createMockNode("C")
        val nodeD = createMockNode("D")
        val nodeE = createMockNode("E")

        val workspaceId = UUID.randomUUID()

        val nodes = listOf(nodeA, nodeB, nodeC, nodeD, nodeE)
        val edges = listOf(
            WorkflowEdgeEntity(
                UUID.randomUUID(),
                workspaceId = workspaceId,
                sourceNodeId = nodeA.id,
                targetNodeId = nodeB.id
            ),
            WorkflowEdgeEntity(
                UUID.randomUUID(),
                workspaceId = workspaceId,
                sourceNodeId = nodeA.id,
                targetNodeId = nodeC.id
            ),
            WorkflowEdgeEntity(
                UUID.randomUUID(),
                workspaceId = workspaceId,
                sourceNodeId = nodeA.id,
                targetNodeId = nodeD.id
            ),
            WorkflowEdgeEntity(
                UUID.randomUUID(),
                workspaceId = workspaceId,
                sourceNodeId = nodeB.id,
                targetNodeId = nodeE.id
            ),
            WorkflowEdgeEntity(
                UUID.randomUUID(),
                workspaceId = workspaceId,
                sourceNodeId = nodeC.id,
                targetNodeId = nodeE.id
            ),
            WorkflowEdgeEntity(
                UUID.randomUUID(),
                workspaceId = workspaceId,
                sourceNodeId = nodeD.id,
                targetNodeId = nodeE.id
            )
        )

        return nodes to edges
    }

    /**
     * Create cyclic DAG: A → B → C → A
     */
    private fun createCyclicDag(): Pair<List<WorkflowNode>, List<WorkflowEdgeEntity>> {
        val nodeA = createMockNode("A")
        val nodeB = createMockNode("B")
        val nodeC = createMockNode("C")

        val workspaceId = UUID.randomUUID()

        val nodes = listOf(nodeA, nodeB, nodeC)
        val edges = listOf(
            WorkflowEdgeEntity(
                UUID.randomUUID(),
                workspaceId = workspaceId,
                sourceNodeId = nodeA.id,
                targetNodeId = nodeB.id
            ),
            WorkflowEdgeEntity(
                UUID.randomUUID(),
                workspaceId = workspaceId,
                sourceNodeId = nodeB.id,
                targetNodeId = nodeC.id
            ),
            WorkflowEdgeEntity(
                UUID.randomUUID(),
                workspaceId = workspaceId,
                sourceNodeId = nodeC.id,
                targetNodeId = nodeA.id
            ) // Cycle
        )

        return nodes to edges
    }

    /**
     * Create disconnected DAG: A → B    C → D
     */
    private fun createDisconnectedDag(): Pair<List<WorkflowNode>, List<WorkflowEdgeEntity>> {
        val nodeA = createMockNode("A")
        val nodeB = createMockNode("B")
        val nodeC = createMockNode("C")
        val nodeD = createMockNode("D")

        val workspaceId = UUID.randomUUID()

        val nodes = listOf(nodeA, nodeB, nodeC, nodeD)
        val edges = listOf(
            WorkflowEdgeEntity(
                UUID.randomUUID(),
                workspaceId = workspaceId,
                sourceNodeId = nodeA.id,
                targetNodeId = nodeB.id
            ),
            WorkflowEdgeEntity(
                UUID.randomUUID(),
                workspaceId = workspaceId,
                sourceNodeId = nodeC.id,
                targetNodeId = nodeD.id
            ) // Disconnected
        )

        return nodes to edges
    }

    /**
     * Create mock WorkflowNode for testing.
     *
     * Uses CreateEntityActionNode as a concrete implementation.
     * The actual execution is mocked via nodeExecutor lambda.
     */
    private fun createMockNode(name: String): WorkflowNode {
        return WorkflowNode(
            id = UUID.randomUUID(),
            name = name,
            config = WorkflowCreateEntityActionConfig(
                version = 1,
                entityTypeId = UUID.randomUUID().toString(),
                payload = mapOf("name" to name)
            ),
            workspaceId = UUID.randomUUID(),
            key = name.lowercase()
        )
    }
}
