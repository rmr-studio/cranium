---
tags:
  - layer/service
  - component/active
  - architecture/component
Domains:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
Created: 2026-02-08
Updated: 2026-02-08
---
# WorkflowNodeExpressionParserService

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/State Management/State Management]]

## Purpose

Recursive descent parser that converts SQL-like expression strings into an Expression AST for workflow condition evaluation — handles tokenization, operator precedence, and property access syntax.

---

## Responsibilities

- Tokenize expression strings into typed tokens (STRING, NUMBER, BOOLEAN, NULL, IDENTIFIER, OPERATOR, AND, OR, LPAREN, RPAREN)
- Parse tokens into Expression AST using recursive descent with correct operator precedence (OR < AND < comparison < primary)
- Handle quoted string literals (single quotes)
- Support comparison operators: `=`, `!=`, `>`, `<`, `>=`, `<=`
- Support logical operators: `AND`, `OR`
- Support parenthesized grouping for precedence override
- Support property access via dot notation (e.g., `steps.node1.output.status`)

---

## Dependencies

- `Expression` model — AST representation (Literal, PropertyAccess, BinaryOp)
- `Operator` enum — Operator types (EQUALS, NOT_EQUALS, GREATER_THAN, LESS_THAN, GREATER_EQUALS, LESS_EQUALS, AND, OR)

## Used By

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/State Management/WorkflowNodeExpressionEvaluatorService]] — Consumes the AST output for evaluation against workflow data store

---

## Key Logic

**Two-phase processing:**

1. **Tokenization** — Split input string into typed tokens
   - Handles quoted strings (single quotes, with escape checking for unterminated strings)
   - Recognizes operators (`=`, `!=`, `>`, `<`, `>=`, `<=`)
   - Distinguishes keywords (`AND`, `OR`, `true`, `false`, `null`)
   - Identifies numbers (integers and decimals)
   - Recognizes identifiers and property paths (dot-separated)

2. **Recursive descent parsing** — Build Expression AST with correct precedence
   - **parseOr()** — Lowest precedence, handles `expr OR expr OR ...`
   - **parseAnd()** — Middle precedence, handles `expr AND expr AND ...`
   - **parseComparison()** — Higher precedence, handles `expr = expr`, `expr > expr`, etc.
   - **parsePrimary()** — Highest precedence, handles literals, property access, parentheses

**Operator precedence (low to high):**

1. OR (lowest)
2. AND
3. Comparison operators (=, !=, >, <, >=, <=)
4. Primary expressions (literals, property access, parentheses) (highest)

**Property access syntax:**

Input: `steps.node1.output.status`
Tokenized as: `IDENTIFIER("steps.node1.output.status")`
Parsed as: `Expression.PropertyAccess(["steps", "node1", "output", "status"])`

Evaluator uses this path to navigate the workflow data store.

---

## Public Methods

### `parse(expression: String): Expression`

Parse SQL-like expression string into Expression AST. Returns root Expression node. Throws `IllegalArgumentException` for syntax errors (unterminated strings, unexpected characters, missing closing parentheses).

**Example inputs and outputs:**

```kotlin
// Simple comparison
parse("status = 'active'")
// → BinaryOp(PropertyAccess(["status"]), EQUALS, Literal("active"))

// Logical AND
parse("status = 'active' AND count > 10")
// → BinaryOp(
//     BinaryOp(PropertyAccess(["status"]), EQUALS, Literal("active")),
//     AND,
//     BinaryOp(PropertyAccess(["count"]), GREATER_THAN, Literal(10))
//   )

// Parentheses override precedence
parse("(a = 1 OR b = 2) AND c = 3")
// → BinaryOp(
//     BinaryOp(PropertyAccess(["a"]), EQUALS, Literal(1), OR, PropertyAccess(["b"]), EQUALS, Literal(2)),
//     AND,
//     BinaryOp(PropertyAccess(["c"]), EQUALS, Literal(3))
//   )

// Property access
parse("steps.node1.output.status = 'completed'")
// → BinaryOp(PropertyAccess(["steps", "node1", "output", "status"]), EQUALS, Literal("completed"))
```

---

## Gotchas

- **Single quotes for strings:** Parser only recognizes single-quoted strings (`'active'`), not double quotes. This matches SQL syntax.
- **Case-insensitive keywords:** `AND`, `and`, `And` all parsed as AND token. Same for `OR`, `true`, `false`, `null`.
- **No operator chaining:** Cannot write `a < b < c`. Must use `a < b AND b < c`.
- **Property paths tokenized as single identifier:** Dot-separated paths like `steps.node1.output` tokenized as one `IDENTIFIER` token, then split on dots during parsing.
- **Error messages reference character position:** Parse errors include position in input string (e.g., "Unexpected character '$' at position 15").
- **No escape sequences in strings:** String literals cannot contain escaped quotes. Unterminated string detection only checks for closing single quote.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/State Management/WorkflowNodeExpressionEvaluatorService]] — Evaluates the parsed AST
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/State Management/State Management]] — Parent subdomain
