---
phase: 05-rest-api
plan: "01"
subsystem: identity
tags: [identity, read-api, dto, repository, service, tdd]
dependency_graph:
  requires:
    - 04-confirmation-and-clusters/04-01-PLAN.md
    - 04-confirmation-and-clusters/04-02-PLAN.md
  provides:
    - IdentityReadService with 5 read operations
    - 5 identity API response DTOs
    - Repository query methods for workspace-scoped list and native count
  affects:
    - 05-rest-api/05-02-PLAN.md (controller builds on this service)
tech_stack:
  added: []
  patterns:
    - companion factory from() on SuggestionResponse for domain-to-DTO mapping
    - Nullable ClusterMemberContext fields for graceful soft-delete handling
    - Native query with explicit deleted=false for PENDING count (bypasses @SQLRestriction)
key_files:
  created:
    - src/main/kotlin/riven/core/models/response/identity/SuggestionResponse.kt
    - src/main/kotlin/riven/core/models/response/identity/ClusterSummaryResponse.kt
    - src/main/kotlin/riven/core/models/response/identity/ClusterDetailResponse.kt
    - src/main/kotlin/riven/core/models/response/identity/ClusterMemberContext.kt
    - src/main/kotlin/riven/core/models/response/identity/PendingMatchCountResponse.kt
    - src/main/kotlin/riven/core/service/identity/IdentityReadService.kt
    - src/test/kotlin/riven/core/service/identity/IdentityReadServiceTest.kt
  modified:
    - src/main/kotlin/riven/core/repository/identity/MatchSuggestionRepository.kt
    - src/main/kotlin/riven/core/repository/identity/IdentityClusterRepository.kt
decisions:
  - "SuggestionResponse excludes rejectionSignals — it is internal state for re-suggestion context, not public API"
  - "ClusterMemberContext uses nullable fields (typeKey, sourceType, identifierKey) — entities may be soft-deleted after joining cluster"
  - "countPendingForEntity uses native query with explicit deleted=false because @SQLRestriction does not apply to native queries"
  - "findByIdAndWorkspaceId on IdentityClusterRepository enforces workspace isolation at query level for cluster detail"
metrics:
  duration: 4min
  completed_date: "2026-03-18"
  tasks_completed: 2
  files_changed: 9
---

# Phase 5 Plan 1: Identity Read Service and Response DTOs Summary

**One-liner:** Read-only identity API layer with 5 response DTOs, workspace-scoped repository queries, and IdentityReadService powering list/detail for suggestions, clusters, and pending match count.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Response DTOs and repository query extensions | 6d94ac6 | 5 DTOs, 2 repo extensions |
| 2 | IdentityReadService with full unit tests | f96517d | IdentityReadService.kt, IdentityReadServiceTest.kt |

## What Was Built

### Response DTOs (`models/response/identity/`)

- **SuggestionResponse** — API shape for match suggestions with companion `from(MatchSuggestion)` factory. Excludes `rejectionSignals` (internal state).
- **ClusterSummaryResponse** — Lightweight cluster list item: id, workspaceId, name, memberCount, createdAt.
- **ClusterDetailResponse** — Full cluster view: metadata + enriched member list.
- **ClusterMemberContext** — Per-member enrichment combining join metadata with entity fields (typeKey, sourceType, identifierKey — all nullable for graceful soft-delete handling).
- **PendingMatchCountResponse** — Wraps entity ID + pending count from native query.

### Repository Extensions

- **MatchSuggestionRepository.findByWorkspaceId** — Derived query, `@SQLRestriction` auto-excludes deleted rows (PENDING + CONFIRMED only).
- **MatchSuggestionRepository.countPendingForEntity** — Native query with explicit `AND deleted = false` (native queries bypass `@SQLRestriction`).
- **IdentityClusterRepository.findByIdAndWorkspaceId** — Workspace-scoped single cluster lookup.

### IdentityReadService

5 public methods, all with `@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")`:

1. `listSuggestions(workspaceId)` — returns `List<SuggestionResponse>`
2. `getSuggestion(workspaceId, suggestionId)` — workspace isolation check, `NotFoundException` on mismatch
3. `listClusters(workspaceId)` — returns `List<ClusterSummaryResponse>`
4. `getClusterDetail(workspaceId, clusterId)` — batch entity enrichment via `EntityService.getEntitiesByIds`
5. `getPendingMatchCount(workspaceId, entityId)` — delegates to native count query

## Decisions Made

1. **SuggestionResponse excludes rejectionSignals** — Internal state used only for re-suggestion context; not part of the public API contract.
2. **Nullable ClusterMemberContext fields** — typeKey, sourceType, identifierKey are nullable because a member entity may be soft-deleted after joining the cluster.
3. **countPendingForEntity uses native query** — `@SQLRestriction` does not apply to native SQL queries; explicit `AND deleted = false` is required.
4. **findByIdAndWorkspaceId enforces workspace isolation at query level** — Cleaner than findById + workspace check inline.

## Deviations from Plan

None — plan executed exactly as written.

## Test Coverage

`IdentityReadServiceTest` (10 tests, all passing):
- `ListSuggestions` (2): returns mapped suggestions; returns empty list
- `GetSuggestion` (3): returns suggestion; throws NotFoundException for wrong workspace; throws NotFoundException for missing id
- `ListClusters` (2): returns mapped cluster summaries; returns empty list
- `GetClusterDetail` (3): returns enriched members; handles missing entity with null fields; throws NotFoundException when cluster not found
- `GetPendingMatchCount` (2): returns count; returns 0

## Self-Check: PASSED

All files verified on disk. Both task commits confirmed in git history.
