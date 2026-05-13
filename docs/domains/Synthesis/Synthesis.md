---
tags:
  - architecture/domain
  - cranium/v1
Created: 2026-05-13
---
# Domain: Synthesis

---

## Overview

The LLM-maintained wiki. A batched Temporal workflow drains the new-source-events queue, reads a batch of `source_entities` + context, extracts atomic insights, resolves each to a page (via [[domains/Page Resolution & Decision Queue/Page Resolution & Decision Queue|Page Resolution]]), and emits page updates — frontmatter, prose, links — plus new pages, coalescing by affected page so a busy domain isn't rewritten ten times an hour. It writes **targeted columns only** (`body`, `aggregations`, `generated_at`, `content_hash`) — never a whole-row replace. This is the adapted Temporal Synthesis Layer: its aggregation engine, narrative arcs, fan-out, dirty tracking, history/windows, compact/full projections all carry over; what changed is the synthesis contracts (per page-kind, in Kotlin) and where it writes (into `pages`/`page_history`, not `entity_synthesis*`). See [[overview]] §Synthesis layer and [[ADR-014]] / [[ADR-017]] / [[ADR-018]].

---

## Boundaries

### This Domain Owns

- The synthesis Temporal workflow (sibling `ConsumerActivity` to embedding; batched/coalesced over `new_source_events_queue`).
- Atomic-insight extraction from a source-entity batch.
- Page-update emission: frontmatter merges, prose (`body`) generation per the kind's `synthesisContract` + `narrativeGenerator` + `bodyTokenBudget`, link emission.
- New-page creation when an insight resolves to "new".
- Coalescing by affected page; marking touched pages for re-embed.
- Fan-out: re-synthesizing a source entity enqueues re-synthesis of its Domain hub(s) + Person hub (author) + any Decision hub the touched files reference; content-hash cycle-break (`last_fanout_trigger_hash`); identity-cluster fan-out at lower priority than direct triggers.
- The `pages.body` **wiki-page renderer** — the per-kind narrative section template (replaces the old 6-section customer renderer; the embedding-text "construct text" step also gets this renderer instead of `SemanticTextBuilderService`).
- The `stale` flag + the backlog dashboard signal.
- The non-blocking on-navigation fill: navigating to a missing/very-stale page enqueues a high-priority refresh and serves the cached row (or a `_(generating…)_` placeholder) immediately — **no LLM call on the request path**.

### This Domain Does NOT Own

- Creating `source_entities` or the deterministic links — that's [[domains/Source Ingestion/Source Ingestion|Source Ingestion]].
- The resolution *policy* (deterministic key → fuzzy → auto-link/queue) — that's [[domains/Page Resolution & Decision Queue/Page Resolution & Decision Queue|Page Resolution & Decision Queue]]. Synthesis *calls* it per insight.
- Writing user-editable columns (`frontmatter` on `USER` pages, `title`, `slug`) — off-limits (invariant 1).
- The embedding pipeline itself — that's reused as-is; Synthesis just marks pages dirty.

---

## Sub-Domains

| Component | Purpose |
| --------- | ------- |
| Synthesis Temporal workflow | per-batch: read → extract → resolve → emit → coalesce → mark-re-embed |
| Atomic-insight extractor | source-entity batch → list of atomic insights |
| Page-update emitter | frontmatter merge · prose generation · link emit · new-page creation |
| `pages.body` wiki renderer | per-kind narrative sections; replaces the 6-section customer renderer |
| Aggregation engine (`riven.core.aggregation`) | SUM/AVG/COUNT/RATIO/P50/P90/TOP_K/LATEST/… windowed (7d/30d/90d) — reused |
| `NarrativeArcGenerator` | Templated default ($0) / LLM opt-in (Haiku + prompt caching); engineering arc kinds (OWNERSHIP_DRIFT, ADR_STALENESS, REVIEW_LOAD, + VOLUME/THEME_EVOLUTION/RECENCY/ANOMALY) |
| Fan-out coordinator | PR→Domain-hub + Person-hub + Decision-hub; content-hash cycle-break |
| On-navigation fill | high-priority refresh enqueue; serve cached / placeholder |
| Dirty tracking | per-section `inputs_hash` — ~50% LLM cost cut |

---

## Flows

