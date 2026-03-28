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

### Entity Ingestion Pipeline — Classify, Route, Map, Resolve

**Priority:** P1
**Effort:** L (human: ~3 weeks) / L (CC: ~2 hours)
**Depends on:** Lifecycle Spine (merged), Integration Sync State (merged), Identity Resolution (PR)

**What:** Build the 4-step ingestion pipeline that bridges integration data → core entity hub. This is the critical path to the "single source of truth" vision.

**Why:** Without this pipeline, integration entities and core entities are disconnected populations. Users can't see Zendesk tickets in their Support Ticket table, can't see a unified Customer view, and can't get aggregation columns like "Open Tickets: 3." This pipeline is the missing piece between "integrations connect" and "data is useful."

**Pipeline steps:**
1. **Classify**: Determine `(LifecycleDomain, SemanticGroup)` from integration manifest metadata
2. **Route**: Match `ProjectionAcceptRule` to target core entity type
3. **Map Fields**: Transform source fields → core schema via `CatalogFieldMappingEntity`
4. **Identity Resolution**: Match incoming data to existing entities (sourceExternalId, then identifier key like email)

**New components:** `FieldMappingService`, `IdentityResolutionService`, `EntityProjectionService`, `IntegrationSyncWorkflow` (Temporal), `IntegrationSyncActivities`

**Key architectural decisions (eng review 2026-03-27):**
- Hub Model: core entity types are user-facing hub, integration entities are hidden infrastructure
- Source wins: mapped fields owned by integration, overwritten on sync. Unmapped fields are user-owned.
- Most recent sync wins: timestamp-based multi-source conflict resolution
- Field-level audit trail on sync overwrites via activityService
- Temporal execution with activity-level retry and cursor pagination
- `SourceType.PROJECTED` + `ProjectionAcceptRule` as `List<ProjectionAcceptRule>`

**Context:** See eng review plan at `.claude/plans/sleepy-doodling-spark.md` and feature design at `docs/system-design/feature-design/1. Planning/Entity Ingestion Pipeline.md`.

---

### Backfill Projection for Unmatched Integration Entities

**Priority:** P2
**Effort:** M (human: ~1 week) / S (CC: ~30 min)
**Depends on:** Entity Ingestion Pipeline (P1 above)

**What:** When a user installs a new core model that matches existing (previously unmatched) integration entities, retroactively project those entities through the ingestion pipeline.

**Why:** If a workspace connects Zendesk before installing the Support Ticket core model, Zendesk tickets are created as integration entities with no projection. When the user later installs Support Ticket, those existing integration entities should be retroactively projected into the core type.

**Pros:** Completes the "opt-in" model for core entity types — users can connect integrations first, install models later, and everything connects automatically. Prevents data being silently "stuck" as hidden integration entities.

**Cons:** Needs a one-time Temporal workflow per core model installation. For large volumes (50k+ integration entities), this could be resource-intensive and needs pagination.

**Context:** Identified by outside voice during eng review 2026-03-27. The ingestion pipeline's classify → route → map → resolve steps apply equally to backfill — just triggered by core model installation rather than sync events.

---

### Custom Integration Builder - Direct Postgres, CSV, and Webhook Ingestion

**Priority:** P2
**Effort:** XL (human: ~6 weeks) / L (CC: ~4 hours)
**Depends on:** Entity Ingestion Pipeline (the 4-step classify → route → map → resolve pipeline must work first)

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
