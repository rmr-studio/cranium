---
tags:
  - architecture/decision
  - adr/accepted
Created: 2026-05-13
Updated: 2026-05-13
---
# ADR-016: Structural Collapse Before Behavioral Change — the Phasing Rule

---

## Context

The pivot is large: rename three core tables, collapse the relationship-definition machinery, rework the classification taxonomy, delete the entity-type/manifest/projection/sync engines, collapse a chunk of `apps/client`, then build a brand-new GitHub-scan + LLM-synthesis pipeline. Doing structural change and behavioral change in one move makes the blast radius unbounded and the rollback impossible to reason about — and the team has done a botched native-SQL rename before (the `target_entity_id`→`target_id` scar). The outside-voice pass (D8) explicitly judged "not sound to start Phase 1 as drafted" and recommended splitting it.

---

## Decision

**Never structural + behavioral in one move.** Phase 1 = structural collapse on the existing behavior; Phase 2 = the new pipeline on the clean foundation. Phase 1 is itself split:

- **1a — pure rename** (`entities`→`pages`, `entity_attributes`→`pages.frontmatter` jsonb, `entity_relationships`→`page_links`; introduce the `page_kind` enum + sealed `Page` hierarchy + the new schema columns). No behavior change; suite stays green except tests of about-to-be-deleted machinery (don't delete those yet). Grep **every** native SQL query — a checklist, not a parenthetical.
- **1b — relationship collapse** (behavior change — backlinks move from auto-inverse-REFERENCE rows to reverse queries). **GATE:** a backlink-parity IT against the 6-case `EnrichmentBacklinkMatrixIT` matrix must pass before merge. Then delete the relationship-definition machinery + the now-dead tests.
- **1c — taxonomy rework + the forced re-embed** (behavior change + migration; kept monolithic). Pre-req: the entity-connotation-pipeline merged to `main` + a dev-DB snapshot-restore dry-run. Then the type/manifest/projection/sync deletes; rename the identity-match tables.
- **1d — `apps/client` collapse** (parallel worktree alongside 1b/1c — disjoint frontend vs backend): rename the entity reader/list/settings routes → page reader/list/settings; delete the screens whose backend dies in 1c. Keep auth + the dashboard shell.

Each sub-phase: snapshot the dev DB first; keep a down-migration (renames are reversible — deletes are not, so deletes are the *last* thing in each sub-phase, after everything else is green); re-run the native-SQL grep. **Phase 1 positive exit assertion:** the `knowledge-entity-graduation` flows (create page → attach frontmatter → link two pages → query backlinks) work against the renamed schema. **Bail criterion:** if Phase 1 (1a+1b+1c) isn't done-and-green by ~week 3 of Claude Code time, stop and reassess.

---

## Rationale

- **Mechanical-then-behavioral keeps each step reviewable** — a rename diff is auditable; a rename-plus-rewrite diff is archaeology.
- **The gates catch the two non-mechanical moves** — the backlink rework (1b) and the taxonomy/re-embed migration (1c) are where data integrity can break; each gets an explicit gate/pre-req.
- **Deletes last** — the renames are reversible, the deletes aren't; keeping deletes at the end of each sub-phase means a botched step is recoverable.
- **The bail criterion is the safety valve on D9** — a foundation-first refactor for a pre-product team can overrun; if Phase 1 blows past ~week 3, that's the signal to revisit the wedge-first path.

---

## Alternatives Considered

### Option 1: One big Phase 1 (rename + collapse + rework + delete together)

- **Pros:** Fewer commits; "rip the band-aid".
- **Cons:** Unbounded blast radius; rollback impossible to reason about; the outside-voice pass judged it unsound.
- **Why rejected:** D8 correction — split it.

### Option 2: Split Phase 1c too (1c-a gated migration / 1c-b mechanical deletes)

- **Pros:** Honors the "never structural + behavioral in one move" rule even within 1c.
- **Cons:** More sub-phases; the founder preferred fewer.
- **Why rejected:** Founder override (R3); the entangled-rollback tradeoff accepted, so the pre-1c snapshot + dry-run are load-bearing.

---

## Consequences

### Positive

- Each sub-phase is small, gated where it needs to be, and rollback-able.
- The bail criterion gives the D9 override an explicit escape hatch.

### Negative

- More sub-phases ⇒ more coordination; 1c stays monolithic, so a botched re-embed and a half-done deletion sweep share one rollback unit (mitigated by the pre-1c snapshot + restore dry-run).
- Phase 1 is ~2 weeks of pure deletion/rename with no demoable value before Phase 2.

### Neutral

- The native-SQL grep is a recurring check across 1a/1b/1c, not a one-time pass.

---

## Implementation Notes

- Hard preconditions for *all* of Phase 1: enrichment-service PR2 Phase 3 merged to `main`; the entity-connotation-pipeline merged to `main` (the 1c-specific one). Separate worktrees; do not let Phase 1 touch a table name until both are in `main`.
- 1b gate: backlink-parity vs the 6-case matrix. 1c pre-req: connotation-pipeline merged + dev-DB snapshot-restore dry-run.

---

## Related

- [[architecture-pivot]]
- [[ADR-011 Reuse Storage Spine, Rebuild Type and Projection Machinery]]
- [[ADR-012 Single page_links Edge Table with In-Code Label Enum]]
- [[ADR-019 Full Pivot Before a Design Partner]]
