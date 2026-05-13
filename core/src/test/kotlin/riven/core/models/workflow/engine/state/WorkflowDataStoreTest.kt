package cranium.core.models.workflow.engine.state

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import cranium.core.enums.util.OperationType
import cranium.core.enums.workflow.WorkflowStatus
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class WorkflowDataStoreTest {

    private lateinit var dataStore: WorkflowDataStore
    private lateinit var metadata: WorkflowMetadata

    private lateinit var state: WorkflowState

    @BeforeEach
    fun setUp() {
        metadata = WorkflowMetadata(
            executionId = UUID.randomUUID(),
            workspaceId = UUID.randomUUID(),
            workflowDefinitionId = UUID.randomUUID(),
            version = 1,
            startedAt = Instant.now()
        )

        state = WorkflowState(
            phase = WorkflowExecutionPhase.INITIALIZING
        )

        dataStore = WorkflowDataStore(metadata, state)
    }

    // ==================== Metadata Tests ====================

    @Test
    fun `metadata is accessible from datastore`() {
        assertEquals(metadata.executionId, dataStore.metadata.executionId)
        assertEquals(metadata.workspaceId, dataStore.metadata.workspaceId)
        assertEquals(metadata.workflowDefinitionId, dataStore.metadata.workflowDefinitionId)
        assertEquals(metadata.version, dataStore.metadata.version)
    }

    // ==================== Step Output Tests ====================

    @Test
    fun `setStepOutput stores and retrieves step`() {
        val stepOutput = createStepOutput("node1")

        dataStore.setStepOutput("node1", stepOutput)

        val retrieved = dataStore.getStepOutput("node1")
        assertNotNull(retrieved)
        assertEquals(stepOutput.nodeId, retrieved!!.nodeId)
        assertEquals(stepOutput.nodeName, retrieved.nodeName)
        assertEquals(stepOutput.status, retrieved.status)
    }

    @Test
    fun `setStepOutput throws IllegalStateException on duplicate key`() {
        val stepOutput1 = createStepOutput("node1")
        val stepOutput2 = createStepOutput("node1")

        dataStore.setStepOutput("node1", stepOutput1)

        val exception = assertThrows(IllegalStateException::class.java) {
            dataStore.setStepOutput("node1", stepOutput2)
        }

        assertEquals("Step output already exists for: node1", exception.message)
    }

    @Test
    fun `getStepOutput returns null for non-existent step`() {
        assertNull(dataStore.getStepOutput("nonexistent"))
    }

    @Test
    fun `getAllStepOutputs returns all steps`() {
        val step1 = createStepOutput("node1")
        val step2 = createStepOutput("node2")
        val step3 = createStepOutput("node3")

        dataStore.setStepOutput("node1", step1)
        dataStore.setStepOutput("node2", step2)
        dataStore.setStepOutput("node3", step3)

        val allSteps = dataStore.getAllStepOutputs()
        assertEquals(3, allSteps.size)
        assertTrue(allSteps.containsKey("node1"))
        assertTrue(allSteps.containsKey("node2"))
        assertTrue(allSteps.containsKey("node3"))
    }

    @Test
    fun `getAllStepOutputs returns copy - modifications do not affect datastore`() {
        val stepOutput = createStepOutput("node1")
        dataStore.setStepOutput("node1", stepOutput)

        val allSteps = dataStore.getAllStepOutputs()

        // Try to modify the returned map (this would throw if truly immutable,
        // but toMap() returns a new Map that can be modified)
        @Suppress("UNCHECKED_CAST")
        val mutableMap = allSteps as? MutableMap<String, StepOutput>

        // If we got a mutable map, verify original is unchanged
        if (mutableMap != null) {
            try {
                mutableMap.remove("node1")
            } catch (e: UnsupportedOperationException) {
                // Expected for immutable maps
            }
        }

        // Original datastore should still have the step
        assertNotNull(dataStore.getStepOutput("node1"))
    }

    // ==================== Variable Tests ====================

    @Test
    fun `setVariable stores and retrieves variable`() {
        dataStore.setVariable("counter", 42)

        assertEquals(42, dataStore.getVariable("counter"))
    }

    @Test
    fun `setVariable allows overwrite - last write wins`() {
        dataStore.setVariable("counter", 1)
        dataStore.setVariable("counter", 2)
        dataStore.setVariable("counter", 3)

        assertEquals(3, dataStore.getVariable("counter"))
    }

    @Test
    fun `setVariable supports null values`() {
        dataStore.setVariable("nullable", "initial")
        dataStore.setVariable("nullable", null)

        assertNull(dataStore.getVariable("nullable"))
    }

    @Test
    fun `getVariable returns null for non-existent variable`() {
        assertNull(dataStore.getVariable("nonexistent"))
    }

    @Test
    fun `getAllVariables returns all variables`() {
        dataStore.setVariable("var1", "value1")
        dataStore.setVariable("var2", 42)
        dataStore.setVariable("var3", listOf(1, 2, 3))

        val allVars = dataStore.getAllVariables()
        assertEquals(3, allVars.size)
        assertEquals("value1", allVars["var1"])
        assertEquals(42, allVars["var2"])
        assertEquals(listOf(1, 2, 3), allVars["var3"])
    }

    @Test
    fun `getAllVariables returns copy - modifications do not affect datastore`() {
        dataStore.setVariable("var1", "value1")

        val allVars = dataStore.getAllVariables()

        @Suppress("UNCHECKED_CAST")
        val mutableMap = allVars as? MutableMap<String, Any?>

        if (mutableMap != null) {
            try {
                mutableMap.remove("var1")
            } catch (e: UnsupportedOperationException) {
                // Expected for immutable maps
            }
        }

        // Original datastore should still have the variable
        assertEquals("value1", dataStore.getVariable("var1"))
    }

    // ==================== Trigger Tests ====================

    @Test
    fun `setTrigger stores trigger`() {
        val triggerData = EntityEventTrigger(
            eventType = OperationType.CREATE,
            entityId = UUID.randomUUID(),
            entityTypeId = UUID.randomUUID(),
            entity = mapOf("name" to "test")
        )

        dataStore.setTrigger(triggerData)

        assertEquals(triggerData, dataStore.getTrigger())
    }

    @Test
    fun `setTrigger throws IllegalStateException when already set`() {
        val trigger1 = EntityEventTrigger(
            eventType = OperationType.CREATE,
            entityId = UUID.randomUUID(),
            entityTypeId = UUID.randomUUID(),
            entity = mapOf("first" to "trigger")
        )
        val trigger2 = EntityEventTrigger(
            eventType = OperationType.UPDATE,
            entityId = UUID.randomUUID(),
            entityTypeId = UUID.randomUUID(),
            entity = mapOf("second" to "trigger")
        )

        dataStore.setTrigger(trigger1)

        val exception = assertThrows(IllegalStateException::class.java) {
            dataStore.setTrigger(trigger2)
        }

        assertEquals("Trigger already set", exception.message)
    }

    @Test
    fun `getTrigger returns null when not set`() {
        assertNull(dataStore.getTrigger())
    }

    // ==================== Loop Context Tests ====================

    @Test
    fun `setLoopContext stores and retrieves loop context`() {
        val loopContext = LoopContext(
            loopId = "loop1",
            currentIndex = 0,
            currentItem = "item1",
            totalItems = 10
        )

        dataStore.setLoopContext("loop1", loopContext)

        val retrieved = dataStore.getLoopContext("loop1")
        assertNotNull(retrieved)
        assertEquals(0, retrieved!!.currentIndex)
        assertEquals("item1", retrieved.currentItem)
        assertEquals(10, retrieved.totalItems)
    }

    @Test
    fun `setLoopContext allows updates - loop iteration tracking`() {
        val loop1 = LoopContext("loop1", 0, "item0", 10)
        val loop2 = LoopContext("loop1", 1, "item1", 10)

        dataStore.setLoopContext("loop1", loop1)
        dataStore.setLoopContext("loop1", loop2)

        val retrieved = dataStore.getLoopContext("loop1")
        assertEquals(1, retrieved!!.currentIndex)
        assertEquals("item1", retrieved.currentItem)
    }

    @Test
    fun `getLoopContext returns null for non-existent loop`() {
        assertNull(dataStore.getLoopContext("nonexistent"))
    }

    // ==================== Concurrency Tests ====================

    @Test
    fun `concurrent setVariable calls all succeed - last writer wins`() {
        val executor = Executors.newFixedThreadPool(10)
        val latch = CountDownLatch(100)

        repeat(100) { i ->
            executor.submit {
                try {
                    dataStore.setVariable("counter", i)
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // Variable should have some value (last writer wins)
        val finalValue = dataStore.getVariable("counter")
        assertNotNull(finalValue)
        assertTrue(finalValue is Int)
        assertTrue((finalValue as Int) in 0..99)
    }

    @Test
    fun `concurrent setStepOutput with different keys all succeed`() {
        val executor = Executors.newFixedThreadPool(10)
        val successCount = AtomicInteger(0)
        val latch = CountDownLatch(10)

        repeat(10) { i ->
            executor.submit {
                try {
                    dataStore.setStepOutput("node$i", createStepOutput("node$i"))
                    successCount.incrementAndGet()
                } catch (e: IllegalStateException) {
                    fail("Should not throw for different keys")
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        assertEquals(10, successCount.get())
        assertEquals(10, dataStore.getAllStepOutputs().size)
    }

    @Test
    fun `concurrent setStepOutput with same key - one succeeds, others throw`() {
        val executor = Executors.newFixedThreadPool(10)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)
        val latch = CountDownLatch(10)

        repeat(10) { i ->
            executor.submit {
                try {
                    dataStore.setStepOutput("node1", createStepOutput("node1", i))
                    successCount.incrementAndGet()
                } catch (e: IllegalStateException) {
                    failCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // Exactly one should succeed, the rest should fail
        assertEquals(1, successCount.get())
        assertEquals(9, failCount.get())

        // The datastore should have exactly one step output
        val allSteps = dataStore.getAllStepOutputs()
        assertEquals(1, allSteps.size)
        assertNotNull(allSteps["node1"])
    }

    @Test
    fun `concurrent mixed operations - steps, variables, loops`() {
        val executor = Executors.newFixedThreadPool(20)
        val latch = CountDownLatch(60)

        // 20 threads write different step outputs
        repeat(20) { i ->
            executor.submit {
                try {
                    dataStore.setStepOutput("step$i", createStepOutput("step$i"))
                } finally {
                    latch.countDown()
                }
            }
        }

        // 20 threads write to variables (some overlap)
        repeat(20) { i ->
            executor.submit {
                try {
                    dataStore.setVariable("var${i % 5}", i)
                } finally {
                    latch.countDown()
                }
            }
        }

        // 20 threads write to loop contexts (some overlap)
        repeat(20) { i ->
            executor.submit {
                try {
                    dataStore.setLoopContext("loop${i % 3}", LoopContext("loop${i % 3}", i, "item$i", 100))
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // All 20 step outputs should be stored
        assertEquals(20, dataStore.getAllStepOutputs().size)

        // 5 unique variables should exist
        assertEquals(5, dataStore.getAllVariables().size)
    }

    // ==================== Helper Functions ====================

    private fun createStepOutput(nodeName: String, index: Int = 0): StepOutput {
        return StepOutput(
            nodeId = UUID.randomUUID(),
            nodeName = nodeName,
            status = WorkflowStatus.COMPLETED,
            output = CreateEntityOutput(
                entityId = UUID.randomUUID(),
                entityTypeId = UUID.randomUUID(),
                payload = mapOf(UUID.randomUUID() to "value$index")
            ),
            executedAt = Instant.now(),
            durationMs = 100L
        )
    }
}
