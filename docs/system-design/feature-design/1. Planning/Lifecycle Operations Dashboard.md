---
tags:
  - "#status/draft"
  - priority/high
  - architecture/feature
  - architecture/frontend
  - domain/knowledge
Created: 2026-03-18
Updated:
Domains:
  - "[[riven/docs/system-design/domains/Knowledge/Knowledge]]"
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
blocked by:
  - "[[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/1. Planning/Lifecycle Domain Model]]"
  - "[[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/5. Backlog/Three-Tier Entity Model and Lifecycle Spine]]"
  - "[[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/1. Planning/Data Chunking and Enrichment Pipeline]]"
---
# Feature: Lifecycle Operations Dashboard

---

## 1. Overview

### Problem Statement

Riven's current workspace view is entity-centric — tables of entity types and instances. For the lifecycle intelligence vertical, the primary user (founder/ops person) needs to answer "how is the business doing right now?" at a glance. They don't open Riven to browse entity tables — they open it to see operational performance across the customer lifecycle and understand what needs their attention.

### Proposed Solution

A default workspace view that shows operational metrics grouped by lifecycle domain, with sub-agent signals surfaced as actionable alerts:

**Layout:**
- **Top row:** Metrics cards grouped by lifecycle domain (Acquisition, Engagement, Support, Revenue)
- **Below:** Sub-agent signals — cross-domain insights and alerts from active perspectives
- **Drill-in:** Click any metric or signal to navigate to the entity data, cohort view, or lifecycle journey diagnostic that explains WHY

**Key design principles:**
- Outcomes first, journeys second — the operator cares about "how is the business doing" not "look at this journey diagram"
- The lifecycle journey/flow visualization is a drill-in diagnostic tool, accessed when investigating a signal
- Partial data is expected — the dashboard renders available metrics and shows placeholders for domains without data (ties to Lifecycle Domain Coverage Indicator)
- Sub-agent signals are the differentiator — this is not just a dashboard, it's an intelligence surface

**Metrics sources:**
- Entity data aggregation per lifecycle domain (counts, trends, computed metrics)
- Sub-agent perspective outputs (structured insights, alerts, recommendations)
- Lifecycle Domain Coverage Indicator (which domains have data flowing)

### Success Criteria

- [ ] Default workspace view shows operational metrics grouped by lifecycle domain
- [ ] Sub-agent signals appear as actionable alerts with cross-domain reasoning
- [ ] Clicking a metric drills into the relevant entity data view
- [ ] Dashboard renders gracefully with partial data (some lifecycle domains connected, others not)
- [ ] Dashboard load time <2s at p95 for workspaces with <100k entity instances
- [ ] Mobile-responsive — operators check this on phones

---

## Related Documents

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/1. Planning/Lifecycle Domain Model]] — metrics grouped by lifecycle domain
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/5. Backlog/Knowledge Layer Sub-Agents]] — signals surfaced on dashboard
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/1. Planning/Lifecycle Domain Coverage Indicator]] — shows connectivity gaps
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/5. Backlog/Churn Retrospective Timeline]] — drill-in diagnostic from churn signals
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/1. Planning/Dashboard Metrics Pre-computation Layer]] — TODO for performance at scale
- CEO Plan: Lifecycle Vertical Scoping (2026-03-18)

---

## Changelog

| Date | Author | Change |
| ---- | ------ | ------------- |
| 2026-03-18 | | Initial skeleton from CEO plan review |
