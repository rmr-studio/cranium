package cranium.core.service.workflow.state

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import cranium.core.configuration.auth.WorkspaceSecurity
import cranium.core.enums.util.OperationType
import cranium.core.enums.workflow.WorkflowStatus
import cranium.core.models.workflow.engine.state.WorkflowExecutionPhase
import cranium.core.models.workflow.engine.state.WorkflowState
import cranium.core.models.workflow.engine.state.*
import cranium.core.service.auth.AuthTokenService
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        WorkflowNodeInputResolverServiceTest.TestConfig::class,
        WorkflowNodeTemplateParserService::class,
        WorkflowNodeInputResolverService::class
    ]
)
class WorkflowNodeInputResolverServiceTest {

    private lateinit var dataStore: WorkflowDataStore

    @Configuration
    class TestConfig

    @Autowired
    private lateinit var workflowNodeTemplateParserService: WorkflowNodeTemplateParserService

    @MockitoBean
    private lateinit var logger: KLogger

    @Autowired
    private lateinit var workflowNodeInputResolverService: WorkflowNodeInputResolverService

    @BeforeEach
    fun setup() {
        val state = WorkflowState(phase = WorkflowExecutionPhase.INITIALIZING)
        val metadata = WorkflowMetadata(
            executionId = UUID.randomUUID(),
            workspaceId = UUID.randomUUID(),
            workflowDefinitionId = UUID.randomUUID(),
            version = 1,
            startedAt = Instant.now()
        )
        dataStore = WorkflowDataStore(metadata, state)
    }

    // ========== Steps Resolution Tests ==========

    @Test
    fun `resolve simple template from steps`() {
        val stepOutput = StepOutput(
            nodeId = UUID.randomUUID(),
            nodeName = "fetch_leads",
            status = WorkflowStatus.COMPLETED,
            output = CreateEntityOutput(
                entityId = UUID.randomUUID(),
                entityTypeId = UUID.randomUUID(),
                payload = mapOf(UUID.randomUUID() to 42)
            ),
            executedAt = Instant.now(),
            durationMs = 100
        )
        dataStore.setStepOutput("fetch_leads", stepOutput)

        val result = workflowNodeInputResolverService.resolve("{{ steps.fetch_leads.entityId }}", dataStore)

        assertNotNull(result)
    }

    @Test
    fun `resolve template with output segment for backward compatibility`() {
        val stepOutput = StepOutput(
            nodeId = UUID.randomUUID(),
            nodeName = "fetch_leads",
            status = WorkflowStatus.COMPLETED,
            output = CreateEntityOutput(
                entityId = UUID.randomUUID(),
                entityTypeId = UUID.randomUUID(),
                payload = emptyMap()
            ),
            executedAt = Instant.now(),
            durationMs = 100
        )
        dataStore.setStepOutput("fetch_leads", stepOutput)

        // Backward compatible path: {{ steps.nodeName.output.field }}
        val result = workflowNodeInputResolverService.resolve("{{ steps.fetch_leads.output.entityId }}", dataStore)

        assertNotNull(result)
    }

    @Test
    fun `resolve nested property access in steps`() {
        val stepOutput = StepOutput(
            nodeId = UUID.randomUUID(),
            nodeName = "query_users",
            status = WorkflowStatus.COMPLETED,
            output = QueryEntityOutput(
                entities = listOf(
                    mapOf("name" to "John Doe", "email" to "john@example.com")
                ),
                totalCount = 1,
                hasMore = false
            ),
            executedAt = Instant.now(),
            durationMs = 50
        )
        dataStore.setStepOutput("query_users", stepOutput)

        val result = workflowNodeInputResolverService.resolve("{{ steps.query_users.totalCount }}", dataStore)

        assertEquals(1, result)
    }

    // ========== Trigger Resolution Tests ==========

    @Test
    fun `resolves trigger path`() {
        val entityId = UUID.randomUUID()
        val entityTypeId = UUID.randomUUID()
        val trigger = EntityEventTrigger(
            eventType = OperationType.CREATE,
            entityId = entityId,
            entityTypeId = entityTypeId,
            entity = mapOf("name" to "Test Entity")
        )
        dataStore.setTrigger(trigger)

        val result = workflowNodeInputResolverService.resolve("{{ trigger.entity.name }}", dataStore)

        assertEquals("Test Entity", result)
    }

