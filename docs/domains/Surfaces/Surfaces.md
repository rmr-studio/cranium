---
tags:
  - architecture/domain
  - cranium/v1
Created: 2026-05-13
---
# Domain: Surfaces

---

## Overview

The three ways a team consumes the wiki: the **web graph** (understand the system), the **page reader** (read an article + its backlinks), and the **PR bot** (a non-blocking comment at the moment that matters). The decision queue (from [[domains/Page Resolution & Decision Queue/Page Resolution & Decision Queue|Page Resolution & Decision Queue]]) lives alongside the graph. (The MCP server — the fourth surface — is its own domain: [[domains/MCP Server/MCP Server|MCP Server]].) Almost all of the *visual* design here is deferred: **"Phase 3 design pass for Cranium web surfaces"** is a tracked TODO; unsettled UI bits below are marked `_(stub — Phase 3 design pass)_`. The aesthetic system (Geist, oklch zero-chroma, 56px dark icon rail, density rules, Framer-Motion-for-comprehension-only) carries over from `apps/client`'s DESIGN.md — only the Product Context needs re-deriving. See [[overview]] §Surfaces and the office-hours design.

---

## Boundaries

### This Domain Owns

- The web graph view (renders in `apps/client`; nodes = `pages` rows, edges = `page_links`, article = `pages.body`).
- The page reader / page list / page settings — the renamed `apps/client` routes (1d-rename): `entity/[key]` → page reader, `entity/` → page list, `entity/[key]/settings` → page settings; renders `frontmatter` + backlinks against the renamed `pages`/`page_links` API.
- The decision-queue UI (the inbox-not-dashboard view; the queue itself is owned by [[domains/Page Resolution & Decision Queue/Page Resolution & Decision Queue|Page Resolution & Decision Queue]]).
- The PR bot — a **GitHub App** (the *only* place a GitHub App is used; the scan uses a read-only PAT — invariant 6).
- The `scan_runs` failure surface (where `parsed`/`skipped`/`synthesis_failed` counts reach a human).
- The freshness indicator ("synthesized N source-events ago / M not yet folded in") and the `pages.confidence` de-emphasis treatment — the **trust signals**; load-bearing, not Phase-3.1-cuttable.

### This Domain Does NOT Own

- The data model — that's [[domains/Pages & Links Core/Pages & Links Core|Pages & Links Core]].
- The scan / synthesis / resolution pipelines — those are [[domains/Source Ingestion/Source Ingestion|Source Ingestion]] / [[domains/Synthesis/Synthesis|Synthesis]] / [[domains/Page Resolution & Decision Queue/Page Resolution & Decision Queue|Page Resolution & Decision Queue]].
- The MCP query facade — that's [[domains/MCP Server/MCP Server|MCP Server]] (a separate surface, a `core/` module).
- `apps/web` — that's the marketing site, untouched by the pivot.

---

## Sub-Domains

| Component | Purpose | Type |
| --------- | ------- | ---- |
| Web graph view | d3/cytoscape/sigma; nodes actionable on click (ownership · decisions · recent activity · "ask this person"); decision queue alongside; re-scan on demand/cron. Monochrome + semantic-only palette; **desktop-only** (mobile = reader + queue, no graph). _(stub — Phase 3 design pass)_ | Frontend |
| Page reader / list / settings | renamed `apps/client` `entity/*` routes; renders frontmatter + backlinks against the renamed API. **1d-rename** | Frontend |
| Decision-queue inbox | Linear-Triage / GitHub-"review requested" pattern; one action per row; keyboard nav (j/k/enter); empty = "you're caught up" (a success state). _(stub — Phase 3 design pass)_ | Frontend |
| PR bot | GitHub App: on PR open, non-blocking comment names the domain owner + auto-requests them as reviewer; touched-file-references-an-ADR → "why" prompt → reply auto-drafts an ADR PR, attributed, queued. GitHub-native (collapsed `<details>`, suggested-reviewer UI, text-first). | Backend (`core/`) + GitHub App |
| `scan_runs` failure surface | shows parsed/skipped/synthesis_failed counts + per-skip-reason rows to a human. _(stub — Phase 3 design pass)_ | Frontend |
| Trust signals | freshness indicator + `pages.confidence` de-emphasis; the graph a11y story = the article's backlinks list IS the parallel accessible view of the edges | Frontend |

