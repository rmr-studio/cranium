---
tags:
  - "#status/draft"
  - priority/critical
  - architecture/feature
  - domain/entity
  - domain/knowledge
Created: 2026-03-18
Updated:
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
  - "[[riven/docs/system-design/domains/Knowledge/Knowledge]]"
blocked by:
  - "[[riven/docs/system-design/feature-design/3. Active/Semantic Entity Groups]]"
---
# Feature: Lifecycle Domain Model

---

## 1. Overview

### Problem Statement

SemanticGroup classifies entity types by WHAT the data is (a person, a product, a financial record). But the knowledge layer also needs to know WHERE data sits in the customer journey — which lifecycle domain it belongs to. Without this, the AI can categorize data but can't reason about causal relationships across lifecycle stages (e.g., "acquisition channel quality drives downstream churn patterns").

The customer lifecycle is not linear — it's a concurrent domain map. A customer exists in multiple domains simultaneously (actively using the product, filing support tickets, and generating billing events at the same time). The model must reflect this concurrency.

### Proposed Solution

Add a `lifecycleDomain` enum column to `entity_types`, orthogonal to `semanticGroup`:

```kotlin
enum class LifecycleDomain {
    ACQUISITION,    // Marketing channels, campaigns, ad spend, referrals
    ONBOARDING,     // First-run events, setup completion, initial support
    USAGE,          // Product activity, feature adoption, engagement
    SUPPORT,        // Help desk, tickets, feedback, complaints
    BILLING,        // Payments, subscriptions, plan changes, invoices
    RETENTION,      // Churn events, win-back, reactivation, derived outcomes
    UNCATEGORIZED   // Non-lifecycle or user-created entity types without assignment
}
```

**Orthogonality with SemanticGroup:**
- SemanticGroup = WHAT the data IS (data category)
- LifecycleDomain = WHERE it sits in the customer journey (analytical context)
- Example: "Communication" has SemanticGroup=COMMUNICATION but LifecycleDomain could be ACQUISITION (marketing email), ONBOARDING (welcome sequence), or SUPPORT (ticket reply)

**Cross-domain analytical relationships (system-defined):**
The knowledge layer understands causal flow between domains:
- ACQUISITION influences ONBOARDING outcomes
- ONBOARDING correlates with USAGE patterns
- USAGE + SUPPORT together correlate with BILLING outcomes
- All domains drive RETENTION (the derived outcome domain)

These relationships inform prompt construction and perspective scoping — when analyzing churn, the knowledge layer knows to look upstream through SUPPORT, USAGE, ONBOARDING, and ACQUISITION.

### Open Design Consideration (Eng Review 2026-03-19)

**Instance-level lifecycle domain classification:** Entity types like Communication and Customer span multiple lifecycle domains. A welcome email is ONBOARDING, a marketing email is ACQUISITION, a support response is SUPPORT. Type-level classification (`lifecycleDomain` on `entity_types`) is UNCATEGORIZED for these types. Instance-level classification should be handled in the [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/1. Planning/Data Chunking and Enrichment Pipeline]] using content analysis + relationship context to determine each entity instance's lifecycle domain during embedding enrichment. See TODO.

### Success Criteria

- [ ] Entity types carry a `lifecycleDomain` column (default: UNCATEGORIZED)
- [ ] Lifecycle spine entity types have pre-assigned lifecycle domains
- [ ] Knowledge layer prompt construction uses lifecycle domain context for cross-domain reasoning
- [ ] Knowledge API supports filtering entity types by lifecycle domain
- [ ] Template and integration manifests can declare lifecycle domain on entity type definitions
- [ ] Existing entity types default to UNCATEGORIZED — no breaking change

---

## Related Documents

- [[riven/docs/system-design/feature-design/3. Active/Semantic Entity Groups]] — orthogonal classification (SemanticGroup = WHAT, LifecycleDomain = WHERE)
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/5. Backlog/Three-Tier Entity Model and Lifecycle Spine]] — spine types carry pre-assigned lifecycle domains
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/1. Planning/Prompt Construction for Knowledge Model Queries]] — uses lifecycle domain for context selection
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/5. Backlog/Knowledge Layer Sub-Agents]] — perspectives scoped to lifecycle domains
- CEO Plan: Lifecycle Vertical Scoping (2026-03-18)

---

## Changelog

| Date | Author | Change |
| ---- | ------ | ------------- |
| 2026-03-18 | | Initial skeleton from CEO plan review |
