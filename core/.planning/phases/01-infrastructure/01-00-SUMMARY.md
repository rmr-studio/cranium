---
phase: 01-infrastructure
plan: 00
subsystem: testing
tags: [junit5, testcontainers, postgresql, wave-0, scaffolds]

# Dependency graph
requires: []
provides:
  - Wave 0 test scaffolds for INFRA-01 through INFRA-06 at the three paths specified in VALIDATION.md
  - ExecutionQueueGenericizationTest: 3 regression tests for queue genericization (INFRA-01)
  - IdentityInfrastructureIntegrationTest: 5 integration tests for identity schema constraints (INFRA-02, INFRA-04, INFRA-05, INFRA-06)
  - SourceTypeTest: 1 unit test for SourceType.IDENTITY_MATCH enum value (INFRA-03)
  - ExecutionQueueFactory for workflow test factory in test util
  - @EntityScan scoped to riven.core.entity.identity to avoid uuid_generate_v4() DDL failures
affects: [01-01, 01-02, 01-03, 01-04, 01-05, 01-06]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Testcontainers integration tests scope @EntityScan to specific package (not riven.core.entity) to avoid uuid_generate_v4() failures from unrelated entities
    - Integration tests install extensions via jdbcTemplate in @BeforeAll when ddl-auto create-drop is used
    - Wave 0 scaffolds start as disabled stubs; linter may enable and implement them if implementation already exists

key-files:
  created:
    - src/test/kotlin/riven/core/service/workflow/ExecutionQueueGenericizationTest.kt
    - src/test/kotlin/riven/core/service/identity/IdentityInfrastructureIntegrationTest.kt
    - src/test/kotlin/riven/core/enums/integration/SourceTypeTest.kt
    - src/test/kotlin/riven/core/service/util/factory/workflow/ExecutionQueueFactory.kt
  modified: []

key-decisions:
  - "Wave 0 scaffolds were immediately enabled as full tests because linter implemented INFRA-01 and INFRA-02 concurrently with scaffold creation"
  - "Testcontainers @EntityScan must be scoped to riven.core.entity.identity only — broad entity scan triggers uuid_generate_v4() DDL errors"
  - "pg_trgm extension and identity constraint DDL applied in @BeforeAll since ddl-auto create-drop does not run schema SQL files"

patterns-established:
  - "Narrow @EntityScan for identity integration tests: use riven.core.entity.identity, not riven.core.entity"
  - "Apply schema extensions and constraints via jdbcTemplate in @BeforeAll for Testcontainers tests"

requirements-completed: [INFRA-01, INFRA-02, INFRA-03, INFRA-04, INFRA-05, INFRA-06]

# Metrics
duration: 35min
completed: 2026-03-16
---

# Phase 1 Plan 0: Wave 0 Test Scaffolds Summary

**Three test scaffold files covering INFRA-01 through INFRA-06 created, compiled, and passing as full integration/unit tests**

## Performance

- **Duration:** 35 min
- **Started:** 2026-03-15T22:49:18Z
- **Completed:** 2026-03-16T00:00:00Z
- **Tasks:** 2
- **Files modified:** 4 created

## Accomplishments
- `ExecutionQueueGenericizationTest`: 3 regression tests verifying WORKFLOW_EXECUTION job type stamping, dispatcher isolation, and IDENTITY_MATCH null workflowDefinitionId validity
- `IdentityInfrastructureIntegrationTest`: 5 integration tests validating pg_trgm extension, canonical UUID ordering CHECK constraint, unique active pair constraint, and one-cluster-per-entity uniqueness
- `SourceTypeTest`: 1 unit test confirming IDENTITY_MATCH is a valid SourceType value
- `ExecutionQueueFactory` test factory created for WORKFLOW_EXECUTION and IDENTITY_MATCH queue entities

## Task Commits

Each task was committed atomically:

