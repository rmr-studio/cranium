---
tags:
  - "#status/draft"
  - priority/high
  - architecture/feature
  - domain/entity
Created: 2026-03-25
Updated:
Domains:
  - "[[Entities]]"
blocked by:
  - "[[Three-Tier Entity Model and Lifecycle Spine]]"
  - "[[Aggregation Query Benchmark]]"
---
# Feature: SQL Aggregation Column Engine

---

## 1. Overview

### Problem Statement

The lifecycle spine installs core entity types (Customer, Subscription, Support Ticket, etc.) with declared aggregation column definitions — computed values like "Total MRR", "Open Ticket Count", "Latest Churn Date" that derive their values by traversing relationships and aggregating attributes from related entities. These declarations exist as `AggregationColumnDefinition` on each `CoreModelDefinition`, but nothing evaluates them. Without an aggregation engine, every downstream feature that needs cross-entity metrics (Churn Retrospective, Lifecycle Analytics, Segment Selection, Operations Dashboard) must build its own ad-hoc query logic.

The entity query engine (`EntityQueryService`) currently supports filtering by attribute values and traversing relationships, but it cannot compute derived values across relationship boundaries. A Customer entity can load its related Subscriptions, but it cannot answer "what is the SUM of subscription.mrr across all related subscriptions?" at query time.

### Proposed Solution

Extend `EntityQueryService` to evaluate `AggregationColumnDefinition` declarations as SQL subqueries at query time. Each aggregation column becomes a correlated subquery that JOINs through `entity_relationships` to `entity_attributes` on the target entity type, applies the declared aggregation function, and returns the result as a virtual column on the source entity.

**Aggregation types and their SQL patterns:**

```
COUNT — Count of related entities matching the filter
────────────────────────────────────────────────────
SELECT COUNT(*)
FROM entity_relationships er
WHERE er.source_id = <entity_id>
  AND er.definition_id = <relationship_definition_id>
  AND er.deleted = false

SUM — Sum of a numeric attribute across related entities
────────────────────────────────────────────────────────
SELECT COALESCE(SUM((ea.value #>> '{}')::numeric), 0)
FROM entity_relationships er
JOIN entity_attributes ea ON ea.entity_id = er.target_id
WHERE er.source_id = <entity_id>
  AND er.definition_id = <relationship_definition_id>
  AND ea.attribute_id = <target_attribute_uuid>
  AND er.deleted = false AND ea.deleted = false

LATEST — Most recent value of a temporal attribute
──────────────────────────────────────────────────
SELECT ea.value #>> '{}'
FROM entity_relationships er
JOIN entity_attributes ea ON ea.entity_id = er.target_id
WHERE er.source_id = <entity_id>
  AND er.definition_id = <relationship_definition_id>
  AND ea.attribute_id = <target_attribute_uuid>
  AND er.deleted = false AND ea.deleted = false
ORDER BY ea.value DESC
LIMIT 1

STATUS — Value of a categorical attribute from the most recently created related entity
───────────────────────────────────────────────────────────────────────────────────────
SELECT ea.value #>> '{}'
FROM entity_relationships er
JOIN entities e ON e.id = er.target_id
JOIN entity_attributes ea ON ea.entity_id = er.target_id
WHERE er.source_id = <entity_id>
  AND er.definition_id = <relationship_definition_id>
  AND ea.attribute_id = <target_attribute_uuid>
  AND er.deleted = false AND ea.deleted = false AND e.deleted = false
ORDER BY e.created_at DESC
LIMIT 1
```

**Integration with existing query engine:**

```
EntityQueryFacadeService.queryEntities(workspaceId, entityTypeId, request)
    │
    ├── [existing] Filter: entity_attributes WHERE conditions
    ├── [existing] Relationships: entity_relationships JOIN entities
    └── [NEW] Aggregations: for each AggregationColumnDefinition on the entity type
               │
               ├── Resolve relationship definition ID from sourceRelationshipKey
               ├── Resolve target attribute UUID from targetAttributeKey (for SUM/LATEST/STATUS)
               ├── Generate correlated subquery
               ├── Apply optional AggregationFilter
               └── Include as virtual column in response payload
```

**Aggregation columns are NOT stored.** They are computed on every query. This ensures consistency without cache invalidation. The Dashboard Metrics Pre-computation Layer is a separate, deferred optimization for when live computation proves insufficient at scale.

**Composability with filters and pagination:**

Aggregation columns must compose with the existing filter system. A user should be able to filter by aggregated values (e.g., "show customers where Total MRR > $100") and sort by aggregated values. This requires the subqueries to be part of the main SQL statement, not post-processing.

