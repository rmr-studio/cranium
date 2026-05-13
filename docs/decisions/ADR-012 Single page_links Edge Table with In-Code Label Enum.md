---
tags:
  - architecture/decision
  - adr/accepted
Created: 2026-05-13
Updated: 2026-05-13
---
# ADR-012: Single `page_links` Edge Table with an Optional In-Code Label Enum; Backlinks = Reverse Query

---

## Context

The pre-pivot relationship layer was a per-type-pair `RelationshipDefinitionEntity` system: cardinality (1:1 / 1:N / N:M), semantic constraints, bidirectional/origin/reference flags, polymorphic `target_kind`, auto-generated inverse-REFERENCE rows, two-pass impact confirmation on definition changes, and `QueryDirection` threading through queries. That machinery existed to let workspace users define their own typed relationships between their own entity types. The pivot deletes user-defined types — everything is a `Page` of a fixed kind — so the relationship layer needs to be a plain edge list, not a constraint engine.

---

## Decision

One `page_links` table — `workspace_id`, `source_page_id`, `target_page_id`, nullable `label` (a small in-code enum: `contains | depends_on | owns | defines | references | mentions`), nullable `weight` (float, for ranked overview sections), nullable `source_entity_id` (FK → `source_entities.id` — the artifact that produced the link), `created_at`, `deleted`. **Backlinks = `SELECT ... WHERE target_page_id = :id`.** Overview grouping = `JOIN source_entities ON source_entity_id ... GROUP BY source_kind`. No relationship definitions, no cardinality, no semantic constraints, no polymorphic `target_kind` (everything is a page), no auto inverse rows, no `QueryDirection`.

---

## Rationale

- **No user-defined types ⇒ no per-type-pair relationship configuration to store.** The label enum is a fixed in-code set, like the page-kind enum.
- **Backlinks become trivial.** The old design materialized inverse-REFERENCE rows so backlinks were a forward lookup; with a flat edge table the reverse query is a single indexed `WHERE target_page_id = ?` — and is always consistent (no inverse-row drift).
- **`weight` covers the only real need the constraint engine served for the wiki** — ranking which links show in an overview section ("top 5 files in this domain").
- **`source_entity_id` keeps provenance** — every derived link points at the source artifact that produced it, which is what the overview `GROUP BY` and the decision queue need.

---

## Alternatives Considered

### Option 1: Keep `RelationshipDefinitionEntity`, just stop exposing the CRUD UI

- **Pros:** Less to delete; the machinery is built and tested.
- **Cons:** Carries cardinality/semantic-constraint/inverse-row/two-pass-confirmation complexity for no v1 use; "nothing kept just in case" forbids it.
- **Why rejected:** Dead weight that the synthesis pipeline would have to thread `QueryDirection` and definition-FK through for nothing.

### Option 2: A separate `page_backlinks` materialized table

- **Pros:** Backlink reads are a direct lookup.
- **Cons:** Re-introduces the inverse-row drift problem; the reverse query is already cheap with an index on `page_links(target_page_id) WHERE deleted = false`.
- **Why rejected:** Solves a non-problem; adds a consistency burden.

---

## Consequences

### Positive

- The edge layer is a plain list; new edge types are an enum value, not a migration + a definition row.
- Backlinks are always consistent (computed, not materialized).
- Overview sections ("3 Slack threads, 10 recent PRs") are one indexed join + `GROUP BY`.

### Negative

- Phase 1b is a behavior change: backlinks move from auto-inverse-REFERENCE rows to reverse queries — not behavior-preserving (the glossary `DEFINES`→attributes machinery that depended on `target_parent_id` propagating through ingestion is ripped out). **Gated:** a backlink-parity IT against the 6-case `EnrichmentBacklinkMatrixIT` matrix must pass before 1b merges.
- Loss of database-enforced cardinality — the synthesis pipeline must not create nonsense edges; that's a code-review/eval-suite concern now, not a constraint-engine one.

### Neutral

- `label` is nullable — a bare structural edge with no semantic label is allowed.

---

## Implementation Notes

- Phase 1b: add `label` / `weight` / `source_entity_id`; drop `target_kind`, `target_parent_id`, `link_source`, the relationship-definition FK. Delete `RelationshipDefinitionEntity`, `EntityRelationshipService` definition logic, `EntityTypeRelationshipService`/`DiffService`, `SemanticGroupValidationService`, `QueryDirection` threading, two-pass impact confirmation.
- Index: `page_links(target_page_id) WHERE deleted = false`.
- Outside-voice fix (D8): the link column is `source_entity_id`, not `source_event_id` — there is no separate events table; the link points at the source entity that produced it.

---

## Related

- [[architecture-pivot]]
- [[ADR-011 Reuse Storage Spine, Rebuild Type and Projection Machinery]]
- [[ADR-013 Thin Per-Kind Frontmatter in Code, Loose Extras]]
- [[ADR-016 Structural Collapse Before Behavioral Change]]
