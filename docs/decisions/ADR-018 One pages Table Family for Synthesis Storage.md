---
tags:
  - architecture/decision
  - adr/accepted
Created: 2026-05-13
Updated: 2026-05-13
---
# ADR-018: One `pages` Table Family for Synthesis Storage

---

## Context

There are two notions of "page": the **structured node** (a row identifying a domain/person/file/decision, with frontmatter and edges) and the **rendered article** (the LLM-synthesized markdown narrative about it). The Temporal Synthesis Layer was designed with separate `entity_synthesis` (current snapshot) + `entity_synthesis_history` (append-on-change, `?at=`/`?since=` time-travel) + `entity_type_synthesis` (per-type rollup) tables sitting on top of the `entities` table. The pivot needs to decide: keep that two-table split (node table + synthesis tables), or fold the synthesis into the node table?

---

## Decision

**One `pages` table family** (D10 = B). `pages.body` (markdown) holds the rendered article. `pages.frontmatter` (jsonb, GIN) holds the per-kind frontmatter. `pages.aggregations` (jsonb, GIN) holds the contract-derived + windowed rollups. `page_history` is the append-on-change log (PK `(page_id, generated_at, schema_version, content_hash)`; index `(page_id, generated_at DESC)` for `?at=`/`?since=`). Per-type rollups are `pages` rows of a rollup kind — no separate `entity_type_synthesis`. `page_links` is the only relationship table; `page_embeddings`, `page_resolution_suggestions`, `page_merge_candidates`, `decision_queue`, `new_source_events_queue`, `scan_runs` round out the family. **The `entity_synthesis*` tables do not exist.** The Synthesis Layer's machinery (aggregation engine, narrative arcs, fan-out, dirty tracking, `last_fanout_trigger_hash`, history/windows, compact/full projections) carries over — but writes into `pages`/`page_history` instead. Page kinds are **Kotlin sealed-class implementations** of a base `Page`: each kind owns its frontmatter shape, its `synthesisContract` (aggregations + windows + narrative sections + `narrativeGenerator: TEMPLATED|LLM` + `bodyTokenBudget`), and helpers.

---

## Rationale

- **The node and its article share a lifecycle** — a page exists, gets resynthesized, gets backlinks; splitting "the row" from "the article about the row" into two tables is a join for no benefit when there's exactly one article per node.
- **One table = one place to reason about RLS, soft-delete, history, time-travel** — the Synthesis Layer's history/window/dirty-tracking machinery applies just as well to `page_history` as to `entity_synthesis_history`.
- **The whole point of the rescope is bespoke-not-DTC-bound** — the Synthesis Layer's table contracts were designed for the DTC product; redesigning them for the wiki is exactly the kind of change the pivot exists to make.
- **Sealed Kotlin kinds + jsonb storage** — the per-kind sealed object *is* the schema; jsonb (`pages.frontmatter`, `pages.aggregations`) is the storage; this replaces the normalized `page_fields` table (jsonb-only from Phase 1a). The aggregation *engine* and the manifest *validation* of synthesis specs carry over against the Kotlin contract instead of a JSON manifest (D1 deletes the manifest engine).

---

## Alternatives Considered

### Option A: Keep the two-table split — `pages` (node) + `entity_synthesis` / `entity_synthesis_history` (article)

- **Pros:** Matches the Synthesis Layer design as written; the article table is independently versioned.
- **Cons:** A join for one-to-one data; two RLS surfaces, two soft-delete surfaces; the per-type-synthesis third table; more migration surface in Phase 1.
- **Why rejected:** No benefit at one-article-per-node; the rescope is the right time to redesign the contracts.

### Option B: One `pages` family (chosen)

- **Pros:** One table to reason about; `page_history` for time-travel; per-type rollups are just rows; sealed-Kotlin kinds.
- **Cons:** Synthesis-write lost-update risk (the synthesis writer and a user editing `title`/`frontmatter` touch the same row) — mitigated by the targeted-column-update invariant.
- **Why chosen:** Simpler, and a deliberate redesign of the Synthesis Layer's table contracts, accepted because bespoke-not-DTC-bound is the point.

---

## Consequences

### Positive

- One table, one RLS policy, one soft-delete pattern; `page_history` gives `?at=`/`?since=` time-travel; per-type rollups need no special table.
- Synthesis Layer machinery (aggregation, narrative arcs, fan-out, dirty tracking, compact/full projections, cost controls) reused as-is, writing into `pages`/`page_history`.

### Negative

- **Lost-update guard required:** the synthesis writer does targeted column updates only — `body`, `aggregations`, `generated_at`, `content_hash` — never a whole-row replace; `frontmatter` on USER pages, `title`, `slug` are off-limits to it.
- Adding a page-kind whose `classification`/`aggregations` shape changes needs a migration + a re-render of adjacent kinds — a real, documented cost.
- Doc-sync debt: the Temporal Synthesis Layer Notion doc + the enrichment-PR2 "fans out to future consumers (synthesis layer, JSONB projection)" contract must be updated to "writes into `pages`/`page_history`, not `entity_synthesis*`".

### Neutral

- `pages.confidence`, `last_fanout_trigger_hash`, `schema_version` are added as columns in Phase 1a.

---

## Implementation Notes

- Phase 1a: rename `entities`→`pages`, fold `entity_attributes`→`pages.frontmatter` jsonb, rename `entity_relationships`→`page_links`; introduce the `page_kind` enum + sealed `Page` hierarchy; add the new columns; GIN(jsonb_path_ops) on `frontmatter` and `aggregations`.
- v1 page kinds (7): `Domain`, `Person`, `File`, `Decision`, `ADR`, `System`, `Flow` — `Flow` from PR-cluster co-change only, ships disabled if untrustworthy on the cranium dogfood. (`feature`/`interaction`/`sop`/`insight` are out of v1.)

---

## Related

- [[architecture-pivot]]
- [[ADR-013 Thin Per-Kind Frontmatter in Code, Loose Extras]]
- [[ADR-014 Deterministic Source-Entity Creation plus Batched LLM Synthesis]]
- [[ADR-017 Async Synthesis Materialization with On-Navigation Fill]]
- [[ADR-012 Single page_links Edge Table with In-Code Label Enum]]