### Why SQL-Level, Not Application-Level

Application-level aggregation (load entities, traverse relationships in Kotlin, compute in memory) was considered and rejected because:

1. **Breaks pagination** — aggregations must be computed before pagination is applied, requiring all entities to be loaded into memory first
2. **Breaks filtering by aggregated values** — "customers where ticket count > 5" requires the aggregation to exist in SQL WHERE clauses
3. **O(n) memory** — loading all entities + their related entities for aggregation doesn't scale
4. **Duplicated logic** — every feature would implement its own aggregation traversal

### Success Criteria

- [ ] COUNT aggregation: count of related entities via a named relationship
- [ ] SUM aggregation: sum of a numeric attribute across related entities
- [ ] LATEST aggregation: most recent value of a temporal attribute from related entities
- [ ] STATUS aggregation: value of a categorical attribute from the most recently created related entity
- [ ] Aggregation columns appear as virtual columns in `EntityQueryResponse.payload`
- [ ] Aggregation columns are filterable (e.g., `total_mrr > 100`)
- [ ] Aggregation columns are sortable
- [ ] Aggregation columns compose with pagination (computed before LIMIT/OFFSET)
- [ ] AggregationFilter is applied when declared (e.g., only count subscriptions where status = "active")
- [ ] Performance: <2s at p95 for 10k source entities with 5 aggregation columns each traversing ~10 relationships

---

## 2. Data Model

### Input: AggregationColumnDefinition (already declared on CoreModelDefinition)

```kotlin
data class AggregationColumnDefinition(
    val name: String,                        // Display name: "Total MRR"
    val aggregation: AggregationType,        // COUNT, SUM, LATEST, STATUS
    val sourceRelationshipKey: String,       // Relationship to traverse: "customer-subscriptions"
    val targetAttributeKey: String?,         // Attribute on target entity: "mrr" (null for COUNT)
    val filter: AggregationFilter?,          // Optional filter on target entities
)
```

### Output: Virtual columns in EntityQueryResponse

Aggregation results are included in the entity payload alongside regular attributes. They are distinguishable by a property type marker (e.g., `EntityPropertyType.AGGREGATION`) so the frontend can render them differently (read-only, no edit affordance).

### Resolution at Installation Time

When a template is installed into a workspace, `AggregationColumnDefinition.sourceRelationshipKey` must be resolved to a `RelationshipDefinitionEntity.id` (UUID), and `targetAttributeKey` must be resolved to an attribute UUID in the target entity type's schema. This resolution happens in `TemplateInstallationService` and the resolved UUIDs are stored alongside the entity type (e.g., as a JSONB column `aggregation_columns` on `entity_types`, or as a separate `entity_type_aggregation_columns` table).

---

## 3. Performance Considerations

The normalized attribute storage model (`entity_attributes` table with one row per attribute value) means aggregation subqueries JOIN through three tables: `entity_relationships` → `entities` → `entity_attributes`. For a Customer with 5 aggregation columns, each query generates 5 correlated subqueries.

**Indexing strategy:**
- `entity_relationships(source_id, definition_id)` — fast relationship lookup per entity
- `entity_attributes(entity_id, attribute_id)` — fast attribute lookup per entity
- Composite index on `entity_relationships(definition_id, source_id)` for batch queries

**Scaling path:**
1. **First:** Live SQL subqueries (this feature)
2. **If >2s at p95:** Evaluate lateral joins or batch subquery patterns
3. **If still slow:** Dashboard Metrics Pre-computation Layer (separate feature, already designed)

The [[Aggregation Query Benchmark]] feature must validate this approach before full implementation begins.

---

## Related Documents

- [[Three-Tier Entity Model and Lifecycle Spine]] — declares the aggregation column definitions on core models
- [[Aggregation Query Benchmark]] — validates feasibility of this approach before implementation
- [[Smart Projection Architecture]] — aggregation columns are how projected integration data surfaces on core entities
- [[Dashboard Metrics Pre-computation Layer]] — deferred optimization if live computation proves insufficient
- [[Lifecycle Analytics Views]] — primary consumer of aggregation columns
- [[Churn Retrospective Timeline]] — uses aggregation for cohort comparison annotations
- [[Pre-written Lifecycle Queries]] — recommended first validation feature
- Eng Review: Lifecycle Spine (2026-03-25)

---

## Changelog

| Date | Author | Change |
| ---- | ------ | ------------- |
| 2026-03-25 | | Initial design from eng review — SQL aggregation column engine |
