---
phase: 03-trigger-and-dispatch
plan: "02"
subsystem: identity
tags: [temporal, shedlock, scheduled, dispatcher, entity-events, spring-events]

# Dependency graph
requires:
  - phase: 03-trigger-and-dispatch
    plan: "01"
    provides: IdentityMatchTriggerEvent, EntityTypeClassificationService, IdentityMatchQueueService, claimPendingIdentityMatchJobs() and findStaleClaimedIdentityMatchItems() native queries
  - phase: 02-matching-pipeline
    provides: IdentityMatchWorkflow interface with workflowId() companion helper
  - phase: 01-infrastructure
    provides: ExecutionQueueEntity with IDENTITY_MATCH job type, WorkflowExecutionQueueService mark* methods
provides:
  - IdentityMatchQueueProcessorService with REQUIRES_NEW per-item dispatch via Temporal WorkflowClient
  - IdentityMatchDispatcherService with @Scheduled+@SchedulerLock polling and stale recovery
  - EntityService.saveEntity() publishing IdentityMatchTriggerEvent with IDENTIFIER-filtered attribute maps
  - Full wired pipeline: EntityService -> event -> listener -> queue -> dispatcher -> Temporal workflow
affects: [end-to-end identity matching pipeline, EntityService event surface]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "@Scheduled + @SchedulerLock with unique lock names per dispatcher to prevent contention"
    - "REQUIRES_NEW per-item transaction boundary for isolated dispatch failures"
    - "WorkflowExecutionAlreadyStarted catch as idempotency mechanism in Temporal dispatch"
    - "Private extraction publishIdentityMatchTriggerEvent() for single-responsibility in EntityService"

key-files:
  created:
    - src/main/kotlin/riven/core/service/identity/IdentityMatchQueueProcessorService.kt
    - src/main/kotlin/riven/core/service/identity/IdentityMatchDispatcherService.kt
    - src/test/kotlin/riven/core/service/identity/IdentityMatchDispatcherServiceTest.kt
    - src/test/kotlin/riven/core/service/identity/IdentityMatchQueueProcessorServiceTest.kt
  modified:
    - src/main/kotlin/riven/core/service/entity/EntityService.kt
    - src/main/kotlin/riven/core/service/workflow/queue/WorkflowExecutionDispatcherService.kt
    - src/main/kotlin/riven/core/service/workflow/queue/WorkflowExecutionQueueProcessorService.kt
    - src/test/kotlin/riven/core/service/entity/EntityServiceTest.kt

key-decisions:
  - "WorkflowExecutionAlreadyStarted (not 'AlreadyStartedException') is the correct Temporal SDK class — io.temporal.client.WorkflowExecutionAlreadyStarted extends WorkflowException"
  - "@ConditionalOnProperty removed from both workflow dispatchers — Temporal is always required; removing makes both dispatchers symmetrically unconditional"
  - "publishIdentityMatchTriggerEvent() extracted as private method to keep saveEntity() readable and single-purpose"

patterns-established:
  - "Dispatcher-processor split: IdentityMatchDispatcherService schedules batch polling; IdentityMatchQueueProcessorService handles per-item REQUIRES_NEW logic"
  - "Domain event enrichment before publish: EntityService filters to IDENTIFIER attributes only before publishing IdentityMatchTriggerEvent"

requirements-completed: [MATCH-01]

# Metrics
duration: 7min
completed: 2026-03-17
---

# Phase 3 Plan 02: Dispatch Infrastructure and EntityService Event Publishing Summary

**@Scheduled+@SchedulerLock identity match dispatcher with REQUIRES_NEW per-item Temporal dispatch, WorkflowExecutionAlreadyStarted idempotency, stale recovery, and EntityService publishing IDENTIFIER-filtered IdentityMatchTriggerEvent to complete the full pipeline**

## Performance

- **Duration:** 7 min
- **Started:** 2026-03-17T08:51:26Z
- **Completed:** 2026-03-17T08:58:42Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments

