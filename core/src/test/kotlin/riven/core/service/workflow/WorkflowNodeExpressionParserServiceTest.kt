package cranium.core.service.workflow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import cranium.core.models.common.Expression
import cranium.core.models.common.Operator
import cranium.core.service.workflow.state.WorkflowNodeExpressionParserService
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkflowNodeExpressionParserServiceTest {

    private val parser = WorkflowNodeExpressionParserService()

    @Test
    fun `parse simple equality comparison`() {
        val result = parser.parse("status = 'active'")

        assertTrue(result is Expression.BinaryOp)
        assertEquals(Operator.EQUALS, result.operator)

        assertTrue(result.left is Expression.PropertyAccess)
        assertEquals(listOf("status"), (result.left as Expression.PropertyAccess).path)

        assertTrue(result.right is Expression.Literal)
        assertEquals("active", (result.right as Expression.Literal).value)
    }

    @Test
    fun `parse number comparison`() {
        val result = parser.parse("count > 10")

        assertTrue(result is Expression.BinaryOp)
        assertEquals(Operator.GREATER_THAN, result.operator)

        assertTrue(result.left is Expression.PropertyAccess)
        assertEquals(listOf("count"), (result.left as Expression.PropertyAccess).path)

        assertTrue(result.right is Expression.Literal)
        assertEquals(10L, (result.right as Expression.Literal).value)
    }

    @Test
    fun `parse logical AND expression`() {
        val result = parser.parse("status = 'active' AND count > 10")

        assertTrue(result is Expression.BinaryOp)
        assertEquals(Operator.AND, result.operator)

        // Left side: status = 'active'
        assertTrue(result.left is Expression.BinaryOp)
        val left = result.left as Expression.BinaryOp
        assertEquals(Operator.EQUALS, left.operator)

        // Right side: count > 10
        assertTrue(result.right is Expression.BinaryOp)
        val right = result.right as Expression.BinaryOp
        assertEquals(Operator.GREATER_THAN, right.operator)
    }

    @Test
    fun `parse logical OR expression`() {
        val result = parser.parse("priority = 'high' OR amount > 1000")

        assertTrue(result is Expression.BinaryOp)
        assertEquals(Operator.OR, result.operator)

        // Left side: priority = 'high'
        assertTrue(result.left is Expression.BinaryOp)
        val left = result.left as Expression.BinaryOp
        assertEquals(Operator.EQUALS, left.operator)

        // Right side: amount > 1000
        assertTrue(result.right is Expression.BinaryOp)
        val right = result.right as Expression.BinaryOp
        assertEquals(Operator.GREATER_THAN, right.operator)
    }

    @Test
    fun `parse nested property access`() {
        val result = parser.parse("client.address.city = 'NYC'")

        assertTrue(result is Expression.BinaryOp)
        assertEquals(Operator.EQUALS, result.operator)

        assertTrue(result.left is Expression.PropertyAccess)
        assertEquals(listOf("client", "address", "city"), (result.left as Expression.PropertyAccess).path)

        assertTrue(result.right is Expression.Literal)
        assertEquals("NYC", (result.right as Expression.Literal).value)
    }

    @Test
    fun `parse expression with parentheses`() {
        val result = parser.parse("(status = 'active' OR status = 'pending') AND count > 5")

        assertTrue(result is Expression.BinaryOp)
        assertEquals(Operator.AND, result.operator)

        // Left side: (status = 'active' OR status = 'pending')
        assertTrue(result.left is Expression.BinaryOp)
        val left = result.left as Expression.BinaryOp
        assertEquals(Operator.OR, left.operator)

        // Right side: count > 5
        assertTrue(result.right is Expression.BinaryOp)
        val right = result.right as Expression.BinaryOp
        assertEquals(Operator.GREATER_THAN, right.operator)
    }

    @Test
    fun `parse all comparison operators`() {
        // Equals
        var result = parser.parse("a = 1")
        assertTrue(result is Expression.BinaryOp)
        assertEquals(Operator.EQUALS, result.operator)

        // Not equals
        result = parser.parse("b != 2")
        assertTrue(result is Expression.BinaryOp)
        assertEquals(Operator.NOT_EQUALS, result.operator)

        // Greater than
        result = parser.parse("c > 3")
        assertTrue(result is Expression.BinaryOp)
        assertEquals(Operator.GREATER_THAN, result.operator)

        // Less than
        result = parser.parse("d < 4")
        assertTrue(result is Expression.BinaryOp)
        assertEquals(Operator.LESS_THAN, result.operator)

        // Greater or equal
        result = parser.parse("e >= 5")
        assertTrue(result is Expression.BinaryOp)
        assertEquals(Operator.GREATER_EQUALS, result.operator)

        // Less or equal
        result = parser.parse("f <= 6")
        assertTrue(result is Expression.BinaryOp)
        assertEquals(Operator.LESS_EQUALS, result.operator)
    }

    @Test
    fun `parse boolean literals`() {
        var result = parser.parse("isActive = true")
        assertTrue(result is Expression.BinaryOp)
        assertTrue((result as Expression.BinaryOp).right is Expression.Literal)
        assertEquals(true, ((result.right as Expression.Literal).value))

        result = parser.parse("isDeleted = false")
        assertTrue(result is Expression.BinaryOp)
        assertTrue((result as Expression.BinaryOp).right is Expression.Literal)
        assertEquals(false, ((result.right as Expression.Literal).value))
    }

    @Test
    fun `parse null literal`() {
        val result = parser.parse("deletedAt = null")
        assertTrue(result is Expression.BinaryOp)
        assertTrue((result as Expression.BinaryOp).right is Expression.Literal)
        assertEquals(null, ((result.right as Expression.Literal).value))
    }

    @Test
    fun `parse decimal numbers`() {
        val result = parser.parse("price > 99.99")
        assertTrue(result is Expression.BinaryOp)
        assertTrue((result as Expression.BinaryOp).right is Expression.Literal)
        assertEquals(99.99, ((result.right as Expression.Literal).value))
    }

    @Test
    fun `parse complex nested expression`() {
        val result = parser.parse("(status = 'active' AND priority = 'high') OR (status = 'pending' AND amount > 1000)")

        assertTrue(result is Expression.BinaryOp)
        assertEquals(Operator.OR, result.operator)

        // Both sides should be AND expressions
        assertTrue(result.left is Expression.BinaryOp)
        assertEquals(Operator.AND, (result.left as Expression.BinaryOp).operator)

        assertTrue(result.right is Expression.BinaryOp)
        assertEquals(Operator.AND, (result.right as Expression.BinaryOp).operator)
    }

    @Test
    fun `parse expression with case insensitive keywords`() {
        // Test AND in different cases
        var result = parser.parse("a = 1 and b = 2")
        assertTrue(result is Expression.BinaryOp)
        assertEquals(Operator.AND, result.operator)

        result = parser.parse("a = 1 And b = 2")
        assertTrue(result is Expression.BinaryOp)
        assertEquals(Operator.AND, result.operator)

        // Test OR in different cases
        result = parser.parse("a = 1 or b = 2")
        assertTrue(result is Expression.BinaryOp)
        assertEquals(Operator.OR, result.operator)

        result = parser.parse("a = 1 Or b = 2")
        assertTrue(result is Expression.BinaryOp)
        assertEquals(Operator.OR, result.operator)
    }

    @Test
    fun `throw error on unterminated string`() {
        val exception = assertThrows<IllegalArgumentException> {
            parser.parse("status = 'active")
        }
        assertTrue(exception.message!!.contains("Unterminated string"))
    }

    @Test
    fun `throw error on unbalanced parentheses`() {
        val exception = assertThrows<IllegalArgumentException> {
            parser.parse("(status = 'active'")
        }
        assertTrue(exception.message!!.contains("Expected closing parenthesis"))
    }

    @Test
    fun `throw error on unexpected character`() {
        val exception = assertThrows<IllegalArgumentException> {
            parser.parse("status = 'active' @ count > 10")
        }
        assertTrue(exception.message!!.contains("Unexpected character"))
    }

    @Test
    fun `throw error on unknown operator`() {
        // This should fail during parsing because ~= is not a recognized operator
        val exception = assertThrows<IllegalArgumentException> {
            parser.parse("status ~= 'active'")
        }
        assertTrue(exception.message!!.contains("Unexpected character") || exception.message!!.contains("Unknown operator"))
    }
}
