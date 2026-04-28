---
tags:
  - "#status/draft"
  - priority/medium
  - architecture/feature
  - domain/workflow
  - domain/knowledge
Created: 2026-03-18
Updated:
Domains:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
  - "[[riven/docs/system-design/domains/Knowledge/Knowledge]]"
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
blocked by:
  - "[[riven/docs/system-design/feature-design/5. Backlog/Conditional Nodes + Loop Support]]"
  - "[[riven/docs/system-design/feature-design/5. Backlog/Workflow Node Output State Management Handling]]"
  - "[[riven/docs/system-design/feature-design/5. Backlog/Knowledge Layer Sub-Agents]]"
  - "[[riven/docs/system-design/feature-design/4. Completed/Three-Tier Entity Model and Lifecycle Spine]]"
---
# Feature: Lifecycle-Scoped Workflow Templates (DEFERRED — Pre-MVP)

---

## 1. Overview

### Problem Statement

The lifecycle spine and sub-agent perspectives provide passive intelligence — they observe and report. Workflow templates would turn this into active operations: when the system detects a lifecycle signal, it can automatically trigger actions. Without this, the operator must manually act on every insight the knowledge layer surfaces.

### Proposed Solution

Pre-built workflow automations tied to lifecycle domains, defined as manifest entries:

**Examples:**
- **Churn Risk Alert** (cross-domain): When usage drops + support ticket filed within 7 days → flag customer as at-risk + create notification
- **Channel Quality Report** (ACQUISITION → RETENTION): Weekly → aggregate acquisition channel metrics vs. retention outcomes → push summary to dashboard
- **Onboarding Stall Detection** (ONBOARDING): When customer hasn't completed key onboarding events within 14 days → create alert entity + trigger notification
- **Support Escalation** (SUPPORT → BILLING): When high-LTV customer files 3rd support ticket in 30 days → escalate priority + notify

Users can modify, disable, or create their own workflow automations.

### Why Deferred

The workflow engine has significant technical debt that blocks this:
- No loop or switch/case support — only binary conditions
- No partial failure recovery — one node failure = entire workflow failure
- No human-in-the-loop approval steps

These must be addressed before pre-built workflow templates can provide reliable lifecycle automation.

### Success Criteria

- [ ] 5-8 pre-built workflow templates per business type
- [ ] Templates are editable and disableable by users
- [ ] Workflows can be triggered by sub-agent signals (knowledge layer → workflow engine)
- [ ] Defined as manifest entries — community-contributable
- [ ] Reliable execution with partial failure handling

---

## Related Documents

- [[riven/docs/system-design/domains/Workflows/Workflows]] — engine that needs maturity before this feature
- [[riven/docs/system-design/feature-design/5. Backlog/Conditional Nodes + Loop Support]] — prerequisite
- [[riven/docs/system-design/feature-design/5. Backlog/Workflow Node Output State Management Handling]] — prerequisite
- [[riven/docs/system-design/feature-design/5. Backlog/Knowledge Layer Sub-Agents]] — perspectives trigger workflows
- [[riven/docs/system-design/feature-design/4. Completed/Three-Tier Entity Model and Lifecycle Spine]] — lifecycle entities as workflow context
- CEO Plan: Lifecycle Vertical Scoping (2026-03-18)

---

## Changelog

| Date | Author | Change |
| ---- | ------ | ------------- |
| 2026-03-18 | | Initial skeleton from CEO plan review — DEFERRED (pre-MVP) |