- IdentityMatchQueueProcessorService: claims IDENTITY_MATCH jobs via SKIP LOCKED, starts IdentityMatchWorkflow on the `identity.match` task queue, handles `WorkflowExecutionAlreadyStarted` as idempotent success, releases to pending or marks failed based on attempt count
- IdentityMatchDispatcherService: @Scheduled every 5s with @SchedulerLock (`processIdentityMatchQueue`) for batch polling, and @Scheduled every 60s with @SchedulerLock (`recoverStaleIdentityMatchItems`) for stale item recovery — unique lock names prevent contention with workflow execution dispatcher
- EntityService: injects `EntityTypeClassificationService`, filters both old and new attribute maps to IDENTIFIER-classified attributes only, publishes `IdentityMatchTriggerEvent` within the `@Transactional` boundary so the `@TransactionalEventListener(AFTER_COMMIT)` listener fires post-commit
- @ConditionalOnProperty removed from WorkflowExecutionDispatcherService and WorkflowExecutionQueueProcessorService — Temporal is always required
- Full pipeline wired end-to-end: EntityService → IdentityMatchTriggerEvent → IdentityMatchTriggerListener → IdentityMatchQueueService → execution_queue → IdentityMatchDispatcherService → IdentityMatchQueueProcessorService → Temporal IdentityMatchWorkflow

## Task Commits

Each task was committed atomically:

1. **Task 1: Dispatcher, processor, stale recovery, and @ConditionalOnProperty removal** - `9969534ea` (feat)
2. **Task 2: Wire EntityService to publish IdentityMatchTriggerEvent on save/update** - `9af8c0523` (feat)

_Note: Both tasks used TDD (failing tests written first, implementation second)_

## Files Created/Modified

- `src/main/kotlin/riven/core/service/identity/IdentityMatchQueueProcessorService.kt` - REQUIRES_NEW per-item processor: claimBatch(), processItem(), WorkflowExecutionAlreadyStarted handling, retry/fail logic
- `src/main/kotlin/riven/core/service/identity/IdentityMatchDispatcherService.kt` - @Scheduled+@SchedulerLock dispatcher with unique lock names and stale recovery
- `src/main/kotlin/riven/core/service/entity/EntityService.kt` - Added EntityTypeClassificationService injection and publishIdentityMatchTriggerEvent() private method
- `src/main/kotlin/riven/core/service/workflow/queue/WorkflowExecutionDispatcherService.kt` - Removed @ConditionalOnProperty
- `src/main/kotlin/riven/core/service/workflow/queue/WorkflowExecutionQueueProcessorService.kt` - Removed @ConditionalOnProperty
- `src/test/kotlin/riven/core/service/identity/IdentityMatchDispatcherServiceTest.kt` - 4 tests: batch delegation, early return, stale recovery, empty stale
- `src/test/kotlin/riven/core/service/identity/IdentityMatchQueueProcessorServiceTest.kt` - 5 tests: claimBatch, happy-path dispatch, AlreadyStarted handling, retry, max-attempts fail
- `src/test/kotlin/riven/core/service/entity/EntityServiceTest.kt` - Added MockitoBean for EntityTypeClassificationService + 3 new IdentityMatchTriggerEvent tests

## Decisions Made

- `WorkflowExecutionAlreadyStarted` (not `WorkflowExecutionAlreadyStartedException`) is the correct Temporal SDK class — the plan used a name that doesn't exist in `io.temporal:temporal-sdk:1.24.1`. Caught during RED phase when the class could not be found.
- `@ConditionalOnProperty` removed from both workflow dispatchers so the existing and new dispatchers are symmetrically unconditional — Temporal is always a required infrastructure dependency per the phase CONTEXT.md locked decision.
- `publishIdentityMatchTriggerEvent()` extracted as a private method rather than inline in the `entityRepository.save(entity).run { ... }` block — keeps saveEntity() readable and follows the project's function-extraction style guidance.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Temporal exception class name corrected**
- **Found during:** Task 1 (RED phase test authoring)
- **Issue:** Plan specified `WorkflowExecutionAlreadyStartedException` which does not exist in the Temporal SDK. The actual class is `io.temporal.client.WorkflowExecutionAlreadyStarted`.
- **Fix:** Used the correct class name `WorkflowExecutionAlreadyStarted` in both production code and tests.
- **Files modified:** IdentityMatchQueueProcessorService.kt, IdentityMatchQueueProcessorServiceTest.kt
- **Verification:** Tests compile and pass with the correct class.
- **Committed in:** 9969534ea (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 — wrong class name)
**Impact on plan:** Necessary correction; no scope change.

## Issues Encountered

None beyond the Temporal exception class name correction documented above.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Full pipeline is wired end-to-end for MATCH-01.
- The IdentityMatchTriggerListener (Plan 01) enqueues jobs; the dispatcher (this plan) processes them and starts Temporal workflows.
- Phase 3 is now complete — both plans executed successfully.
- `EntityTypeClassificationService.invalidate()` integration with `EntityTypeAttributeService` remains a future improvement point when attributes are added/removed from entity types.

---
*Phase: 03-trigger-and-dispatch*
*Completed: 2026-03-17*
