---
tags:
  - architecture/design
Created: 2026-05-13
Updated: 2026-05-13
---
# Design Docs — Workflow & Backlog (Cranium v1 pivot)

The design-doc workflow for the new scope: **Cranium v1 = "the open team-context layer for AI-amplified engineering teams."** Point at a GitHub org → a living domain/ownership/SME map + ADR/decision index → three surfaces (web graph, MCP server, non-blocking PR comment), with every derived signal landing in the decision/suggestion queue. See [[architecture-pivot]] for the canonical spec and `flows/` for the end-to-end flow docs.

---

## Where designs live

```
docs/designs/<area>/<Design Name>.md
```

Areas track the v1 domains: `source-ingestion/`, `synthesis/`, `page-resolution/`, `mcp/`, `surfaces/`, `oss-packaging/`. Phase 1 (1a–1d) is mechanical — see below.

## Which template when

| Template | Use when | File |
|---|---|---|
| **Feature Design — Quick** | a contained backend component (one service, a status surface, an install flow) | `templates/Design/Feature Design - Quick.md` |
| **Feature Design — Full** | a cross-cutting backend feature (the scan pipeline, the synthesis workflow, the resolution policy, the MCP server, the PR bot) | `templates/Design/Feature Design - Full.md` |
| **Frontend Feature Design — Quick** | a contained UI component | `templates/Design/Frontend Feature Design - Quick.md` |
| **Frontend Feature Design — Full** | a cross-cutting UI feature (the web graph, the decision-queue UI) | `templates/Design/Frontend Feature Design - Full.md` |
| **Page Design** | a single route/page composition (the renamed `apps/client` page reader / list / settings) | `templates/Design/Page Design.md` |
| **Sub-Domain Plan** | building out a whole sub-domain (the OSS self-host bundle) | `templates/Design/Sub-Domain Plan.md` |

(Cheat sheet for all templates incl. the `Documentation/` flow templates: [[_TEMPLATE-INDEX]].)

## How to start a design doc

1. **Copy the template** into `docs/designs/<area>/<Design Name>.md`.
2. **Fill the frontmatter** — `Created:`/`Updated:` = today, `Domains:` = the parent `[[Domain - …]]`, `tags:` = `status/draft` + a `priority/*`, plus `Backend-Feature:` / `Pages:` on frontend docs.
3. **Link the parent domain + the relevant ADRs + the relevant `[[Flow - …]]`** in the Related section.
4. **Get it reviewed via `/plan-eng-review`** (and `/plan-design-review` for UI-bearing docs) **before coding** — the eng review locks the architecture; the design review catches UX gaps.
5. Flip `status/draft` → `status/designed` once reviewed, `status/implemented` once shipped.

---

## Backlog of design docs to write, grouped by phase

### Phase 1 (1a–1d) — mechanical, no new design docs

Phase 1 (the `entities`→`pages` rename, the relationship collapse, the taxonomy rework, the `apps/client` collapse) is **mechanical structural collapse — no behavior change**. **No new design docs needed beyond the pivot ADRs** (those are owned under `docs/decisions/`, not here). One test artifact to track:

