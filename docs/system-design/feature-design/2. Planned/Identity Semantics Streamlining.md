---
tags:
  - status/designed
  - priority/high
  - architecture/design
  - architecture/feature
Created: 2026-03-19
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
  - "[[riven/docs/system-design/domains/Identity Resolution/Identity Resolution]]"
Sub-Domain: "[[riven/docs/system-design/domains/Identity Resolution/Identity Resolution]]"
---
# Feature: Identity Semantics Streamlining

---

## 1. Overview

### Problem Statement

Identity resolution depends on attributes classified as `IDENTIFIER` in the semantic metadata layer. For lifecycle spine entity types (Contact, Company, Deal, etc.) and integration entity types, this classification is pre-configured during workspace initialization — identity matching works immediately and correctly.

However, when a user creates a custom entity type and adds attributes like "Customer Email" or "Phone Number", nothing prompts them to classify those attributes as `IDENTIFIER`. The classification field is nullable, stored in a separate metadata table (`entity_type_semantic_metadata`), and managed through a dedicated API (`KnowledgeController`). If the user doesn't know this classification exists — and most won't — the `IdentityMatchTriggerListener` silently skips their entities because `hasIdentifierAttributes()` returns false.

The system already contains the knowledge to solve this: `MatchSignalType.fromSchemaType()` maps `SchemaType.EMAIL → EMAIL` and `SchemaType.PHONE → PHONE`. But the matching trigger gates on the manual `IDENTIFIER` classification, not on SchemaType. Two parallel systems encode the same knowledge, and the weaker one (manual labeling) is the gatekeeper for the stronger one (schema-type inference).

The result is a degraded experience that the user cannot diagnose — their custom entities simply don't appear in match suggestions, with no indication of why.

### Proposed Solution

A layered approach combining system-level inference (reducing the need for manual classification) with contextual UX prompts (guiding users when manual classification is needed). The layers are designed as independent defences — each catches failures the others miss.

**Layer 1 — SchemaType Inference (System):** Treat attributes with identity-relevant SchemaTypes (EMAIL, PHONE) as implicit IDENTIFIERs in the matching pipeline, regardless of explicit classification. This closes the most common failure mode with zero UX changes.

**Layer 2 — Inline Classification Prompt (UX):** When a user adds an attribute with a SchemaType or name that suggests identity relevance, surface a contextual toggle: "Enable identity matching for this field?" — not a taxonomy dropdown, a binary decision.

**Layer 3 — Workspace Health Indicator (System + UX):** A background check that flags entity types with entities but zero IDENTIFIER attributes. Surfaced as a workspace-level health indicator and on entity type settings pages.

**Layer 4 — Empty-State Nudge (UX):** When viewing an entity from a type with no IDENTIFIER attributes and no match suggestions, show a contextual prompt explaining why and offering a path to configure matching.

### Success Criteria

- [ ] An attribute with `SchemaType.EMAIL` on a custom entity type participates in identity matching without any explicit `IDENTIFIER` classification
- [ ] An attribute with `SchemaType.PHONE` on a custom entity type participates in identity matching without any explicit `IDENTIFIER` classification
- [ ] When a user adds an attribute with an identity-relevant SchemaType or name pattern (email, phone, mobile), the UI presents a classification prompt inline
- [ ] A workspace admin can see which entity types are not participating in identity matching and why
- [ ] An entity detail view for a non-matching entity type shows an explanatory empty state with a configuration action

---

## 2. Scope

### In Scope

| Area | What |
|------|------|
| SchemaType inference | `EntityTypeClassificationService` expanded to treat EMAIL/PHONE SchemaTypes as implicit IDENTIFIERs |
| Candidate query update | `IdentityMatchCandidateService` SQL updated to include implicit IDENTIFIER attributes in the two-phase trigram query |
| Trigger listener update | `IdentityMatchTriggerListener` updated to check both explicit classification and implicit SchemaType |
| Attribute name heuristics | Pattern-matching on attribute names (`email`, `phone`, `mobile`, `name`) to suggest IDENTIFIER classification during entity type creation |
| Workspace health check | Service method that identifies entity types with >0 entities and 0 effective IDENTIFIER attributes (explicit or implicit) |
| UX specifications | Interaction design for inline classification prompt, health indicator placement, and empty-state nudge copy |

### Out of Scope

| Feature | Reason |
|---------|--------|
| Auto-classification of all SchemaTypes | Only EMAIL and PHONE have unambiguous identity semantics. TEXT, NUMBER, etc. require user judgment. |
| ML-based classification suggestion | Requires training data volume that doesn't exist yet. Heuristic name matching is sufficient for v1. |
| Retroactive bulk re-classification | Users can use the existing bulk metadata API. No new migration tooling needed. |
| Workspace-configurable inference rules | Over-engineering for v1. The EMAIL/PHONE → IDENTIFIER mapping is universal. |
| Changes to scoring weights | Signal weights remain as-is. This feature is about getting attributes *into* the pipeline, not changing how they're scored. |

