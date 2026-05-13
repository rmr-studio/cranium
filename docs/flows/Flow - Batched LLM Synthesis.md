---
tags:
  - flow/planned
  - flow/background
  - architecture/flow
Created: 2026-05-13
Updated: 2026-05-13
Critical: true
Domains:
  - "[[Domain - Synthesis]]"
---
# Flow: Batched LLM Synthesis

_(stub — Phase 2; design pass during Phase 2 — "Synthesis Prompt + Insight Extraction" and "Synthesis Temporal Workflow & Fan-out" feature designs)_

> See [[architecture-pivot]] for the canonical spec. This is Stage 2 of the Phase 2 pipeline — the LLM step that turns `source_entities` into rendered `pages`. Reuses the `enrichment.embed` isolated-queue / ShedLock+SKIP-LOCKED dispatcher / workflow-ID-dedup pattern and the Temporal Synthesis Layer machinery.

---

## Overview

A ShedLock + `SKIP LOCKED` dispatcher drains `new_source_events_queue`, **coalesces** the queued `source_entity` ids by likely-affected page, and kicks off a Temporal workflow per batch. The workflow reads the batch + context, extracts atomic insights, runs each insight through [[Flow - Page Resolution and Decision Queue]], emits targeted page updates (`body`, `aggregations`, `generated_at`, `content_hash`) + new pages + links, coalesces by affected page (10 PRs touching one Domain → one Domain-page rewrite, not 10), and marks touched pages for re-embed (the existing embedding pipeline picks them up). Batch shape: time-windowed (~5 min) **or** size-capped (~50 source-events), whichever first.

---

## Trigger

| Trigger Type | Source | Condition |
|---|---|---|
| Schedule (dispatcher) | ShedLock'd poller over `new_source_events_queue` | unclaimed `source_entity` ids present; batch window elapsed or size cap hit |

**Entry Point:** `SynthesisDispatcher` → `SynthesisWorkflow` (Temporal, isolated task queue; sibling `ConsumerActivity` to embedding).

---

## Flow Steps (what runs end-to-end)

`drain new_source_events_queue (ShedLock + SKIP LOCKED, dedup) → batch + coalesce by likely affected page → Temporal workflow: read source_entities + context → extract atomic insights → per-insight page resolution → emit page updates (frontmatter, prose, links) + new pages → coalesce by affected page → mark touched pages for re-embed.`

1. **Drain + dedup** (`SynthesisDispatcher`) — `SKIP LOCKED` claim, ShedLock-guarded; workflow-ID dedup so a replayed/duplicated `source_entity` doesn't double-process.
2. **Batch + coalesce** — group claimed `source_entity` ids by likely-affected page (a busy Domain's PRs land in one batch, not N batches — the perf note: don't thrash a hot page).
3. **Read batch + context** (workflow activity) — pull the `source_entities` + their deterministic links + the current state of the candidate-affected `pages` (the compact projection).
4. **Extract atomic insights** — LLM extracts atomic claims/changes from the batch (one concept = one insight).
5. **Page resolution per insight** — hand each insight to [[Flow - Page Resolution and Decision Queue]]: deterministic key → fuzzy candidate-find + score → auto-link above threshold OR a `decision_queue` suggestion. **No synthesis-LLM output bypasses this gate** (prompt-injection-on-synth-content guard).
6. **Emit page updates + new pages** — for each affected page: rewrite `body`, recompute `aggregations` (per-kind `synthesisContract`: aggregations + windows + narrative sections + `narrativeGenerator: TEMPLATED|LLM` + `bodyTokenBudget`), update frontmatter (DERIVED pages only), add/update `page_links`. **Invariant: targeted column updates only** (`body`, `aggregations`, `generated_at`, `content_hash`) — never a whole-row replace; never touches user-editable columns (`frontmatter` on USER pages, `title`, `slug`).
7. **Coalesce by affected page** — N source-entities touching one page → one page rewrite + one history append (`page_history`).
8. **Mark touched pages for re-embed** — set the stale/re-embed flag; the existing `page_embeddings` pipeline (`EnrichmentWorkflow`) picks it up; stale-`CLAIMED` recovery still works.
9. **Fan-out** — re-synthesizing a PR's `source_entity` fans out to the Domain hub(s) it touches + the Person hub (author) + any Decision hub the touched files reference; content-hash cycle-break (`last_fanout_trigger_hash`) prevents A→B→A; cluster-fan-out priority < direct-trigger priority (sheds load under burst).

---

## Data Touched

- **Writes:** `pages` (targeted column updates: `body`, `aggregations`, `generated_at`, `content_hash`, `stale`; new pages on insert), `page_history` (append-on-change), `page_links` (synthesis-derived links with `source_entity_id`), `decision_queue` (SME-assign / promote-to-Decision / stale-ADR / ambiguous-resolution), re-embed flag on `pages` / enqueue onto the embed queue.
- **Reads:** `new_source_events_queue`, `source_entities`, `pages` (compact projection), `page_links`, `page_embeddings` (for the resolution candidate-find).
- **Does not touch:** `source_entities` (read-only here), `scan_runs` except a `synthesis_failed` count bump on workflow failure.

---

## Failure Modes

| Codepath | Failure | Detection | User-visible | Recovery |
|---|---|---|---|---|
| Per `source_entity` in a batch | one malformed/odd source throws mid-batch | per-source-entity try/catch isolation — one bad source doesn't abort the batch | counted; `decision_queue` entry if it's an ambiguous-resolution case | the rest of the batch completes; the bad one is retried next batch |
| Synthesis workflow | Temporal activity exhausts retries (LLM API down) | workflow fails after retries; the queue item stays claimable | **`scan_runs.synthesis_failed` count bumped — "scan complete" must not lie** | re-run the (idempotent) batch |
| Idempotent re-run | the same batch processed twice (replay) | deterministic workflow ID + `source_external_id` dedup; targeted column updates are content-hash-guarded | none | re-run is a no-op if `content_hash` unchanged |
| Resolution gate bypass attempt | synthesis-LLM output tries to auto-merge below threshold | the threshold gate is mandatory on every insight | tech lead sees a `decision_queue` suggestion, not a silent merge | n/a — by construction |
| Lost-update | synthesis writer races a user edit on a USER page | synthesis never writes `frontmatter` on USER pages / `title` / `slug` | none — user edits are off-limits to the writer | n/a — by construction (D10 lost-update guard) |

---

## Test Bar

- **Synthesis Temporal workflow** — batch coalescing (10 PRs to one Domain → 1 page rewrite, not 10); idempotent re-run of the same batch (no-op on unchanged `content_hash`); per-source-entity try/catch isolation (one bad source doesn't abort the batch); `decision_queue` entries created for SME-assign / promote-to-Decision / stale-ADR / ambiguous-resolution.
- **GitHub scan → pages E2E** (the downstream half of [[Flow - GitHub Repo Scan]]'s E2E) — point at the **fixture repo**, drain the workflow, assert the expected Domain/Person/File/ADR pages + links exist.
- **Re-embedding** — a page update marks the page for re-embed; the embedding pipeline picks it up; stale `CLAIMED` recovery still works.

---

## Related

- [[architecture-pivot]] — canonical spec; Phase 2 ASCII pipeline diagram; the Temporal Synthesis Layer reconciliation
- [[Flow - GitHub Repo Scan]] — Stage 1, produces the `source_entities` + `new_source_events_queue` ids
- [[Flow - Page Resolution and Decision Queue]] — step 5, the per-insight resolution policy
- [[Domain - Synthesis]]
