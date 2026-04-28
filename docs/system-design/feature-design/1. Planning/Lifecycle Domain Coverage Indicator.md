---
tags:
  - "#status/draft"
  - priority/medium
  - architecture/feature
  - architecture/frontend
  - domain/entity
  - domain/knowledge
Created: 2026-03-18
Updated:
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
  - "[[riven/docs/system-design/domains/Knowledge/Knowledge]]"
  - "[[riven/docs/system-design/domains/Integrations/Integrations]]"
blocked by:
  - "[[riven/docs/system-design/feature-design/4. Completed/Lifecycle Domain Model]]"
---
# Feature: Lifecycle Domain Coverage Indicator

---

## 1. Overview

### Problem Statement

Users need to understand how visible their customer lifecycle is — which domains have data flowing and which have gaps. Without this, they don't know what the knowledge layer can and can't reason about. The indicator must be source-agnostic (integration, database, CSV, webhook, manual entry all count) — consistent with the SaaS Decline thesis that data sources diversify beyond just SaaS integrations.

### Proposed Solution

A visible metric on every workspace: "Your lifecycle visibility: 5/6 domains covered."

Computed by checking which `LifecycleDomain` values have at least one entity type with data flowing (entity instances exist). Surfaces during:
- Onboarding (coverage summary step)
- Operations dashboard (persistent indicator)
- Knowledge layer responses (when a query touches a domain with no data)

**Source-agnostic:** The indicator doesn't count integrations — it counts lifecycle domains with entity instances, regardless of how those instances were created (sync, import, manual, webhook).

**Gap explanation:** When a domain has no data, explain what the knowledge layer can't do: "Your support domain has no data source — the knowledge layer can't correlate support patterns with churn."

### Success Criteria

- [ ] Coverage indicator shows X/6 lifecycle domains with data flowing
- [ ] Source-agnostic — counts entity instances, not integration connections
- [ ] Visible during onboarding and on the operations dashboard
- [ ] Gap explanations describe what intelligence is missing per uncovered domain
- [ ] Updates dynamically as data flows in (not just at onboarding time)

---

## Related Documents

- [[riven/docs/system-design/feature-design/4. Completed/Lifecycle Domain Model]] — domains being measured
- [[riven/docs/system-design/feature-design/4. Completed/Lifecycle-Aware Onboarding Flow]] — coverage summary during onboarding
- [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Operations Dashboard]] — persistent indicator on dashboard
- [[SaaS Decline & Strategic Positioning]] — source-agnostic design rationale
- CEO Plan: Lifecycle Vertical Scoping (2026-03-18)

---

## Changelog

| Date | Author | Change |
| ---- | ------ | ------------- |
| 2026-03-18 | | Initial skeleton from CEO plan review |
