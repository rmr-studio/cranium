---
tags:
  - architecture/decision
  - adr/accepted
Created: 2026-05-13
Updated: 2026-05-13
---
# ADR-014: Deterministic Source-Entity Creation + Batched LLM Synthesis Routing

---

## Context

The v1 product turns a GitHub org into a wiki of synthesis pages. Raw artifacts (PRs, commits, files, ADR files; later Slack threads, Notion pages) are Layer 0; the LLM-maintained pages are Layer 1. The pre-pivot projection mechanism — `EntityProjectionService` + `ProjectionRuleEntity` + a `(LifecycleDomain, SemanticGroup)` matcher + `TemplateMaterializationService` — is a deterministic rule engine; it can't express the judgment a wiki page needs ("is this PR worth a sentence on the Domain page?"). The opposite extreme — run a full LLM pass on every artifact — is expensive and non-reproducible. The Temporal Synthesis Layer (designed-but-unbuilt) and enrichment-service PR2 (`EntityKnowledgeView` + sibling-consumer model) already cover most of what a synthesis pipeline needs.

---

## Decision

A two-stage pipeline. **Stage 1 — deterministic source-entity creation:** parse each artifact (PR / commit / file / ADR file → `source_entity` row) + emit cheap deterministic links (PR→files, commit→files, thread→participants) + deterministic page links (file→directory→Domain page, commit-author-email→Person page); skip re-emitting a source-event when `source_entities.content_hash` is unchanged. **Stage 2 — batched LLM synthesis routing:** a Temporal workflow over the `new_source_events_queue` (ShedLock + `SKIP LOCKED` dispatcher, dedup — the `enrichment.embed` pattern), batched (time-windowed ~5 min + size cap ~50 source-events, whichever fires first) and **coalesced by likely-affected page**: read batch + context → extract atomic insights → resolve affected pages (ADR-015) → emit page updates (frontmatter, prose, links) + new pages → mark touched pages for re-embed. This is the Temporal Synthesis Layer, adapted: its machinery (`riven.core.aggregation`, narrative arcs, fan-out, dirty tracking, history/windows, compact/full projections, Haiku + prompt caching, per-section `inputs_hash`) is domain-agnostic and reused as-is, consuming the per-kind Kotlin `synthesisContract`, writing into `pages`/`page_history` (not `entity_synthesis*` — that's D10).

---

## Rationale

- **Deterministic Stage 1 is cheap, testable, reproducible** — no LLM on the parse path; per-`source_kind` parser suites; `content_hash` keeps a re-scan from churning the synthesis queue on no-op files.
- **Batched + coalesced Stage 2 avoids thrash** — 10 PRs/hour to one Domain would be 10 page rewrites + 10 re-embeds if synthesized per-event; coalescing by likely-affected page makes it one rewrite.
- **Reusing the Synthesis Layer reframes D4 from "build" to "adapt"** — the aggregation engine, narrative arc kinds, fan-out queue, dirty tracking, cost controls are all domain-agnostic; what changes is the per-kind synthesis contracts (Domain/Person/Decision/File-rolls-up-to-Domain) and a few engineering arc kinds (`OWNERSHIP_DRIFT`, `ADR_STALENESS`, `REVIEW_LOAD`).
- **Synthesis contracts in Kotlin, not JSON manifests** — D1 deletes the manifest engine; the aggregation/window/narrative-section spec becomes a Kotlin object on the sealed `Page` subclass (compile-time safety > contribution friction for rarely-changing core defs). The aggregation *engine* and the manifest *validation* of synthesis specs (rejecting refs to nonexistent attributes) carry over against the Kotlin contract.
- **Fan-out IS the closed loop** — a new PR → re-synth its source entity → fan out to the Domain hub(s) it touches + the Person hub (author) + any Decision hub the touched files reference; content-hash cycle-break (`last_fanout_trigger_hash`) prevents A→B→A; identity-cluster fan-out priority < direct-trigger priority sheds load under burst.

---

## Alternatives Considered

### Option 1: Keep the deterministic projection-rule engine

- **Pros:** Built; fast; reproducible.
- **Cons:** Can't express wiki judgment — a `(LifecycleDomain, SemanticGroup)` match can't decide what belongs in a synthesis narrative.
- **Why rejected:** The product *is* the judgment; a rule engine produces structure, not prose.

### Option 2: Full LLM on every artifact, synchronously

- **Pros:** Maximal freshness; simplest dataflow.
- **Cons:** Cost (every PR/commit/file an LLM call); non-reproducible; rewrites hot pages repeatedly; LLM latency on the ingestion path.
- **Why rejected:** Unaffordable and thrashy; batching + coalescing is the design.

### Option 3: Per-event synthesis (one LLM call per source-event, async)

- **Pros:** Simple queue model.
- **Cons:** 10 PRs to one Domain = 10 rewrites + 10 re-embeds; the page thrashes.
- **Why rejected:** Coalescing by likely-affected page is the whole point.

---

## Consequences

### Positive

- Stage 1 is unit-testable in isolation; Stage 2 reuses a designed-and-mostly-specified pipeline.
- Cost is bounded by batch size/cadence, not artifact count.
- The fan-out delivers the "every shipped change compounds into the next decision" loop mechanically.

### Negative

- Two notions of "page" must stay distinct: the structured node (`pages` row + `page_links`) vs the rendered article (`pages.body`). They stack; D10 keeps them in one table.
- The synthesis writer must do targeted column updates only (`body`, `aggregations`, `generated_at`, `content_hash`) — never a whole-row replace; `frontmatter` on USER pages, `title`, `slug` are off-limits (lost-update guard).
- **No synthesis-LLM output bypasses the resolution-threshold / decision-queue gate** (prompt-injection-on-synth-content invariant).
- `scan_runs` must record `parsed / skipped / synthesis_failed` counts so "scan complete" never lies (closes the three silent-failure risks).
- Doc-sync debt: the Temporal Synthesis Layer Notion doc + the enrichment-PR2 "future consumers" contract need updating to "writes into `pages`/`page_history`, not `entity_synthesis*`".

### Neutral

- Depends on enrichment-service PR2 Phase 3 being merged to `main` first (it wires `EntityKnowledgeViewProjector`, deletes `SemanticTextBuilderService`) — a hard precondition for Phase 1.

---

## Implementation Notes

- Build order: source parsers → page-resolution eval suite + policy → synthesis Temporal workflow → "why"-prompt spike → MCP server → web graph → PR bot.
- `new_source_events_queue` reuses the `enrichment.embed` isolated-queue / dispatcher / dedup shape verbatim.
- Re-embedding rides the existing `EnrichmentWorkflow` pipeline (the "construct text" step gets a wiki-page renderer instead of the 6-section customer renderer).

---

## Related

- [[architecture-pivot]]
- [[ADR-015 Page Resolution Policy]]
- [[ADR-017 Async Synthesis Materialization with On-Navigation Fill]]
- [[ADR-018 One pages Table Family for Synthesis Storage]]
- [[ADR-013 Thin Per-Kind Frontmatter in Code, Loose Extras]]
