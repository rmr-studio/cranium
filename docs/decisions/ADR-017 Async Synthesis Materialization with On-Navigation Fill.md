---
tags:
  - architecture/decision
  - adr/accepted
Created: 2026-05-13
Updated: 2026-05-13
---
# ADR-017: Synthesis Pages Materialized Async During Ingestion + a Non-Blocking On-Navigation Fill

---

## Context

A `pages` row's `body` is an LLM-synthesized article (~1–2s to generate, more for an LLM-narrative kind). When does it get generated? Two failure shapes: pure-async means a cold page (new repo, never synthesized) is empty on first navigation — the office-hours cold-start concern; generate-on-navigation puts a multi-second LLM call on the request hot path — which the Temporal Synthesis Layer design explicitly forbids.

---

## Decision

**Both.** Synthesis pages are **materialized async during ingestion** — the Temporal Synthesis Layer's sibling-`ConsumerActivity` model (sibling to embedding), with a `stale` flag + a backlog dashboard. **Plus a non-blocking on-navigation fill:** navigating to a missing or very-stale page enqueues a high-priority refresh and serves the cached row (or a "generating…" placeholder) immediately. **The LLM call never sits on the request path.** Cold-start is covered because navigation can enqueue.

---

## Rationale

- **Async-during-ingestion is the steady state** — every PR/commit/file fans out to re-synth the pages it touches; most pages are already fresh when someone navigates to them.
- **The on-navigation fill closes the cold-start hole** — a never-synthesized page enqueues a high-priority job on first view; the user sees a placeholder, not a blank page, and the page fills in seconds.
- **No LLM on the request path** — the request always reads the cached `pages.body` (or a placeholder); the generation happens off-path in Temporal. This is the Synthesis Layer's `stale`-flag + reanalyze pattern, reused.
- **`pages.confidence` + the freshness indicator ("synthesized N source-events ago / M not yet folded in") are the trust mechanism** — they're load-bearing, not Phase-3.1-cuttable; an LLM-maintained wiki that confidently shows wrong ownership burns trust on first use.

---

## Alternatives Considered

### Option 1: Pure async (only ingestion-triggered synthesis)

- **Pros:** Simplest; no request-path logic at all.
- **Cons:** Cold pages (new repo, no synthesis yet) are empty on first navigation — the office-hours cold-start concern.
- **Why rejected:** A blank page on first view is a bad first impression for an LLM-wiki product.

### Option 2: Generate-on-navigation (synthesize when the page is requested)

- **Pros:** Always-fresh on view.
- **Cons:** A multi-second LLM call on the hot path; the Synthesis Layer design forbids it.
- **Why rejected:** Latency on the request path is unacceptable.

---

## Consequences

### Positive

- Steady-state pages are fresh; cold pages fill within seconds of first view; the request path never blocks on an LLM.
- Rides the Synthesis Layer's already-specified `stale` flag + backlog dashboard + reanalyze pattern — no new mechanism.

### Negative

- The "generating…" placeholder is a real UI state to design (Phase 3); the freshness indicator + `pages.confidence` de-emphasis must ship with the surfaces, not get descoped.
- A burst of navigations to cold pages enqueues a burst of high-priority jobs — identity-cluster fan-out priority sits below direct-trigger priority to shed load.

### Neutral

- "Very stale" is a freshness threshold (config), not a constant — tunable per deployment.

---

## Implementation Notes

- Navigation reads the cached `pages.body`; if missing or older than the freshness threshold, enqueue a high-priority refresh on `new_source_events_queue` (or a dedicated refresh path) and return the cached row / placeholder.
- The web graph's freshness indicator reads "synthesized N events ago / M not yet folded in" off `pages.generated_at` + the unfolded-events count; `pages.confidence` drives visual de-emphasis + decision-queue ordering.

---

## Related

- [[architecture-pivot]]
- [[ADR-014 Deterministic Source-Entity Creation plus Batched LLM Synthesis]]
- [[ADR-018 One pages Table Family for Synthesis Storage]]
