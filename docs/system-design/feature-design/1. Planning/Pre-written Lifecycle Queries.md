---
tags:
  - "#status/draft"
  - priority/high
  - architecture/feature
  - domain/knowledge
  - domain/catalog
Created: 2026-03-18
Updated:
Domains:
  - "[[riven/docs/system-design/domains/Knowledge/Knowledge]]"
  - "[[riven/docs/system-design/domains/Catalog/Catalog]]"
blocked by:
  - "[[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/1. Planning/Prompt Construction for Knowledge Model Queries]]"
  - "[[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/1. Planning/Data Extraction and Retrieval from Queries]]"
  - "[[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/1. Planning/Lifecycle Domain Model]]"
---
# Feature: Pre-written Lifecycle Queries

---

## 1. Overview

### Problem Statement

A blank natural language query box is intimidating and tells the user nothing about what the system can do. The proof-of-value moment for Riven is when a user runs a cross-domain query that would take them hours in spreadsheets and gets an answer in seconds. Pre-written queries eliminate the "what do I even ask?" friction and immediately demonstrate the lifecycle intelligence value proposition.

### Proposed Solution

Ship 15-20 natural language queries per business type (DTC E-commerce / B2C SaaS), defined as manifest entries alongside the lifecycle spine templates. Queries are scoped to lifecycle domains and demonstrate cross-domain reasoning.

**DTC E-commerce examples:**
- "Which acquisition channel produces customers with the highest LTV after returns and support costs?"
- "What's the return rate by acquisition source?"
- "Which product categories drive the most repeat purchases?"
- "What's the average support load for customers from each marketing channel?"
- "Which customer segments are most at risk of churning in the next 30 days?"

**B2C SaaS examples:**
- "Which signup cohort has the highest 90-day retention?"
- "What's the correlation between onboarding completion and 6-month retention?"
- "Which pricing tier has the most support tickets relative to revenue?"
- "Which acquisition channel drives users who adopt the most features?"
- "What's the expansion revenue opportunity by customer segment?"

**Shared (both types):**
- "Why are customers from [channel] churning faster than average?"
- "What happened differently for customers who stayed vs. those who left?"
- "Which lifecycle domain has the weakest signal?"

Defined in the manifest system as `queryTemplates` within lifecycle spine manifests. Users can modify, duplicate, or create their own. Queries are surfaced in the knowledge layer UI as clickable suggestions.

### Success Criteria

- [ ] 15-20 pre-written queries per business type available immediately after onboarding
- [ ] Queries are runnable with a single click — no typing required
- [ ] Each query demonstrates cross-domain reasoning (touches 2+ lifecycle domains)
- [ ] Queries degrade gracefully when data is missing — explains what domain data is needed
- [ ] Users can customize, duplicate, or create their own queries
- [ ] Defined as manifest entries — community-contributable via PR

---

## Related Documents

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/1. Planning/Prompt Construction for Knowledge Model Queries]] — execution engine for queries
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/1. Planning/Data Extraction and Retrieval from Queries]] — retrieval pipeline queries depend on
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/1. Planning/Lifecycle Domain Model]] — queries scoped to lifecycle domains
- [[riven/docs/system-design/feature-design/3. Active/Semantic Metadata Baked Entity Data Model Templates]] — queries shipped alongside templates
- CEO Plan: Lifecycle Vertical Scoping (2026-03-18)

---

## Changelog

| Date | Author | Change |
| ---- | ------ | ------------- |
| 2026-03-18 | | Initial skeleton from CEO plan review |
