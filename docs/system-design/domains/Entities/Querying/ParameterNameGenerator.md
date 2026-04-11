---
tags:
  - layer/utility
  - component/active
  - architecture/component
Created: 2026-02-08
Updated: 2026-02-08
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
---
# ParameterNameGenerator

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/Querying]]

## Purpose

Generates sequential unique parameter names for SQL query construction, preventing parameter name collisions.

---

## Responsibilities

- Generate unique parameter names with incrementing counter
- Maintain internal counter state across calls
- Provide predictable naming: `{prefix}_{counter}`

---

## Dependencies

None (simple utility class)

## Used By

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/EntityQueryService]] — Creates one instance per query
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/EntityQueryAssembler]] — Passes to visitor and generators
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/AttributeFilterVisitor]] — Shares across entire filter tree
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/AttributeSqlGenerator]] — Generates unique param names
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/RelationshipSqlGenerator]] — Generates unique param names

---

## Key Logic

**Usage pattern:**

Create ONE instance per query, pass it through all fragment generation calls. This ensures parameter names are globally unique within the query tree.

```kotlin
val paramGen = ParameterNameGenerator()
val eqParam = paramGen.next("eq")     // "eq_0"
val gtParam = paramGen.next("gt")     // "gt_1"
val eqParam2 = paramGen.next("eq")    // "eq_2"
```

**Counter state:**

Internal `counter` starts at 0, increments with each call to `next()`.

---

## Public Methods

### `next(prefix: String): String`

Generates unique parameter name with format `{prefix}_{counter}`. Counter increments after each call.

---

## Gotchas

- **Single instance per query:** MUST share one instance across entire query assembly (visitor, all generators)
- **Not thread-safe:** Use separate instance per concurrent query construction
- **Counter never resets:** Counter increments indefinitely during query construction (fine for single query)

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/SqlFragment]] — Uses generated parameter names
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/EntityQueryService]] — Creates instance per query
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/Querying]] — Parent subdomain