    @Test
    fun `resolves trigger eventType`() {
        val trigger = EntityEventTrigger(
            eventType = OperationType.CREATE,
            entityId = UUID.randomUUID(),
            entityTypeId = UUID.randomUUID(),
            entity = mapOf("name" to "Test")
        )
        dataStore.setTrigger(trigger)

        val result = workflowNodeInputResolverService.resolve("{{ trigger.eventType }}", dataStore)

        assertEquals("CREATE", result)
    }

    @Test
    fun `resolves trigger entityId`() {
        val entityId = UUID.randomUUID()
        val trigger = EntityEventTrigger(
            eventType = OperationType.UPDATE,
            entityId = entityId,
            entityTypeId = UUID.randomUUID(),
            entity = emptyMap()
        )
        dataStore.setTrigger(trigger)

        val result = workflowNodeInputResolverService.resolve("{{ trigger.entityId }}", dataStore)

        assertEquals(entityId, result)
    }

    @Test
    fun `throws when trigger not set`() {
        val exception = assertThrows<IllegalStateException> {
            workflowNodeInputResolverService.resolve("{{ trigger.entity.id }}", dataStore)
        }

        assertEquals("Trigger not set. Cannot resolve {{ trigger.* }}", exception.message)
    }

    // ========== Variables Resolution Tests ==========

    @Test
    fun `resolves variables path`() {
        dataStore.setVariable("counter", 42)

        val result = workflowNodeInputResolverService.resolve("{{ variables.counter }}", dataStore)

        assertEquals(42, result)
    }

    @Test
    fun `resolves nested variable`() {
        dataStore.setVariable("user", mapOf("name" to "Alice", "email" to "alice@example.com"))

        val result = workflowNodeInputResolverService.resolve("{{ variables.user.name }}", dataStore)

        assertEquals("Alice", result)
    }

    @Test
    fun `resolves variable with null value`() {
        dataStore.setVariable("nullableField", null)

        val result = workflowNodeInputResolverService.resolve("{{ variables.nullableField }}", dataStore)

        assertNull(result)
    }

    @Test
    fun `returns null for missing variable`() {
        // Variable not set
        val result = workflowNodeInputResolverService.resolve("{{ variables.missing }}", dataStore)

        assertNull(result)
    }

    // ========== Loops Resolution Tests ==========

    @Test
    fun `resolves loops path`() {
        dataStore.setLoopContext(
            "processItems", LoopContext(
                loopId = "processItems",
                currentIndex = 2,
                currentItem = mapOf("id" to "item-3", "value" to 100),
                totalItems = 10
            )
        )

        val result = workflowNodeInputResolverService.resolve("{{ loops.processItems.currentItem.id }}", dataStore)

        assertEquals("item-3", result)
    }

    @Test
    fun `resolves loops currentIndex`() {
        dataStore.setLoopContext(
            "myLoop", LoopContext(
                loopId = "myLoop",
                currentIndex = 5,
                currentItem = "test",
                totalItems = 20
            )
        )

        val result = workflowNodeInputResolverService.resolve("{{ loops.myLoop.currentIndex }}", dataStore)

        assertEquals(5, result)
    }

    @Test
    fun `resolves loops totalItems`() {
        dataStore.setLoopContext(
            "batch", LoopContext(
                loopId = "batch",
                currentIndex = 0,
                currentItem = null,
                totalItems = 100
            )
        )

        val result = workflowNodeInputResolverService.resolve("{{ loops.batch.totalItems }}", dataStore)

        assertEquals(100, result)
    }

    @Test
    fun `returns null for missing loop context`() {
        val result = workflowNodeInputResolverService.resolve("{{ loops.nonexistent.currentItem }}", dataStore)

        assertNull(result)
    }

    // ========== Static Value Tests ==========

    @Test
    fun `resolve static string returns unchanged`() {
        val result = workflowNodeInputResolverService.resolve("static value", dataStore)

        assertEquals("static value", result)
    }

    @Test
    fun `resolve non-string value returns unchanged`() {
        assertEquals(42, workflowNodeInputResolverService.resolve(42, dataStore))
        assertEquals(true, workflowNodeInputResolverService.resolve(true, dataStore))
        assertEquals(3.14, workflowNodeInputResolverService.resolve(3.14, dataStore))
    }

    @Test
    fun `resolve null returns null`() {
        assertNull(workflowNodeInputResolverService.resolve(null, dataStore))
    }

