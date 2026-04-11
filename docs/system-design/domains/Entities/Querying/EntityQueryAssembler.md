---
tags:
  - component/active
  - layer/service
  - architecture/component
Created: 2026-02-08
Updated: 2026-02-08
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
---
Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/Querying]]

# EntityQueryAssembler

---

## Purpose

Assembles complete parameterized SELECT and COUNT queries from filter visitor output, acting as the bridge between filter SQL generation and query execution. Wraps filter WHERE clauses with workspace isolation, entity type filtering, soft-delete exclusion, default ordering, and pagination.

---

## Responsibilities

**This component owns:**
- Assembling paired data and count queries from filter visitor output
- Adding workspace isolation (`workspace_id = :ws_N`) to all queries
- Adding entity type filtering (`type_id = :type_N`) to all queries
- Adding soft-delete exclusion (`deleted = false`) to all queries
- Adding default ordering (`created_at DESC, id ASC`) for stable pagination
- Adding pagination (`LIMIT :limit OFFSET :offset`) to data queries
- Validating pagination parameters (limit 1-500, offset >= 0)
- Coordinating shared ParameterNameGenerator across base conditions and filter tree

**Explicitly NOT responsible for:**
- Generating filter WHERE clauses (delegated to AttributeFilterVisitor)
- Executing queries (delegated to query executor in Phase 5)
- Hydrating entity data (delegated to entity hydration service)
- Parsing or validating filter structure (delegated to validation layer)

---

## Dependencies

### Internal Dependencies

|Component|Purpose|Coupling|
|---|---|---|
|[[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/AttributeFilterVisitor]]|Traverses filter trees and produces SQL fragments|High|
|[[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/SqlFragment]]|Immutable container for SQL with parameters|High|
|[[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/ParameterNameGenerator]]|Generates unique parameter names across query tree|High|

### External Dependencies

|Service/Library|Purpose|Failure Impact|
|---|---|---|
|Spring Framework|Dependency injection via @Service|Cannot instantiate component|

### Injected Dependencies

```kotlin
@Service
class EntityQueryAssembler(
    private val filterVisitor: AttributeFilterVisitor
)
```

---

## Consumed By

|Component|How It Uses This|Notes|
|---|---|---|
|[[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/EntityQueryService]] (Phase 5)|Calls assemble() to build queries before execution|Main consumer - coordinates entire query pipeline|

---

## Public Interface

### Key Methods

#### `assemble(entityTypeId: UUID, workspaceId: UUID, filter: QueryFilter?, pagination: QueryPagination, paramGen: ParameterNameGenerator): AssembledQuery`

- **Purpose:** Assembles paired data and count queries from filter and pagination parameters
- **When to use:** Before executing entity queries - called once per query request
- **Side effects:** None (pure function - no state mutation)
- **Throws:** SchemaValidationException if pagination parameters are invalid
- **Returns:** AssembledQuery with separate data and count SqlFragments

**Assembly process:**
1. Validate pagination parameters
2. Build base WHERE clause (workspace, type, deleted)
3. Visit filter tree to get filter fragment (if filter provided)
4. Combine base and filter fragments with AND
5. Build data query (SELECT with ORDER BY, LIMIT, OFFSET)
6. Build count query (SELECT COUNT with same WHERE, no ORDER BY or pagination)
7. Return paired queries

---

## Key Logic

### Core Algorithm / Business Rules

**Query Assembly Flow:**

```
1. Validate pagination
   ├─> limit must be 1-500
   ├─> offset must be >= 0
   └─> throw SchemaValidationException if invalid

2. Build base WHERE clause
   ├─> workspace_id = :ws_N
   ├─> type_id = :type_N
   └─> deleted = false (literal, not parameterized)

3. Generate filter fragment (if filter provided)
   └─> filterVisitor.visit(filter, paramGen)

4. Combine fragments
   ├─> If filter exists: baseFragment.and(filterFragment)
   └─> If no filter: baseFragment only

5. Build data query
   ├─> SELECT e.id
   ├─> FROM entities e
   ├─> WHERE {combined fragment}
   ├─> ORDER BY e.created_at DESC, e.id ASC
   └─> LIMIT :limit OFFSET :offset

6. Build count query
   ├─> SELECT COUNT(*)
   ├─> FROM entities e
   └─> WHERE {combined fragment}
   (no ORDER BY or LIMIT/OFFSET)

7. Return AssembledQuery(dataQuery, countQuery)
```

