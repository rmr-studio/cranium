---
phase: 01-infrastructure
plan: 01
subsystem: infra
tags: [postgres, jpa, spring-data, enums, execution-queue]

# Dependency graph
requires:
  - phase: 01-infrastructure-00
    provides: Wave 0 scaffold tests with @Disabled annotations ready to be enabled
provides:
  - execution_queue table (renamed from workflow_execution_queue) with job_type and entity_id columns
  - ExecutionJobType enum (WORKFLOW_EXECUTION, IDENTITY_MATCH)
  - SourceType.IDENTITY_MATCH enum value
  - Dedup partial unique index uq_execution_queue_pending_identity_match
  - Workflow dispatcher isolation via job_type = 'WORKFLOW_EXECUTION' SQL filter on both native queries
affects: [01-02, 01-03, 01-04, identity-match-dispatcher, entity-relationship-service]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Job type discriminator on a shared queue table (execution_queue.job_type)
    - Nullable FK pattern: workflow_definition_id is nullable because IDENTITY_MATCH jobs have no workflow

key-files:
  created:
    - db/schema/01_tables/workflow.sql (execution_queue table definition, renamed from workflow_execution_queue)
    - src/main/kotlin/riven/core/enums/workflow/ExecutionJobType.kt
    - src/test/kotlin/riven/core/service/util/factory/workflow/ExecutionQueueFactory.kt
  modified:
    - db/schema/02_indexes/workflow_indexes.sql
    - src/main/kotlin/riven/core/entity/workflow/ExecutionQueueEntity.kt
    - src/main/kotlin/riven/core/repository/workflow/ExecutionQueueRepository.kt
    - src/main/kotlin/riven/core/models/workflow/engine/queue/ExecutionQueueRequest.kt
    - src/main/kotlin/riven/core/enums/integration/SourceType.kt
    - src/main/kotlin/riven/core/service/workflow/queue/WorkflowExecutionQueueService.kt
    - src/main/kotlin/riven/core/service/workflow/queue/WorkflowExecutionQueueProcessorService.kt
    - src/test/kotlin/riven/core/service/workflow/ExecutionQueueGenericizationTest.kt
    - src/test/kotlin/riven/core/enums/integration/SourceTypeTest.kt
    - src/test/kotlin/riven/core/service/identity/IdentityInfrastructureIntegrationTest.kt

key-decisions:
  - "workflow_definition_id is nullable on execution_queue; callers that need it non-null must check and handle null at their call site (not in toModel())"
  - "Dispatcher isolation enforced at SQL layer with AND job_type = 'WORKFLOW_EXECUTION' in both claimPendingExecutions and findStaleClaimedItems native queries"
  - "Dedup partial unique index (workspace_id, entity_id, job_type) WHERE status='PENDING' AND entity_id IS NOT NULL prevents race-condition duplicate identity match jobs"

patterns-established:
  - "Discriminator pattern: shared execution_queue.job_type column determines which dispatcher owns each job type"
  - "Factory pattern: ExecutionQueueFactory.createWorkflowExecutionJob / createIdentityMatchJob for test data construction"

requirements-completed: [INFRA-01, INFRA-02, INFRA-03]

# Metrics
duration: 9min
completed: 2026-03-16
---

# Phase 1 Plan 01: Generic Execution Queue with Job Type Discriminator Summary

**execution_queue table genericized from workflow-only to multi-job with WORKFLOW_EXECUTION/IDENTITY_MATCH discriminator, dedup partial unique index, and SourceType.IDENTITY_MATCH — the prerequisite infrastructure for Phase 3+ identity matching**

## Performance

- **Duration:** 9 min
- **Started:** 2026-03-16T09:48:24Z
- **Completed:** 2026-03-16T09:57:00Z
- **Tasks:** 2
- **Files modified:** 11

## Accomplishments

- Renamed `workflow_execution_queue` to `execution_queue` in SQL schema and all JPA/repository layers
- Added `job_type` VARCHAR(30) discriminator column and `entity_id` UUID nullable FK; made `workflow_definition_id` nullable
- Created `ExecutionJobType` enum with `WORKFLOW_EXECUTION` and `IDENTITY_MATCH` values
- Added dedup partial unique index `uq_execution_queue_pending_identity_match` preventing race-condition duplicate identity match jobs
- Both native queries now filter `AND job_type = 'WORKFLOW_EXECUTION'` so the workflow dispatcher never claims identity match jobs
- Added `IDENTITY_MATCH` to `SourceType` enum (INFRA-03)
- Enabled all Wave 0 test scaffolds: 3 tests in `ExecutionQueueGenericizationTest` and 1 in `SourceTypeTest`

## Task Commits

1. **Task 1: Genericize execution queue SQL, entity, repository, and model** - `677e3e0db` (feat)
2. **Task 2: Add IDENTITY_MATCH to SourceType, enable Wave 0 tests** - `fdd801c95` (feat)

**Plan metadata:** `02e290e99` (docs: complete plan)

