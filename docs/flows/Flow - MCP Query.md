---
tags:
  - flow/planned
  - flow/integration
  - flow/user-facing
  - architecture/flow
Created: 2026-05-13
Updated: 2026-05-13
Critical: false
Domains:
  - "[[Domain - MCP Server]]"
---
# Flow: MCP Query

_(stub — Phase 2; design pass during Phase 2 — "MCP Server (core/ module)" feature design. Read-side only for v1.)_

> See [[architecture-pivot]] for the canonical spec. **The MCP server is a Kotlin module inside `core/`** (Spring) — a read-only query facade over the `pages`/`page_links` repos + the compact `pages.body` projection, reusing auth/RLS directly. **Not** a separate TS service — keeps the OSS self-host at "one backend + one frontend + postgres + temporal" and keeps Phase 2's Lane B independent of Lane A.

---

## Overview

An MCP client in an IDE (Cursor, Claude Code) calls one of four read-only tools and gets a structured result computed from the page graph, RLS-scoped to the caller's workspace:

- `who_owns(path)` — the Domain owner (Person page) for a file/directory.
- `decisions_for(path | domain)` — the ADR/Decision pages referenced by that file or domain.
- `recent_changes(domain)` — recent `source_entities` (PRs/commits) linked to that Domain page.
- `why_context(diff)` — for a proposed diff, the relevant decisions + owners + recent activity for the touched paths (the "before you write this, here's the context" answer).

---

## Trigger

| Trigger Type | Source | Condition |
|---|---|---|
| MCP tool call | an MCP client (IDE agent) over the MCP transport | the agent invokes one of the four tools with valid auth |

**Entry Point:** the MCP module in `core/` → `McpQueryService` → the `pages` / `page_links` repos (RLS-scoped).

---

## Flow Steps (what runs end-to-end)

`MCP client calls who_owns / decisions_for / recent_changes / why_context → the core/ MCP module authenticates + RLS-scopes → queries the pages/page_links repos + the compact pages.body projection → returns a structured result (empty structured result, never a 500, if no page matches).`

1. **Receive the tool call + authenticate** — reuse the existing auth/RLS layer; resolve the caller's workspace; reject if unauthenticated.
2. **Dispatch by tool:**
   - `who_owns(path)` → resolve path → directory → Domain page → its `owns`-linked Person page.
   - `decisions_for(path | domain)` → from the File/Domain page, follow `references` links to ADR/Decision pages.
   - `recent_changes(domain)` → from the Domain page, follow `page_links` to `source_entities`, ordered by `source_timestamp`, windowed.
   - `why_context(diff)` → extract touched paths from the diff → for each: owner + decisions + recent changes; aggregate.
3. **Project + return** — read the compact `pages.body` projection (not the full article), serialize a structured MCP result.
4. **No-page path** — if nothing matches, return an **empty structured result** (e.g. `{ owner: null }`, `{ decisions: [] }`) — **not a 500**.

---

## Data Touched

- **Reads only (v1):** `pages`, `page_links`, `source_entities` (for `recent_changes`), the compact `pages.body` projection, the auth/RLS context.
- **Writes:** none — read-side only for v1. (MCP-as-router and "planning infra" are out of v1.)

---

## Failure Modes

| Codepath | Failure | Detection | User-visible | Recovery |
|---|---|---|---|---|
| No page for the path/domain | the queried path/domain has no page yet (new repo, scan in progress) | the query returns zero rows | the agent gets an explicit "no data" structured result — **not a crash** | re-query after the scan/synthesis drains |
| Auth / RLS | a caller queries a workspace they can't see | RLS denies the rows | empty result (no leak across workspaces) | n/a — by construction |
| Stale page | the page exists but its synthesis is old | the page carries a freshness marker (echoed in the result) | the agent gets the cached answer + a "synthesized N events ago" hint | a navigation/refresh enqueues a re-synth (see [[Flow - Page Reader Render (apps-client)]] for the on-demand-refresh pattern) |

---

## Test Bar

- Each of the four methods returns the correct structured result against the **fixture-repo** page graph **and** honors RLS (no cross-workspace leak). The **no-page path returns an empty structured result, not a 500.** Kotlin integration tests against the real `pages`/`page_links` repos.

---

## Related

- [[architecture-pivot]] — canonical spec; ER3 (MCP-in-`core/`)
- [[Flow - Batched LLM Synthesis]] — produces the `pages`/`page_links` the MCP server reads
- [[Flow - Page Reader Render (apps-client)]] — the other read surface over the same graph
- [[Domain - MCP Server]]