### Dependencies

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/2. Planned/Semantic Metadata Foundation]] — The `entity_type_semantic_metadata` table and `SemanticAttributeClassification` enum must exist (already shipped)
- [[riven/docs/system-design/domains/Identity Resolution/Identity Resolution]] — The matching pipeline (trigger, candidates, scoring, suggestions) must be functional (already shipped in identity-resolution milestone v1.0)

---

## 3. Requirements

### System Requirements

| ID | Requirement | Layer |
|----|-------------|-------|
| ISS-01 | `EntityTypeClassificationService.hasIdentifierAttributes()` returns true when an entity type has attributes with SchemaType EMAIL or PHONE, even if no explicit IDENTIFIER classification exists | Inference |
| ISS-02 | `EntityTypeClassificationService.getIdentifierAttributeIds()` returns attribute IDs for both explicitly classified IDENTIFIER attributes and implicitly inferred ones (EMAIL/PHONE SchemaType) | Inference |
| ISS-03 | `IdentityMatchCandidateService` candidate SQL includes attributes matched by SchemaType inference, not only explicit classification joins | Query |
| ISS-04 | `IdentityMatchTriggerListener` fires for entity types that have implicit IDENTIFIER attributes (SchemaType-based) even when no explicit IDENTIFIER classification exists | Trigger |
| ISS-05 | A workspace health service method returns entity types with >0 entities and 0 effective IDENTIFIER attributes (neither explicit nor implicit), with count of entities affected | Health |
| ISS-06 | Attribute name heuristic service detects common identity patterns (`email`, `e-mail`, `phone`, `mobile`, `cell`, `name`, `full_name`, `username`) and returns classification suggestions | Heuristic |

### UX Requirements

| ID | Requirement | Layer |
|----|-------------|-------|
| ISS-07 | When a user adds an attribute with SchemaType EMAIL or PHONE, the entity type editor shows a non-blocking informational badge: "This field will be used for identity matching" | Inline prompt |
| ISS-08 | When a user adds an attribute with a name matching identity heuristics but a non-identity SchemaType (e.g. TEXT field named "email"), the entity type editor prompts: "This looks like an identity field. Enable identity matching?" with yes/no | Inline prompt |
| ISS-09 | The workspace settings or entity type list includes a health indicator showing how many entity types are not participating in identity matching, with drill-down to specific types | Health indicator |
| ISS-10 | When viewing an entity whose type has no effective IDENTIFIER attributes, the identity section shows an explanatory empty state: "This entity type isn't configured for identity matching" with a link to entity type settings | Empty-state nudge |

### Non-Functional Requirements

| ID | Requirement |
|----|-------------|
| ISS-11 | SchemaType inference adds zero latency to the matching pipeline — the check is a local enum comparison, not a database query |
| ISS-12 | The workspace health check is not real-time — it can be cached or computed on a schedule (workspace settings page load is sufficient) |
| ISS-13 | Heuristic name matching uses a static pattern list, not an external service or ML model |

---

## 4. Design Considerations

### Why SchemaType Inference Is the Highest-Impact Change

The identity matching pipeline already contains `MatchSignalType.fromSchemaType()` which maps `SchemaType.EMAIL → MatchSignalType.EMAIL` and `SchemaType.PHONE → MatchSignalType.PHONE`. The scoring service uses these mappings to assign weights. But the *trigger* and *candidate query* use a completely separate classification system (`SemanticAttributeClassification.IDENTIFIER`) that requires manual user action.

This means the system knows what to do with an email attribute once it's in the pipeline — but refuses to let it into the pipeline without a manual label. Closing this gap eliminates the most common and highest-value failure mode: a user adds an Email column, the system already knows it's an email (via SchemaType), but doesn't match on it because a metadata record in a separate table wasn't updated.

### Why This Is Primarily a Systems Problem, Not a UX Problem

UX improvements (inline prompts, health indicators, empty-state nudges) are valuable but depend on users reaching the right screen at the right time with the right context. A user who creates an entity type, imports data, and then moves on to other work may never see a prompt or health indicator until weeks later when they wonder why their custom entities don't appear in clusters.

System-level inference (SchemaType → implicit IDENTIFIER) works immediately, silently, and correctly for the most common identifier types. It doesn't require the user to understand the classification taxonomy, navigate to the right settings page, or respond to a prompt. The UX layers are safety nets for cases where inference can't help — attributes with ambiguous SchemaTypes like TEXT that happen to contain identity data.

