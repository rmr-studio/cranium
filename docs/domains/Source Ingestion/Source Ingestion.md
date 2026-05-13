---
tags:
  - architecture/domain
  - cranium/v1
Created: 2026-05-13
---
# Domain: Source Ingestion

---

## Overview

Turns a GitHub org into Layer-0 raw artifacts. Clones/lists the repo tree, walks the commit log, lists PRs, parses `/docs/decisions/*.md` — each becomes a `source_entities` row. Also creates the **cheap deterministic links** that don't need an LLM (PR→files, commit→files, file→Domain page by directory, commit-author-email→Person page). Enqueues new source events for [[domains/Synthesis/Synthesis|Synthesis]]. Records `scan_runs` status so a half-finished scan can't lie. v1 = **GitHub only**; Slack/Notion are v1.1. See [[overview]] §Layer model.

---

## Boundaries

### This Domain Owns

- The GitHub repo scanner (clone/list tree, commit log, PR list, ADR file discovery) using a **read-only PAT** (invariant 6).
- The deterministic source parsers — one per `source_kind` (`github_pr | commit | file | adr`; `slack_thread | notion_page` stubbed for v1.1).
- `source_entities` rows + `content_hash` + skip-if-unchanged (E1).
- Deterministic `page_links`: PR→files, commit→files, file→Domain page (directory), commit-author-email→Person page.
- `new_source_events_queue` (ShedLock + SKIP LOCKED dispatcher; dedup — the `enrichment.embed` pattern).
- `scan_runs` — per-run `parsed` / `skipped` / `synthesis_failed` counts + per-skip-reason rows.
- The `SourceConnector` interface shape (so v1.1 connectors plug in — but no manifest engine, no second connector ships).

### This Domain Does NOT Own

- The LLM synthesis pass over the queue — that's [[domains/Synthesis/Synthesis|Synthesis]].
- Fuzzy page resolution — that's [[domains/Page Resolution & Decision Queue/Page Resolution & Decision Queue|Page Resolution & Decision Queue]] (this domain only does the *deterministic* key links).
- The PR bot's GitHub App (separate auth, separate domain) — that's [[domains/Surfaces/Surfaces|Surfaces]]. This domain's GitHub access is the read-only-PAT scan only.

---

## Sub-Domains

| Component | Purpose |
| --------- | ------- |
| GitHub repo scanner | clone/list tree · commit log · PR list · ADR discovery; paged + rate-limit-throttled |
| Source parsers (per `source_kind`) | PR→files+author+timestamp+links; commit→tree-delta; ADR file→structured row |
| Deterministic link emitter | PR→files, commit→files, file→Domain (dir), commit-author-email→Person |
| `new_source_events_queue` dispatcher | ShedLock + SKIP LOCKED + dedup |
| `scan_runs` status writer | parsed/skipped/synthesis_failed + per-skip-reason rows |
| `SourceConnector` interface | v1.1 plug-point (GitHub is the only impl in v1) |

### Integrations

| Component | External System |
|---|---|
| GitHub repo scanner | GitHub REST / Git over HTTPS (read-only PAT) |
| `SlackConnector` _(stub — v1.1)_ | Slack |
| `NotionConnector` _(stub — v1.1)_ | Notion |

---

## Flows

| Flow | Type | Description |
| ---- | ---- | ----------- |
| [[Flow - GitHub Org Scan]] _(stub — flesh out during Phase 2)_ | Background | point at an org → parse repos → `source_entities` + deterministic links → enqueue source events → write a `scan_runs` row |
| [[Flow - Incremental Re-scan]] _(stub — Phase 2)_ | Scheduled / on-demand | re-scan; `content_hash` skip-if-unchanged; only new/changed artifacts enqueue |

---

## Data

### Owned Entities

| Entity | Purpose | Key Fields |
|---|---|---|
| `SourceEntity` | a raw ingested artifact | `source_kind`, `source_external_id`, `author_ref`, `source_timestamp`, `raw` (jsonb), `content_hash`, `parsed_at` |
| `ScanRun` | one scan's outcome | `parsed`, `skipped`, `synthesis_failed` counts; per-skip-reason child rows; `started_at`, `finished_at` |

### Database Tables

| Table | Entity | Notes |
|---|---|---|
| `source_entities` | `SourceEntity` | renamed from the old INTEGRATION-sourced entities; `content_hash` added (E1) |
| `new_source_events_queue` | — | dispatcher queue; reuses `enrichment.embed` shape |
| `scan_runs` | `ScanRun` | new in Phase 2; closes the silent-failure gaps |

---

## External Dependencies

| Dependency | Purpose | Failure Impact |
|---|---|---|
| GitHub REST / Git | repo + PR + commit + ADR data | scan can't run; existing pages go stale, surfaces keep serving cached |
| PostgreSQL | `source_entities` / `scan_runs` / queue | hard down |

---

## Domain Interactions

### Depends On

| Domain | What We Need | How We Access |
|---|---|---|
| [[domains/Pages & Links Core/Pages & Links Core|Pages & Links Core]] | create deterministic `page_links`; look up Domain/Person pages by key | Direct (repo) |
| [[domains/Workspace & Auth/Workspace & Auth|Workspace & Auth]] | `workspace_id` scoping; the workspace's GitHub PAT | Direct |

### Consumed By

| Domain | What They Need | How They Access |
|---|---|---|
| [[domains/Synthesis/Synthesis|Synthesis]] | new source-event ids off the queue | Event (queue) |
| [[domains/Surfaces/Surfaces|Surfaces]] | `scan_runs` status to surface to a human | API |

---

## Key Decisions

| Decision | Summary |
|---|---|
| [[ADR-014]] | deterministic source-parse + batched LLM synthesis — not full-LLM-on-every-artifact, not the rule engine |
| [[ADR-021]] | GitHub-only v1 — one hardcoded scanner, no manifest engine; Slack/Notion are v1.1 |
| [[ADR-016]] | phasing — Source Ingestion is Phase 2 (built on the clean post-Phase-1 foundation) |

---

## Technical Debt

| Issue | Impact | Effort |
|---|---|---|
| Per-artifact try/catch silent skips — must surface via `scan_runs` (critical if unhandled) | High | Low (built inline in Phase 2) |
| GitHub initial scan: thousands of PRs/commits — page the scan, throttle the synthesis backlog drain, show progress | Med | Med |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-05-13 | Domain created from the architecture pivot — replaces the Nango integration sync for v1 | [[architecture-pivot]] |
