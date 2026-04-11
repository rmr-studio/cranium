---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-02-08
Updated: 2026-03-09
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
---
# EntityQueryService

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/Querying]]

## Purpose

Entry point for entity queries, orchestrating validation → assembly → execution → hydration pipeline with parallel query execution.

---

## Responsibilities

- Load entity type schema for filter validation
- Validate filter references (attributes and relationships)
- Assemble parameterized SQL via query assembler
- Execute data and count queries in parallel
- Hydrate entity IDs into full domain models
- Re-sort results to preserve SQL ORDER BY
- Build EntityQueryResult with pagination metadata

---

## Dependencies

- `EntityTypeRepository` — Load entity type schema for validation
- `EntityRepository` — Batch-load entities by IDs
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/EntityQueryAssembler]] — Convert filters to SQL
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/QueryFilterValidator]] — Pre-validate filter structure
- `RelationshipDefinitionRepository` — Load relationship definitions for filter validation and direction resolution
- `RelationshipTargetRuleRepository` — Load relationship definitions for filter validation and direction resolution
- `NamedParameterJdbcTemplate` — Execute parameterized SQL with configured timeout
- [[riven/docs/system-design/domains/Entities/Entity Management/EntityAttributeService]] — Batch-load attributes for query result hydration
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/ParameterNameGenerator]] — Unique parameter naming

## Used By

- Workflow execution layer — Query execution in workflow nodes
- Entity API controllers — REST endpoints for entity queries

---

## Key Logic

**Query execution pipeline:**

1. **Load entity type** from repository
2. **Load relationship definitions** via `loadRelationshipDefinitions`: fetches forward definitions (entity type is source) and inverse-visible definitions (entity type is target with `inverseVisible=true`). Returns `Map<UUID, Pair<RelationshipDefinition, QueryDirection>>` — each definition keyed by ID and paired with its resolved direction (`FORWARD` or `INVERSE`).
3. **Validate filter** (if present):
   - Part A: Walk tree checking attribute IDs exist in schema
   - Part B: Delegate to QueryFilterValidator, passing `RelationshipDefinition` objects (not legacy JSONB models)
   - `QueryFilter.IsRelatedTo` nodes are skipped during attribute validation (no attribute references) and during relationship definition validation (no definition ID)
   - Collect all errors, throw QueryValidationException if any found
4. **Assemble SQL** via EntityQueryAssembler, passing `relationshipDirections: Map<UUID, QueryDirection>` so the assembler can thread direction through to `AttributeFilterVisitor` and `RelationshipSqlGenerator`
5. **Execute in parallel:**
   - Data query: `SELECT e.id ... ORDER BY ... LIMIT/OFFSET`
   - Count query: `SELECT COUNT(*) ...`
6. **Load entities** by IDs from repository
6b. **Load attributes** via `entityAttributeService.getAttributesForEntities()` — batch-loads all attributes for matched entity IDs
7. **Re-sort** entities to match SQL ORDER BY and pass attributes to `toModel(attributes = ...)`
8. **Build result** with entities, totalCount, hasNextPage

**Query timeout:**

Configured via `riven.query.timeout-seconds` property. Applied to JDBC template during initialization.

**Relationship direction resolution:**

Direction is determined by whether the queried entity type is the source or target of each relationship definition:

- **FORWARD** — the queried entity type is the source of the definition. SQL correlates on `source_entity_id`.
- **INVERSE** — the queried entity type is a target of the definition, and `inverseVisible=true`. SQL correlates on `target_entity_id`.

This direction is stored in the `Map<UUID, QueryDirection>` returned by `loadRelationshipDefinitions` and flows through: service → assembler → `AttributeFilterVisitor.visit()` → `RelationshipSqlGenerator`.

**Phase 5 limitation — attribute validation:**

Nested relationship filters validate against the ROOT entity type's attributes, not the target entity type. Cross-type validation requires loading target schemas (future phase).

---

## Public Methods

### `execute(query, workspaceId, pagination, projection): EntityQueryResult`

Executes entity query with optional filters and pagination. Returns matching entities with metadata.

- **Throws:** `NotFoundException` if entity type not found
- **Throws:** `QueryValidationException` if filter references invalid
- **Throws:** `QueryExecutionException` if SQL execution fails

---

## Gotchas

- **Parallel execution:** Data and count queries run concurrently via coroutines (Dispatchers.IO)
- **Re-sorting required:** `EntityRepository.findByIdIn()` doesn't preserve order, must re-sort by SQL ORDER BY
- **Attribute validation limitation:** Nested filters validate against root type attributes only (known Phase 5 simplification)
- **Attribute hydration in query results:** Query results now include hydrated attributes via `entityAttributeService.getAttributesForEntities()`. Relationships still returned as `emptyMap()` in query results (hydration deferred to future phase).
- **FORWARD/INVERSE direction affects SQL:** The correlation column in `RelationshipSqlGenerator` differs by direction — `source_entity_id` for FORWARD, `target_entity_id` for INVERSE. Using the wrong direction silently produces incorrect results rather than an error.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/EntityQueryAssembler]] — SQL assembly
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/QueryFilterValidator]] — Filter validation
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/Querying]] — Parent subdomain

---

## Changelog

### 2026-02-21 — Relationship direction resolution

- Added `RelationshipDefinitionRepository` and `RelationshipTargetRuleRepository` as dependencies for loading relationship definitions.
- Added `loadRelationshipDefinitions` private method: resolves both forward and inverse-visible definitions into a `Map<UUID, Pair<RelationshipDefinition, QueryDirection>>`.
- Filter validation now passes `RelationshipDefinition` objects to `QueryFilterValidator` (replacing legacy JSONB-sourced models).
- SQL assembly now receives `relationshipDirections` map, threading direction through assembler → `AttributeFilterVisitor` → `RelationshipSqlGenerator`.
- Documented FORWARD vs INVERSE direction semantics and the SQL correlation column implication.

### 2026-03-01 — IsRelatedTo filter support

- `QueryFilter.IsRelatedTo` nodes are now handled in the validation pipeline — skipped during both attribute validation and relationship definition validation (no references to validate).

### 2026-03-09 — Attribute hydration in query results

- Added `EntityAttributeService` as a dependency for batch-loading attributes.
- Query execution pipeline now includes attribute hydration step after entity loading.
- `EntityEntity.toModel()` called with `attributes` parameter populated from normalized table.
