---
tags:
  - architecture/decision
  - adr/accepted
Created: 2026-05-13
Updated: 2026-05-13
---
# ADR-019: Full Architecture Pivot Before Securing a Design Partner — a Deliberate Override of the Wedge-First Recommendation

---

## Context

Cranium is pre-product: 6 design-partner conversations with strong verbal interest, zero who have paid, asked for a pilot, or built around a prototype. Three independent reviews looked at how to get from the generic typed-entity engine to the v1 wiki-over-a-codebase product:

- **Office-hours design doc** — recommended the "thin spine" wedge: build the GitHub-scan + decision-queue + MCP + PR-bot **on the current `entities` layer**, get a design partner, pivot later. ("The assignment is a *person*, not a feature.")
- **Eng review** — recommended the same: wedge-first on the current layer, pivot later.
- **Outside voice (D8, Claude subagent; codex unavailable)** — recommended the same; raised the strategic challenge "do the pivot at all before a design partner."

The founder chose, twice, deliberately: **do the full architecture pivot now, then build.** The clean foundation is judged worth ~5–7 weeks of Claude Code time (≈3–4 months human-equivalent) of non-demoable refactor before anything a design partner sees.

---

## Decision

**Approach A — full architecture pivot now (the `pages`/`page_links`/LLM-synthesis foundation), then build the GitHub-scan + synthesis + surfaces on the clean foundation.** This is recorded as a **deliberate founder override** of the office-hours, eng-review, and outside-voice recommendations — not a gap, not an oversight. The override comes with a **bail criterion** as its safety valve: **if Phase 1 (1a+1b+1c) isn't done-and-green by ~week 3 of Claude Code time, stop and reassess** — that's the signal the foundation-first bet is costing more than the clean-foundation conviction priced in, and the wedge-first path (Approach B) is back on the table. The bail criterion is not a re-litigation of this decision; it's the safety valve the decision should have come with.

---

## Rationale (the founder's, recorded honestly)

- **The clean foundation is worth the cost.** Building the wedge on the current `entities` layer means doing the synthesis-contract work twice (once on the old type system, once after the pivot); the founder judges the ~2 weeks of non-demoable refactor cheaper than that, and worth more than having a demo sooner on a foundation that's about to be torn out.
- **Conviction over consensus, knowingly.** Every advisor said wedge-first; the founder weighed that and chose otherwise. The decision doc captures the disagreement rather than papering over it — see [[architecture-pivot]] §Why and the CEO plan's "strategic note that stays on the record."

### The case against (what the advisors said — recorded so it isn't lost)

- All current evidence is interest, not demand. The team has validated *features* and zero validated *buyers*.
- ~5–7 weeks of Claude Code time spent refactoring with no demoable checkpoint until Phase 2; foundation-first refactors routinely overrun ~2×.
- Worst case: the team finishes with a beautiful foundation and the same zero validated buyers — and may then learn the wedge needs a different shape from the partner it never got.
- Mitigation isn't in the pivot plan; it's the office-hours assignment — convert one of the six conversations into a design partner who commits a real repo + 30 min/week. **Run it in parallel.**

---

## Alternatives Considered

### Option B: Wedge-first — build the GitHub-scan wedge on the current `entities` layer, get a design partner, pivot later

- **Pros:** Demoable in ~10–14 weeks human / ~4–5 weeks CC; a design partner before the foundation-first bet; learns whether the wedge has the right shape before the big refactor.
- **Cons:** The synthesis-contract work gets done twice; ships on a foundation that's about to be torn out.
- **Why rejected:** Founder override — the clean-foundation conviction outweighs the "demo sooner" value, twice.

### Option C: Rename-only hybrid — rename the tables now, defer the deletions and the new pipeline

- **Pros:** A smaller first cut; some cleanup without the full commitment.
- **Cons:** Leaves the type/manifest/projection machinery half-dead ("kept just in case"); violates the discipline gate; doesn't actually de-risk anything.
- **Why rejected:** Half-measures; the discipline gate forbids "kept just in case."

---

## Consequences

### Positive

- A clean foundation for the v1 product; no double-implementation of synthesis contracts.
- The disagreement is on the record; the bail criterion gives the override an explicit escape hatch.

### Negative

- ~5–7 weeks of Claude Code time with no demoable checkpoint until Phase 2; overrun risk ~2×.
- Pre-product team stays pre-product through the refactor unless a design partner is landed in parallel — that mitigation lives in the office-hours assignment, not this plan.

### Neutral

- The mid-pivot checkpoint at the end of Phase 1c is the hard gate; week 3 of CC time is the line.

---

## Implementation Notes

- The bail criterion is enforced at the Phase 1c exit: Phase 1 done-and-green by ~week 3 of CC time, or stop and reassess (Approach B back on the table).
- The parallel mitigation — landing one design partner — is tracked via the office-hours design doc's assignment, not this ADR.

---

## Related

- [[architecture-pivot]]
- [[ADR-011 Reuse Storage Spine, Rebuild Type and Projection Machinery]]
- [[ADR-016 Structural Collapse Before Behavioral Change]]
- Office-hours design doc: `~/.gstack/projects/Cranium/jared-unknown-design-20260512-183238.md`
- CEO plan: `~/.gstack/projects/Cranium/ceo-plans/2026-05-12-architecture-pivot.md`
