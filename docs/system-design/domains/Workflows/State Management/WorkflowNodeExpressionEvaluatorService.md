---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-02-08
Updated: 2026-02-08
Domains:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
---
# WorkflowNodeExpressionEvaluatorService

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/State Management/State Management]]

## Purpose

Evaluates Expression AST against data context with type safety, supporting comparisons, logical operations, and property access for CONDITION nodes.

---

## Responsibilities

- Evaluate `Expression` AST (Literal, PropertyAccess, BinaryOp) against context
- Support comparison operators: `==`, `!=`, `>`, `<`, `>=`, `<=`
- Support logical operators: `AND`, `OR` (with short-circuit evaluation)
- Traverse nested properties in context maps
- Type coercion for numeric comparisons
- Truthy/falsy evaluation for logical operations

---

## Dependencies

- `Expression` model classes — AST representation
- `Operator` enum — Operator types

## Used By

- CONDITION node config — Evaluates condition expressions to determine branch path

---

## Key Logic

**Expression types:**

- **Literal:** Returns value directly (e.g., `42`, `"active"`, `true`)
- **PropertyAccess:** Traverses path in context map (e.g., `["client", "status"]` → `context["client"]["status"]`)
- **BinaryOp:** Evaluates left and right operands, applies operator

**Operator evaluation:**

| Operator | Type | Behavior |
|---|---|---|
| `AND` | Logical | Short-circuit: if left is falsy, return false (don't eval right) |
| `OR` | Logical | Short-circuit: if left is truthy, return true (don't eval right) |
| `==`, `!=` | Comparison | Equality with null handling and numeric coercion |
| `>`, `<`, `>=`, `<=` | Comparison | Numeric comparison with type coercion |

**Type coercion:**

For numeric comparisons, all `Number` types (Int, Long, Float, Double, Short, Byte) are converted to Double for consistent comparison.

**Truthy/falsy:**

- **Falsy:** `null`, `false`
- **Truthy:** Everything else (including `0`, empty strings)

---

## Public Methods

### `evaluate(expression: Expression, context: Map<String, Any?>): Any?`

Evaluates expression AST against context. Returns Boolean for comparisons/logical ops, Any? for property access/literals.

---

## Gotchas

- **Property access requires maps:** Context must be nested `Map<String, Any?>` structures. Throws `IllegalArgumentException` if trying to access property on non-map value.
- **Missing property throws:** If property not found in context, throws error with available keys. Not a silent null return.
- **Numeric type handling:** Any `Number` subtype is valid. Comparison converts all to Double. Prevents Int vs Long comparison failures.
- **Truthy evaluation not JavaScript-like:** `0` is truthy (not falsy like JavaScript). Only `null` and `false` are falsy.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/State Management/WorkflowNodeExpressionParserService]] — Parses string expressions into AST (separate service)
- CONDITION node config — Primary consumer
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/State Management/State Management]] — Parent subdomain