### Parameter Name Uniqueness Strategy

**Single shared ParameterNameGenerator ensures uniqueness:**

The caller creates a single `ParameterNameGenerator` instance and passes it to `assemble()`. The assembler uses it for:
- Base WHERE conditions: `ws_N`, `type_N`
- Pagination parameters: `limit_N`, `offset_N`
- Filter tree traversal (passed to visitor)

Since the same generator flows through all SQL generation, parameter names are guaranteed unique across:
- Base conditions
- User filters (attributes and relationships)
- Nested relationship subqueries at any depth

**Why this matters:** Prevents parameter binding collisions when the executor runs the SQL.

### Workspace Isolation

**Workspace filtering enforced here only:**

The assembler adds `e.workspace_id = :ws_N` to the base WHERE clause. This is the **ONLY** place workspace_id filtering occurs in the query tree.

**Relationship subqueries intentionally omit workspace filtering** because:
- FK constraints on entity_relationships guarantee same-workspace references
- RLS (Row Level Security) provides database-level safety net
- Simpler SQL with fewer redundant checks

### Soft-Delete Exclusion

**Literal condition, not parameterized:**

```sql
e.deleted = false
```

`deleted = false` is a literal (not `:deleted`) because:
- Value is always false (never changes)
- Partial index on `WHERE deleted = false` can match literal condition
- Parameterized version would prevent index optimization

### Default Ordering

**Stable pagination with compound sort:**

```sql
ORDER BY e.created_at DESC, e.id ASC
```

**Rationale:**
- `created_at DESC`: Newest entities first (common use case)
- `id ASC`: Deterministic tiebreaker for entities created at same millisecond
- UUID tiebreaker ensures stable pagination (same page always returns same entities)

> [!warning] Custom Ordering Not Yet Supported
> The `pagination.orderBy` field exists in the model but is not implemented in this version. All queries use the default `created_at DESC, id ASC` ordering.
>
> **Planned:** Future version will support custom ordering via attribute field extraction from JSONB payload.

### Validation Rules

|Field/Input|Rule|Error|
|---|---|---|
|pagination.limit|Must be >= 1|"Limit must be at least 1, was: {value}"|
|pagination.limit|Must be <= 500|"Limit must not exceed 500, was: {value}"|
|pagination.offset|Must be >= 0|"Offset must be non-negative, was: {value}"|

---

## Data Access

_This component does NOT access database directly - generates SQL for executor to run._

---

## Error Handling

### Errors Thrown

|Error/Exception|When|Expected Handling|
|---|---|---|
|SchemaValidationException|limit < 1 or limit > MAX_LIMIT|Propagate to API layer - return 400 Bad Request|
|SchemaValidationException|offset < 0|Propagate to API layer - return 400 Bad Request|

### Errors Handled

|Error/Exception|Source|Recovery Strategy|
|---|---|---|
|None|N/A|This component does not catch exceptions - delegates validation errors upstream|

---

## Observability

### Log Events

|Event|Level|When|Key Fields|
|---|---|---|---|
|None|N/A|Component does not emit logs (pure assembly logic)|N/A|

**Rationale:** Assembly is a pure transformation with no side effects or failure modes requiring logging. Validation errors throw exceptions with descriptive messages.

---

## Gotchas & Edge Cases

> [!warning] MAX_LIMIT Enforced at 500
> Queries requesting more than 500 entities per page are rejected with SchemaValidationException.
>
> **Why:** Prevents performance degradation from large result sets and unbounded memory usage.
>
> **Workaround:** Use pagination to fetch entities in chunks of <= 500.

