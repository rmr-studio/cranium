---
phase: 03-read-surface-and-integration-tests
plan: 02
subsystem: testing
tags: [testcontainers, postgresql, integration-tests, spring-boot-test, manifest-loader]

# Dependency graph
requires:
  - phase: 03-read-surface-and-integration-tests
    provides: "ManifestCatalogService, query models, batch repository methods from plan 01"
  - phase: 02-loader-pipeline
    provides: "ManifestScannerService, ManifestResolverService, ManifestUpsertService, ManifestLoaderService pipeline"
provides:
  - "Integration tests verifying full scan-resolve-upsert pipeline against real PostgreSQL"
  - "Configurable base path on ManifestScannerService for test fixture isolation"
  - "Bug fixes: flush after deleteChildren, clearAutomatically on markAllStale"
affects: [production-stability, catalog-service-consumers]

# Tech tracking
tech-stack:
  added: []
  patterns: [testcontainers-postgresql-singleton, file-based-scanner-override, temp-dir-fixture-management]

key-files:
  created:
    - src/test/kotlin/riven/core/service/catalog/ManifestLoaderIntegrationTest.kt
    - src/test/kotlin/riven/core/service/catalog/ManifestLoaderIntegrationTestConfig.kt
  modified:
    - src/main/kotlin/riven/core/service/catalog/ManifestScannerService.kt
    - src/main/kotlin/riven/core/service/catalog/ManifestUpsertService.kt
    - src/main/kotlin/riven/core/repository/catalog/ManifestCatalogRepository.kt
    - src/test/kotlin/riven/core/service/catalog/ManifestScannerServiceTest.kt

key-decisions:
  - "Use file: temp dir for all integration tests (not classpath) to enable file deletion for removal test"
  - "Single test class with @Order annotations instead of separate class for removal test"

patterns-established:
  - "Catalog integration test config: wire all catalog services as beans with mock KLogger"
  - "Temp dir fixture management: copy classpath fixtures to temp dir, set riven.manifests.base-path via DynamicPropertySource"

requirements-completed: [TEST-06, TEST-07, TEST-08]

# Metrics
duration: 7min
completed: 2026-03-05
---

# Phase 3 Plan 2: Integration Tests Summary

**End-to-end integration tests for manifest loader pipeline using Testcontainers PostgreSQL, verifying startup load, idempotent reload, and manifest removal reconciliation**

## Performance

- **Duration:** 7 min
- **Started:** 2026-03-05T08:40:40Z
- **Completed:** 2026-03-05T08:47:35Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- Added configurable `basePath` to ManifestScannerService with backward-compatible `classpath:manifests` default
- Created 3 integration tests exercising the full loader pipeline against real PostgreSQL
- Discovered and fixed 2 production bugs: missing flush after derived deletes, and stale persistence context after bulk update

## Task Commits

Each task was committed atomically:

1. **Task 1: Refactor ManifestScannerService for configurable base path** - `251c2c6be` (feat)
2. **Task 2: Integration test config and end-to-end tests** - `714bb595c` (feat)

## Files Created/Modified
- `src/test/kotlin/riven/core/service/catalog/ManifestLoaderIntegrationTest.kt` - 3 integration tests (full load, idempotent reload, manifest removal)
- `src/test/kotlin/riven/core/service/catalog/ManifestLoaderIntegrationTestConfig.kt` - Spring test config wiring all catalog services
- `src/main/kotlin/riven/core/service/catalog/ManifestScannerService.kt` - Added @Value basePath parameter
- `src/main/kotlin/riven/core/service/catalog/ManifestUpsertService.kt` - Added flush after deleteChildren
- `src/main/kotlin/riven/core/repository/catalog/ManifestCatalogRepository.kt` - Added clearAutomatically on markAllStale
- `src/test/kotlin/riven/core/service/catalog/ManifestScannerServiceTest.kt` - Updated constructor call with basePath

## Decisions Made
- Used file-based temp dir for all integration tests rather than classpath resources, enabling file deletion for the removal test
- Single test class with @Order annotations keeps tests in a shared Spring context (faster than separate classes)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed missing flush after derived delete methods in ManifestUpsertService**
- **Found during:** Task 2 (integration tests)
- **Issue:** Derived delete methods (deleteByManifestId) use em.remove() which defers SQL execution until flush. When insertEntityTypes runs immediately after, Hibernate tries to INSERT before DELETEs are flushed, causing unique constraint violations on (manifest_id, key).
- **Fix:** Added `catalogEntityTypeRepository.flush()` at the end of `deleteChildren()` to force pending DELETEs to execute before INSERTs
- **Files modified:** ManifestUpsertService.kt
- **Verification:** All 3 integration tests pass, including idempotent reload which exercises delete-then-reinsert
- **Committed in:** 714bb595c (Task 2 commit)

**2. [Rule 1 - Bug] Fixed stale persistence context after markAllStale bulk JPQL update**
- **Found during:** Task 2 (integration tests)
- **Issue:** `markAllStale()` uses `@Modifying` JPQL UPDATE which bypasses Hibernate entity cache. Without `clearAutomatically = true`, subsequent queries within the same persistence context may return cached entities with stale=false instead of the DB value stale=true.
- **Fix:** Added `clearAutomatically = true` to `@Modifying` annotation on `markAllStale()`
- **Files modified:** ManifestCatalogRepository.kt
- **Verification:** Idempotent reload test passes -- second load correctly finds and updates stale entries
- **Committed in:** 714bb595c (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (2 bugs)
**Impact on plan:** Both bugs were production defects in the upsert/reconciliation flow that only manifest under real database constraints (not visible in unit tests with mocks). The integration tests successfully surfaced them.

## Issues Encountered
None beyond the auto-fixed bugs above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All 3 phases complete: database foundation, loader pipeline, read surface + integration tests
- The manifest catalog system is fully tested end-to-end with real PostgreSQL

---
*Phase: 03-read-surface-and-integration-tests*
*Completed: 2026-03-05*