### Integrations

| Component | External System |
|---|---|
| PR bot | GitHub App (webhooks: install, PR opened, PR comment) |

---

## Flows

| Flow | Type | Description |
| ---- | ---- | ----------- |
| [[Flow - Open a Page]] _(stub — flesh out during Phase 1d / Phase 3)_ | User-facing | navigate to `entity/[key]` → page reader renders `frontmatter` + backlinks; very-stale → on-nav fill placeholder (see [[domains/Synthesis/Synthesis|Synthesis]]) |
| [[Flow - PR Bot Comment]] _(stub — Phase 2/3)_ | Integration | PR open → name owner + request reviewer; ADR-referencing file → "why" prompt → reply → ADR PR drafted, attributed, queued |
| [[Flow - Re-scan on Demand]] _(stub — Phase 3)_ | User-facing | "re-scan" button / cron → triggers [[domains/Source Ingestion/Source Ingestion|Source Ingestion]] → graph refreshes |
| [[Flow - Triage the Decision Queue]] _(stub — Phase 3)_ | User-facing | approve/reject a suggestion; keyboard-driven |

---

## Data

### Owned Entities

This domain owns no tables — it renders and acts on data owned elsewhere (`pages`, `page_links`, `decision_queue`, `scan_runs`). The PR bot's drafted ADR is a GitHub PR, not a row.

---

## External Dependencies

| Dependency | Purpose | Failure Impact |
|---|---|---|
| GitHub App API | PR bot comments, reviewer requests, ADR PR drafts | PR bot silent; warn-not-block by construction, so degraded not broken |
| The `pages`/`page_links` read API | the graph + reader | UI empty; surfaces nothing |

---

## Domain Interactions

### Depends On

| Domain | What We Need | How We Access |
|---|---|---|
| [[domains/Pages & Links Core/Pages & Links Core|Pages & Links Core]] | page + backlink read API; graph edges | API |
| [[domains/Synthesis/Synthesis|Synthesis]] | rendered `body`, freshness, `stale` flag; on-nav fill enqueue | API |
| [[domains/Page Resolution & Decision Queue/Page Resolution & Decision Queue|Page Resolution & Decision Queue]] | `decision_queue` items + approve/reject | API |
| [[domains/Source Ingestion/Source Ingestion|Source Ingestion]] | `scan_runs` status; re-scan trigger | API |
| [[domains/Workspace & Auth/Workspace & Auth|Workspace & Auth]] | auth, dashboard shell, RLS | Direct |

### Consumed By

| Domain | What They Need | How They Access |
|---|---|---|
| (end users) | the graph, the reader, the PR comment | UI / GitHub |

---

## Key Decisions

| Decision | Summary |
|---|---|
| [[ADR-016]] | phasing — Surfaces is Phase 3, gated by the "Phase 3 design pass" TODO; the page reader rename (1d) is Phase 1d |
| [[ADR-021]] | GitHub-only v1 — the PR bot is the only GitHub App; the scan is read-only PAT |
| _(design-review DR1–DR4 — see [[architecture-pivot]] §Design review addendum)_ | Phase 1d frontend collapse; `pages.confidence` + freshness UI load-bearing; monochrome+semantic-only graph palette; graph desktop-only / backlinks-list = a11y view |

---

## Technical Debt

| Issue | Impact | Effort |
|---|---|---|
| The "Phase 3 design pass" — 4 surfaces specified at ~1–2 sentences; without it Phase 3 ships AI-slop defaults | High | the design pass is the work |
| Re-derive `apps/client` DESIGN.md Product Context (refs → Sourcegraph/Backstage/Obsidian/Graphite); resolve `apps/client/DESIGN.md` vs `apps/web/DESIGN.md` as the app's source of truth | Med | Low–Med |
| The graph's retained-value problem — does a force-directed graph get opened in week 3? Needs a usage hypothesis + a fallback (push the important stuff via PR comment / MCP / digest) | Med | — |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-05-13 | Domain created from the architecture pivot — the three consumer surfaces; the `apps/client` entity→page route rename (1d); deletes the entity-TYPE / relationship-def / manifest-catalog / integration-sync screens | [[architecture-pivot]] |
