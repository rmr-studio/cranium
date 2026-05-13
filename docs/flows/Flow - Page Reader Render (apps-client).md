---
tags:
  - flow/planned
  - flow/user-facing
  - architecture/flow
Created: 2026-05-13
Updated: 2026-05-13
Critical: true
Domains:
  - "[[Domain - Surfaces]]"
---
# Flow: Page Reader Render (apps/client)

_(stub — Phase 1d (the renamed routes) + Phase 3 (the new graph/queue surfaces dock alongside); design pass during Phase 3 — "Page Reader / List / Settings" page designs. The "Phase 3 design pass for Cranium web surfaces" is already a tracked TODO.)_

> See [[architecture-pivot]] for the canonical spec. **Phase 1d renamed the `apps/client` routes** (the dashboard app, NOT `apps/web` the marketing site): `entity/[key]` → page reader, `entity/` → page list, `entity/[key]/settings` → page settings. **Deleted:** entity-TYPE management, the relationship-definition editor, the manifest/catalog UI, the integration-sync UI. **Kept:** auth + the dashboard shell (icon rail) + the page-reader bones.

---

## Overview

The user opens the `apps/client` app, navigates to a page, and the page reader fetches the `pages` row + its `page_links` and renders the frontmatter + the rendered `body` article + the backlinks list (the backlinks list is also the parallel accessible view of the same edges the graph surface shows). On a missing or very-stale page: enqueue a high-priority refresh and serve the cached row immediately (or a "generating…" placeholder) — **the LLM call never sits on the request path** (D7).

---

## Trigger

| Trigger Type | Source | Condition |
|---|---|---|
| User action | navigate to `/dashboard/workspace/[workspaceId]/page/[key]` (renamed from `entity/[key]`) | the user clicks a page link / node / search result |

**Entry Point:** the page-reader route in `@cranium/client` → the data hook → the renamed `pages` / `page_links` read API.

---

## Flow Steps (what runs end-to-end)

`open the app → navigate to a page → fetch the pages row + page_links → render frontmatter + body + backlinks against the renamed pages/page_links API contract → on missing/very-stale page, enqueue a high-priority refresh and serve the cached row or a "generating…" placeholder immediately.`

1. **Navigate** — the user lands on the page-reader route with a page key.
2. **Fetch** — the data hook calls the renamed read API: `GET` the `pages` row + its `page_links` (forward links + backlinks via the reverse query `WHERE target_page_id = :id`).
3. **Branch on freshness:**
   - page exists and is fresh → render.
   - page exists but is older than the freshness threshold → render the cached row **and** enqueue a high-priority re-synth ([[Flow - Batched LLM Synthesis]] picks it up).
   - page missing (cold-start: new repo, no synthesis yet) → enqueue a high-priority synth + show a **"generating…"** placeholder.
4. **Render** — frontmatter block (per-kind shape), the `body` markdown article, the backlinks list grouped by source kind (the overview `GROUP BY` over `page_links JOIN source_entities`), a freshness indicator ("synthesized N source-events ago / M not yet folded in") and the `pages.confidence` de-emphasis treatment (DR2 — these are the trust mechanism, not Phase-3.1-cuttable).
5. **Settings sub-route** — `page/[key]/settings` (renamed from `entity/[key]/settings`) — page-level settings against the renamed API.

---

## Data Touched

- **Reads:** `pages` (the row + the compact `body`/frontmatter/aggregations), `page_links` (forward + reverse), `source_entities` (for the backlinks `GROUP BY`), `page_history` (if `?at=`/`?since=` time-travel is requested).
- **Writes:** none directly — a stale/missing page only **enqueues** a re-synth (no LLM on the request path).

---

## Failure Modes

| Codepath | Failure | Detection | User-visible | Recovery |
|---|---|---|---|---|
| Renamed API contract drift | the API shape changed under these screens in Phase 1d → a data hook hits the old shape | the CRITICAL Phase-1d regression test | broken render | **fixed before 1d merges** — the regression test is the gate |
| Missing page | navigate to a page that doesn't exist yet (cold-start) | the fetch returns 404/empty | a **"generating…"** placeholder + a queued refresh — not an error page | the placeholder resolves once synthesis drains |
| Very-stale page | the synthesis is old | the freshness indicator | the cached article + a "synthesized N events ago" badge | the queued high-priority refresh updates it |
| Deleted screen referenced | a link points at a removed screen (entity-TYPE mgmt, relationship-def editor, manifest/catalog, integration-sync) | 404 | a not-found state | those screens are gone by design (Phase 1d delete list) |

---

## Test Bar

- **CRITICAL regression** — open the app → navigate to a page → the frontmatter + backlinks render against the **renamed** `pages` / `page_links` API contract (the API shape changed under these screens — a regression, not new behavior). E2E: open app → navigate to a page → frontmatter + backlinks visible.
- **`@cranium/client` Jest suite** green except the deleted-screen tests; the surviving screens (auth, dashboard shell, page reader / list / settings) — data hooks hit the new API shape; smoke + happy-path render.

---

## Related

- [[architecture-pivot]] — canonical spec; ER1 (Phase 1d → `apps/client`, split rename/delete); DR2 (confidence + freshness UI load-bearing)
- [[Flow - Batched LLM Synthesis]] — the re-synth a stale/missing page enqueues
- [[Flow - MCP Query]] — the other read surface over the same `pages`/`page_links` graph
- [[Domain - Surfaces]]
