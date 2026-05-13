package cranium.core.service.workflow.state

import org.springframework.stereotype.Service
import cranium.core.models.common.Expression
import cranium.core.models.common.Operator

/**
 * Evaluates Expression AST against data context with type safety
 */
@Service
class WorkflowNodeExpressionEvaluatorService {

    /**
     * Evaluate expression against data context
     * Returns Boolean for comparisons/logical ops, Any? for property access
     */
    fun evaluate(expression: Expression, context: Map<String, Any?>): Any? {
        return when (expression) {
            is Expression.Literal -> expression.value

            is Expression.PropertyAccess -> evaluatePropertyAccess(expression.path, context)

            is Expression.BinaryOp -> evaluateBinaryOp(expression, context)
        }
    }

    /**
     * Traverse nested properties in context
     * Example: ["client", "address", "city"] with nested maps
     */
    private fun evaluatePropertyAccess(path: List<String>, context: Map<String, Any?>): Any? {
        var current: Any? = context

        for (key in path) {
            if (current == null) {
                throw IllegalArgumentException("Cannot access property '$key' on null value")
            }

            if (current !is Map<*, *>) {
                throw IllegalArgumentException("Cannot access property '$key' on non-map value: ${current.javaClass.simpleName}")
            }

            @Suppress("UNCHECKED_CAST")
            val map = current as Map<String, Any?>
            if (!map.containsKey(key)) {
                throw IllegalArgumentException(
                    "Property '$key' not found in context (available keys: ${
                        map.keys.joinToString(
                            ", "
                        )
                    })"
                )
            }

            current = map[key]
        }

        return current
    }

    /**
     * Evaluate binary operation with type coercion
     */
    private fun evaluateBinaryOp(op: Expression.BinaryOp, context: Map<String, Any?>): Any {
        return when (op.operator) {
            Operator.AND -> evaluateLogicalAnd(op, context)
            Operator.OR -> evaluateLogicalOr(op, context)
            else -> evaluateComparison(op, context)
        }
    }

    /**
     * Evaluate logical AND (short-circuit)
     */
    private fun evaluateLogicalAnd(op: Expression.BinaryOp, context: Map<String, Any?>): Boolean {
        val left = evaluate(op.left, context)
        if (!isTruthy(left)) return false

        val right = evaluate(op.right, context)
        return isTruthy(right)
    }

    /**
     * Evaluate logical OR (short-circuit)
     */
    private fun evaluateLogicalOr(op: Expression.BinaryOp, context: Map<String, Any?>): Boolean {
        val left = evaluate(op.left, context)
        if (isTruthy(left)) return true

        val right = evaluate(op.right, context)
        return isTruthy(right)
    }

    /**
     * Evaluate comparison operation
     */
    private fun evaluateComparison(op: Expression.BinaryOp, context: Map<String, Any?>): Boolean {
        val left = evaluate(op.left, context)
        val right = evaluate(op.right, context)

        return when (op.operator) {
            Operator.EQUALS -> equals(left, right)
            Operator.NOT_EQUALS -> !equals(left, right)
            Operator.GREATER_THAN -> compareNumbers(left, right) > 0
            Operator.LESS_THAN -> compareNumbers(left, right) < 0
            Operator.GREATER_EQUALS -> compareNumbers(left, right) >= 0
            Operator.LESS_EQUALS -> compareNumbers(left, right) <= 0
            else -> throw IllegalStateException("Unexpected operator in comparison: ${op.operator}")
        }
    }

    /**
     * Equality comparison with null handling
     */
    private fun equals(left: Any?, right: Any?): Boolean {
        if (left == null && right == null) return true
        if (left == null || right == null) return false

        // Handle number comparisons with type coercion
        if (isNumber(left) && isNumber(right)) {
            return compareNumbers(left, right) == 0
        }

        // Direct equality for other types
        return left == right
    }

    /**
     * Compare numeric values with type coercion
     */
    private fun compareNumbers(left: Any?, right: Any?): Int {
        if (!isNumber(left)) {
            throw IllegalArgumentException("Expected number but got: ${left?.javaClass?.simpleName ?: "null"}")
        }
        if (!isNumber(right)) {
            throw IllegalArgumentException("Expected number but got: ${right?.javaClass?.simpleName ?: "null"}")
        }

        val leftDouble = toDouble(left!!)
        val rightDouble = toDouble(right!!)

        return leftDouble.compareTo(rightDouble)
    }

    /**
     * Check if value is a number type
     */
    private fun isNumber(value: Any?): Boolean {
        return value is Number
    }

    /**
     * Convert number to Double for comparison
     */
    private fun toDouble(value: Any): Double {
        return when (value) {
            is Int -> value.toDouble()
            is Long -> value.toDouble()
            is Float -> value.toDouble()
            is Double -> value
            is Short -> value.toDouble()
            is Byte -> value.toDouble()
            else -> throw IllegalArgumentException("Cannot convert ${value.javaClass.simpleName} to Double")
        }
    }

    /**
     * Truthy evaluation for logical operators
     * null and false are falsy, everything else is truthy
     */
    private fun isTruthy(value: Any?): Boolean {
        return when (value) {
            null -> false
            is Boolean -> value
            else -> true
        }
    }
}