## Files Created/Modified

- `db/schema/01_tables/workflow.sql` - Renamed table to `execution_queue`, added `job_type`, `entity_id`, made `workflow_definition_id` nullable
- `db/schema/02_indexes/workflow_indexes.sql` - Updated existing index table reference; added `uq_execution_queue_pending_identity_match` dedup index
- `src/main/kotlin/riven/core/enums/workflow/ExecutionJobType.kt` - New enum: WORKFLOW_EXECUTION, IDENTITY_MATCH
- `src/main/kotlin/riven/core/entity/workflow/ExecutionQueueEntity.kt` - Table name, jobType, entityId fields, nullable workflowDefinitionId
- `src/main/kotlin/riven/core/repository/workflow/ExecutionQueueRepository.kt` - Both native queries updated with table name and job_type filter
- `src/main/kotlin/riven/core/models/workflow/engine/queue/ExecutionQueueRequest.kt` - Added jobType, entityId; workflowDefinitionId now nullable
- `src/main/kotlin/riven/core/enums/integration/SourceType.kt` - Added IDENTITY_MATCH
- `src/main/kotlin/riven/core/service/workflow/queue/WorkflowExecutionQueueService.kt` - Explicit jobType on enqueue
- `src/main/kotlin/riven/core/service/workflow/queue/WorkflowExecutionQueueProcessorService.kt` - Null-safety for nullable workflowDefinitionId; getOrCreateExecution signature updated
- `src/test/kotlin/riven/core/service/util/factory/workflow/ExecutionQueueFactory.kt` - New: test factory for ExecutionQueueEntity
- `src/test/kotlin/riven/core/service/workflow/ExecutionQueueGenericizationTest.kt` - 3 tests enabled (INFRA-01 coverage)
- `src/test/kotlin/riven/core/enums/integration/SourceTypeTest.kt` - 1 test enabled (INFRA-03 coverage)

## Decisions Made

- `workflow_definition_id` made nullable at the entity and model layer — callers that process WORKFLOW_EXECUTION jobs must null-check at their call site. `toModel()` does not assert non-null because the model field is legitimately nullable for IDENTITY_MATCH jobs.
- Dispatcher isolation is at the SQL layer (not application layer) for both `claimPendingExecutions` and `findStaleClaimedItems` — ensures correctness even if future callers forget to filter by job type.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed WorkflowExecutionQueueProcessorService null-safety for nullable workflowDefinitionId**
- **Found during:** Task 1 (entity/service updates)
- **Issue:** `dispatchToTemporal()` directly accessed `item.workflowDefinitionId` (now nullable) without null check, causing compile error and potential NPE
- **Fix:** Added null check at the top of `dispatchToTemporal()`, extracted `workflowDefinitionId` as a local val, threaded it through `getOrCreateExecution()` signature
- **Files modified:** `WorkflowExecutionQueueProcessorService.kt`
- **Verification:** `./gradlew build` succeeded; all workflow tests pass
- **Committed in:** `677e3e0db` (Task 1 commit)

**2. [Rule 3 - Blocking] Fixed pre-existing `PostgreSQLContainer<*>` type argument compile error in IdentityInfrastructureIntegrationTest**
- **Found during:** Task 2 (test compilation)
- **Issue:** `IdentityInfrastructureIntegrationTest.kt` used `PostgreSQLContainer<*>` but the class doesn't accept type arguments in the Testcontainers version in use — blocked entire test suite compilation
- **Fix:** Removed `<*>` type argument (handled by linter automatically)
- **Files modified:** `IdentityInfrastructureIntegrationTest.kt`
- **Verification:** `./gradlew compileTestKotlin` succeeded
- **Committed in:** `fdd801c95` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (1 bug, 1 blocking)
**Impact on plan:** Both fixes necessary for correct compilation and runtime safety. No scope creep.

## Issues Encountered

- `IdentityInfrastructureIntegrationTest` integration tests (5 tests) fail because they require a real PostgreSQL database with the identity schema applied. These are pre-existing failures introduced by plan 01-02 scaffolding and are expected to fail until those plans are fully applied against a real PostgreSQL instance. They were failing (as part of 96 total failures) before this plan's work and are not caused by these changes.

## Next Phase Readiness

- execution_queue table is the shared infrastructure prerequisite for all identity resolution phases
- `ExecutionJobType.IDENTITY_MATCH` and `SourceType.IDENTITY_MATCH` are ready for use in Phase 3 (identity match dispatcher)
- Dedup index in place — Phase 3 can safely enqueue identity match jobs without application-level dedup logic
- All existing workflow dispatch tests continue to pass

---
*Phase: 01-infrastructure*
*Completed: 2026-03-16*

## Self-Check: PASSED

- ExecutionJobType.kt: FOUND
- ExecutionQueueEntity.kt: FOUND
- 01-01-SUMMARY.md: FOUND
- Commit 677e3e0db (Task 1): FOUND
- Commit fdd801c95 (Task 2): FOUND