> [!warning] Workspace Isolation Added Only at Root
> The assembler adds `e.workspace_id = :ws_N` to the root WHERE clause only. Relationship subqueries generated by RelationshipSqlGenerator do NOT include workspace filtering.
>
> **Why:** FK constraints and RLS enforce workspace isolation at database level. Redundant checks in every subquery would bloat SQL without adding safety.
>
> **Watch out:** Do not rely on subquery SQL containing workspace checks - isolation is enforced at root only.

> [!warning] Shared ParameterNameGenerator MUST Be Used
> The caller MUST create a single ParameterNameGenerator and pass it to assemble(). Creating a new generator inside assemble() would cause parameter name collisions between base conditions and filter fragments.
>
> **Why:** Base conditions (workspace, type) are added AFTER the filter visitor runs. If separate generators were used, both might generate `:eq_0` (collision).
>
> **Correct usage:**
> ```kotlin
> val paramGen = ParameterNameGenerator()
> val query = assembler.assemble(typeId, workspaceId, filter, pagination, paramGen)
> ```
>
> **Incorrect usage:**
> ```kotlin
> // DON'T create paramGen inside assemble() - would cause collisions
> ```

### Known Limitations

- Custom ordering via `pagination.orderBy` not implemented (always uses `created_at DESC, id ASC`)
- Pagination limit capped at 500 (cannot fetch more than 500 entities per request)
- No support for cursor-based pagination (only offset-based)
- No query optimization hints or index hints (relies on PostgreSQL query planner)

### Thread Safety / Concurrency

**Thread-safe** with external paramGen:
- No mutable state in assembler service
- All methods are pure functions (no side effects)
- Safe for concurrent use across multiple requests
- Each request has its own ParameterNameGenerator instance (not shared)

**Concurrency model:**
- Singleton Spring bean shared across threads
- No synchronization needed (stateless service)
- Each query assembly is independent

---

## Testing

### Unit Test Coverage

- **Location:** `src/test/kotlin/riven/core/service/entity/query/EntityQueryAssemblerTest.kt`
- **Key scenarios covered:**
  - Assembles data query with workspace, type, deleted, ordering, pagination
  - Assembles count query with same WHERE clause, no ORDER BY or LIMIT/OFFSET
  - Combines base fragment with filter fragment using AND
  - Validates pagination: limit < 1 throws exception
  - Validates pagination: limit > MAX_LIMIT throws exception
  - Validates pagination: offset < 0 throws exception
  - Null filter produces query with base conditions only
  - Parameter names are unique across base and filter fragments

### How to Test Manually

1. Create EntityQueryAssembler with filter visitor
2. Create ParameterNameGenerator
3. Call assemble() with test parameters
4. Inspect returned AssembledQuery:
   - Data query has SELECT, WHERE, ORDER BY, LIMIT, OFFSET
   - Count query has SELECT COUNT, WHERE (same as data)
   - Parameters map includes workspace, type, limit, offset
5. Verify parameter name uniqueness across both queries

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/Querying]] - Parent subdomain
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/AttributeFilterVisitor]] - Generates filter WHERE clauses
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/AttributeSqlGenerator]] - Generates attribute filter SQL
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/RelationshipSqlGenerator]] - Generates relationship filter SQL
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/SqlFragment]] - Immutable SQL container
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/ParameterNameGenerator]] - Unique parameter name generation

---

## Assembly Examples

### Example 1: Query with No Filter

