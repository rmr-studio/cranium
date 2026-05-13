package cranium.core.models.common

/**
 * SQL-like expression operators for workflow conditions and data access
 */
enum class Operator {
    // Comparison operators
    EQUALS,
    NOT_EQUALS,
    GREATER_THAN,
    LESS_THAN,
    GREATER_EQUALS,
    LESS_EQUALS,

    // Logical operators
    AND,
    OR
}

/**
 * Expression AST for SQL-like conditional logic
 * Supports: comparisons, logical operators, property access, literals
 */
sealed class Expression {
    /**
     * Static literal value (string, number, boolean, null)
     * Examples: "active", 42, true, null
     */
    data class Literal(val value: Any?) : Expression()

    /**
     * Property access with dot notation traversal
     * Examples: entity.status, client.address.city
     */
    data class PropertyAccess(val path: List<String>) : Expression()

    /**
     * Binary operation (comparison or logical)
     * Examples: status = 'active', count > 10, a AND b
     */
    data class BinaryOp(
        val left: Expression,
        val operator: Operator,
        val right: Expression
    ) : Expression()
}
