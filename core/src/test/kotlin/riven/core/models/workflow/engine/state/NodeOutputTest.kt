package cranium.core.models.workflow.engine.state

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class NodeOutputTest {

    // =========================================================================
    // CreateEntityOutput Tests
    // =========================================================================

    @Test
    fun `CreateEntityOutput toMap contains all fields`() {
        val entityId = UUID.randomUUID()
        val entityTypeId = UUID.randomUUID()
        val payloadKey = UUID.randomUUID()
        val output = CreateEntityOutput(
            entityId = entityId,
            entityTypeId = entityTypeId,
            payload = mapOf(payloadKey to "value")
        )

        val map = output.toMap()

        assertEquals(entityId, map["entityId"])
        assertEquals(entityTypeId, map["entityTypeId"])
        assertNotNull(map["payload"])
        assertEquals(3, map.size)
    }

    @Test
    fun `CreateEntityOutput toMap payload preserves structure`() {
        val key1 = UUID.randomUUID()
        val key2 = UUID.randomUUID()
        val payload = mapOf<UUID, Any?>(
            key1 to "string value",
            key2 to 42
        )
        val output = CreateEntityOutput(
            entityId = UUID.randomUUID(),
            entityTypeId = UUID.randomUUID(),
            payload = payload
        )

        val map = output.toMap()

        @Suppress("UNCHECKED_CAST")
        val resultPayload = map["payload"] as Map<UUID, Any?>
        assertEquals("string value", resultPayload[key1])
        assertEquals(42, resultPayload[key2])
    }

    // =========================================================================
    // UpdateEntityOutput Tests
    // =========================================================================

    @Test
    fun `UpdateEntityOutput toMap contains all fields`() {
        val entityId = UUID.randomUUID()
        val payloadKey = UUID.randomUUID()
        val output = UpdateEntityOutput(
            entityId = entityId,
            updated = true,
            payload = mapOf(payloadKey to "updated value")
        )

        val map = output.toMap()

        assertEquals(entityId, map["entityId"])
        assertEquals(true, map["updated"])
        assertNotNull(map["payload"])
        assertEquals(3, map.size)
    }

    @Test
    fun `UpdateEntityOutput toMap updated flag reflects state`() {
        val successOutput = UpdateEntityOutput(
            entityId = UUID.randomUUID(),
            updated = true,
            payload = emptyMap()
        )
        val failureOutput = UpdateEntityOutput(
            entityId = UUID.randomUUID(),
            updated = false,
            payload = emptyMap()
        )

        assertTrue(successOutput.toMap()["updated"] as Boolean)
        assertFalse(failureOutput.toMap()["updated"] as Boolean)
    }

    // =========================================================================
    // DeleteEntityOutput Tests
    // =========================================================================

    @Test
    fun `DeleteEntityOutput toMap contains all fields`() {
        val entityId = UUID.randomUUID()
        val output = DeleteEntityOutput(
            entityId = entityId,
            deleted = true,
            impactedEntities = 5
        )

        val map = output.toMap()

        assertEquals(entityId, map["entityId"])
        assertEquals(true, map["deleted"])
        assertEquals(5, map["impactedEntities"])
        assertEquals(3, map.size)
    }

    @Test
    fun `DeleteEntityOutput toMap impactedEntities can be zero`() {
        val output = DeleteEntityOutput(
            entityId = UUID.randomUUID(),
            deleted = true,
            impactedEntities = 0
        )

        val map = output.toMap()

        assertEquals(0, map["impactedEntities"])
    }

    // =========================================================================
    // QueryEntityOutput Tests
    // =========================================================================

    @Test
    fun `QueryEntityOutput toMap contains all fields`() {
        val entities = listOf(
            mapOf("id" to "1", "name" to "Entity 1"),
            mapOf("id" to "2", "name" to "Entity 2")
        )
        val output = QueryEntityOutput(
            entities = entities,
            totalCount = 10,
            hasMore = true
        )

        val map = output.toMap()

        assertEquals(entities, map["entities"])
        assertEquals(10, map["totalCount"])
        assertEquals(true, map["hasMore"])
        assertEquals(3, map.size)
    }

    @Test
    fun `QueryEntityOutput toMap empty entities list`() {
        val output = QueryEntityOutput(
            entities = emptyList(),
            totalCount = 0,
            hasMore = false
        )

        val map = output.toMap()

        @Suppress("UNCHECKED_CAST")
        val entities = map["entities"] as List<Map<String, Any?>>
        assertTrue(entities.isEmpty())
        assertEquals(0, map["totalCount"])
        assertFalse(map["hasMore"] as Boolean)
    }

    @Test
    fun `QueryEntityOutput hasMore reflects pagination state`() {
        val withMore = QueryEntityOutput(
            entities = emptyList(),
            totalCount = 100,
            hasMore = true
        )
        val noMore = QueryEntityOutput(
            entities = emptyList(),
            totalCount = 5,
            hasMore = false
        )

        assertTrue(withMore.toMap()["hasMore"] as Boolean)
        assertFalse(noMore.toMap()["hasMore"] as Boolean)
    }

    // =========================================================================
    // HttpResponseOutput Tests
    // =========================================================================

    @Test
    fun `HttpResponseOutput toMap contains all fields`() {
        val headers = mapOf("Content-Type" to "application/json")
        val output = HttpResponseOutput(
            statusCode = 200,
            headers = headers,
            body = """{"status": "ok"}""",
            url = "https://api.example.com/test",
            method = "GET"
        )

        val map = output.toMap()

        assertEquals(200, map["statusCode"])
        assertEquals(headers, map["headers"])
        assertEquals("""{"status": "ok"}""", map["body"])
        assertEquals("https://api.example.com/test", map["url"])
        assertEquals("GET", map["method"])
        assertEquals(true, map["success"])
        assertEquals(6, map.size)
    }

    @Test
    fun `HttpResponseOutput success computed correctly for 2xx`() {
        val successCodes = listOf(200, 201, 202, 204, 299)

        for (code in successCodes) {
            val output = HttpResponseOutput(
                statusCode = code,
                headers = emptyMap(),
                body = null,
                url = "http://test",
                method = "GET"
            )
            assertTrue(output.success, "Status code $code should be success")
            assertTrue(output.toMap()["success"] as Boolean, "toMap() for $code should show success")
        }
    }

    @Test
    fun `HttpResponseOutput success computed correctly for non-2xx`() {
        val failureCodes = listOf(199, 300, 400, 401, 403, 404, 500, 502)

        for (code in failureCodes) {
            val output = HttpResponseOutput(
                statusCode = code,
                headers = emptyMap(),
                body = null,
                url = "http://test",
                method = "GET"
            )
            assertFalse(output.success, "Status code $code should not be success")
            assertFalse(output.toMap()["success"] as Boolean, "toMap() for $code should not show success")
        }
    }

    @Test
    fun `HttpResponseOutput body can be null`() {
        val output = HttpResponseOutput(
            statusCode = 204,
            headers = emptyMap(),
            body = null,
            url = "http://test",
            method = "DELETE"
        )

        val map = output.toMap()

        assertNull(map["body"])
    }

    @Test
    fun `HttpResponseOutput headers preserved in toMap`() {
        val headers = mapOf(
            "Content-Type" to "text/html",
            "X-Custom-Header" to "custom-value"
        )
        val output = HttpResponseOutput(
            statusCode = 200,
            headers = headers,
            body = "<html></html>",
            url = "http://test",
            method = "GET"
        )

        val map = output.toMap()

        @Suppress("UNCHECKED_CAST")
        val resultHeaders = map["headers"] as Map<String, String>
        assertEquals("text/html", resultHeaders["Content-Type"])
        assertEquals("custom-value", resultHeaders["X-Custom-Header"])
    }

    // =========================================================================
    // ConditionOutput Tests
    // =========================================================================

    @Test
    fun `ConditionOutput toMap contains all fields`() {
        val output = ConditionOutput(
            result = true,
            evaluatedExpression = "entity.status == 'active'"
        )

        val map = output.toMap()

        assertEquals(true, map["result"])
        assertEquals(true, map["conditionResult"])
        assertEquals("entity.status == 'active'", map["evaluatedExpression"])
        assertEquals(3, map.size)
    }

    @Test
    fun `ConditionOutput includes backward compatibility alias`() {
        val trueOutput = ConditionOutput(result = true, evaluatedExpression = "x > 5")
        val falseOutput = ConditionOutput(result = false, evaluatedExpression = "x > 5")

        // Both result and conditionResult should have same value
        assertEquals(trueOutput.toMap()["result"], trueOutput.toMap()["conditionResult"])
        assertEquals(falseOutput.toMap()["result"], falseOutput.toMap()["conditionResult"])

        // Verify actual values
        assertEquals(true, trueOutput.toMap()["conditionResult"])
        assertEquals(false, falseOutput.toMap()["conditionResult"])
    }

    @Test
    fun `ConditionOutput result reflects boolean value`() {
        val trueResult = ConditionOutput(result = true, evaluatedExpression = "true")
        val falseResult = ConditionOutput(result = false, evaluatedExpression = "false")

        assertTrue(trueResult.result)
        assertTrue(trueResult.toMap()["result"] as Boolean)
        assertFalse(falseResult.result)
        assertFalse(falseResult.toMap()["result"] as Boolean)
    }

    @Test
    fun `ConditionOutput evaluatedExpression preserved`() {
        val expression = "entity.balance > 1000 && entity.status == 'active'"
        val output = ConditionOutput(
            result = true,
            evaluatedExpression = expression
        )

        val map = output.toMap()

        assertEquals(expression, map["evaluatedExpression"])
    }

    // =========================================================================
    // Type Safety Tests
    // =========================================================================

    @Test
    fun `all output types implement NodeOutput`() {
        val outputs: List<NodeOutput> = listOf(
            CreateEntityOutput(UUID.randomUUID(), UUID.randomUUID(), emptyMap()),
            UpdateEntityOutput(UUID.randomUUID(), true, emptyMap()),
            DeleteEntityOutput(UUID.randomUUID(), true, 0),
            QueryEntityOutput(emptyList(), 0, false),
            HttpResponseOutput(200, emptyMap(), null, "http://test", "GET"),
            ConditionOutput(true, "true")
        )

        assertEquals(6, outputs.size)
        outputs.forEach { output ->
            assertNotNull(output.toMap())
        }
    }
}
