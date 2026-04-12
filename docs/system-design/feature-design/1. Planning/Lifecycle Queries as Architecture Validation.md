---
tags:
  - "#status/draft"
  - priority/high
  - architecture/feature
  - domain/knowledge
  - domain/entity
Created: 2026-03-25
Updated:
Domains:
  - "[[riven/docs/system-design/domains/Knowledge/Knowledge]]"
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
blocked by:
  - "[[SQL Aggregation Column Engine]]"
  - "[[Aggregation Query Benchmark]]"
  - "[[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/5. Backlog/Three-Tier Entity Model and Lifecycle Spine]]"
  - "[[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/1. Planning/Prompt Construction for Knowledge Model Queries]]"
---
# Feature: Lifecycle Queries as Architecture Validation

---

## 1. Overview

### Problem Statement

The lifecycle spine introduces a foundation layer (core entity types, relationships, lifecycle domains, aggregation declarations, projection rules) that 13 downstream features depend on. But the foundation has never been exercised end-to-end: no feature yet traverses relationships across lifecycle domains, computes aggregations, or demonstrates the cross-tool intelligence that is Riven's core value proposition.

Building all 13 features before validating the foundation is high-risk speculative inventory. If the aggregation engine, relationship traversal, or data model shape proves wrong, the cost of discovery grows with each feature built on top.

The Pre-written Lifecycle Queries feature (already designed as a standalone feature) is the ideal validation target because it forces every layer of the stack to work together — entity type lookup, relationship traversal, aggregation computation, cross-domain reasoning, and user-facing output — while being small enough to ship as a focused deliverable.

### Proposed Solution

Implement Pre-written Lifecycle Queries as the first feature after the lifecycle spine and aggregation engine ship. This is not a new feature — it is a sequencing decision that prioritizes the existing [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/1. Planning/Pre-written Lifecycle Queries]] design as the architecture validation milestone.

**What this validates:**

```
LAYER                           VALIDATED BY
═══════════════════════════════════════════════════════════════
Core model definitions          Query templates reference entity
                                types, attributes, relationships
                                by key → proves declarations are
                                correct and resolvable

Aggregation engine              Queries like "highest LTV after
                                returns" require SUM across
                                relationships → proves SQL
                                aggregation works end-to-end

Cross-domain traversal          Queries like "correlation between
                                onboarding completion and retention"
                                span ONBOARDING → RETENTION →
                                BILLING domains → proves LifecycleDomain
                                classification enables meaningful
                                cross-domain reasoning

Relationship model              Queries traverse Customer → Subscription,
                                Customer → Support Ticket, etc. →
                                proves relationship definitions are
                                correctly installed and traversable

Partial data handling           Queries degrade gracefully when a
                                lifecycle domain lacks data →
                                proves the system works with
                                incomplete integrations

Schema correctness              Query execution requires attribute
                                values to be in the expected format
                                (numeric for SUM, temporal for LATEST)
                                → proves schema type declarations
                                match actual data
```

**Validation queries (minimum viable set — 5 per business type):**

The full Pre-written Lifecycle Queries feature ships 15-20 per type. For validation, select the 5 that exercise the most architectural surface area:

**B2C SaaS validation queries:**
1. "Which signup cohort has the highest 90-day retention?" — temporal grouping, COUNT aggregation, cross-domain (ACQUISITION → RETENTION)
2. "Which pricing tier has the most support tickets relative to revenue?" — SUM + COUNT aggregation, ratio computation, cross-domain (BILLING → SUPPORT)
3. "What's the correlation between onboarding completion and 6-month retention?" — temporal filtering, cross-domain (ONBOARDING → RETENTION)
4. "Which acquisition channel drives users who adopt the most features?" — COUNT aggregation, relationship traversal depth 2 (ACQUISITION → Customer → USAGE)
5. "What happened differently for customers who stayed vs. those who left?" — comparative analysis, full lifecycle domain scan

**DTC E-commerce validation queries:**
1. "Which acquisition channel produces customers with the highest LTV after returns?" — SUM aggregation, cross-domain (ACQUISITION → BILLING)
2. "What's the return rate by product category?" — COUNT + ratio, cross-domain (Product → Order → BILLING)
3. "Which customer segments are most at risk of churning in the next 30 days?" — predictive signal, multi-domain scoring
4. "What's the average support load for customers from each marketing channel?" — COUNT aggregation, cross-domain (ACQUISITION → SUPPORT)
5. "Which product categories drive the most repeat purchases?" — COUNT with filtering, cross-domain (Product → Order → Customer)

### What This Is NOT

This is not a redesign of the Pre-written Lifecycle Queries feature. The full feature design lives at [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/1. Planning/Pre-written Lifecycle Queries]]. This document captures the architectural validation rationale — why this feature should ship first, what it validates, and how to use its success or failure as a signal for the remaining 12 features.

### Success Criteria

- [ ] 5 validation queries per business type execute successfully against a workspace with seeded test data
- [ ] Each query demonstrates cross-domain traversal (touches 2+ lifecycle domains)
- [ ] At least 2 queries exercise the aggregation engine (COUNT, SUM, or LATEST)
- [ ] At least 1 query exercises partial data handling (responds gracefully when a domain lacks data)
- [ ] Query execution time <5s per query (generous for validation; optimize in full feature)
- [ ] Results are interpretable by a human reading the query output (demonstrates value proposition)

### Decision Gate

After validation queries are running:

**If all 5 queries work correctly and perform within targets:**
- Proceed with remaining 12 downstream features
- Ship the full Pre-written Lifecycle Queries feature (15-20 queries per type)
- Begin Smart Projection Architecture (integration data flowing into core models)

**If queries expose architectural issues:**
- Document the specific failure mode (aggregation performance, relationship model shape, data type mismatches)
- Fix the foundation before building more features on top
- Re-evaluate which of the 12 remaining features are still feasible with the revised architecture

---

## Related Documents

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/1. Planning/Pre-written Lifecycle Queries]] — the full feature design this validates
- [[SQL Aggregation Column Engine]] — the aggregation engine these queries exercise
- [[Aggregation Query Benchmark]] — raw performance validation (runs before this)
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/5. Backlog/Three-Tier Entity Model and Lifecycle Spine]] — the entity type foundation
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/1. Planning/Lifecycle Domain Model]] — cross-domain traversal this validates
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/1. Planning/Smart Projection Architecture]] — next major feature after validation passes
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/5. Backlog/Churn Retrospective Timeline]] — example of a feature that depends on this validation passing
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/1. Planning/Lifecycle Analytics Views]] — example of a feature that depends on this validation passing
- Eng Review: Lifecycle Spine (2026-03-25) — outside voice recommended identifying a single validation feature

---

## Changelog

| Date | Author | Change |
| ---- | ------ | ------------- |
| 2026-03-25 | | Initial design from eng review — architecture validation via lifecycle queries |
