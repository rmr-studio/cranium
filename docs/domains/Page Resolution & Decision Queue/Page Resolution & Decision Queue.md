---
tags:
  - architecture/domain
  - cranium/v1
Created: 2026-05-13
---
# Domain: Page Resolution & Decision Queue

---

## Overview

Decides, for every LLM-extracted insight, *which page it's about* — and keeps a human in the loop when it isn't sure. Deterministic key lookup first (file→directory→Domain page; commit-author-email→Person page). Fuzzy fallback when the key misses (name/alias/embedding-similarity candidate-find + a scoring model). Auto-link above a **configurable** threshold; below it, a suggestion in the `decision_queue` for a tech lead. Reuses the identity-match harness — `FindCandidates → ScoreCandidates → PersistSuggestions`, isolated queue, dedup, no auto-confirm; identity clusters → page merge candidates — with a new policy. The `decision_queue` is the office-hours "spine": every derived signal lands here. The ≥85%-precision eval suite ships *before* the policy and is a **CI gate**. See [[ADR-015]], [[overview]] §Locked invariants, and the office-hours design.

---

## Boundaries

### This Domain Owns

- The page-resolution policy: deterministic key lookup → fuzzy candidate-find + score → auto-link / queue.
- The configurable auto-link threshold and the ≥85%-precision eval suite (golden set: extracted concept → expected page | "new") wired as a CI gate.
- The Decision-page resolution rule: ADR-backed → keys on the ADR file path; no backing file → fuzzy-only, **never auto-created**, **always** via the decision queue.
- `page_resolution_suggestions` (reuses the identity-match suggestion-row shape) and `page_merge_candidates` (reuses the identity-cluster shape).
- The `decision_queue` and its v1 suggestion types: `promote-to-Decision`, `assign/confirm-SME`, `stale-ADR` (incl. the deterministic "stale ADR" signal, E5), `ambiguous-resolution`.
- The reused identity-match Temporal harness with the rewritten policy (the harness "bones" = the workflow shell + ShedLock/SKIP-LOCKED dispatcher + the dedup index + the suggestion-row upsert; the *policy* is the new part).

### This Domain Does NOT Own

- Extracting insights or generating prose — that's [[domains/Synthesis/Synthesis|Synthesis]] (which *calls* resolution per insight).
- Creating the deterministic links at scan time — that's [[domains/Source Ingestion/Source Ingestion|Source Ingestion]] (this domain owns the *fuzzy* path and the *policy* around the deterministic one).
- The decision-queue *UI* — that's [[domains/Surfaces/Surfaces|Surfaces]] (the inbox view; this domain owns the queue data + the suggestion types).

---

## Sub-Domains

| Component | Purpose | Type |
| --------- | ------- | ---- |
| Deterministic key resolver | file→dir→Domain page; commit-author-email→Person page; ADR path→Decision/ADR page | Service |
| Fuzzy candidate-find | name/alias match + embedding-similarity over `page_embeddings` (HNSW, not pg_trgm); batched ANN | Service |
| Scoring model + threshold gate | score candidates; auto-link above config threshold, else queue | Service |
| Eval suite (CI gate) | golden set; ≥85% precision floor; ships before the policy | Test artifact |
| Identity-match harness (reused) | `FindCandidates → ScoreCandidates → PersistSuggestions`; isolated queue; dedup; no auto-confirm | Temporal workflow |
| `decision_queue` service | suggestion CRUD; approve/reject; v1 suggestion types | Service |
| Page-merge-candidate handler | identity clusters → merge candidates (never silent auto-merge) | Service |

---

## Flows

| Flow | Type | Description |
| ---- | ---- | ----------- |
| [[Flow - Resolve Insight to Page]] _(stub — flesh out during Phase 2)_ | Background | per insight: deterministic key → fuzzy candidate-find + score → auto-link OR `decision_queue` suggestion |
| [[Flow - Decision Queue Triage]] _(stub — Phase 3)_ | User-facing | tech lead approves/rejects a suggestion; approval applies the link / promotes the Decision / confirms the SME |
| [[Flow - Page Merge Candidate]] _(stub — Phase 2)_ | Background | identity cluster forms → merge candidate row → human confirms (never silent) |

---

## Data

### Owned Entities

| Entity | Purpose | Key Fields |
|---|---|---|
| `PageResolutionSuggestion` | a fuzzy-resolution candidate awaiting a decision | `extracted_concept`, `candidate_page_id`, `score`, `status` |
| `PageMergeCandidate` | two pages that may be the same thing | `page_a_id`, `page_b_id`, `cluster_id`, `status` |
| `DecisionQueueItem` | the office-hours spine | `suggestion_type` (`promote-to-Decision` \| `assign/confirm-SME` \| `stale-ADR` \| `ambiguous-resolution`), `provenance`, `status`, `actor` |

### Database Tables

| Table | Entity | Notes |
|---|---|---|
| `page_resolution_suggestions` | `PageResolutionSuggestion` | renamed from the identity-match suggestion queue (Phase 1c); partial-unique-index dedup |
| `page_merge_candidates` | `PageMergeCandidate` | renamed from identity clusters |
| `decision_queue` | `DecisionQueueItem` | new in Phase 2; the unifying primitive across engineer + PM surfaces |

---

## External Dependencies

| Dependency | Purpose | Failure Impact |
|---|---|---|
| `page_embeddings` (HNSW) | fuzzy candidate-find | fuzzy path degrades to name/alias only; deterministic path unaffected |
| Temporal | the identity-match harness runtime | resolution backlog grows; nothing auto-merges |
| LLM (extraction is upstream) | — | n/a here |

---

## Domain Interactions

### Depends On

| Domain | What We Need | How We Access |
|---|---|---|
| [[domains/Pages & Links Core/Pages & Links Core|Pages & Links Core]] | key lookups; candidate-find over `page_embeddings`; create `page_links` on auto-link | Direct (repo) |
| [[domains/Source Ingestion/Source Ingestion|Source Ingestion]] | the deterministic key links it already created (resolution layers on top) | Direct |

### Consumed By

| Domain | What They Need | How They Access |
|---|---|---|
| [[domains/Synthesis/Synthesis|Synthesis]] | resolve(insight) → page \| "new" \| queued | Direct (per insight) |
| [[domains/Surfaces/Surfaces|Surfaces]] | the `decision_queue` items (the inbox); approve/reject actions | API |

---

## Key Decisions

| Decision | Summary |
|---|---|
| [[ADR-015]] | page resolution — reuse the identity-match bones, rebuild the policy; deterministic key → fuzzy → auto-link/queue; keep the human gate; Decision pages are fuzzy-only and never auto-created |
| [[ADR-014]] | the trigger model is per-insight LLM output, not an entity-save event — the `@TransactionalEventListener` trigger doesn't apply |
| [[ADR-016]] | phasing — the eval suite ships before the policy; both are Phase 2 |

---

## Technical Debt

| Issue | Impact | Effort |
|---|---|---|
| If the threshold is too loose → silent duplicate pages — **the eval suite IS the guard** | High | Med (the eval suite is the work) |
| Decision-page canonical identity — "no canonical key, always fuzzy for Decisions" — confirm before building the policy | Med | Low |
| Fuzzy path runs per insight per batch — batch the small ANN queries | Med | Med |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-05-13 | Domain created from the architecture pivot — reuses the identity-match harness with a new entity-linking policy; introduces the `decision_queue` | [[architecture-pivot]] |