    // ========== Missing Data Tests (Graceful Degradation) ==========

    @Test
    fun `resolve template for missing step returns null`() {
        val result = workflowNodeInputResolverService.resolve("{{ steps.missing_node.output }}", dataStore)

        assertNull(result)
    }

    @Test
    fun `resolve template for missing property returns null`() {
        val stepOutput = StepOutput(
            nodeId = UUID.randomUUID(),
            nodeName = "fetch_leads",
            status = WorkflowStatus.COMPLETED,
            output = CreateEntityOutput(
                entityId = UUID.randomUUID(),
                entityTypeId = UUID.randomUUID(),
                payload = emptyMap()
            ),
            executedAt = Instant.now(),
            durationMs = 100
        )
        dataStore.setStepOutput("fetch_leads", stepOutput)

        val result =
            workflowNodeInputResolverService.resolve("{{ steps.fetch_leads.output.missing_field }}", dataStore)

        assertNull(result)
    }

    @Test
    fun `resolve template for failed step returns null`() {
        val stepOutput = StepOutput(
            nodeId = UUID.randomUUID(),
            nodeName = "failed_node",
            status = WorkflowStatus.FAILED,
            output = UnsupportedNodeOutput("Node failed"),
            executedAt = Instant.now(),
            durationMs = 10
        )
        dataStore.setStepOutput("failed_node", stepOutput)

        val result = workflowNodeInputResolverService.resolve("{{ steps.failed_node.output }}", dataStore)

        assertNull(result)
    }

    @Test
    fun `resolve template with null in traversal path returns null`() {
        val stepOutput = StepOutput(
            nodeId = UUID.randomUUID(),
            nodeName = "fetch_user",
            status = WorkflowStatus.COMPLETED,
            output = QueryEntityOutput(
                entities = emptyList(),
                totalCount = 0,
                hasMore = false
            ),
            executedAt = Instant.now(),
            durationMs = 100
        )
        dataStore.setStepOutput("fetch_user", stepOutput)

        // entities is an empty list, so [0] doesn't exist
        val result =
            workflowNodeInputResolverService.resolve("{{ steps.fetch_user.entities.nonexistent }}", dataStore)

        assertNull(result)
    }

    // ========== Error Cases ==========

    @Test
    fun `throws on invalid root segment`() {
        val exception = assertThrows<IllegalArgumentException> {
            workflowNodeInputResolverService.resolve("{{ invalid.path }}", dataStore)
        }
        assertEquals(
            "Invalid root segment 'invalid'. Must be: steps, trigger, variables, loops",
            exception.message
        )
    }

    @Test
    fun `resolve template with only steps segment throws error`() {
        val exception = assertThrows<IllegalArgumentException> {
            workflowNodeInputResolverService.resolve("{{ steps }}", dataStore)
        }
        assert(exception.message!!.contains("must include node name"))
    }

    @Test
    fun `resolve template with only variables segment throws error`() {
        val exception = assertThrows<IllegalArgumentException> {
            workflowNodeInputResolverService.resolve("{{ variables }}", dataStore)
        }
        assert(exception.message!!.contains("must include variable name"))
    }

    @Test
    fun `resolve template with only loops segment throws error`() {
        val exception = assertThrows<IllegalArgumentException> {
            workflowNodeInputResolverService.resolve("{{ loops }}", dataStore)
        }
        assert(exception.message!!.contains("must include loop name"))
    }

    // ========== Embedded Template Tests ==========

    @Test
    fun `resolves embedded template in string`() {
        val trigger = EntityEventTrigger(
            eventType = OperationType.CREATE,
            entityId = UUID.randomUUID(),
            entityTypeId = UUID.randomUUID(),
            entity = mapOf("name" to "Acme Corp")
        )
        dataStore.setTrigger(trigger)

        val result = workflowNodeInputResolverService.resolve("Welcome to {{ trigger.entity.name }}!", dataStore)

        assertEquals("Welcome to Acme Corp!", result)
    }

    @Test
    fun `resolves multiple embedded templates`() {
        dataStore.setVariable("greeting", "Hello")
        dataStore.setVariable("name", "Alice")

        val result =
            workflowNodeInputResolverService.resolve("{{ variables.greeting }}, {{ variables.name }}!", dataStore)

        assertEquals("Hello, Alice!", result)
    }

    // ========== Recursive Resolution Tests ==========

