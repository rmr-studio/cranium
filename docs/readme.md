# Cranium — `docs/`

The canonical home for architecture, decisions, design docs, and flows. (Roadmap / phase tracker / GTM / brand live in Notion; synthesized insight + decision rationale live in the `~/Documents/wiki/Cranium` Obsidian wiki. Every new architecture/ADR/design/flow doc goes here, in the repo.)

Best viewed in the Obsidian client.

## Start here

**[[architecture-pivot]]** — the canonical record of the 2026-05 architecture pivot: the rescope (generic typed-entity engine → an LLM-synthesized wiki over an engineering codebase), the v1 product (point Cranium at a GitHub org → a living domain/ownership/SME + ADR/decision map, delivered as a web graph + an MCP server + a non-blocking PR comment, with a decision queue as the spine), the target `pages` data model, the 7 page kinds, the Reuse/Rework/Replace/Delete table, the migration sequence (preconditions → Phase 1a/1b/1c/1d → Phase 2 → Phase 3 → Phase 4), the locked decisions, and the open questions.

## Tree

| Path | Contents |
|---|---|
| `architecture-pivot.md` | The pivot spec — read this first. |
| `architecture/` | Architecture docs (system overviews, component maps). *Populated by other agents — referenced here, not created here.* |
| `domains/` | Per-domain docs. *Populated by other agents.* |
| `flows/` | End-to-end flow docs (event → … → outcome). *Populated by other agents.* |
| `designs/` | Feature / page design docs. *Populated by other agents.* |
| `decisions/` | ADRs — see [[decisions/README]] for the index. ADR-011 … ADR-021 are the pivot decisions; ADR-001 … ADR-010 are pre-pivot (status updated by the teardown). **`docs/decisions/*.md` is itself a Phase-2 ingestion source — Cranium dogfoods on this repo.** |
| `templates/` | Doc templates (ADR, feature design, page design, …). Never write content here. |

Other top-level files in `docs/` (`architecture-changelog.md`, `architecture-suggestions.md`, `blog-publishing-guide.md`) predate the pivot and are out of scope for this migration.