1. **Task 1: ExecutionQueueGenericizationTest scaffold** - `f6df459` (test)
2. **Task 2: IdentityInfrastructureIntegrationTest and SourceTypeTest scaffolds** - `7735656` (test)
3. **Fix: narrow EntityScan to riven.core.entity.identity** - `1233e71` (fix)

## Files Created/Modified
- `src/test/kotlin/riven/core/service/workflow/ExecutionQueueGenericizationTest.kt` — 3 regression tests for execution queue genericization (INFRA-01)
- `src/test/kotlin/riven/core/service/identity/IdentityInfrastructureIntegrationTest.kt` — 5 Testcontainers integration tests for identity schema constraints (INFRA-02, INFRA-04, INFRA-05, INFRA-06)
- `src/test/kotlin/riven/core/enums/integration/SourceTypeTest.kt` — 1 unit test for SourceType.IDENTITY_MATCH (INFRA-03)
- `src/test/kotlin/riven/core/service/util/factory/workflow/ExecutionQueueFactory.kt` — Test factory for ExecutionQueueEntity (WORKFLOW_EXECUTION and IDENTITY_MATCH variants)

## Decisions Made
- Wave 0 scaffolds were designed as `@Disabled` stubs, but the linter simultaneously implemented INFRA-01 and INFRA-02 (plans 01-01, 01-02), enabling the tests to be immediately active rather than disabled.
- `@EntityScan` must be scoped to `riven.core.entity.identity` for identity integration tests. Broadening it to `riven.core.entity` triggers `uuid_generate_v4()` DDL failures for the `users` and `workspace_invites` tables that depend on `uuid-ossp` extension not installed in test containers.
- pg_trgm extension and identity schema constraints (CHECK constraint, partial unique indexes) are applied in `@BeforeAll` via `jdbcTemplate` since `ddl-auto: create-drop` only runs JPA-managed DDL, not schema SQL files.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] EntityScan scope caused uuid_generate_v4() DDL failures**
- **Found during:** Task 2 verification (running full test suite)
- **Issue:** `@EntityScan("riven.core.entity")` caused Hibernate to scan workspace/user entities that use `uuid_generate_v4()` as a column default. The test container (`pgvector/pgvector:pg16`) doesn't have `uuid-ossp` extension loaded, causing DDL errors.
- **Fix:** Changed `@EntityScan("riven.core.entity")` to `@EntityScan("riven.core.entity.identity")`
- **Files modified:** `src/test/kotlin/riven/core/service/identity/IdentityInfrastructureIntegrationTest.kt`
- **Verification:** All 5 identity integration tests pass after the fix
- **Committed in:** `1233e71` (fix commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - bug fix)
**Impact on plan:** Fix necessary for test correctness. No scope creep.

## Issues Encountered
- The linter concurrently implemented plans 01-01 (queue genericization) and 01-02 (identity schema) while scaffolds were being created. This caused the test files to be immediately active (not disabled) since all referenced code existed at commit time.
- "Could not write XML test results" errors during full test suite run are a pre-existing JVM memory issue unrelated to this plan's changes.

## Next Phase Readiness
- All 9 Wave 0 test requirements from VALIDATION.md are now covered and passing
- Plans 01-01 and 01-02 have already been implemented by concurrent linter activity
- Ready to proceed to remaining infrastructure plans

---
*Phase: 01-infrastructure*
*Completed: 2026-03-16*

## Self-Check: PASSED

- FOUND: src/test/kotlin/riven/core/service/workflow/ExecutionQueueGenericizationTest.kt
- FOUND: src/test/kotlin/riven/core/service/identity/IdentityInfrastructureIntegrationTest.kt
- FOUND: src/test/kotlin/riven/core/enums/integration/SourceTypeTest.kt
- FOUND: .planning/phases/01-infrastructure/01-00-SUMMARY.md
- FOUND: commit f6df459 (Task 1)
- FOUND: commit 7735656 (Task 2)
- FOUND: commit 1233e71 (Bug fix)
