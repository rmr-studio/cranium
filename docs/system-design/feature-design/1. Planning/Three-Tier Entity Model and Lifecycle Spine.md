---
tags:
  - "#status/draft"
  - priority/critical
  - architecture/feature
  - domain/entity
  - domain/catalog
  - domain/knowledge
Created: 2026-03-18
Updated:
Domains:
  - "[[Entities]]"
  - "[[Catalog]]"
  - "[[Knowledge]]"
blocked by:
  - "[[Semantic Entity Groups]]"
---
# Feature: Three-Tier Entity Model and Lifecycle Spine

---

## 1. Overview

### Problem Statement

Riven's vertical scoping to lifecycle intelligence requires a guaranteed set of entity types that the knowledge layer can reason over. Currently, templates install as fully editable copies — users can delete or restructure any entity type, leaving the knowledge layer with no stable data shape to build cross-domain reasoning on. The application needs a protected core data model that represents the customer lifecycle, while preserving user extensibility for custom needs.

### Proposed Solution

Introduce a three-tier entity model:

- **Tier 1: Lifecycle Spine** (`protectionLevel=PROTECTED`, `source=LIFECYCLE_SPINE`) — System-defined core types representing lifecycle stages. Non-deletable. Core attributes are schema-locked. Users CAN add custom attributes and CAN modify semantic metadata descriptions. Installed automatically during workspace creation based on business type selection.
- **Tier 2: Integration Types** (`protectionLevel=READONLY`, `source=MANIFEST`) — Existing readonly integration entity types. No changes.
- **Tier 3: User Types** (`protectionLevel=NONE`, `source=NULL`) — Fully editable user-created types. No changes.

Protection is implemented using **existing columns** — no new enum or column needed:

- **READONLY tier** (integration types): `readonly=true` — already enforced in 4+ guard locations
- **PROTECTED tier** (lifecycle spine): `protected=true`, `sourceType=LIFECYCLE_SPINE` — enforcement added to `deleteEntityType()` and `removeAttributeDefinition()`
- **User tier**: both `false` — fully editable

Per-attribute protection uses the existing `Schema.protected` boolean inside the JSONB schema. Core spine attributes are marked `protected=true` during manifest installation. User-added attributes default to `protected=false`.

#### Lifecycle Spine Entity Types

**Shared models (both DTC and B2C SaaS) — assignments corrected per eng review 2026-03-19:**
- Customer (SemanticGroup: CUSTOMER, LifecycleDomain: UNCATEGORIZED — spans all domains)
- Acquisition Source (SemanticGroup: OPERATIONAL, LifecycleDomain: ACQUISITION)
- Support Interaction (SemanticGroup: SUPPORT, LifecycleDomain: SUPPORT)
- Billing Event (SemanticGroup: FINANCIAL, LifecycleDomain: BILLING)
- Churn Event (SemanticGroup: FINANCIAL, LifecycleDomain: RETENTION — revenue outcome, not operational)
- Communication (SemanticGroup: COMMUNICATION, LifecycleDomain: UNCATEGORIZED — spans acquisition, onboarding, support)

**DTC E-commerce specific:**
- Order (SemanticGroup: TRANSACTION, LifecycleDomain: BILLING)
- Product (SemanticGroup: PRODUCT, LifecycleDomain: USAGE)

**B2C SaaS specific:**
- Subscription (SemanticGroup: TRANSACTION, LifecycleDomain: BILLING)
- Feature Usage Event (SemanticGroup: OPERATIONAL, LifecycleDomain: USAGE)

Defined as JSON manifests in the Catalog domain using the existing `$ref` + `extend` composition system. Spine manifests use `manifestType=LIFECYCLE_SPINE` (new ManifestType enum value).

#### Protection Enforcement (Eng Review 2026-03-19)

Guards added to existing services — no new service needed. Extract shared `EntityTypeProtectionGuard` for DRY.

- `EntityTypeService#deleteEntityType` — add `require(!existing.protected)` guard (alongside existing `require(!existing.readonly)`)
- `EntityTypeAttributeService#removeAttributeDefinition` — add `require(attribute?.protected != true)` guard to check `Schema.protected` per-attribute
- `EntityTypeAttributeService#saveAttributeDefinition` — add `require(attribute?.protected != true)` guard when modifying existing protected attributes. New attributes (not in schema yet) are allowed.
- `EntityTypeService#updateEntityTypeConfiguration` — allowed for metadata changes (name, description, icon, semantic metadata) on PROTECTED types. Block SemanticGroup and LifecycleDomain changes on PROTECTED types.
- Core vs. user-added attributes distinguished by `Schema.protected` field inside JSONB schema — no separate source marker needed. Manifest-installed attributes get `protected=true`. User-added attributes default to `protected=false`.
- Protected attributes are **fully immutable** — users cannot change label, type, required, or any property. They can only be created by manifest installation.

### Success Criteria

- [ ] Workspace creation auto-installs lifecycle spine entity types based on business type (DTC / B2C SaaS)
- [ ] PROTECTED entity types cannot be deleted — returns 403 with clear explanation
- [ ] Core (manifest-defined) attributes on PROTECTED types cannot be removed — returns 403
- [ ] Users CAN add custom attributes to PROTECTED types
- [ ] Users CAN modify semantic metadata descriptions on PROTECTED types
- [ ] `entity_types.protected` is enforced in `deleteEntityType()` — returns 403 for protected types
- [ ] `Schema.protected` is enforced in `removeAttributeDefinition()` — returns 403 for protected attributes
- [ ] `Schema.protected` is enforced in `saveAttributeDefinition()` — blocks modification of existing protected attributes
- [ ] `EntityTypeProtectionGuard` extracted to centralize protection logic (DRY)
- [ ] Existing READONLY integration types continue to work identically
- [ ] Existing user-created types are unaffected (protectionLevel=NONE)
- [ ] Spine manifests load via the existing Catalog manifest pipeline

---

## Related Documents

- [[Semantic Entity Groups]] — prerequisite (SemanticGroup enum)
- [[Lifecycle Domain Model]] — LifecycleDomain enum on entity types
- [[Predefined Integration Entity Types]] — Tier 2 pattern this extends
- [[Semantic Metadata Baked Entity Data Model Templates]] — template manifest format
- [[Declarative Manifest Catalog and Consumption Pipeline]] — manifest loading pipeline
- CEO Plan: Lifecycle Vertical Scoping (2026-03-18)

---

## Changelog

| Date | Author | Change |
| ---- | ------ | ------------- |
| 2026-03-18 | | Initial skeleton from CEO plan review |
