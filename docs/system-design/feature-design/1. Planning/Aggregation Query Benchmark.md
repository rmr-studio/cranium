---
tags:
  - "#status/draft"
  - priority/high
  - architecture/feature
  - domain/entity
Created: 2026-03-25
Updated:
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
blocked by:
  - "[[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/5. Backlog/Three-Tier Entity Model and Lifecycle Spine]]"
---
# Feature: Aggregation Query Benchmark

---

## 1. Overview

### Problem Statement

The entire lifecycle analytics feature set assumes that SQL-level computed columns (COUNT, SUM, LATEST, STATUS aggregations across entity relationships) will perform within acceptable bounds against the normalized attribute storage model. The `entity_attributes` table stores one row per attribute per entity instance — meaning aggregation queries must JOIN through `entity_relationships` → `entities` → `entity_attributes` with JSONB value casting.

This assumption has never been validated with realistic data volumes. If multi-join aggregation queries against normalized attribute storage prove too slow, the fix isn't incremental — it could require denormalization, a separate analytics schema, or materialized views. Every downstream feature (Lifecycle Analytics, Churn Retrospective, Segment Selection, Operations Dashboard) inherits this architectural risk.

The benchmark must run before the aggregation engine is built to avoid investing in an approach that can't meet performance targets.

### Proposed Solution

A targeted benchmark using synthetic data that simulates a realistic B2C SaaS workspace:

**Synthetic data profile:**
```
Workspace:
  └── 10,000 Customer entities
        ├── ~8,000 with 1-3 Subscriptions (avg 1.5 each = 12,000 subscriptions)
        ├── ~6,000 with 1-10 Support Tickets (avg 3 each = 18,000 tickets)
        ├── ~10,000 with 1-5 Billing Events (avg 2.5 each = 25,000 billing events)
        ├── ~2,000 with 1 Churn Event (2,000 churn events)
        ├── ~5,000 with 1-3 Communications (avg 1.5 each = 7,500 communications)
        └── ~10,000 with 1 Acquisition Source link

Total entity instances: ~84,500
Total entity_attributes rows: ~84,500 × 6 avg attributes = ~507,000
Total entity_relationships rows: ~64,500
```

**Queries to benchmark:**

1. **Single aggregation:** Customer list with COUNT of support tickets
   - Target: <500ms for page of 50 customers

2. **Multi-aggregation:** Customer list with 5 aggregation columns (ticket count, total MRR, latest churn date, subscription status, communication count)
   - Target: <2s for page of 50 customers

3. **Filtered aggregation:** Customers WHERE total_mrr > $100 AND ticket_count > 3, sorted by total_mrr DESC
   - Target: <2s for page of 50 (requires aggregation in WHERE clause)

4. **Full fan-out:** Single customer detail with all relationships expanded + all aggregation columns
   - Target: <500ms

5. **Dashboard aggregation:** COUNT of entities grouped by lifecycle_domain (no pagination, workspace-wide)
   - Target: <1s

**Benchmark methodology:**
- Use `@ActiveProfiles("benchmark")` with a dedicated Testcontainers PostgreSQL instance
- Seed data via factory methods + batch insert
- Run each query 10 times, discard first 2 (warm-up), report p50/p95/p99
- Compare with and without proposed indexes
- Run with `EXPLAIN ANALYZE` to capture query plans

### Success Criteria

- [ ] Synthetic data seeded: 10k customers, 80k+ related entities, 500k+ attribute rows
- [ ] All 5 query patterns benchmarked with p50/p95/p99 timings
- [ ] Query plans captured via EXPLAIN ANALYZE for each pattern
- [ ] Results documented with go/no-go recommendation for SQL-level aggregation approach
- [ ] If any query exceeds 2x the target, document the bottleneck and alternative approaches

### Possible Outcomes

**Green (all targets met):** Proceed with SQL Aggregation Column Engine as designed.

**Yellow (some targets exceeded by <3x):** Proceed with SQL aggregation but add specific indexes or query optimizations identified by EXPLAIN ANALYZE. Document the optimizations as requirements for the engine.

**Red (targets exceeded by >3x or fundamental bottleneck identified):** Evaluate alternatives:
- Denormalized aggregation columns on `entities` table (pre-computed on write)
- Separate `entity_aggregations` table updated via triggers or application events
- Hybrid: simple aggregations (COUNT) via SQL, complex aggregations (SUM with filters) via materialized views

The red path would require revising the SQL Aggregation Column Engine design before implementation.

---

## 2. Implementation Notes

### Data Seeding

Use existing test factory patterns from `src/test/kotlin/riven/core/service/util/factory/`. Extend with:
- `EntityFactory.createBatch(count, typeId, workspaceId)` — bulk entity creation
- `EntityAttributeFactory.createBatchForEntities(entities, schema)` — generate realistic attribute values per schema
- `EntityRelationshipFactory.createRandomLinks(sources, targets, definitionId, avgPerSource)` — Poisson-distributed relationship creation

### Aggregation SQL Prototypes

The benchmark should test raw SQL that mirrors what the aggregation engine would generate, not application-level aggregation. Write the SQL patterns directly as native queries in the benchmark test class.

### Indexing Experiments

Test with and without these candidate indexes:
```sql
-- Composite index for relationship traversal by definition + source
CREATE INDEX idx_entity_rel_def_source ON entity_relationships(definition_id, source_id) WHERE deleted = false;

-- Composite index for attribute lookup by entity + attribute
CREATE INDEX idx_entity_attr_entity_attr ON entity_attributes(entity_id, attribute_id) WHERE deleted = false;

-- Partial index for active entities only
CREATE INDEX idx_entities_workspace_type_active ON entities(workspace_id, type_id) WHERE deleted = false;
```

---

## Related Documents

- [[SQL Aggregation Column Engine]] — the feature this benchmark validates
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/5. Backlog/Three-Tier Entity Model and Lifecycle Spine]] — provides the entity type definitions and aggregation declarations
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/1. Planning/Dashboard Metrics Pre-computation Layer]] — fallback if SQL aggregation proves insufficient
- [[Hub Entity Batch Loading]] — batch fan-out pattern also benefits from benchmark results
- Eng Review: Lifecycle Spine (2026-03-25) — outside voice identified this as a critical risk

---

## Changelog

| Date | Author | Change |
| ---- | ------ | ------------- |
| 2026-03-25 | | Initial design from eng review — aggregation query feasibility benchmark |
