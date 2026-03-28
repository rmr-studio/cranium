---
tags:
  - "#status/draft"
  - priority/high
  - architecture/feature
  - architecture/frontend
  - domain/catalog
  - domain/entity
Created: 2026-03-18
Updated:
Domains:
  - "[[Catalog]]"
  - "[[Entities]]"
  - "[[Integrations]]"
blocked by:
  - "[[2. Areas/2.1 Startup & Business/Riven/2. System Design/feature-design/1. Planning/Three-Tier Entity Model and Lifecycle Spine]]"
  - "[[Integration Access Layer]]"
---
# Feature: Lifecycle-Aware Onboarding Flow

---

## 1. Overview

### Problem Statement

Current workspace creation offers template selection — "pick a template to get started." For a lifecycle intelligence platform, this positions Riven as a generic tool with optional starting points. The vertical scoping requires onboarding that makes the lifecycle thesis tangible in the first 5 minutes. The user should feel that Riven understands their business type and immediately sets up the right lifecycle foundation.

### Proposed Solution

Replace generic template selection with a lifecycle-aware onboarding flow:

1. **Select business type** — DTC E-commerce or B2C SaaS (determines which lifecycle spine variant to install)
2. **Connect your tools** — Guided integration connection for the most common tools per business type
   - DTC: Shopify, Stripe, Gorgias/Zendesk, Klaviyo/Mailchimp, Meta Ads/Google Ads
   - B2C SaaS: Stripe/Chargebee, Intercom/Zendesk, Mixpanel/Amplitude/PostHog, HubSpot
3. **Lifecycle coverage summary** — "Your Lifecycle, Connected" — shows which lifecycle domains have data sources and which have gaps. Ties to Lifecycle Domain Coverage Indicator.
4. **Auto-install lifecycle spine** — Not optional. The lifecycle spine entity types are installed automatically based on business type. Users can extend later but the foundation is non-negotiable.
5. **Ready to explore** — Drop user into the Lifecycle Operations Dashboard with pre-written queries available

**Key shift:** The lifecycle spine IS the workspace. Users extend from there, not build from scratch. Templates become the default, not a choice.

### Success Criteria

- [ ] New workspace creation walks through business type → tool connection → coverage summary
- [ ] Lifecycle spine installs automatically — no "pick a template" step
- [ ] Onboarding completes in under 5 minutes for a user with tool credentials ready
- [ ] Coverage summary accurately reflects which lifecycle domains have connected data sources
- [ ] User lands on the operations dashboard after onboarding, not an empty entity list
- [ ] Onboarding handles partial tool connection gracefully — user can skip tools and connect later

---

## Related Documents

- [[2. Areas/2.1 Startup & Business/Riven/2. System Design/feature-design/1. Planning/Three-Tier Entity Model and Lifecycle Spine]] — spine installed during onboarding
- [[Lifecycle Domain Model]] — coverage summary shows domain-level connectivity
- [[Lifecycle Domain Coverage Indicator]] — reused in onboarding summary
- [[Integration Access Layer]] — tool connection during onboarding
- [[Lifecycle Operations Dashboard]] — landing page after onboarding
- CEO Plan: Lifecycle Vertical Scoping (2026-03-18)

---

## Changelog

| Date | Author | Change |
| ---- | ------ | ------------- |
| 2026-03-18 | | Initial skeleton from CEO plan review |