### Interaction Between Explicit and Implicit Classification

When both exist, explicit classification takes precedence. If a user explicitly classifies an EMAIL attribute as `CATEGORICAL` (unusual but possible), the explicit classification wins and the attribute is excluded from matching. This preserves user agency — the system infers only when the user hasn't expressed a preference.

The effective IDENTIFIER set is: `explicit IDENTIFIER ∪ (implicit SchemaType-based − explicit non-IDENTIFIER)`.

### Heuristic Name Matching — Scope and Limits

Attribute name heuristics are a suggestion mechanism, not an automatic classification. The system surfaces a prompt ("This looks like an identity field") but does not auto-classify. This is deliberate:

- Column names are user-authored and unreliable ("email_preference" contains "email" but isn't an identifier)
- False positives from auto-classification would silently add noise to matching
- A prompt respects user agency while reducing the knowledge gap

The heuristic pattern list is intentionally conservative: exact matches and common variants of `email`, `phone`, `mobile`, `name`, `username`. It does not attempt fuzzy matching on column names.

---

## 5. Success Metrics

| Metric | Baseline | Target | How to measure |
|--------|----------|--------|----------------|
| Custom entity types participating in matching | 0% (unless manually classified) | >90% of types with EMAIL/PHONE columns | Query: entity types with EMAIL/PHONE SchemaType attributes that have >0 match suggestions |
| Time from entity type creation to first match suggestion | Depends on user discovering classification | Same as lifecycle spine types (seconds after entity save) | Measure time delta between first entity save and first suggestion for custom types |
| Workspace health indicator engagement | N/A (new) | >50% of workspaces with custom types view the indicator within 30 days | Analytics event on health indicator render |

---

## 6. Open Questions

| # | Question | Impact | Status |
|---|----------|--------|--------|
| 1 | Should implicit IDENTIFIER inference extend beyond EMAIL/PHONE to other SchemaTypes? (e.g. URL for social profile matching) | Scope — could increase match coverage but also false positive rate | Open — defer to v2 data |
| 2 | Should the workspace health check run on a schedule or on-demand? | Performance — scheduled adds background load, on-demand adds latency to settings page | Open — start with on-demand, add caching if slow |
| 3 | Should the inline prompt for name-heuristic matches be a modal or inline badge? | UX — modal is more visible but more disruptive during bulk column creation | Open — needs UX input |

---

## 7. Implementation Phasing

This feature can be delivered incrementally. Each phase is independently valuable.

### Phase A: SchemaType Inference (Backend Only)

**Scope:** ISS-01, ISS-02, ISS-03, ISS-04, ISS-11

Modify `EntityTypeClassificationService` to include SchemaType-based implicit IDENTIFIERs. Update the candidate query and trigger listener to use the expanded attribute set. No UX changes — custom entity types with EMAIL/PHONE columns immediately start participating in matching.

**Impact:** Closes the primary failure mode for ~80% of cases (EMAIL and PHONE are the most common identity attributes).

### Phase B: Workspace Health & Empty States (Backend + Frontend)

**Scope:** ISS-05, ISS-09, ISS-10, ISS-12

Add the workspace health check service method and expose it via an API endpoint. Frontend implements the health indicator on workspace settings and the empty-state nudge on entity detail views.

**Impact:** Makes the remaining gap visible. Users with TEXT-type identity columns (e.g. custom username fields) can discover and fix the misconfiguration.

### Phase C: Inline Classification Prompts (Frontend)

**Scope:** ISS-06, ISS-07, ISS-08, ISS-13

Add attribute name heuristics to the entity type editor. Surface inline prompts during attribute creation for both SchemaType-inferred fields (informational) and heuristic-matched fields (actionable).

**Impact:** Prevents the gap from forming in the first place for new entity types.

---

## Related Documents

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/2. Planned/Semantic Metadata Foundation]] — The semantic metadata layer that stores IDENTIFIER classifications
- [[riven/docs/system-design/domains/Identity Resolution/Identity Resolution]] — The identity matching pipeline that consumes IDENTIFIER classifications
- [[riven/docs/system-design/domains/Identity Resolution/Clusters/Clusters]] — Identity clusters formed from confirmed match suggestions
- [[riven/docs/system-design/domains/Entities/Type Definitions/EntityTypeService]] — Entity type creation and attribute management
- [[riven/docs/system-design/domains/Entities/Entity Semantics/EntityTypeSemanticMetadataService]] — Semantic classification CRUD and lifecycle hooks

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-03-19 | Jared | Initial design from expert panel analysis — SchemaType inference, workspace health indicators, inline classification prompts, empty-state nudges |
