# TODOs

## Backend

### Migrate EntityQueryService to cursor-based pagination

**What:** Refactor EntityQueryService from LIMIT/OFFSET to cursor-based seek pagination using the shared `CursorPagination` utility.

**Why:** Offset pagination causes duplicate/skip bugs with infinite scroll (items shift when data changes between page loads). All data lists are infinite scroll.

**Context:** EntityQueryService currently uses `QueryPagination(limit, offset, orderBy)` with LIMIT/OFFSET SQL via `EntityQueryAssembler`. The `CursorPagination` utility (created in the workspace notes PR) provides `encodeCursor()`/`decodeCursor()` and `CursorPage<T>` response wrapper. Migration involves: changing `QueryPagination` model to accept cursor instead of offset, updating `EntityQueryAssembler` to generate `WHERE (sort_col < :cursorVal OR (sort_col = :cursorVal AND id < :cursorId))` instead of `OFFSET`, and updating frontend `useEntityQuery` hooks. The main complexity is that EntityQueryService supports user-defined `orderBy` columns — the cursor key must match the sort key, which means encoding the sort value (from `entity_attributes.value`) in the cursor.

**Depends on:** Workspace notes PR (CursorPagination utility must exist first).

---

## Frontend

### Handle null entityDisplayName in workspace notes list

**What:** Show a fallback (e.g., "Unnamed" or entity ID) when `WorkspaceNote.entityDisplayName` is null.

**Why:** The backend sub-SELECT for display name returns null if the entity's identifier attribute was deleted or never set. Rare edge case but prevents blank cells in the notes DataTable entity column.

**Context:** `WorkspaceNote.entityDisplayName` is `String?` (nullable). The frontend entity badge/column in the notes list and sidebar panel should show a sensible fallback. This also applies to the breadcrumb in the full-page editor route.

**Depends on:** Workspace notes backend PR delivering the `WorkspaceNote` model.

---

## Strategic

### Custom Integration Builder - Direct Postgres, CSV, and Webhook Ingestion

**Priority:** P2
**Effort:** XL (human: ~6 weeks) / L (CC: ~4 hours)
**Depends on:** Smart Projection (domain-based projection routing must work first)

Per the SaaS Decline thesis, data sources will diversify beyond SaaS integrations. Users need to:
- Connect internal Postgres tables directly
- Import CSVs with schema inference
- Receive webhooks from custom internal systems
- Poll internal APIs

All of these produce entities classified by LifecycleDomain. Domain-based projection routing
handles them automatically — any SUPPORT-domain entity from any source routes to the SupportTicket
core model without additional configuration. See `docs/architecture-suggestions.md` for the
SUPPORT → SupportTicket routing decision and any related cross-domain dependency notes.

**Pros:** Directly addresses the SaaS Decline thesis. Domain-based routing makes this architecturally
clean. Positions Riven as "operational data layer" not "SaaS connector."

**Cons:** Large scope. Requires UI for connection setup, schema inference, field mapping.
Each ingestion type has unique edge cases (Postgres connection pooling, CSV encoding, webhook auth).

**Context:** See the "SaaS Decline & Strategic Positioning" document in the philosophy vault
for the strategic thesis. The expanded ingestion model is defined there. Core model architecture
provides the foundation — domain-based routing means new ingestion types work without touching
core model code.

**Documentation tasks:**
- [ ] After each structural change, append an entry to `docs/architecture-changelog.md` (owner: implementer)
- [ ] When new inter-domain dependencies or responsibility changes are introduced, append suggestions to `docs/architecture-suggestions.md` with affected domains and link to the change PR (owner: implementer)