| Flow | Type | Description |
| ---- | ---- | ----------- |
| [[Flow - Batched Synthesis]] _(stub — flesh out during Phase 2)_ | Background | drain queue → batch (≤~5 min window OR ≤~50 events) → read+context → extract → resolve → emit updates+new pages → coalesce → re-embed → fan out |
| [[Flow - On-Navigation Page Fill]] _(stub — Phase 2/3)_ | User-facing | nav to missing/very-stale page → enqueue high-priority refresh → serve cached row / `_(generating…)_` placeholder |

---

## Data

### Owned Entities

| Entity | Purpose | Key Fields |
|---|---|---|
| (writes into `Page`) | targeted columns | `body`, `aggregations`, `generated_at`, `content_hash`, `stale`, `last_fanout_trigger_hash` |
| (writes into `PageHistory`) | append-on-change snapshot | `body`, `frontmatter`, `aggregations`, `sourceFacts`, narrative-section snapshot |

### Database Tables

| Table | Entity | Notes |
|---|---|---|
| `pages` | `Page` | written by targeted column update only — see [[domains/Pages & Links Core/Pages & Links Core|Pages & Links Core]] |
| `page_history` | `PageHistory` | append-on-change; the Synthesis Layer's `entity_synthesis_history` folded in here ([[ADR-018]]) |
| `new_source_events_queue` | — | consumed (produced by [[domains/Source Ingestion/Source Ingestion|Source Ingestion]]) |

---

## External Dependencies

| Dependency | Purpose | Failure Impact |
|---|---|---|
| LLM API (synthesis + arc generation) | atomic-insight extraction; LLM narrative arcs | workflow fails after retries; queue item stays claimable; `scan_runs.synthesis_failed` count rises — "scan complete" must not lie |
| Temporal | the synthesis workflow runtime | synthesis stalls; pages go stale; surfaces keep serving cached |
| PostgreSQL | `pages` / `page_history` writes | hard down |

---

## Domain Interactions

### Depends On

| Domain | What We Need | How We Access |
|---|---|---|
| [[domains/Source Ingestion/Source Ingestion|Source Ingestion]] | source-event ids + the `source_entities` rows | Event (queue) |
| [[domains/Page Resolution & Decision Queue/Page Resolution & Decision Queue|Page Resolution & Decision Queue]] | resolve(insight) → page \| "new" \| decision-queue suggestion | Direct (per insight) |
| [[domains/Pages & Links Core/Pages & Links Core|Pages & Links Core]] | targeted writes; new-page creation; `page_links` upsert | Direct (repo) |

### Consumed By

| Domain | What They Need | How They Access |
|---|---|---|
| (embedding pipeline, reused) | dirty pages to re-embed | flag on `pages` |
| [[domains/Surfaces/Surfaces|Surfaces]] | rendered `body`, freshness ("synthesized N events ago / M not folded in"), `stale` flag | API |

---

## Key Decisions

| Decision | Summary |
|---|---|
| [[ADR-014]] | deterministic parse + **batched** LLM synthesis routing; reuses the `enrichment.embed` isolated-queue/dispatcher/dedup shape |
| [[ADR-017]] | async synthesis during ingestion + non-blocking on-navigation fill — never an LLM call on the request path |
| [[ADR-018]] | one `pages` table family — `pages.body` = the rendered article; the Synthesis Layer's `entity_synthesis*` tables folded into `pages`/`page_history` |
| [[ADR-013]] | per-kind frontmatter shape in code — the sealed object is the schema, jsonb is the storage |

---

## Technical Debt

| Issue | Impact | Effort |
|---|---|---|
| Synthesis-failure count must be visible somewhere — "scan complete" must not lie | High (critical if unhandled) | Low (`scan_runs` row) |
| Batch window/cap tuning so a busy repo doesn't thrash a hot page | Med | Med |
| Fuzzy-resolution path runs per insight per batch — batch the ANN queries if a batch yields hundreds | Med | Med |
| Engineering synthesis contracts (Domain/Person/Decision/File-rolls-up-to-Domain) + engineering arc kinds still to be authored | — | _(stub — flesh out during Phase 2)_ |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-05-13 | Domain created from the architecture pivot — adapts the Temporal Synthesis Layer for engineering; replaces `EntityProjectionService` / `ProjectionRuleEntity` / `TemplateMaterializationService` | [[architecture-pivot]] |
