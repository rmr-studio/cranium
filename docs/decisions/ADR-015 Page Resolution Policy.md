---
tags:
  - architecture/decision
  - adr/accepted
Created: 2026-05-13
Updated: 2026-05-13
---
# ADR-015: Page Resolution — Deterministic Key Lookup → Fuzzy Candidate-Find + Score → Auto-Link or Decision Queue; Keep the Human Gate

---

## Context

When the synthesis LLM extracts an atomic insight ("this PR touches the payments domain"), the pipeline must resolve "payments domain" to an existing `pages` row or decide it's a new page. Get this wrong loose and the wiki silently grows duplicate pages (`payments` and `payments-v2` meaning the same thing) — and the tech lead's trust burns on first use. The pre-pivot system has identity-match machinery (`FindCandidates → ScoreCandidates → PersistSuggestions`, an isolated Temporal queue, partial-unique-index dedup, no-auto-confirm, identity clusters) built for entity *deduplication*; the pivot needs entity *linking* — same harness, new policy.

---

## Decision

Reuse the identity-match bones; rebuild the policy in three steps:

1. **Deterministic key lookup first** — file path → directory → Domain page; commit-author-email → Person page; ADR file path → Decision page. Keeps the common case off the vector path entirely.
2. **Fuzzy fallback** — name / alias / embedding-similarity candidate-find over `page_embeddings` (HNSW, not pg_trgm) + a scoring model + a calibrated threshold. (Batch the ANN queries — a busy batch can yield hundreds of insights.)
3. **Auto-link above threshold; else queue a suggestion in the `decision_queue`** for a tech lead.

**Keep the human gate** — no silent LLM auto-merge; ambiguous → `decision_queue`. **Decision-page resolution:** ADR-backed → keys on the ADR file path; **no backing file → fuzzy-only, never auto-created, always via the decision queue** (a Decision originating from a Slack thread or a meeting outcome has no canonical key). **The auto-link threshold is configuration, not a code constant.** A page-resolution **eval suite with a ≥85%-precision floor ships before the resolution policy and is a CI gate.** Identity clusters → "page merge candidates". No synthesis-LLM output bypasses the threshold / decision-queue gate.

---

## Rationale

- **Deterministic-first keeps the hot case cheap and exact** — file→Domain and email→Person never hit the vector path; only genuinely fuzzy concepts (free-text from Slack/Notion, ambiguous names) do.
- **Embedding-similarity over HNSW > pg_trgm** for semantic candidate-finding (`auth` / `authentication` / `authn` are far in edit distance, close in embedding space); fine at thousands of pages.
- **The threshold is config because it's calibrated against the eval suite, not guessed** — and an org with a noisier codebase may need a different floor than the cranium dogfood.
- **The eval suite is the guard, not a reviewer's eye** — page resolution is the highest-risk component; a regression silently rots the wiki, so it's a CI gate, not a manual check.
- **The human gate stays because silent auto-merge rots the wiki** — and because the decision queue is the office-hours product surface; routing ambiguous resolutions there gives PMs/tech-leads the "adaptive control, not bypassed" feeling.
- **The trigger model is new** — the input is per-insight LLM output, not an entity-save event; the `@TransactionalEventListener` trigger doesn't apply.

---

## Alternatives Considered

### Option 1: Pure LLM resolution (ask the LLM "is this an existing page? which one?")

- **Pros:** Simplest; one prompt.
- **Cons:** Non-reproducible; prompt-injectable from synth content; no calibratable precision floor; silent auto-merge.
- **Why rejected:** Can't gate it; rots the wiki on a bad call.

### Option 2: Deterministic-only (no fuzzy fallback, everything else → "new page")

- **Pros:** Fully reproducible; no eval suite needed.
- **Cons:** Every alias / free-text concept becomes a duplicate page; the wiki fragments.
- **Why rejected:** The common-but-not-keyable case (aliases, Slack/Notion free text) is exactly where a wiki needs resolution.

### Option 3: Auto-link on any candidate match, no threshold, no queue

- **Pros:** Fastest path; no human in the loop.
- **Cons:** Weak-score auto-links create silent duplicates; the failure-modes analysis flags this as the precision trap.
- **Why rejected:** The threshold + the eval suite + the decision queue are the whole point.

---

## Consequences

### Positive

- Common case (file/email/ADR-path) is exact and cheap; only genuinely ambiguous concepts cost a fuzzy lookup.
- Precision is measured and CI-gated, not asserted.
- Ambiguous resolutions surface in the decision queue — a feature (the PM/tech-lead control surface), not just a safety net.

### Negative

- The eval suite (the golden set of `concept → expected page | "new"`) is itself a test artifact to build, before the policy.
- The fuzzy path runs per-insight per-batch — ANN queries must be batched or a big batch hammers the index.
- Decisions without a backing file are never auto-created — a small ongoing human-triage cost, accepted as the trust price.

### Neutral

- Reuses the identity-match Temporal shell + ShedLock/`SKIP LOCKED` dispatcher + the dedup index + the suggestion-row upsert (~100–150 lines) verbatim; only the matching policy is new.

---

## Implementation Notes

- Eval-suite cases: exact key match (file→Domain), alias match (`auth`/`authentication`/`authn`), embedding-only match, true-new (no candidate), ambiguous (two plausible candidates → MUST hit `decision_queue`, MUST NOT auto-link).
- Phase 1c renames the identity-match suggestion/cluster tables → `page_resolution_suggestions` / `page_merge_candidates` (logic unchanged there); Phase 2 rewrites the policy.

---

## Related

- [[architecture-pivot]]
- [[ADR-014 Deterministic Source-Entity Creation plus Batched LLM Synthesis]]
- [[ADR-018 One pages Table Family for Synthesis Storage]]
- [[ADR-009 Unique Index Deduplication over Mapping Table]]
