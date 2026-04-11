---
tags:
  - "#status/draft"
  - priority/medium
  - architecture/feature
  - domain/knowledge
  - domain/entity
Created: 2026-03-18
Updated:
Domains:
  - "[[riven/docs/system-design/domains/Knowledge/Knowledge]]"
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
blocked by:
  - "[[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/1. Planning/Lifecycle Operations Dashboard]]"
---
# Feature: Dashboard Metrics Pre-computation Layer (DEFERRED — Performance Optimization)

---

## 1. Overview

### Problem Statement

The Lifecycle Operations Dashboard needs to aggregate metrics across all entity types per lifecycle domain on every load. At scale (100k+ entity instances), computing these metrics on-the-fly becomes a performance bottleneck. The operator's daily tool cannot be slow — dashboard load time directly impacts adoption and retention.

### Proposed Solution

A pre-computation layer that materializes dashboard metrics, triggered by data changes rather than on every dashboard load:

**Options to evaluate:**
- PostgreSQL materialized views refreshed on a schedule
- Application-level pre-computation job triggered by entity change events
- Hybrid: materialized views for heavy aggregations + live queries for recent data

**Metrics to pre-compute:**
- Entity counts per lifecycle domain
- Trend calculations (week-over-week, month-over-month)
- Cohort aggregations (retention rates, support load per cohort)
- Cross-domain correlations (channel quality metrics)

### Why Deferred

Premature optimization at this stage. The dashboard should ship with live queries first, and this optimization layer should be built when actual performance data proves it's needed. Target: dashboard load time <2s at p95.

### Success Criteria

- [ ] Dashboard metrics load in <2s at p95 for workspaces with 100k+ entity instances
- [ ] Metric freshness: <5 minutes from entity change to dashboard update
- [ ] Pre-computation doesn't block entity CRUD operations

---

## Related Documents

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/1. Planning/Lifecycle Operations Dashboard]] — the consumer of pre-computed metrics
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/1. Planning/Lifecycle Domain Model]] — metrics grouped by lifecycle domain
- CEO Plan: Lifecycle Vertical Scoping (2026-03-18)

---

## Changelog

| Date | Author | Change |
| ---- | ------ | ------------- |
| 2026-03-18 | | Initial skeleton from CEO plan review — DEFERRED (performance optimization) |
