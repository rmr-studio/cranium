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
# AssembledQuery

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/Querying]]

## Purpose

Value object holding paired data and count SQL queries sharing the same WHERE clause conditions.

---

## Responsibilities

- Hold data query with `SELECT e.id`, ORDER BY, LIMIT/OFFSET
- Hold count query with `SELECT COUNT(*)`, no pagination
- Both queries share WHERE clause (workspace, type, soft-delete, filters)

---

## Dependencies

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/SqlFragment]] — Both queries are SqlFragment instances

## Used By

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/EntityQueryAssembler]] — Produces AssembledQuery from filter
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/EntityQueryService]] — Executes both queries (potentially in parallel)

---

## Key Logic

**Query pairing:**

Both queries have identical WHERE conditions but differ in SELECT/ORDER/LIMIT:

- **dataQuery:** `SELECT e.id FROM entities e WHERE ... ORDER BY created_at DESC, id ASC LIMIT X OFFSET Y`
- **countQuery:** `SELECT COUNT(*) FROM entities e WHERE ...`

**Parallel execution:**

EntityQueryService runs both queries concurrently via coroutines. Data query returns entity IDs, count query returns total across all pages.

---

## Public Methods

Data class with no methods beyond constructor and property accessors.

---

## Gotchas

- **Shared parameters:** Both fragments may share parameter names/values (same WHERE clause)
- **No validation:** Class doesn't validate that WHERE clauses actually match (caller's responsibility)

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/EntityQueryAssembler]] — Assembles this structure
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/EntityQueryService]] — Executes both queries
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/SqlFragment]] — Component type
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/Querying]] — Parent subdomain
