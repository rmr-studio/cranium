---
tags:
  - "#status/draft"
  - priority/medium
  - architecture/feature
  - domain/entity
Created: 2026-03-25
Updated:
Domains:
  - "[[Entities]]"
blocked by:
  - "[[2. Areas/2.1 Startup & Business/Riven/2. System Design/feature-design/1. Planning/Three-Tier Entity Model and Lifecycle Spine]]"
  - "[[SQL Aggregation Column Engine]]"
---
# Feature: Hub Entity Batch Loading

---

## 1. Overview

### Problem Statement

The lifecycle spine's relationship model is generic — any entity type can relate to any other, with no concept of directionality or centrality. But the downstream features (Churn Retrospective, Lifecycle Analytics, Segment Selection, Operations Dashboard) all share a common access pattern: start from Customer, fan out across 5-8 relationship types simultaneously, aggregate or display data from all of them.

The current query engine handles relationship traversal one relationship at a time (depth-based traversal). Loading a Customer's full lifecycle picture — subscriptions, support tickets, billing events, churn events, communications, acquisition source — requires 6 sequential relationship queries. For a list of 50 customers, that's 300 queries.

### Proposed Solution

Add a `hub` boolean property to `CoreModelDefinition` that marks Customer as a hub entity. When the query engine detects a hub entity, it switches from sequential per-relationship loading to a batch fan-out pattern: a single query that JOINs across all declared relationships and loads related entity links in one pass.

**Sequential (current):**
```
For each customer in page:
  For each relationship on Customer:
    SELECT * FROM entity_relationships WHERE source_id = ? AND definition_id = ?

Total queries: page_size × relationship_count = 50 × 6 = 300
```

**Batch fan-out (proposed):**
```
SELECT er.source_id, er.definition_id, er.target_id, e.type_key,
       ea.attribute_id, ea.value  -- identifier attribute for label
FROM entity_relationships er
JOIN entities e ON e.id = er.target_id
JOIN entity_attributes ea ON ea.entity_id = er.target_id
  AND ea.attribute_id = e.identifier_key
WHERE er.source_id IN (<page_of_customer_ids>)
  AND er.definition_id IN (<all_relationship_definition_ids>)
  AND er.deleted = false AND e.deleted = false AND ea.deleted = false

Total queries: 1 (regardless of page size or relationship count)
```

The result is grouped by `(source_id, definition_id)` in application code and merged into each Customer's payload.

**Why a model-level property, not a query hint:**

The hub designation is semantic — it tells the system "this entity type is the center of a star schema and features will consistently need all its relationships loaded." This is different from a per-query optimization hint because:

1. It enables the aggregation engine to pre-plan its subqueries knowing the relationship structure
2. It informs the frontend to render a multi-panel detail view (subscriptions panel, tickets panel, etc.)
3. It guides the projection pipeline to route integration data toward the hub

### What Changes in CoreModelDefinition

```kotlin
abstract class CoreModelDefinition(
    // ... existing properties ...
    val hub: Boolean = false,  // NEW: marks this as a hub entity for batch loading
)
```

Only `CustomerModel` (both SaaS and DTC variants) would set `hub = true` initially. The property is available for future hub entities if the model expands (e.g., a B2B vertical might have `Account` as a hub).

### Success Criteria

- [ ] `hub` property added to `CoreModelDefinition` with default `false`
- [ ] `CustomerModel` variants set `hub = true`
- [ ] `EntityQueryFacadeService` detects hub entity types and uses batch fan-out query
- [ ] Batch query loads all relationship links for a page of hub entities in a single SQL statement
- [ ] Relationship data is correctly partitioned by `(source_id, definition_id)` and merged into entity payloads
- [ ] Performance: hub entity list query with 50 entities and 6 relationships completes in <500ms (vs. ~3s sequential)
- [ ] Non-hub entity types continue to use existing sequential loading (no regression)

---

## 2. Interaction with Aggregation Engine

The batch fan-out pattern and the aggregation engine are complementary but independent:

- **Batch fan-out** loads relationship *links* (entity IDs, labels, icons) for display
- **Aggregation engine** computes *derived values* (COUNT, SUM, LATEST) across relationships

Both benefit from knowing the hub entity's full relationship structure upfront. The aggregation engine can batch its subqueries when it knows all aggregation columns will be evaluated on the same set of source entities.

```
Hub Entity Query (Customer list view with aggregations)
    │
    ├── Batch fan-out: 1 query to load all relationship links
    ├── Aggregation subqueries: 1 query per aggregation column (batched across all customers in page)
    └── Total: 1 + N_aggregation_columns queries (vs. page_size × (relationships + aggregations))
```

---

## Related Documents

- [[2. Areas/2.1 Startup & Business/Riven/2. System Design/feature-design/1. Planning/Three-Tier Entity Model and Lifecycle Spine]] — defines the core models where hub is declared
- [[SQL Aggregation Column Engine]] — aggregation engine benefits from hub designation
- [[Churn Retrospective Timeline]] — requires full relationship fan-out from Customer
- [[Lifecycle Analytics Views]] — GROUP BY customer with multi-relationship aggregation
- [[Action Primitives - Tags Segments Alerts Write-back]] — segments filter customers by aggregated properties
- Eng Review: Lifecycle Spine (2026-03-25)

---

## Changelog

| Date | Author | Change |
| ---- | ------ | ------------- |
| 2026-03-25 | | Initial design from eng review — hub entity batch loading |
