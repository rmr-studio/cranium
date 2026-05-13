package cranium.core.service.workflow.state

import org.springframework.stereotype.Service
import cranium.core.models.common.Expression
import cranium.core.models.common.Operator

/**
 * Recursive descent parser for SQL-like expressions
 * Converts expression strings to Expression AST
 */
@Service
class WorkflowNodeExpressionParserService {

    /**
     * Parse SQL-like expression string into Expression AST
     * Example: "status = 'active' AND count > 10"
     */
    fun parse(expression: String): Expression {
        val tokens = tokenize(expression)
        val parser = Parser(tokens)
        return parser.parseExpression()
    }

    /**
     * Tokenize expression string, preserving quoted strings and operators
     */
    private fun tokenize(expression: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0

        while (i < expression.length) {
            val char = expression[i]

            // Skip whitespace
            if (char.isWhitespace()) {
                i++
                continue
            }

            // Handle quoted strings
            if (char == '\'') {
                val start = i
                i++
                while (i < expression.length && expression[i] != '\'') {
                    i++
                }
                if (i >= expression.length) {
                    throw IllegalArgumentException("Unterminated string at position $start")
                }
                i++ // Skip closing quote
                tokens.add(Token(TokenType.STRING, expression.substring(start + 1, i - 1)))
                continue
            }

            // Handle parentheses
            if (char == '(') {
                tokens.add(Token(TokenType.LPAREN, "("))
                i++
                continue
            }
            if (char == ')') {
                tokens.add(Token(TokenType.RPAREN, ")"))
                i++
                continue
            }

            // Handle operators
            if (char == '=' || char == '!' || char == '>' || char == '<') {
                val start = i
                i++
                // Check for two-character operators (!=, >=, <=)
                if (i < expression.length && (expression[i] == '=' || char == '!' && expression[i] == '=')) {
                    i++
                }
                val op = expression.substring(start, i)
                tokens.add(Token(TokenType.OPERATOR, op))
                continue
            }

            // Handle identifiers, keywords, and numbers
            if (char.isLetterOrDigit() || char == '.' || char == '_') {
                val start = i
                while (i < expression.length && (expression[i].isLetterOrDigit() || expression[i] == '.' || expression[i] == '_')) {
                    i++
                }
                val value = expression.substring(start, i)

                // Check if it's a number
                val numberValue = value.toDoubleOrNull()
                if (numberValue != null && !value.contains('.', ignoreCase = true)) {
                    // Check if it's an integer
                    val intValue = value.toLongOrNull()
                    if (intValue != null) {
                        tokens.add(Token(TokenType.NUMBER, intValue))
                    } else {
                        tokens.add(Token(TokenType.NUMBER, numberValue))
                    }
                } else if (numberValue != null) {
                    tokens.add(Token(TokenType.NUMBER, numberValue))
                } else if (value.equals("true", ignoreCase = true)) {
                    tokens.add(Token(TokenType.BOOLEAN, true))
                } else if (value.equals("false", ignoreCase = true)) {
                    tokens.add(Token(TokenType.BOOLEAN, false))
                } else if (value.equals("null", ignoreCase = true)) {
                    tokens.add(Token(TokenType.NULL, null))
                } else if (value.equals("AND", ignoreCase = true)) {
                    tokens.add(Token(TokenType.AND, "AND"))
                } else if (value.equals("OR", ignoreCase = true)) {
                    tokens.add(Token(TokenType.OR, "OR"))
                } else {
                    // Property access or identifier
                    tokens.add(Token(TokenType.IDENTIFIER, value))
                }
                continue
            }

            throw IllegalArgumentException("Unexpected character '$char' at position $i")
        }

        return tokens
    }

    /**
     * Token types for lexical analysis
     */
    private enum class TokenType {
        STRING, NUMBER, BOOLEAN, NULL, IDENTIFIER,
        OPERATOR, AND, OR,
        LPAREN, RPAREN
    }

    /**
     * Token representation
     */
    private data class Token(val type: TokenType, val value: Any?)

    /**
     * Recursive descent parser
     */
    private class Parser(private val tokens: List<Token>) {
        private var position = 0

        fun parseExpression(): Expression {
            return parseOr()
        }

        /**
         * Parse OR expression (lowest precedence)
         * expr OR expr OR ...
         */
        private fun parseOr(): Expression {
            var left = parseAnd()

            while (peek()?.type == TokenType.OR) {
                consume() // Consume OR
                val right = parseAnd()
                left = Expression.BinaryOp(left, Operator.OR, right)
            }

            return left
        }

        /**
         * Parse AND expression
         * expr AND expr AND ...
         */
        private fun parseAnd(): Expression {
            var left = parseComparison()

            while (peek()?.type == TokenType.AND) {
                consume() // Consume AND
                val right = parseComparison()
                left = Expression.BinaryOp(left, Operator.AND, right)
            }

            return left
        }

        /**
         * Parse comparison expression
         * expr = expr, expr > expr, etc.
         */
        private fun parseComparison(): Expression {
            var left = parsePrimary()

            val token = peek()
            if (token?.type == TokenType.OPERATOR) {
                consume()
                val operator = when (token.value as String) {
                    "=" -> Operator.EQUALS
                    "!=" -> Operator.NOT_EQUALS
                    ">" -> Operator.GREATER_THAN
                    "<" -> Operator.LESS_THAN
                    ">=" -> Operator.GREATER_EQUALS
                    "<=" -> Operator.LESS_EQUALS
                    else -> throw IllegalArgumentException("Unknown operator: ${token.value}")
                }
                val right = parsePrimary()
                left = Expression.BinaryOp(left, operator, right)
            }

            return left
        }

        /**
         * Parse primary expression (literals, property access, parentheses)
         */
        private fun parsePrimary(): Expression {
            val token = peek() ?: throw IllegalArgumentException("Unexpected end of expression")

            return when (token.type) {
                TokenType.LPAREN -> {
                    consume() // Consume (
                    val expr = parseExpression()
                    if (peek()?.type != TokenType.RPAREN) {
                        throw IllegalArgumentException("Expected closing parenthesis")
                    }
                    consume() // Consume )
                    expr
                }

                TokenType.STRING, TokenType.NUMBER, TokenType.BOOLEAN, TokenType.NULL -> {
                    consume()
                    Expression.Literal(token.value)
                }

                TokenType.IDENTIFIER -> {
                    consume()
                    val path = (token.value as String).split(".")
                    Expression.PropertyAccess(path)
                }

                else -> throw IllegalArgumentException("Unexpected token: ${token.type}")
            }
        }

        private fun peek(): Token? {
            return if (position < tokens.size) tokens[position] else null
        }

        private fun consume(): Token {
            if (position >= tokens.size) {
                throw IllegalArgumentException("Unexpected end of expression")
            }
            return tokens[position++]
        }
    }
}
