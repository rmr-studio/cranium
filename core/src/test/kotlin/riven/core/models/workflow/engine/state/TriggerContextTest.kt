package cranium.core.models.workflow.engine.state

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import cranium.core.enums.util.OperationType
import java.time.Instant
import java.util.UUID

class TriggerContextTest {

    @Test
    fun `EntityEventTrigger toMap contains all fields`() {
        val entityId = UUID.randomUUID()
        val entityTypeId = UUID.randomUUID()
        val trigger = EntityEventTrigger(
            eventType = OperationType.CREATE,
            entityId = entityId,
            entityTypeId = entityTypeId,
            entity = mapOf("name" to "Test", "status" to "active"),
            previousEntity = null
        )
        val map = trigger.toMap()

        assertEquals("CREATE", map["eventType"])
        assertEquals(entityId, map["entityId"])
        assertEquals(entityTypeId, map["entityTypeId"])
        assertNotNull(map["entity"])
        assertNull(map["previousEntity"])

        @Suppress("UNCHECKED_CAST")
        val entity = map["entity"] as Map<String, Any?>
        assertEquals("Test", entity["name"])
        assertEquals("active", entity["status"])
    }

    @Test
    fun `EntityEventTrigger toMap includes previousEntity for UPDATE`() {
        val trigger = EntityEventTrigger(
            eventType = OperationType.UPDATE,
            entityId = UUID.randomUUID(),
            entityTypeId = UUID.randomUUID(),
            entity = mapOf("name" to "Updated"),
            previousEntity = mapOf("name" to "Original")
        )
        val map = trigger.toMap()

        assertEquals("UPDATE", map["eventType"])
        assertNotNull(map["previousEntity"])

        @Suppress("UNCHECKED_CAST")
        val previous = map["previousEntity"] as Map<String, Any?>
        assertEquals("Original", previous["name"])
    }

    @Test
    fun `EntityEventTrigger supports DELETE event type`() {
        val trigger = EntityEventTrigger(
            eventType = OperationType.DELETE,
            entityId = UUID.randomUUID(),
            entityTypeId = UUID.randomUUID(),
            entity = mapOf("id" to "deleted-entity")
        )
        val map = trigger.toMap()

        assertEquals("DELETE", map["eventType"])
    }

    @Test
    fun `WebhookTrigger toMap enables nested access`() {
        val trigger = WebhookTrigger(
            headers = mapOf("Content-Type" to "application/json", "Authorization" to "Bearer token"),
            body = mapOf("userId" to "123", "action" to "submit"),
            queryParams = mapOf("page" to "1", "limit" to "10")
        )
        val map = trigger.toMap()

        @Suppress("UNCHECKED_CAST")
        val headers = map["headers"] as Map<String, String>
        assertEquals("application/json", headers["Content-Type"])
        assertEquals("Bearer token", headers["Authorization"])

        @Suppress("UNCHECKED_CAST")
        val body = map["body"] as Map<String, Any?>
        assertEquals("123", body["userId"])
        assertEquals("submit", body["action"])

        @Suppress("UNCHECKED_CAST")
        val queryParams = map["queryParams"] as Map<String, String>
        assertEquals("1", queryParams["page"])
        assertEquals("10", queryParams["limit"])
    }

    @Test
    fun `WebhookTrigger handles empty collections`() {
        val trigger = WebhookTrigger(
            headers = emptyMap(),
            body = emptyMap(),
            queryParams = emptyMap()
        )
        val map = trigger.toMap()

        @Suppress("UNCHECKED_CAST")
        val headers = map["headers"] as Map<String, String>
        assertTrue(headers.isEmpty())

        @Suppress("UNCHECKED_CAST")
        val body = map["body"] as Map<String, Any?>
        assertTrue(body.isEmpty())
    }

    @Test
    fun `ScheduleTrigger toMap includes scheduledAt as string`() {
        val now = Instant.parse("2026-02-01T12:30:00Z")
        val trigger = ScheduleTrigger(
            scheduledAt = now,
            cronExpression = "0 * * * *"
        )
        val map = trigger.toMap()

        assertEquals("2026-02-01T12:30:00Z", map["scheduledAt"])
        assertEquals("0 * * * *", map["cronExpression"])
        assertNull(map["interval"])
    }

    @Test
    fun `ScheduleTrigger supports interval-based schedules`() {
        val now = Instant.now()
        val trigger = ScheduleTrigger(
            scheduledAt = now,
            interval = 3600L
        )
        val map = trigger.toMap()

        assertEquals(now.toString(), map["scheduledAt"])
        assertNull(map["cronExpression"])
        assertEquals(3600L, map["interval"])
    }

    @Test
    fun `ScheduleTrigger handles both cron and interval null`() {
        val now = Instant.now()
        val trigger = ScheduleTrigger(scheduledAt = now)
        val map = trigger.toMap()

        assertEquals(now.toString(), map["scheduledAt"])
        assertNull(map["cronExpression"])
        assertNull(map["interval"])
    }

    @Test
    fun `FunctionTrigger toMap contains arguments`() {
        val trigger = FunctionTrigger(
            arguments = mapOf("input" to "value", "count" to 5, "enabled" to true)
        )
        val map = trigger.toMap()

        @Suppress("UNCHECKED_CAST")
        val args = map["arguments"] as Map<String, Any?>
        assertEquals("value", args["input"])
        assertEquals(5, args["count"])
        assertEquals(true, args["enabled"])
    }

    @Test
    fun `FunctionTrigger handles empty arguments`() {
        val trigger = FunctionTrigger(arguments = emptyMap())
        val map = trigger.toMap()

        @Suppress("UNCHECKED_CAST")
        val args = map["arguments"] as Map<String, Any?>
        assertTrue(args.isEmpty())
    }

    @Test
    fun `FunctionTrigger handles nested arguments`() {
        val trigger = FunctionTrigger(
            arguments = mapOf(
                "user" to mapOf("id" to "123", "name" to "Test User"),
                "options" to mapOf("debug" to true)
            )
        )
        val map = trigger.toMap()

        @Suppress("UNCHECKED_CAST")
        val args = map["arguments"] as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val user = args["user"] as Map<String, Any?>
        assertEquals("123", user["id"])
        assertEquals("Test User", user["name"])
    }

    @Test
    fun `All TriggerContext implementations are sealed subtypes`() {
        val triggers: List<TriggerContext> = listOf(
            EntityEventTrigger(
                eventType = OperationType.CREATE,
                entityId = UUID.randomUUID(),
                entityTypeId = UUID.randomUUID(),
                entity = emptyMap()
            ),
            WebhookTrigger(
                headers = emptyMap(),
                body = emptyMap(),
                queryParams = emptyMap()
            ),
            ScheduleTrigger(scheduledAt = Instant.now()),
            FunctionTrigger(arguments = emptyMap())
        )

        // Verify all can be treated as TriggerContext
        triggers.forEach { trigger ->
            assertNotNull(trigger.toMap())
        }

        // Verify exhaustive when expression compiles (sealed interface benefit)
        triggers.forEach { trigger ->
            val description = when (trigger) {
                is EntityEventTrigger -> "entity event"
                is WebhookTrigger -> "webhook"
                is ScheduleTrigger -> "schedule"
                is FunctionTrigger -> "function"
            }
            assertNotNull(description)
        }
    }
}
