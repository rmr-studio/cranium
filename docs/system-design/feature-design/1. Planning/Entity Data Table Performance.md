---
tags:
  - "#status/draft"
  - priority/high
  - domain/entity
  - architecture/design
Created: 2026-03-08
Updated:
Domains:
  - "[[Entities]]"
---
# Feature: Entity Data Table Performance

---

## 1. Overview

### Problem Statement

Data tables need to handle 10k+ entities with fast filtering, sorting, search, and pagination. As entity counts grow, the current query implementation has two performance gaps:

1. **Redundant COUNT queries** — every paginated request executes a parallel `COUNT(*)` query, even on scroll-pagination pages where the frontend already has `totalCount` from the initial load. This doubles DB load for every page fetch.
2. **No payload projection** — every query returns the full entity payload (all attributes + all relationships), even when the data table only displays 8 of 30+ columns. This wastes bandwidth and serialisation time.

These gaps compound at scale: a user scrolling through a 10k-entity table with 30 attributes triggers unnecessary count queries on every page and transfers 3-4x more payload data than needed.

### Proposed Solution

Add an `includeCount` flag to skip the COUNT query on scroll-pagination requests, using a `limit + 1` fetch pattern to determine `hasNextPage` without a full count. Wire the existing `QueryProjection` model to filter entity payloads down to visible columns only, applied as a post-hydration step in the facade service.

### Success Criteria

- [ ] `includeCount: false` requests skip the COUNT query and return `totalCount: null`
- [ ] `hasNextPage` is correctly determined via limit+1 fetch when count is skipped
- [ ] Projection filters entity payloads to only requested attributes and relationships
- [ ] No regression in existing query behaviour (includeCount defaults to true)

---

## 2. Frontend Interaction Model

The full request lifecycle for a data table:

1. **Initial load** — `POST /query` with `includeCount: true`, default pagination. Frontend receives `totalCount` and caches it for scroll indicators.
2. **Scroll pagination** — `POST /query` with `includeCount: false`, incremented offset. Frontend uses cached `totalCount`; `hasNextPage` comes from limit+1 detection.
3. **Filter change** — `POST /query` with `includeCount: true` (new filter changes total). Frontend replaces cached `totalCount`.
4. **Sort change** — `POST /query` with `includeCount: false` (sort doesn't change total). Same cached count.
5. **Search** — `POST /query` with `includeCount: true` and search filter. New count needed.

**State machine:** Frontend owns view state (cached totalCount, current filters, sort, scroll position). Backend is a stateless query executor — no server-side session or cursor state.

**Cancellation/debounce:** Frontend should cancel in-flight requests when filters/sort change rapidly. Backend queries are stateless so abandoned requests have no server-side cleanup cost beyond the DB query itself.

---

## 3. Performance Gaps (Prioritised)

### Implementing Now

1. **`includeCount` optimisation** — Skip COUNT query on scroll-pagination. Saves one DB round-trip per page fetch. Uses standard limit+1 pattern (GitHub GraphQL, Stripe, etc.).
2. **Projection filtering** — Filter entity payloads to visible columns. Applied post-hydration in the facade. Reduces response payload size proportional to hidden columns.

### Future

3. **`QueryFilter.Search`** — Cross-attribute text search. Requires defining search semantics (which attribute types are searchable, partial vs full match). Would add a new `QueryFilter` variant.
4. **Cursor-based pagination** — Replace offset-based pagination for deep pagination scenarios. Low priority at current scale (offset-based is correct and performant for <100k rows with proper indexing).
5. **`pg_trgm` indexing** — GIN trigram indexes for fuzzy text search. Dependent on search filter implementation. Would enable `ILIKE '%term%'` queries without full table scans.

---

## 4. API Changes

### `includeCount` Flag

**Request:**
```json
{
  "includeCount": false,
  "pagination": { "limit": 100, "offset": 200 }
}
```

**Response when `includeCount: false`:**
```json
{
  "entities": [...],
  "totalCount": null,
  "hasNextPage": true,
  "limit": 100,
  "offset": 200
}
```

`totalCount` becomes nullable (`Long?`). When `includeCount` is false, the backend fetches `limit + 1` rows and returns `limit` rows. If `limit + 1` rows were returned, `hasNextPage = true`.

### Projection

**Request:**
```json
{
  "projection": {
    "includeAttributes": ["uuid-1", "uuid-2"],
    "includeRelationships": ["uuid-3"]
  }
}
```

**Response:** Entity payloads contain only the specified attribute and relationship keys. Null projection returns full payload (backward compatible).

---

## 5. What's Already Correct

- **Stateless backend** — no session state, cursor state, or server-side view management
- **Single POST endpoint** — `POST /api/v1/entity/workspace/{workspaceId}/type/{entityTypeId}/query` handles all query variations
- **Full filter tree per request** — AND/OR composition, attribute filters with typed operators, relationship traversal filters
- **Offset-based pagination** — correct at current scale, deterministic ordering with `created_at DESC, id ASC`
- **Parallel data+count execution** — coroutine-based async when count is needed

---

## 6. Open Questions

- **Search UX** — Should search be a filter bar (adds to existing filter tree) or a global search (separate from filters)? Impacts whether search is a `QueryFilter` variant or a top-level request field.
- **Saved views / view configs** — Should the backend persist view configurations (column selection, default filters, sort order) per entity type per user? Or is this purely frontend state?
- **Real-time updates** — Should the data table reflect entity changes in real-time (WebSocket/SSE push), or rely on periodic re-fetch? Impacts whether we need a change notification system.
- **Virtual scrolling** — For 10k+ rows, should the backend support a streaming/chunked response format, or is standard pagination sufficient with frontend virtual scrolling?
