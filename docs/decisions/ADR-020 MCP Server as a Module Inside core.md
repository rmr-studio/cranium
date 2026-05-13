---
tags:
  - architecture/decision
  - adr/accepted
Created: 2026-05-13
Updated: 2026-05-13
---
# ADR-020: MCP Server as a Module Inside `core/` (Kotlin/Spring), Not a Separate Service

---

## Context

The v1 product's engineer-facing delivery layer is an MCP server exposing read-only methods — `who_owns(path)`, `decisions_for(domain)`, `recent_changes(domain)`, `why_context(diff)` — over the page graph, so Cursor / Claude Code become team-aware. MCP servers are commonly shipped as standalone Node/TS processes. For an OSS-self-hosted product, every extra process is friction; and the MCP methods are pure reads over data that already lives behind `core/`'s auth/RLS.

---

## Decision

The MCP server is a **module inside `core/`** (Kotlin/Spring) — a read-only query facade over the `pages` / `page_links` repositories + the compact `pages.body` projection, reusing `core/`'s auth/RLS directly. **Not a separate TS service.** The OSS self-host stays "one backend (`core`) + one frontend (`apps/client`) + postgres + temporal" — no extra process.

---

## Rationale

- **The methods are pure reads over `core/`'s data** — they need `pages`/`page_links` repos and the compact `pages.body` projection; building a separate service would mean re-implementing or RPC-ing into exactly that.
- **OSS self-host simplicity** — "5 minutes to first value" (the design doc) wants the fewest moving parts; a Kotlin module in `core/` adds zero processes.
- **Reuses auth/RLS directly** — no second auth surface, no token-passing between a TS MCP process and `core/`.
- **Keeps Phase 2's lanes clean** — the MCP lane depends on Phase 1's renamed `pages`/`page_links` API, not on Phase 2's parser lane, so it's genuinely independent and parallelizable.

---

## Alternatives Considered

### Option 1: Standalone Node/TS MCP server

- **Pros:** Matches the common MCP packaging; TS MCP SDK is mature.
- **Cons:** A second process for the self-hoster; a second auth surface; RPC/HTTP into `core/` for every method; "5 minutes to first value" gets harder.
- **Why rejected:** Friction for the OSS audience; the methods are reads over `core/` data — no reason to leave the JVM.

### Option 2: MCP methods exposed via the existing REST API, no MCP framing

- **Pros:** Zero new surface.
- **Cons:** Not an MCP server — IDE agents expect the MCP protocol; "make Cursor team-aware" needs the MCP contract.
- **Why rejected:** The product spec is an MCP server; REST endpoints don't satisfy it.

---

## Consequences

### Positive

- One backend, no extra process; one auth/RLS surface; the MCP lane parallelizes cleanly.
- Kotlin integration tests run against the real `pages`/`page_links` repos.

### Negative

- A Kotlin/JVM MCP server is a less-trodden path than the TS SDK — some protocol plumbing to do in Kotlin.

### Neutral

- The compact `pages.body` projection (the per-kind `bodyTokenBudget` from the sealed `Page` contract, E6) is what MCP responses are budgeted against — a synthesis-contract decision, not an MCP-server decision.
- No-page paths (`who_owns` on an unknown path) return an empty structured result, not a 500.

---

## Implementation Notes

- Phase 2, Lane B: the MCP module reads `pages`/`page_links` + the compact `pages.body` projection; honors RLS; no-data → empty structured result.
- `why_context(diff)` injects into an IDE agent's context window — respect the per-kind `bodyTokenBudget`.

---

## Related

- [[architecture-pivot]]
- [[ADR-018 One pages Table Family for Synthesis Storage]]
- [[ADR-021 GitHub-Only v1, Slack and Notion as v1.1]]
