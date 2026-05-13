---
tags:
  - flow/planned
  - flow/integration
  - architecture/flow
Created: 2026-05-13
Updated: 2026-05-13
Critical: false
Domains:
  - "[[Domain - OSS Packaging]]"
---
# Flow: Self-Host Quickstart

_(stub — Phase 4; design pass during Phase 4 — "OSS Self-Host Bundle" sub-domain plan + the "GitHub App Install Flow" / "GitHub Actions Release Pipeline" / "Forward-Only Migration Path" feature designs.)_

> See [[architecture-pivot]] for the canonical spec. The GTM is open-source-led — v1 isn't distributable without this, so it's a phase, not a TODO. Foundation already in the repo: `docker-compose.yml` + Dockerfiles (`core`, `apps/client`).

---

## Overview

A self-hoster clones the repo, runs `docker compose up` (which brings up `core` + `apps/client` + postgres + Temporal — **the MCP server is a module in `core/`, so no extra process**), waits for the healthcheck to pass, registers a **read-only GitHub PAT**, points Cranium at an org, and the first scan completes within the "5-minute to first value" budget. Flyway forward-only migrations run on `core` startup so a self-hoster's DB is always upgradeable.

---

## Trigger

| Trigger Type | Source | Condition |
|---|---|---|
| User action (CLI) | a self-hoster following the README quickstart | `git clone` + `docker compose up` on a machine with Docker |

**Entry Point:** `docker-compose.yml` (the hardened self-host bundle) → `core` startup (runs Flyway migrations) → the app's "register a PAT" onboarding screen.

---

## Flow Steps (what runs end-to-end)

`clone → docker compose up (core + apps/client + postgres + Temporal; MCP is in core) → core runs Flyway forward-only migrations → healthcheck passes → register a read-only GitHub PAT → point at an org → the first scan ([[Flow - GitHub Repo Scan]] → [[Flow - Batched LLM Synthesis]]) completes within the "5-minute" budget.`

1. **`git clone` + `docker compose up`** — the bundle starts postgres, Temporal, `core` (with the MCP module inside it), and `apps/client`.
2. **`core` startup** — runs **Flyway forward-only migrations** against postgres (the OSS self-hoster's upgrade path — no down-migrations in prod).
3. **Healthcheck** — the bundle exposes a healthcheck endpoint; CI asserts it passes (the automated "5-minute to first value" path).
4. **Register a read-only GitHub PAT** — through the app's onboarding screen (PAT for the scan; the GitHub App is a separate, optional install for [[Flow - PR Bot]]).
5. **Point at an org / repo** — triggers [[Flow - GitHub Repo Scan]] → enqueues onto `new_source_events_queue` → [[Flow - Batched LLM Synthesis]] drains it.
6. **First value** — the domain/ownership/SME map + the ADR/decision index are visible in the web surface within the budget; the scan is paged + the synthesis backlog throttled so a busy repo doesn't blow the budget.
7. **(Optional) install the GitHub App** — for the PR bot; the one piece that genuinely needs an install UX.

---

## Data Touched

- **Writes:** the Flyway schema (all the `pages`-family tables, on first startup), then everything [[Flow - GitHub Repo Scan]] + [[Flow - Batched LLM Synthesis]] write.
- **Reads:** the PAT (stored), the org/repo config, the GitHub API.

---

## Failure Modes

| Codepath | Failure | Detection | User-visible | Recovery |
|---|---|---|---|---|
| `docker compose up` | a container fails to come up (port clash, bad image) | the healthcheck never goes green | "the stack failed to start" | fix the config; re-run |
| Flyway migration | a migration fails on an existing (older) DB | `core` refuses to start | "migration failed" in the logs | forward-only — never auto-rollback; the self-hoster restores from a backup if needed |
| PAT registration | the PAT is wrong / lacks `repo:read` | the scan fails fast | "re-check the PAT" | re-register |
| First scan over budget | a huge repo's backlog blows the "5-minute" budget | the scan-progress UI | the map fills in progressively, not all at once | the scan is paged + the synthesis backlog throttled — progress is shown, not a hang |
| GitHub App install | the install/permissions flow fails | the App webhook never arrives | the PR bot stays inactive | re-install the App (the PAT scan still works without it) |

---

## Test Bar

- **CI job** — `docker compose up` against the self-host bundle → the healthcheck passes (the "5-minute to first value" path, automated).
- (Adjacent, owned by [[Flow - PR Bot]] and the Phase 4 feature designs:) the **GitHub Actions release workflow** runs green on a version tag (build + test + publish artifacts); the **GitHub App install flow** — mock GitHub webhook → app receives + processes the install + a PR event.

---

## Related

- [[architecture-pivot]] — canonical spec; ER4 (Phase 4 — OSS packaging + release + self-host quickstart + GitHub App install flow)
- [[Flow - GitHub Repo Scan]] — the first scan the quickstart triggers
- [[Flow - Batched LLM Synthesis]] — what fills the map after the scan
- [[Flow - PR Bot]] — the GitHub App install flow (optional, separate from the PAT)
- [[Domain - OSS Packaging]]
