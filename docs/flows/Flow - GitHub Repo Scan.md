---
tags:
  - flow/planned
  - flow/integration
  - architecture/flow
Created: 2026-05-13
Updated: 2026-05-13
Critical: true
Domains:
  - "[[Domain - Source Ingestion]]"
---
# Flow: GitHub Repo Scan

_(stub — Phase 2; design pass during Phase 2 — "GitHub Scan & Source Parsers" feature design)_

> See [[architecture-pivot]] for the canonical spec. This is Stage 1 of the Phase 2 pipeline (deterministic source parse) — the cheap, no-LLM step that feeds [[Flow - Batched LLM Synthesis]].

---

## Overview

Point Cranium at a GitHub org/repo with a **read-only PAT** (GitHub App is used only by [[Flow - PR Bot]]). Clone/scan the repo, parse the tree, commit log, PR list, and `/docs/decisions/*.md` into `source_entities` rows (`source_kind` ∈ `github_pr | commit | file | adr`), draw the cheap **deterministic** links, enqueue the new `source_entity` ids onto `new_source_events_queue`, and write a `scan_runs` status row. No LLM in this flow.

---

## Trigger

| Trigger Type | Source | Condition |
|---|---|---|
| User action / API call | "register a repo + PAT, point at an org" | a new repo registration, a re-scan request, or a scheduled cron re-scan |
| Webhook (incremental) | GitHub push/PR webhook (PR bot's App) | a new PR/commit lands on a tracked repo |

**Entry Point:** `GitHubScanService` (in `core/`, `ingestion/` module).

---

## Flow Steps (what runs end-to-end)

`clone/list tree → File source_entities; commit log → Commit source_entities; PR list → PR source_entities; /docs/decisions/*.md → ADR source_entities; draw deterministic links; enqueue new source_event ids; write scan_runs.`

1. **Clone / shallow-fetch the repo** (`GitHubScanService`) — read-only PAT auth; page the GitHub API; respect rate limits / throttle.
2. **List tree → File `source_entities`** — one row per tracked file path; `source_external_id` = repo+path; `raw` = blob metadata. `source_entities.content_hash` set; **skip-if-unchanged** vs the prior scan (E1).
3. **Commit log → Commit `source_entities`** — one row per commit; `author_ref` = commit-author-email; `source_timestamp` = commit time; `raw` = tree-delta summary.
4. **PR list → PR `source_entities`** — one row per PR; `raw` = diff summary, files touched, author, merge state.
5. **`/docs/decisions/*.md` → ADR `source_entities`** — parse ADR frontmatter (status, title, supersedes); `source_external_id` = ADR file path.
6. **Deterministic links** (no LLM): PR→files, commit→files (from the diff/tree-delta); **deterministic page links**: file→Domain page (by directory), commit-author-email→Person page. Links carry `source_entity_id`.
7. **Enqueue** the new `source_entity` ids onto `new_source_events_queue` for [[Flow - Batched LLM Synthesis]].
8. **Write `scan_runs`** — `parsed / skipped / synthesis_failed` counts + one row per skip with the skip reason (silent-skip visibility — the failure-mode fix from the eng review).

---

## Data Touched

- **Writes:** `source_entities` (insert/upsert by `source_external_id` + `content_hash`), `page_links` (deterministic links only; `source_entity_id` FK set), `new_source_events_queue` (enqueue), `scan_runs` (one row per run + per-skip-reason rows).
- **Reads:** existing `pages` (to resolve directory→Domain page, email→Person page for the deterministic links), prior `scan_runs` + `source_entities.content_hash` (skip-if-unchanged).
- **Does not touch:** `pages.body`/`frontmatter`/`aggregations` (that's synthesis), `page_embeddings`.

---

## Failure Modes

| Codepath | Failure | Detection | User-visible | Recovery |
|---|---|---|---|---|
| File/commit/PR/ADR parse (per artifact) | empty diff, binary file, renamed file, merge commit, malformed ADR frontmatter → parser throws | per-artifact try/catch (mirrors integration-sync Pass 1) | **counted in `scan_runs.skipped` + a per-skip-reason row** — never a silent skip | next scan retries the artifact |
| GitHub API | rate-limit / throttle / pagination mid-scan | HTTP 403/429 + `Retry-After` | scan shows "in progress / throttled" | back off, resume from the last page (idempotent — `source_external_id` dedup) |
| Enqueue | duplicate `source_entity` re-enqueued (re-scan, webhook replay) | workflow-ID / `source_external_id` dedup on `new_source_events_queue` (the `enrichment.embed` pattern) | none | idempotent — the dispatcher dedups |
| Whole-scan abort | clone fails / auth revoked | scan_runs row not written / written with a failure marker | "scan failed — re-check the PAT" | re-register the PAT, re-scan |

---

## Test Bar

- **Source parsers** — one suite per `source_kind` (`github_pr`, `commit`, `file`, `adr`). PR diff → correct files + author + timestamp + links; commit → tree-delta; ADR file → structured row. Edge cases: empty diff, binary files, renamed files, merge commits, malformed ADR frontmatter, scan paging/throttling.
- **GitHub scan → pages E2E** — points at a small committed deterministic **fixture repo** (2–3 domains, an ADR, some PRs/commits — itself a test artifact to create); after the scan, assert the expected File/Commit/PR/ADR `source_entities` + deterministic links exist and the `scan_runs` counts are correct. (The downstream "Domain/Person/ADR pages exist" assertion lives in [[Flow - Batched LLM Synthesis]].)

---

## Related

- [[architecture-pivot]] — canonical spec; Phase 2 ASCII pipeline diagram
- [[Flow - Batched LLM Synthesis]] — Stage 2, the consumer of `new_source_events_queue`
- [[Flow - Page Resolution and Decision Queue]] — how insights map to pages (downstream of synthesis)
- [[Flow - PR Bot]] — the webhook-driven incremental path (GitHub App)
- [[Domain - Source Ingestion]]
