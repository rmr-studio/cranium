---
phase: 02-loader-pipeline
plan: 03
subsystem: api
tags: [idempotent-upsert, application-ready-event, stale-reconciliation, pipeline-orchestration, transactional-isolation]

# Dependency graph
requires:
  - phase: 02-loader-pipeline
    plan: 01
    provides: Catalog entities, repositories with delete-by methods, pipeline data classes, stale column
  - phase: 02-loader-pipeline
    plan: 02
    provides: ManifestScannerService, ManifestResolverService for scan and resolve stages
provides:
  - ManifestUpsertService for idempotent persistence with delete-reinsert child reconciliation
  - ManifestLoaderService orchestrator with ApplicationReadyEvent trigger
  - Full operational pipeline: scan -> resolve -> upsert with stale-based reconciliation
  - Integration definitions stale sync post-load
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns: [per-manifest-transactional-isolation, delete-reinsert-child-reconciliation, stale-mark-then-sweep, event-driven-startup-loading]

key-files:
  created:
    - src/main/kotlin/riven/core/service/catalog/ManifestUpsertService.kt
    - src/main/kotlin/riven/core/service/catalog/ManifestLoaderService.kt
    - src/test/kotlin/riven/core/service/catalog/ManifestUpsertServiceTest.kt
    - src/test/kotlin/riven/core/service/catalog/ManifestLoaderServiceTest.kt
  modified:
    - src/main/kotlin/riven/core/repository/catalog/ManifestCatalogRepository.kt

key-decisions:
  - "Added @Transactional to markAllStale() repository method since @Modifying queries require an active transaction and loadAllManifests is deliberately non-transactional"
  - "syncIntegrationDefinitionsStale uses @Transactional protected method within ManifestLoaderService for its own transaction boundary"

patterns-established:
  - "Pipeline orchestrator: non-transactional coordinator delegates to transactional per-item methods for isolation"
  - "Delete-reinsert: cascading child deletion (grandchildren first) followed by fresh insert within single transaction"
  - "Stale sync: post-load sync of derived tables (integration_definitions) from catalog source of truth"

requirements-completed: [LOAD-01, LOAD-02, PERS-01, PERS-02, PERS-03, PERS-04]

# Metrics
duration: 5min
completed: 2026-03-05
---

# Phase 2 Plan 03: Upsert Service and Loader Orchestrator Summary

**ManifestUpsertService with idempotent delete-reinsert persistence, ManifestLoaderService orchestrating ApplicationReadyEvent -> markAllStale -> scan+resolve+upsert (models -> templates -> integrations) -> integration stale sync -> summary log, with per-manifest transaction isolation and 11 unit tests**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-05T07:18:09Z
- **Completed:** 2026-03-05T07:23:37Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- ManifestUpsertService provides idempotent persistence: find-or-create catalog entry, delete all children in cascading order, reinsert fresh children -- all within a single @Transactional boundary
- ManifestLoaderService orchestrates full pipeline on ApplicationReadyEvent: markAllStale -> load models (build index) -> load templates (with model index for $ref) -> load integrations -> sync integration_definitions.stale -> summary log
- Per-manifest exception isolation ensures one bad manifest does not cascade to others
- Stale manifests (failed resolution) only upsert the catalog row with stale=true, no children persisted
- 11 unit tests across both services covering creation, update, stale handling, delete ordering, semantic metadata, load ordering, model index, exception isolation, and integration stale sync

## Task Commits

Each task was committed atomically:

1. **Task 1: ManifestUpsertService + ManifestUpsertServiceTest** - `d1e6fa81e` (test: RED), `ac06cf435` (feat: GREEN)
2. **Task 2: ManifestLoaderService + ManifestLoaderServiceTest** - `ca2ad09e3` (test: RED), `92b371501` (feat: GREEN)

## Files Created/Modified
- `src/main/kotlin/riven/core/service/catalog/ManifestUpsertService.kt` - Idempotent persistence with delete-reinsert child reconciliation
- `src/main/kotlin/riven/core/service/catalog/ManifestLoaderService.kt` - Pipeline orchestrator with ApplicationReadyEvent, stale reconciliation, integration sync
- `src/test/kotlin/riven/core/service/catalog/ManifestUpsertServiceTest.kt` - 5 unit tests for upsert (create, update, stale, delete order, semantic metadata)
- `src/test/kotlin/riven/core/service/catalog/ManifestLoaderServiceTest.kt` - 6 unit tests for loader (stale marking, load order, model index, exception isolation, integration sync, summary log)
- `src/main/kotlin/riven/core/repository/catalog/ManifestCatalogRepository.kt` - Added @Transactional to markAllStale()

## Decisions Made
- Added `@Transactional` to `markAllStale()` repository method -- `@Modifying` JPQL queries require an active transaction, and `loadAllManifests()` is deliberately non-transactional for per-manifest isolation. Putting `@Transactional` on the repository method is the simplest approach.
- `syncIntegrationDefinitionsStale()` is a `@Transactional protected` method within ManifestLoaderService, giving it its own transaction boundary separate from individual manifest upserts.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added @Transactional to markAllStale() repository method**
- **Found during:** Task 2 (ManifestLoaderService)
- **Issue:** markAllStale() is a @Modifying JPQL query that requires an active transaction. Since loadAllManifests() is NOT @Transactional, calling markAllStale() would fail at runtime.
- **Fix:** Added @Transactional annotation directly on markAllStale() in ManifestCatalogRepository
- **Files modified:** ManifestCatalogRepository.kt
- **Verification:** All loader tests pass
- **Committed in:** 92b371501

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Necessary for correctness. Plan explicitly called this out as needed. No scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Full manifest loading pipeline is operational: ApplicationReadyEvent -> scan -> validate -> resolve -> normalize -> upsert -> reconcile
- All 4 service classes complete: ManifestScannerService, ManifestResolverService, ManifestUpsertService, ManifestLoaderService
- Phase 2 (Loader Pipeline) is complete -- all 3 plans executed
- 35 unit tests across all catalog services (6 scanner + 18 resolver + 5 upsert + 6 loader)

---
*Phase: 02-loader-pipeline*
*Completed: 2026-03-05*