    @Test
    fun `resolveAll resolves templates in flat config`() {
        val trigger = EntityEventTrigger(
            eventType = OperationType.CREATE,
            entityId = UUID.randomUUID(),
            entityTypeId = UUID.randomUUID(),
            entity = mapOf(
                "email" to "user@example.com",
                "name" to "John Doe"
            )
        )
        dataStore.setTrigger(trigger)

        val config = mapOf(
            "to" to "{{ trigger.entity.email }}",
            "subject" to "Welcome {{ trigger.entity.name }}!"
        )

        val resolved = workflowNodeInputResolverService.resolveAll(config, dataStore)

        assertEquals("user@example.com", resolved["to"])
        assertEquals("Welcome John Doe!", resolved["subject"])
    }

    @Test
    fun `resolveAll resolves templates in nested config`() {
        dataStore.setVariable("url", "https://api.example.com")
        dataStore.setVariable("token", "secret123")

        val config = mapOf(
            "http" to mapOf(
                "url" to "{{ variables.url }}",
                "headers" to mapOf(
                    "Authorization" to "Bearer {{ variables.token }}"
                )
            ),
            "static" to "value"
        )

        val resolved = workflowNodeInputResolverService.resolveAll(config, dataStore)

        @Suppress("UNCHECKED_CAST")
        val http = resolved["http"] as Map<String, Any?>
        assertEquals("https://api.example.com", http["url"])

        @Suppress("UNCHECKED_CAST")
        val headers = http["headers"] as Map<String, Any?>
        assertEquals("Bearer secret123", headers["Authorization"])

        assertEquals("value", resolved["static"])
    }

    @Test
    fun `resolveAll resolves templates in lists`() {
        dataStore.setVariable("primary", "primary@example.com")
        dataStore.setVariable("secondary", "secondary@example.com")

        val config = mapOf(
            "recipients" to listOf(
                "{{ variables.primary }}",
                "{{ variables.secondary }}",
                "static@example.com"
            )
        )

        val resolved = workflowNodeInputResolverService.resolveAll(config, dataStore)

        @Suppress("UNCHECKED_CAST")
        val recipients = resolved["recipients"] as List<Any?>
        assertEquals(listOf("primary@example.com", "secondary@example.com", "static@example.com"), recipients)
    }

    @Test
    fun `resolveAll preserves static values`() {
        val config = mapOf(
            "string" to "static",
            "number" to 42,
            "boolean" to true,
            "null" to null,
            "nested" to mapOf("key" to "value")
        )

        val resolved = workflowNodeInputResolverService.resolveAll(config, dataStore)

        assertEquals(config, resolved)
    }

    @Test
    fun `resolveAll handles missing data gracefully`() {
        val config = mapOf(
            "field1" to "{{ steps.missing.output }}",
            "field2" to "static"
        )

        val resolved = workflowNodeInputResolverService.resolveAll(config, dataStore)

        assertNull(resolved["field1"])
        assertEquals("static", resolved["field2"])
    }

    // ========== Cross-prefix Tests ==========

    @Test
    fun `resolves mixed prefix templates in same config`() {
        // Set up trigger
        val trigger = EntityEventTrigger(
            eventType = OperationType.UPDATE,
            entityId = UUID.randomUUID(),
            entityTypeId = UUID.randomUUID(),
            entity = mapOf("status" to "active")
        )
        dataStore.setTrigger(trigger)

        // Set up variable
        dataStore.setVariable("threshold", 100)

        // Set up step output
        val stepOutput = StepOutput(
            nodeId = UUID.randomUUID(),
            nodeName = "get_count",
            status = WorkflowStatus.COMPLETED,
            output = QueryEntityOutput(
                entities = emptyList(),
                totalCount = 50,
                hasMore = false
            ),
            executedAt = Instant.now(),
            durationMs = 25
        )
        dataStore.setStepOutput("get_count", stepOutput)

        val config = mapOf(
            "triggerStatus" to "{{ trigger.entity.status }}",
            "configThreshold" to "{{ variables.threshold }}",
            "actualCount" to "{{ steps.get_count.totalCount }}"
        )

        val resolved = workflowNodeInputResolverService.resolveAll(config, dataStore)

        assertEquals("active", resolved["triggerStatus"])
        assertEquals(100, resolved["configThreshold"])
        assertEquals(50, resolved["actualCount"])
    }
}
