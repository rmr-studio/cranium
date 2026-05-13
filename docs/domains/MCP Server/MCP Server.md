---
tags:
  - architecture/domain
  - cranium/v1
Created: 2026-05-13
---
# Domain: MCP Server

---

## Overview

The team-context layer made available to coding agents (Cursor, Claude Code) — a read-only query facade exposed as MCP tools: `who_owns(path)`, `decisions_for(path|domain)`, `recent_changes(domain)`, `why_context(diff)`. It is a **module inside `core/`** (Kotlin/Spring), not a separate TypeScript service — so the OSS self-host stays "one backend + one frontend + postgres + temporal" (no extra process). It reads the `pages` / `page_links` repos plus the compact `pages.body` projection, and reuses auth/RLS directly. v1 = the **read side only** — no MCP-as-router (aggregating GitHub/Linear/Slack MCP servers), no "planning infrastructure". See [[ADR-020]], [[overview]] §Repo↔runtime mapping, and the office-hours design.

---

## Boundaries

### This Domain Owns

- The MCP tool definitions and handlers: `who_owns(path)`, `decisions_for(path|domain)`, `recent_changes(domain)`, `why_context(diff)`.
- The structured-result shapes for each tool.
- The compact `pages.body` projection used by `why_context` and the answer-shaping.
- The "no-page path → empty structured result, **not a 500**" contract.
- Honoring auth/RLS on every query (reuses the existing layer directly — in-process).

### This Domain Does NOT Own

- Any write path — read-only by construction.
- The `pages`/`page_links` schema — that's [[domains/Pages & Links Core/Pages & Links Core|Pages & Links Core]].
- Page resolution / synthesis — it reads what those produced.
- The web graph / reader / PR bot — that's [[domains/Surfaces/Surfaces|Surfaces]].
- MCP-as-router, planning infra — explicitly out of v1.

---

## Sub-Domains

| Component | Purpose | Type |
| --------- | ------- | ---- |
| MCP transport / tool registry | exposes the 4 tools over MCP; lives in `core/` | Module |
| `who_owns(path)` handler | path → File page → owners (blame) + Domain page → owner/SMEs | Service |
| `decisions_for(path\|domain)` handler | path/domain → Domain/File page → linked Decision/ADR pages (status, rationale, superseded-by) | Service |
| `recent_changes(domain)` handler | domain → recent PRs/commits via `page_links JOIN source_entities ... GROUP BY` + windowed aggregations | Service |
| `why_context(diff)` handler | diff → touched files → File/Domain pages → linked Decisions/ADRs + the compact `body` projection (the "why is this different from what we wrote down" answer) | Service |
| Compact `body` projection | ~500-token vs ~3000-token rendering for tool responses | Service |

---

## Flows

| Flow | Type | Description |
| ---- | ---- | ----------- |
| [[Flow - MCP who_owns]] _(stub — flesh out during Phase 2)_ | API | agent calls `who_owns(path)` → resolve File→Domain pages → return owner + SMEs (structured); unknown path → empty result |
| [[Flow - MCP why_context]] _(stub — Phase 2)_ | API | agent calls `why_context(diff)` → touched files → linked Decisions/ADRs + compact body → structured "here's the relevant prior decision" |

---

## Data

### Owned Entities

None — read-only over `pages` / `page_links` / `source_entities` (via `page_links.source_entity_id`).

---

## External Dependencies

| Dependency | Purpose | Failure Impact |
|---|---|---|
| The `pages`/`page_links` repos (in-process) | every query | hard down with the backend |
| Auth/RLS layer (in-process) | per-query scoping | n/a — same process |

---

## Domain Interactions

### Depends On

| Domain | What We Need | How We Access |
|---|---|---|
| [[domains/Pages & Links Core/Pages & Links Core|Pages & Links Core]] | `pages` + `page_links` repos; compact `body` projection | Direct (in-process, `core/`) |
| [[domains/Workspace & Auth/Workspace & Auth|Workspace & Auth]] | auth/RLS context | Direct (in-process) |

### Consumed By

| Domain | What They Need | How They Access |
|---|---|---|
| (coding agents — Cursor, Claude Code) | team-aware retrieval in the IDE | MCP protocol |

---

## Key Decisions

| Decision | Summary |
|---|---|
| [[ADR-020]] | the MCP server is a Kotlin module inside `core/` — not a separate TS service; keeps OSS self-host to one backend + one frontend + postgres + temporal, and keeps Phase 2's Lane B independent of Lane A |
| [[ADR-021]] | GitHub-only v1; MCP = read side only (no router, no planning infra) |

---

## Technical Debt

| Issue | Impact | Effort |
|---|---|---|
| MCP response shape reviewed at the tool-signature level, not pixel-/schema-designed — finalize during Phase 2 | Med | Low |
| The fixture-repo page graph is a test artifact the MCP integration tests depend on (shared with the GitHub-scan + PR-bot E2Es) | Med | Med |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-05-13 | Domain created from the architecture pivot — read-only query facade in `core/` (Phase 2, Lane B) | [[architecture-pivot]] |
