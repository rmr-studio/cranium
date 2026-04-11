---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-02-08
Updated: 2026-02-21
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
---
# AttributeFilterVisitor

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/Querying]]

## Purpose

Visitor that traverses QueryFilter trees and produces SqlFragment output, dispatching to specialized generators for attributes and relationships.

---

## Responsibilities

- Walk filter tree recursively with depth tracking
- Dispatch attribute filters to AttributeSqlGenerator
- Dispatch relationship filters to RelationshipSqlGenerator
- Dispatch IsRelatedTo filters to RelationshipSqlGenerator (bidirectional cross-definition check)
- Combine fragments with AND/OR logic
- Enforce AND/OR nesting depth limit (prevents excessive SQL complexity)
- Enforce relationship traversal depth limit (prevents expensive subqueries)
- Propagate entity alias through tree (e.g., "e" at root, "t_0" in subquery)
- Extract literal values from FilterValue, reject unresolved templates

---

## Dependencies

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/AttributeSqlGenerator]] — Generate SQL for attribute filters
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/RelationshipSqlGenerator]] — Generate SQL for relationship filters with EXISTS subqueries
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/SqlFragment]] — Immutable SQL + parameters container
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/ParameterNameGenerator]] — Unique parameter naming
- Entity models: `QueryFilter`, `FilterValue`
- Exception types: `FilterNestingDepthExceededException`, `RelationshipDepthExceededException`

## Used By

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/EntityQueryAssembler]] — Root visitor entry point
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/RelationshipSqlGenerator]] — Passes nested visitor callback for recursive filtering

---

## Key Logic

**Two independent depth limits:**

1. **AND/OR nesting depth** (`depth` / `maxNestingDepth = 10`):
   - Tracks how deeply AND/OR combinations are nested
   - Resets to 0 when entering relationship subquery (each subquery gets own budget)
   - Prevents SQL complexity explosion

2. **Relationship traversal depth** (`relationshipDepth` / `maxRelationshipDepth = 3`):
   - Tracks how many EXISTS subqueries are nested
   - Incremented only on `QueryFilter.Relationship` nodes
   - Prevents expensive multi-level joins

**Entity alias propagation:**

- Root level: `entityAlias = "e"` (main entity table)
- Nested relationships: RelationshipSqlGenerator provides target alias (e.g., `"t_0"`)
- Alias propagates through all nested visit calls for correct column references

**Visitor pattern dispatch:**

- `QueryFilter.And` → visit all conditions, combine with `and()`
- `QueryFilter.Or` → visit all conditions, combine with `or()`
- `QueryFilter.Attribute` → delegate to AttributeSqlGenerator
- `QueryFilter.Relationship` → delegate to RelationshipSqlGenerator with nested callback. Looks up direction from `relationshipDirections` map (defaults to FORWARD) and passes to RelationshipSqlGenerator.
- `QueryFilter.IsRelatedTo` → delegate to RelationshipSqlGenerator.generateIsRelatedTo (no direction needed — checks both)

**Template handling:**

Template expressions (`FilterValue.Template`) MUST be resolved by caller (workflow layer) before reaching this visitor. Unresolved templates throw `IllegalStateException`.

**Nested visitor callback:**

When delegating to RelationshipSqlGenerator, provides callback:
```kotlin
(nestedFilter, paramGen, targetAlias) -> visitInternal(
  nestedFilter,
  depth = 0,                          // Reset AND/OR depth
  relationshipDepth = relationshipDepth + 1, // Increment relationship depth
  paramGen,
  targetAlias
)
```

---

## Public Methods

### `visit(filter, paramGen, entityAlias = "e", relationshipDirections: Map<UUID, QueryDirection> = emptyMap()): SqlFragment`

Entry point for filter tree traversal. Returns SQL fragment with parameterized SQL and bound values.

- **Throws:** `FilterNestingDepthExceededException` if AND/OR nesting exceeds limit
- **Throws:** `RelationshipDepthExceededException` if relationship depth exceeds limit
- **Throws:** `IllegalStateException` if unresolved template expressions encountered

**`relationshipDirections`** — Query direction map — each relationship definition ID maps to FORWARD or INVERSE direction, passed through to RelationshipSqlGenerator.

---

## Gotchas

- **Depth reset on relationships:** AND/OR depth resets to 0 in subqueries (each context gets own depth budget)
- **Templates rejected:** Caller must resolve templates before calling visitor (separation of concerns)
- **Redundant depth check:** QueryFilterValidator already checks depth, but visitor enforces it too (defense in depth)
- **Empty AND returns `1=1`:** Vacuous truth for empty conjunction
- **Empty OR returns `1=0`:** No match possible for empty disjunction
- **Direction map defaults:** If a relationship ID is not in the directions map, defaults to FORWARD. This is correct for most use cases but may produce wrong results for inverse-visible relationships not included in the map.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/EntityQueryAssembler]] — Entry point for visitor
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/RelationshipSqlGenerator]] — Receives nested visitor callback
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/AttributeSqlGenerator]] — Attribute filter delegation
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/SqlFragment]] — Returned fragment type
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/Querying]] — Parent subdomain

---

## Changelog

| Date | Change | Reason |
| ---- | ------ | ------ |
| 2026-02-21 | Added `relationshipDirections` parameter for FORWARD/INVERSE query direction support | Entity Relationships |
| 2026-03-01 | Added `IsRelatedTo` dispatch to `visitIsRelatedTo` — delegates to `RelationshipSqlGenerator.generateIsRelatedTo` for bidirectional cross-definition existence checks | Entity Connections |
