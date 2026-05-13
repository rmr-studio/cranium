package cranium.core.service.workflow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import cranium.core.models.common.Expression
import cranium.core.models.common.Operator
import cranium.core.service.workflow.state.WorkflowNodeExpressionEvaluatorService
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkflowNodeExpressionEvaluatorServiceTest {

    private val evaluator = WorkflowNodeExpressionEvaluatorService()

    @Test
    fun `evaluate simple equality - true`() {
        val expr = Expression.BinaryOp(
            Expression.PropertyAccess(listOf("status")),
            Operator.EQUALS,
            Expression.Literal("active")
        )
        val context = mapOf("status" to "active")

        val result = evaluator.evaluate(expr, context)
        assertTrue(result as Boolean)
    }

    @Test
    fun `evaluate simple equality - false`() {
        val expr = Expression.BinaryOp(
            Expression.PropertyAccess(listOf("status")),
            Operator.EQUALS,
            Expression.Literal("active")
        )
        val context = mapOf("status" to "inactive")

        val result = evaluator.evaluate(expr, context)
        assertFalse(result as Boolean)
    }

    @Test
    fun `evaluate not equals`() {
        val expr = Expression.BinaryOp(
            Expression.PropertyAccess(listOf("status")),
            Operator.NOT_EQUALS,
            Expression.Literal("inactive")
        )
        val context = mapOf("status" to "active")

        val result = evaluator.evaluate(expr, context)
        assertTrue(result as Boolean)
    }

    @Test
    fun `evaluate greater than - true`() {
        val expr = Expression.BinaryOp(
            Expression.PropertyAccess(listOf("count")),
            Operator.GREATER_THAN,
            Expression.Literal(10)
        )
        val context = mapOf("count" to 15)

        val result = evaluator.evaluate(expr, context)
        assertTrue(result as Boolean)
    }

    @Test
    fun `evaluate greater than - false`() {
        val expr = Expression.BinaryOp(
            Expression.PropertyAccess(listOf("count")),
            Operator.GREATER_THAN,
            Expression.Literal(10)
        )
        val context = mapOf("count" to 5)

        val result = evaluator.evaluate(expr, context)
        assertFalse(result as Boolean)
    }

    @Test
    fun `evaluate less than`() {
        val expr = Expression.BinaryOp(
            Expression.PropertyAccess(listOf("count")),
            Operator.LESS_THAN,
            Expression.Literal(10)
        )
        val context = mapOf("count" to 5)

        val result = evaluator.evaluate(expr, context)
        assertTrue(result as Boolean)
    }

    @Test
    fun `evaluate greater or equals`() {
        var expr = Expression.BinaryOp(
            Expression.PropertyAccess(listOf("count")),
            Operator.GREATER_EQUALS,
            Expression.Literal(10)
        )
        var context = mapOf("count" to 10)
        var result = evaluator.evaluate(expr, context)
        assertTrue(result as Boolean)

        context = mapOf("count" to 15)
        result = evaluator.evaluate(expr, context)
        assertTrue(result as Boolean)

        context = mapOf("count" to 5)
        result = evaluator.evaluate(expr, context)
        assertFalse(result as Boolean)
    }

    @Test
    fun `evaluate less or equals`() {
        var expr = Expression.BinaryOp(
            Expression.PropertyAccess(listOf("count")),
            Operator.LESS_EQUALS,
            Expression.Literal(10)
        )
        var context = mapOf("count" to 10)
        var result = evaluator.evaluate(expr, context)
        assertTrue(result as Boolean)

        context = mapOf("count" to 5)
        result = evaluator.evaluate(expr, context)
        assertTrue(result as Boolean)

        context = mapOf("count" to 15)
        result = evaluator.evaluate(expr, context)
        assertFalse(result as Boolean)
    }

    @Test
    fun `evaluate logical AND - both true`() {
        val expr = Expression.BinaryOp(
            Expression.BinaryOp(
                Expression.PropertyAccess(listOf("status")),
                Operator.EQUALS,
                Expression.Literal("active")
            ),
            Operator.AND,
            Expression.BinaryOp(
                Expression.PropertyAccess(listOf("count")),
                Operator.GREATER_THAN,
                Expression.Literal(10)
            )
        )
        val context = mapOf("status" to "active", "count" to 15)

        val result = evaluator.evaluate(expr, context)
        assertTrue(result as Boolean)
    }

    @Test
    fun `evaluate logical AND - first false`() {
        val expr = Expression.BinaryOp(
            Expression.BinaryOp(
                Expression.PropertyAccess(listOf("status")),
                Operator.EQUALS,
                Expression.Literal("active")
            ),
            Operator.AND,
            Expression.BinaryOp(
                Expression.PropertyAccess(listOf("count")),
                Operator.GREATER_THAN,
                Expression.Literal(10)
            )
        )
        val context = mapOf("status" to "inactive", "count" to 15)

        val result = evaluator.evaluate(expr, context)
        assertFalse(result as Boolean)
    }

    @Test
    fun `evaluate logical AND - second false`() {
        val expr = Expression.BinaryOp(
            Expression.BinaryOp(
                Expression.PropertyAccess(listOf("status")),
                Operator.EQUALS,
                Expression.Literal("active")
            ),
            Operator.AND,
            Expression.BinaryOp(
                Expression.PropertyAccess(listOf("count")),
                Operator.GREATER_THAN,
                Expression.Literal(10)
            )
        )
        val context = mapOf("status" to "active", "count" to 5)

        val result = evaluator.evaluate(expr, context)
        assertFalse(result as Boolean)
    }

    @Test
    fun `evaluate logical OR - first true`() {
        val expr = Expression.BinaryOp(
            Expression.BinaryOp(
                Expression.PropertyAccess(listOf("priority")),
                Operator.EQUALS,
                Expression.Literal("high")
            ),
            Operator.OR,
            Expression.BinaryOp(
                Expression.PropertyAccess(listOf("amount")),
                Operator.GREATER_THAN,
                Expression.Literal(1000)
            )
        )
        val context = mapOf("priority" to "high", "amount" to 500)

        val result = evaluator.evaluate(expr, context)
        assertTrue(result as Boolean)
    }

    @Test
    fun `evaluate logical OR - second true`() {
        val expr = Expression.BinaryOp(
            Expression.BinaryOp(
                Expression.PropertyAccess(listOf("priority")),
                Operator.EQUALS,
                Expression.Literal("high")
            ),
            Operator.OR,
            Expression.BinaryOp(
                Expression.PropertyAccess(listOf("amount")),
                Operator.GREATER_THAN,
                Expression.Literal(1000)
            )
        )
        val context = mapOf("priority" to "low", "amount" to 1500)

        val result = evaluator.evaluate(expr, context)
        assertTrue(result as Boolean)
    }

    @Test
    fun `evaluate logical OR - both false`() {
        val expr = Expression.BinaryOp(
            Expression.BinaryOp(
                Expression.PropertyAccess(listOf("priority")),
                Operator.EQUALS,
                Expression.Literal("high")
            ),
            Operator.OR,
            Expression.BinaryOp(
                Expression.PropertyAccess(listOf("amount")),
                Operator.GREATER_THAN,
                Expression.Literal(1000)
            )
        )
        val context = mapOf("priority" to "low", "amount" to 500)

        val result = evaluator.evaluate(expr, context)
        assertFalse(result as Boolean)
    }

    @Test
    fun `evaluate nested property access`() {
        val expr = Expression.BinaryOp(
            Expression.PropertyAccess(listOf("client", "address", "city")),
            Operator.EQUALS,
            Expression.Literal("NYC")
        )
        val context = mapOf(
            "client" to mapOf(
                "address" to mapOf(
                    "city" to "NYC"
                )
            )
        )

        val result = evaluator.evaluate(expr, context)
        assertTrue(result as Boolean)
    }

    @Test
    fun `evaluate property access returns value`() {
        val expr = Expression.PropertyAccess(listOf("status"))
        val context = mapOf("status" to "active")

        val result = evaluator.evaluate(expr, context)
        assertEquals("active", result)
    }

    @Test
    fun `evaluate literal returns value`() {
        val expr = Expression.Literal(42)
        val context = emptyMap<String, Any?>()

        val result = evaluator.evaluate(expr, context)
        assertEquals(42, result)
    }

    @Test
    fun `evaluate with number type coercion`() {
        // Compare Int and Long
        var expr = Expression.BinaryOp(
            Expression.Literal(10),
            Operator.EQUALS,
            Expression.Literal(10L)
        )
        var result = evaluator.evaluate(expr, emptyMap())
        assertTrue(result as Boolean)

        // Compare Int and Double
        expr = Expression.BinaryOp(
            Expression.Literal(10),
            Operator.EQUALS,
            Expression.Literal(10.0)
        )
        result = evaluator.evaluate(expr, emptyMap())
        assertTrue(result as Boolean)
    }

    @Test
    fun `evaluate null equality`() {
        var expr = Expression.BinaryOp(
            Expression.Literal(null),
            Operator.EQUALS,
            Expression.Literal(null)
        )
        var result = evaluator.evaluate(expr, emptyMap())
        assertTrue(result as Boolean)

        expr = Expression.BinaryOp(
            Expression.PropertyAccess(listOf("deletedAt")),
            Operator.EQUALS,
            Expression.Literal(null)
        )
        result = evaluator.evaluate(expr, mapOf("deletedAt" to null))
        assertTrue(result as Boolean)

        result = evaluator.evaluate(expr, mapOf("deletedAt" to "2024-01-01"))
        assertFalse(result as Boolean)
    }

    @Test
    fun `evaluate boolean literals`() {
        var expr = Expression.BinaryOp(
            Expression.PropertyAccess(listOf("isActive")),
            Operator.EQUALS,
            Expression.Literal(true)
        )
        var result = evaluator.evaluate(expr, mapOf("isActive" to true))
        assertTrue(result as Boolean)

        expr = Expression.BinaryOp(
            Expression.PropertyAccess(listOf("isDeleted")),
            Operator.EQUALS,
            Expression.Literal(false)
        )
        result = evaluator.evaluate(expr, mapOf("isDeleted" to false))
        assertTrue(result as Boolean)
    }

    @Test
    fun `throw error on type mismatch in comparison`() {
        val expr = Expression.BinaryOp(
            Expression.PropertyAccess(listOf("count")),
            Operator.GREATER_THAN,
            Expression.Literal("abc")
        )
        val context = mapOf("count" to 10)

        val exception = assertThrows<IllegalArgumentException> {
            evaluator.evaluate(expr, context)
        }
        assertTrue(exception.message!!.contains("Expected number"))
    }

    @Test
    fun `throw error on missing property`() {
        val expr = Expression.PropertyAccess(listOf("unknownField"))
        val context = mapOf("status" to "active")

        val exception = assertThrows<IllegalArgumentException> {
            evaluator.evaluate(expr, context)
        }
        assertTrue(exception.message!!.contains("Property 'unknownField' not found"))
    }

    @Test
    fun `throw error on nested property not found`() {
        val expr = Expression.PropertyAccess(listOf("client", "unknownField"))
        val context = mapOf("client" to mapOf("name" to "Test"))

        val exception = assertThrows<IllegalArgumentException> {
            evaluator.evaluate(expr, context)
        }
        assertTrue(exception.message!!.contains("Property 'unknownField' not found"))
    }

    @Test
    fun `throw error on property access on null`() {
        val expr = Expression.PropertyAccess(listOf("client", "address", "city"))
        val context = mapOf("client" to null)

        val exception = assertThrows<IllegalArgumentException> {
            evaluator.evaluate(expr, context)
        }
        assertTrue(exception.message!!.contains("Cannot access property 'address' on null value"))
    }

    @Test
    fun `throw error on property access on non-map`() {
        val expr = Expression.PropertyAccess(listOf("count", "value"))
        val context = mapOf("count" to 42)

        val exception = assertThrows<IllegalArgumentException> {
            evaluator.evaluate(expr, context)
        }
        assertTrue(exception.message!!.contains("Cannot access property 'value' on non-map value"))
    }

    @Test
    fun `evaluate complex nested expression`() {
        val expr = Expression.BinaryOp(
            Expression.BinaryOp(
                Expression.BinaryOp(
                    Expression.PropertyAccess(listOf("status")),
                    Operator.EQUALS,
                    Expression.Literal("active")
                ),
                Operator.AND,
                Expression.BinaryOp(
                    Expression.PropertyAccess(listOf("priority")),
                    Operator.EQUALS,
                    Expression.Literal("high")
                )
            ),
            Operator.OR,
            Expression.BinaryOp(
                Expression.BinaryOp(
                    Expression.PropertyAccess(listOf("status")),
                    Operator.EQUALS,
                    Expression.Literal("pending")
                ),
                Operator.AND,
                Expression.BinaryOp(
                    Expression.PropertyAccess(listOf("amount")),
                    Operator.GREATER_THAN,
                    Expression.Literal(1000)
                )
            )
        )

        // First branch true: status = active AND priority = high
        var context = mapOf("status" to "active", "priority" to "high", "amount" to 500)
        var result = evaluator.evaluate(expr, context)
        assertTrue(result as Boolean)

        // Second branch true: status = pending AND amount > 1000
        context = mapOf("status" to "pending", "priority" to "low", "amount" to 1500)
        result = evaluator.evaluate(expr, context)
        assertTrue(result as Boolean)

        // Both branches false
        context = mapOf("status" to "inactive", "priority" to "low", "amount" to 500)
        result = evaluator.evaluate(expr, context)
        assertFalse(result as Boolean)
    }
}
