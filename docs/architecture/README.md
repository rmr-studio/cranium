---
type: resource
Created: 2026-05-13
Updated: 2026-05-13
tags:
  - architecture/index
---
# `docs/architecture/`

The architecture of **Cranium v1** — "the open team-context layer for AI-amplified engineering teams". This directory replaces the stale "6-layer stack" framing; see [[architecture-pivot]] (2026-05-13) for the pivot record and the locked decisions [[ADR-011]]–[[ADR-021]].

## In this directory

| Doc | What it covers |
|---|---|
| [[overview]] | The v1 layer model (Source → Synthesis → Surfaces), the `pages` family data model + indexes, page kinds at a glance, the locked invariants, the reuse/rework/replace/delete table, and the repo↔runtime mapping. |
| [[page-kinds]] | Deeper per-kind detail: `DomainPage`, `PersonPage`, `FilePage`, `DecisionPage`, `ADRPage`, `SystemPage`, `FlowPage` — frontmatter shape, synthesis contract, `narrativeGenerator`, `bodyTokenBudget`, helpers, resolution. Plus how to add a kind. |

## Elsewhere in `docs/`

- [[architecture-pivot]] (`../architecture-pivot.md`) — the pivot plan, phasing, and locked decisions.
- `../domains/` — one doc per v1 domain ([[domains/Pages & Links Core/Pages & Links Core|Pages & Links Core]], [[domains/Source Ingestion/Source Ingestion|Source Ingestion]], [[domains/Synthesis/Synthesis|Synthesis]], [[domains/Page Resolution & Decision Queue/Page Resolution & Decision Queue|Page Resolution & Decision Queue]], [[domains/Surfaces/Surfaces|Surfaces]], [[domains/MCP Server/MCP Server|MCP Server]], [[domains/OSS Packaging & Self-Host/OSS Packaging & Self-Host|OSS Packaging & Self-Host]], [[domains/Workspace & Auth/Workspace & Auth|Workspace & Auth]]); `../domains/_archived.md` lists what's out of v1 scope.
- `../flows/` — end-to-end flow docs.
- `../designs/` — feature designs.
- `../decisions/` — ADRs (ADR-001…010 are pre-pivot; ADR-011…021 record the pivot).
