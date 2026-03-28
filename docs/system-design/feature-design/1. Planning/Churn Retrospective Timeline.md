---
tags:
  - "#status/draft"
  - priority/high
  - architecture/feature
  - architecture/frontend
  - domain/knowledge
  - domain/entity
Created: 2026-03-18
Updated:
Domains:
  - "[[Knowledge]]"
  - "[[Entities]]"
blocked by:
  - "[[2. Areas/2.1 Startup & Business/Riven/2. System Design/feature-design/1. Planning/Three-Tier Entity Model and Lifecycle Spine]]"
  - "[[Lifecycle Domain Model]]"
  - "[[Identity Resolution System]]"
---
# Feature: Churn Retrospective Timeline

---

## 1. Overview

### Problem Statement

When a customer churns, the operator's first question is "why?" and their second question is "could we have known earlier?" Answering these requires tracing the customer back through their entire lifecycle — from acquisition source through onboarding behavior, usage patterns, support interactions, and billing events. Today, this investigation requires manually cross-referencing 4+ tools. It's the exact problem Riven solves — and the churn retrospective timeline is the feature that proves it.

### Proposed Solution

When a customer entity is linked to a Churn Event (cancellation detected via billing integration), the system auto-generates a retrospective timeline: a reverse-chronological view tracing the customer through their lifecycle.

**Timeline structure:**
```
CHURN EVENT (2026-03-15)
  └── Billing: Subscription cancelled after 4 months
      └── Support: 3 tickets in last 30 days (2x above cohort average)
          └── Usage: Feature adoption dropped to 12% in month 3 (from 68% in month 1)
              └── Onboarding: Completed setup but skipped optional integration step
                  └── Acquisition: Instagram campaign (March cohort)
```

**Key features:**
- Auto-generated from relationship traversal across lifecycle spine entity types
- Annotated with cohort comparisons ("2x above average", "dropped from 68% to 12%")
- Highlights the earliest signal: "First warning sign: usage drop at day 47"
- Links to each entity instance for detailed investigation
- Accessible from the customer entity detail view and from churn-related sub-agent signals on the dashboard

**This is Riven's signature feature** — the single view that validates the lifecycle intelligence thesis. It's also structurally unreplicable by single-domain competitors (they'd need data from 4+ tools connected).

### Success Criteria

- [ ] Churn event triggers auto-generation of retrospective timeline for the associated customer
- [ ] Timeline traverses relationships across all connected lifecycle domains
- [ ] Each event on the timeline shows lifecycle domain, timestamp, and relevant metrics
- [ ] Cohort comparison annotations highlight deviations from norms
- [ ] "First warning sign" is identified and highlighted
- [ ] Timeline renders with partial data (some lifecycle domains may not be connected)
- [ ] Accessible from customer entity detail and from dashboard signals

---

## Related Documents

- [[2. Areas/2.1 Startup & Business/Riven/2. System Design/feature-design/1. Planning/Three-Tier Entity Model and Lifecycle Spine]] — entity types forming the timeline
- [[Lifecycle Domain Model]] — cross-domain traversal logic
- [[Identity Resolution System]] — linking customer across integration types to spine types
- [[Knowledge Layer Sub-Agents]] — churn signals trigger retrospective generation
- [[Lifecycle Operations Dashboard]] — drill-in from churn signals
- CEO Plan: Lifecycle Vertical Scoping (2026-03-18)

---

## Changelog

| Date | Author | Change |
| ---- | ------ | ------------- |
| 2026-03-18 | | Initial skeleton from CEO plan review |