#### Fixture repo for tests
- Scope: a small, deterministic Git repo (2–3 domains, an ADR, a handful of PRs/commits) committed as a test artifact — the "GitHub scan → pages" E2E and the "PR bot" E2E both run against it.
- Template: none (it's a Git repo, not a doc) — but its shape should be agreed in the "GitHub Scan & Source Parsers" design.
- Domain: Source Ingestion.
- Status: not started

### Phase 2 — the GitHub scan + synthesis pipeline + the surfaces' read sides

> Build order within Phase 2: source parsers → **page-resolution policy (build the eval suite FIRST — it ships before the policy and is a ≥85%-precision CI gate)** → the synthesis Temporal workflow → MCP server → web graph → PR bot.

#### Page Resolution Policy + Eval Suite
- Scope: the per-insight resolution policy (deterministic key lookup → fuzzy candidate-find + score → auto-link above a config threshold OR a `decision_queue` suggestion), reusing the identity-match harness; **the golden-set eval suite is built first** and wired into CI with a ≥85% precision floor. Decision-page rule: ADR-backed keys on the ADR file path; no backing file → fuzzy-only, never auto-created, always via the queue. See [[Flow - Page Resolution and Decision Queue]].
- Template: Feature Design — Full.
- Domain: Page Resolution & Decision Queue.
- Status: not started

#### Synthesis Prompt + Insight Extraction
- Scope: the LLM prompt(s) that read a batch of `source_entities` + context and extract atomic insights; the prompt-injection-on-synth-content guard (no LLM output bypasses the resolution-threshold/decision-queue gate); per-kind narrative-section prompts and `bodyTokenBudget`.
- Template: Feature Design — Full.
- Domain: Synthesis.
- Status: not started

#### GitHub Scan & Source Parsers
- Scope: clone/scan a repo with a read-only PAT; parse tree→File, commit log→Commit, PR list→PR, `/docs/decisions/*.md`→ADR `source_entities`; deterministic links (PR→files, commit→files, file→Domain by directory, commit-author-email→Person); `source_entities.content_hash` + skip-if-unchanged; enqueue onto `new_source_events_queue`. Defines the **fixture repo** shape. See [[Flow - GitHub Repo Scan]].
- Template: Feature Design — Full.
- Domain: Source Ingestion.
- Status: not started

#### Synthesis Temporal Workflow & Fan-out
- Scope: the ShedLock + SKIP-LOCKED dispatcher over `new_source_events_queue` (the `enrichment.embed` pattern); batch/coalesce by likely-affected page (time-windowed ~5 min or size-capped ~50 events); the Temporal workflow (read batch → extract insights → resolve → emit targeted page-column updates + new pages/links → coalesce → mark for re-embed); fan-out PR→Domain-hub + Person-hub + Decision-hub with content-hash cycle-break; the targeted-column-update / lost-update invariant. See [[Flow - Batched LLM Synthesis]].
- Template: Feature Design — Full.
- Domain: Synthesis.
- Status: not started

#### MCP Server (core/ module)
- Scope: the read-only query facade inside `core/` — `who_owns(path)` / `decisions_for(path|domain)` / `recent_changes(domain)` / `why_context(diff)` over `pages`/`page_links` + the compact `pages.body` projection, reusing auth/RLS; no-page path → empty structured result, not a 500; read-side only for v1. See [[Flow - MCP Query]].
- Template: Feature Design — Full.
- Domain: MCP Server.
- Status: not started

#### PR Bot (GitHub App)
- Scope: the GitHub-App-driven non-blocking PR comment — name the domain owner + auto-request as reviewer; touched-file-references-an-ADR → the "why" prompt → reply auto-drafts an attributed ADR PR queued in `decision_queue`. v1 ships ONLY those two high-confidence signals; no fuzzy reimplementation detection. HMAC webhook validation. The "why"-prompt thin spike (E4) runs early in Phase 2's build order. See [[Flow - PR Bot]].
- Template: Feature Design — Full.
- Domain: Surfaces.
- Status: not started

#### scan_runs Status Surface
- Scope: the `scan_runs` table (per-run `parsed / skipped / synthesis_failed` counts + per-skip-reason rows) and the API that exposes it — closes the three silent-failure risks (source-skip count, synthesis-failure count, scan-status honesty). The human-facing UI is Phase 3.
- Template: Feature Design — Quick.
- Domain: Source Ingestion.
- Status: not started

### Phase 3 — the surfaces (web graph, decision queue, page reader)

> The "Phase 3 design pass for Cranium web surfaces" is already a tracked TODO — re-derive `apps/web` (or `apps/client`) DESIGN.md Product Context first; mockup-driven `/plan-design-review` runs at Phase 3 planning time. Constraints already locked: monochrome + semantic-only graph palette (DR3); graph desktop-only, backlinks-list = the a11y view (DR4); decision queue is an inbox not a dashboard; `pages.confidence` + freshness UI are load-bearing (DR2). Key flows to verify when built: empty-graph (new repo / scan in progress); decision-queue-empty = "you're caught up" (a success state, not "no items found"); freshness + confidence rendering; keyboard nav (j/k/enter) on the queue.

#### Web Graph Surface
- Scope: the visual map — nodes = `pages` rows, edges = `page_links`, article = `pages.body`; nodes actionable on click (ownership / decisions / recent activity / "ask this person"); freshness indicator per node; desktop-only; monochrome with semantic-only color. References: Obsidian's graph view, Sourcegraph, Backstage.
- Template: Frontend Feature Design — Full.
- Domain: Surfaces.
- Status: not started

#### Decision Queue UI
- Scope: the inbox surface — `decision_queue` rows (SME-assign / promote-to-Decision / stale-ADR / ambiguous-resolution) with provenance; approve/reject; keyboard nav (j/k/enter); empty = "you're caught up"; mobile-usable (triage from anywhere). Pattern: Linear Triage / GitHub "review requested".
- Template: Frontend Feature Design — Full.
- Domain: Surfaces.
- Status: not started

#### Page Reader / List / Settings (renamed apps/client routes) — Page Design ×3
- Scope: three Page Design docs for the Phase-1d-renamed routes — `page/[key]` (the reader: frontmatter + body article + backlinks list, freshness + confidence treatment, "generating…" placeholder for cold/stale pages), `page/` (the list), `page/[key]/settings` (page-level settings). See [[Flow - Page Reader Render (apps-client)]].
- Template: Page Design (×3).
- Domain: Surfaces.
- Status: not started

### Phase 4 — OSS packaging + release + self-host quickstart

#### OSS Self-Host Bundle
- Scope: the hardened `docker-compose` (or equivalent) bringing up `core` + `apps/client` + postgres + Temporal (MCP is in `core`, no extra process); the "5-minute to first value" path; healthcheck; the README quickstart. See [[Flow - Self-Host Quickstart]].
- Template: Sub-Domain Plan.
- Domain: OSS Packaging & Self-Host.
- Status: not started

#### GitHub Actions Release Pipeline
- Scope: build + test + publish on a version tag (the OSS package + the container images); target platforms for any published artifact.
- Template: Feature Design — Quick.
- Domain: OSS Packaging & Self-Host.
- Status: not started

#### GitHub App Install Flow
- Scope: registration + the install/permissions flow for the PR bot (the one piece that genuinely needs an install UX); mock-webhook test for install + PR events.
- Template: Feature Design — Quick.
- Domain: OSS Packaging & Self-Host.
- Status: not started

#### Forward-Only Migration Path (Flyway/Liquibase)
- Scope: the OSS self-hoster's upgrade path — forward-only migrations run on `core` startup; no down-migrations in prod; the convention + tooling. Needed before the first external self-hoster.
- Template: Feature Design — Quick.
- Domain: OSS Packaging & Self-Host.
- Status: not started

---

## Related

- [[architecture-pivot]] — the canonical pivot spec
- [[_TEMPLATE-INDEX]] — template cheat sheet
- `flows/` — the end-to-end flow docs (GitHub Repo Scan, Batched LLM Synthesis, Page Resolution and Decision Queue, PR Bot, MCP Query, Page Reader Render, Self-Host Quickstart)
