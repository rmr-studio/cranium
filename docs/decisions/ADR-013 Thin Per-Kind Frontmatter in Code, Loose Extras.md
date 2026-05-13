---
tags:
  - architecture/decision
  - adr/accepted
Created: 2026-05-13
Updated: 2026-05-13
---
# ADR-013: Thin Per-Kind Frontmatter Declared in Code; Loose Extra Keys Stored, Not Validated

---

## Context

The pre-pivot system had a full JSON-schema-per-entity-type validation engine (`EntityValidationService`), schema-breaking-change detection (`detectSchemaBreakingChanges()`), and an impact-confirmation flow on schema changes — all so workspace users could define and evolve their own attribute schemas safely. The pivot removes user-defined types: a `Page` is one of 7 fixed kinds, each with a thin, code-owned frontmatter shape. The question is how strictly to validate the frontmatter the synthesis LLM fills in, and where to store it.

---

## Decision

Each page kind's frontmatter shape is **declared in Kotlin** (a `frontmatterSchema` on the sealed `Page` subclass — like a `CoreModelDefinition`). The synthesis LLM is prompted to fill it. **Extra keys the LLM extracts are stored, not validated** — frontmatter lives in a single `pages.frontmatter` jsonb column with a `GIN(jsonb_path_ops)` index (jsonb-only from Phase 1a — no transitional normalized `page_fields` table). The JSON-schema validation engine + `detectSchemaBreakingChanges()` + the impact-confirmation flow leave scope (no user types ⇒ no migration problem).

---

## Rationale

- **No user-evolved schemas ⇒ no breaking-change-detection need.** Page-kind frontmatter shapes change via a code PR + (if needed) a migration — a normal review, not a runtime safety problem.
- **Loose extras are an asset for a wiki.** The LLM may extract a useful key the kind's declared shape didn't anticipate; storing it (queryable via jsonb path) beats discarding it. Strict validation would force a code change before the LLM could record anything new.
- **jsonb + GIN > a normalized one-row-per-field table** for this access pattern: the per-kind sealed Kotlin object *is* the schema; jsonb is just storage. Carrying both `page_fields` and `pages.frontmatter` would be redundant — D10's model points at jsonb-only.

---

## Alternatives Considered

### Option 1: Keep the JSON-schema validation engine, point it at the per-kind shapes

- **Pros:** Reuses built machinery; rejects malformed LLM output at write time.
- **Cons:** No user types ⇒ the breaking-change-detection half is dead weight; strict rejection of extra keys is the wrong default for an LLM-fed wiki.
- **Why rejected:** Solves a problem the pivot removed; would block useful extracted keys.

### Option 2: Keep the normalized `page_fields` table (one row per frontmatter field)

- **Pros:** Each field independently queryable/indexable with plain SQL; transitional during the rename.
- **Cons:** Redundant with `pages.frontmatter` jsonb; the addendum says don't carry both; jsonb + GIN already covers the query needs.
- **Why rejected:** D10's sealed-Kotlin model is jsonb-first; pick one, in Phase 1a.

---

## Consequences

### Positive

- Frontmatter shapes are compile-time-checked Kotlin; adding a key to a kind is a typed code change.
- The LLM can record extracted keys beyond the declared shape without a code change.
- One column, one GIN index — simple storage.

### Negative

- No write-time guarantee that frontmatter conforms to the declared shape — the synthesis pipeline + the eval suite carry that, not a schema validator.
- A kind whose `classification`/`aggregations` shape changes still needs a migration + a re-render of adjacent kinds — documented as a real (not free) cost.

### Neutral

- `is_identifier`-style semantics (which key is the canonical handle) move into the sealed Kotlin object (Domain → directory path, Person → git commit-author-email, ADR → ADR file path).

---

## Implementation Notes

- Phase 1a: introduce the `page_kind` enum + the sealed `Page` hierarchy + the per-kind `frontmatterSchema` registry; fold `entity_attributes` into `pages.frontmatter` jsonb; `GIN(jsonb_path_ops)` index.
- Delete `EntityValidationService` schema-per-type validation, `detectSchemaBreakingChanges()`, the impact-confirmation flow, `EntityTypeRole` (Phase 1c).

---

## Related

- [[architecture-pivot]]
- [[ADR-018 One pages Table Family for Synthesis Storage]]
- [[ADR-014 Deterministic Source-Entity Creation plus Batched LLM Synthesis]]
- [[ADR-011 Reuse Storage Spine, Rebuild Type and Projection Machinery]]