```kotlin
// Input
val typeId = UUID.fromString("...")
val workspaceId = UUID.fromString("...")
val filter = null
val pagination = QueryPagination(limit = 50, offset = 0)
val paramGen = ParameterNameGenerator()

// Output
val result = assembler.assemble(typeId, workspaceId, filter, pagination, paramGen)

// result.dataQuery.sql:
"""
SELECT e.id
FROM entities e
WHERE e.workspace_id = :ws_0 AND e.type_id = :type_1 AND e.deleted = false
ORDER BY e.created_at DESC, e.id ASC
LIMIT :limit_2 OFFSET :offset_3
"""

// result.countQuery.sql:
"""
SELECT COUNT(*)
FROM entities e
WHERE e.workspace_id = :ws_0 AND e.type_id = :type_1 AND e.deleted = false
"""

// parameters (shared by both queries):
{
  "ws_0" -> workspaceId,
  "type_1" -> typeId,
  "limit_2" -> 50,
  "offset_3" -> 0
}
```

### Example 2: Query with Attribute Filter

```kotlin
// Input
val filter = QueryFilter.Attribute(
  attributeId = statusAttributeId,
  operator = FilterOperator.EQUALS,
  value = FilterValue.Literal("Active")
)
val pagination = QueryPagination(limit = 100, offset = 0)
val paramGen = ParameterNameGenerator()

// Output
val result = assembler.assemble(typeId, workspaceId, filter, pagination, paramGen)

// result.dataQuery.sql:
"""
SELECT e.id
FROM entities e
WHERE (e.workspace_id = :ws_0 AND e.type_id = :type_1 AND e.deleted = false) AND (e.payload @> :eq_2::jsonb)
ORDER BY e.created_at DESC, e.id ASC
LIMIT :limit_3 OFFSET :offset_4
"""

// result.countQuery.sql:
"""
SELECT COUNT(*)
FROM entities e
WHERE (e.workspace_id = :ws_0 AND e.type_id = :type_1 AND e.deleted = false) AND (e.payload @> :eq_2::jsonb)
"""

// parameters:
{
  "ws_0" -> workspaceId,
  "type_1" -> typeId,
  "eq_2" -> "{\"status-uuid\": {\"value\": \"Active\"}}",
  "limit_3" -> 100,
  "offset_4" -> 0
}
```

### Example 3: Query with Mixed Filter (AND of Attribute + Relationship)

```kotlin
// Input
val filter = QueryFilter.And(listOf(
  QueryFilter.Attribute(statusId, EQUALS, FilterValue.Literal("Active")),
  QueryFilter.Relationship(clientRelId, RelationshipFilter.Exists)
))
val pagination = QueryPagination(limit = 200, offset = 50)
val paramGen = ParameterNameGenerator()

// Output
val result = assembler.assemble(typeId, workspaceId, filter, pagination, paramGen)

// result.dataQuery.sql:
"""
SELECT e.id
FROM entities e
WHERE (e.workspace_id = :ws_0 AND e.type_id = :type_1 AND e.deleted = false) AND ((e.payload @> :eq_2::jsonb) AND (EXISTS (
    SELECT 1 FROM entity_relationships r_3
    WHERE r_3.source_entity_id = e.id
      AND r_3.relationship_field_id = :rel_4
      AND r_3.deleted = false
)))
ORDER BY e.created_at DESC, e.id ASC
LIMIT :limit_5 OFFSET :offset_6
"""

// result.countQuery.sql:
"""
SELECT COUNT(*)
FROM entities e
WHERE (e.workspace_id = :ws_0 AND e.type_id = :type_1 AND e.deleted = false) AND ((e.payload @> :eq_2::jsonb) AND (EXISTS (
    SELECT 1 FROM entity_relationships r_3
    WHERE r_3.source_entity_id = e.id
      AND r_3.relationship_field_id = :rel_4
      AND r_3.deleted = false
)))
"""

// parameters:
{
  "ws_0" -> workspaceId,
  "type_1" -> typeId,
  "eq_2" -> "{\"status-uuid\": {\"value\": \"Active\"}}",
  "a_3" -> "a", // alias counter from paramGen
  "rel_4" -> clientRelId,
  "limit_5" -> 200,
  "offset_6" -> 50
}
```

---

## Changelog

|Date|Change|Reason|
|---|---|---|
|2026-02-08|Initial documentation|Phase 2 - Entities domain documentation (Plan 02-03)|
