---
tags:
  - flow/planned
  - flow/background
  - architecture/flow
Created: 2026-05-13
Updated: 2026-05-13
Critical: true
Domains:
  - "[[Domain - Page Resolution]]"
---
# Flow: Page Resolution and Decision Queue

_(stub — Phase 2; design pass during Phase 2 — "Page Resolution Policy + Eval Suite" feature design. Build the eval suite FIRST; it ships before the policy and is a CI gate.)_

> See [[architecture-pivot]] for the canonical spec. Highest-risk component in Phase 2. Reuses the identity-match harness (`FindCandidates → ScoreCandidates → PersistSuggestions`, isolated queue, partial-unique-index dedup, identity-clusters → page-merge-candidates, stale-`CLAIMED` recovery) with a **new policy**.

---

## Overview

Given one LLM-extracted insight (a concept + the source context), decide which `pages` row it maps to — or "new" — without ever silently auto-merging. Order: (1) deterministic key lookup first — file→directory→Domain page; commit-author-email→Person page; (2) fuzzy fallback — name/alias/embedding-similarity candidate-find over `page_embeddings` (HNSW) + a scoring model; (3) auto-link above the threshold (the threshold is **config, not a constant**), else queue a suggestion in `decision_queue` for a tech lead. **Decision-page rule:** ADR-backed → keys on the ADR file path; no backing file → fuzzy-only, never auto-created, always via the queue. Ambiguous (two plausible candidates) → `decision_queue`, never auto-link.

---

## Trigger

| Trigger Type | Source | Condition |
|---|---|---|
| In-process call | [[Flow - Batched LLM Synthesis]] step 5 | one LLM-extracted insight per call, per batch — the input is per-insight LLM output, **not** an entity-save event (the `@TransactionalEventListener` trigger does not apply here) |

**Entry Point:** `PageResolutionService` → `FindCandidates → ScoreCandidates → PersistSuggestions` (the reused identity-match harness shell).

---

## Flow Steps (what runs end-to-end)

`per insight: deterministic key lookup first → if no hit, fuzzy candidate-find (name/alias/embedding-similarity) + score → if score ≥ threshold (config) auto-link → else a decision_queue suggestion for a tech lead.`

1. **Deterministic key lookup** — file path → its directory → the Domain page for that directory; commit-author-email → the Person page for that email. Hit → resolve, done (no vector path — the common case stays off ANN entirely).
2. **Fuzzy candidate-find** (`FindCandidates`) — no deterministic hit → name/alias match (`auth` / `authentication` / `authn`) + embedding-similarity ANN over `page_embeddings` (HNSW); batch the ANN queries when a batch yields many insights (the perf note).
3. **Score candidates** (`ScoreCandidates`) — scoring model over name/alias/embedding features → a confidence score per candidate.
4. **Decide:**
   - score ≥ threshold (config) **and** one clear winner → **auto-link** the insight to that page.
   - below threshold, or two plausible candidates, or a Decision-page with no backing ADR file → **queue a `decision_queue` suggestion** (via `page_resolution_suggestions`, partial-unique-index dedup) for a tech lead to approve/reject. **Never auto-link below threshold; never silent LLM auto-merge.**
   - no candidate at all → emit a **new page** of the inferred kind (except Decision pages, which are never auto-created).
5. **Identity clusters → page-merge-candidates** — when resolution surfaces that two existing pages are probably the same thing, write a `page_merge_candidates` row (reused identity-cluster shape); a human decides the merge.
6. **Stale-`CLAIMED` recovery** — the harness's existing recovery for suggestion rows stuck `CLAIMED` carries over unchanged.

---

## Data Touched

- **Writes:** `page_resolution_suggestions` (suggestion rows; feeds `decision_queue`), `decision_queue` (ambiguous-resolution + the deterministic stale-ADR suggestion E5), `page_merge_candidates` (identity clusters), `page_links` (on auto-link), `pages` (insert on "new", non-Decision only).
- **Reads:** `pages` (deterministic key lookup), `page_embeddings` (HNSW candidate-find), `source_entities` (the insight's context), config (the auto-link threshold).

---

## Failure Modes

| Codepath | Failure | Detection | User-visible | Recovery |
|---|---|---|---|---|
| Auto-link decision | LLM proposes `payments-v2` meaning the existing `payments` → auto-link on a weak score | the eval suite's ambiguous case is the guard; the threshold gate | if the threshold is too loose → a silent duplicate page | **the eval suite IS the guard** — ≥85% precision CI gate before the policy ships |
| Decision-page resolution | a Decision insight with no backing ADR file gets auto-created | the policy: no backing file → fuzzy-only, never auto-created | tech lead sees a `decision_queue` "promote to Decision" suggestion | n/a — by construction |
| Ambiguous resolution | two plausible candidates → which one? | scoring surfaces ≥2 near-tied candidates | **must hit `decision_queue`; must NOT auto-link** | tech lead picks the right page |
| Suggestion dedup | the same insight resolved twice across batch re-runs | partial-unique-index on `page_resolution_suggestions` | none | upsert is a no-op |
| Stuck claim | a suggestion row stuck `CLAIMED` (worker died) | the harness's stale-`CLAIMED` recovery | none | recovered automatically |

---

## Test Bar

- **Page-resolution EVAL suite** — a golden set of (LLM-extracted concept → expected page | "new"). Cases: **exact key match** (file→Domain); **alias match** (`auth` / `authentication` / `authn`); **embedding-only match**; **true-new** (no candidate); **ambiguous** (two plausible candidates → MUST hit `decision_queue`, MUST NOT auto-link). Track precision; **≥85% precision CI gate**; the auto-link threshold is config not a constant; **the eval suite ships before the policy.**

---

## Related

- [[architecture-pivot]] — canonical spec; D5 (page resolution policy); the identity-match reuse note
- [[Flow - Batched LLM Synthesis]] — the caller (step 5, per insight)
- [[Flow - GitHub Repo Scan]] — the deterministic file→Domain / email→Person links it leans on
- [[Domain - Page Resolution]]
