---
phase: 03-trigger-and-dispatch
plan: "01"
subsystem: identity
tags: [spring-events, transactional-event-listener, concurrent-hash-map, execution-queue, identity-match]

# Dependency graph
requires:
  - phase: 01-infrastructure
    provides: ExecutionQueueEntity with IDENTITY_MATCH job type, dedup partial unique index, ExecutionQueueRepository
  - phase: 02-matching-pipeline
    provides: EntityTypeSemanticMetadataRepository with IDENTIFIER classification queries
provides:
  - IdentityMatchTriggerEvent domain event class
  - EntityTypeClassificationService with ConcurrentHashMap IDENTIFIER attribute cache
  - IdentityMatchQueueService with DataIntegrityViolationException dedup pattern
  - claimPendingIdentityMatchJobs() and findStaleClaimedIdentityMatchItems() native queries on ExecutionQueueRepository
  - IdentityMatchTriggerListener (@TransactionalEventListener AFTER_COMMIT + @Transactional REQUIRES_NEW)
affects: [03-trigger-and-dispatch plan 02, EntityService event publishing]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "@TransactionalEventListener(AFTER_COMMIT) + @Transactional(REQUIRES_NEW) for post-commit side effects"
    - "ConcurrentHashMap<UUID, Set<UUID>> for immutable classification cache with explicit invalidation"
    - "DataIntegrityViolationException catch as idempotency mechanism for dedup partial unique index"
    - "Separate native query per job_type to avoid dispatcher cross-contamination"

key-files:
  created:
    - src/main/kotlin/riven/core/models/identity/IdentityMatchTriggerEvent.kt
    - src/main/kotlin/riven/core/service/identity/EntityTypeClassificationService.kt
    - src/main/kotlin/riven/core/service/identity/IdentityMatchQueueService.kt
    - src/main/kotlin/riven/core/service/entity/IdentityMatchTriggerListener.kt
    - src/test/kotlin/riven/core/service/identity/EntityTypeClassificationServiceTest.kt
    - src/test/kotlin/riven/core/service/identity/IdentityMatchQueueServiceTest.kt
    - src/test/kotlin/riven/core/service/entity/IdentityMatchTriggerListenerTest.kt
  modified:
    - src/main/kotlin/riven/core/repository/workflow/ExecutionQueueRepository.kt

key-decisions:
  - "Cache stores Set<UUID> of IDENTIFIER attribute IDs (not Boolean) — enables both hasIdentifierAttributes() and getIdentifierAttributeIds() without a second query"
  - "IdentityMatchTriggerListener placed in service.entity package (not service.identity) — it consumes entity domain events, mirroring WorkspaceAnalyticsListener placement in service.analytics"
  - "On create, always enqueue if entity type has IDENTIFIER attributes even if newIdentifierAttributes is empty — attribute values may be set separately; create always warrants matching"

patterns-established:
  - "Listener-in-domain-package: listeners for domain events live in the domain's service package, not the consuming service's package"
  - "Classification cache: ConcurrentHashMap<UUID, Set<UUID>> with invalidate() for attribute lifecycle events"

requirements-completed: [MATCH-01]

# Metrics
duration: 4min
completed: 2026-03-17
---

# Phase 3 Plan 01: Trigger Infrastructure Summary

**@TransactionalEventListener AFTER_COMMIT trigger pipeline with ConcurrentHashMap IDENTIFIER attribute cache, DataIntegrityViolationException dedup enqueue, and IDENTITY_MATCH-filtered native repository queries**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-17T08:41:09Z
- **Completed:** 2026-03-17T08:45:56Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments

- IdentityMatchTriggerEvent data class carries entityId, workspaceId, entityTypeId, isUpdate flag, and pre/post IDENTIFIER attribute value maps
- EntityTypeClassificationService caches IDENTIFIER attribute IDs per entity type using ConcurrentHashMap with explicit invalidation — single method serves both boolean check and ID set retrieval without double queries
- IdentityMatchQueueService enqueues IDENTITY_MATCH jobs with DataIntegrityViolationException catch matching the dedup partial unique index on (workspace_id, entity_id, job_type) WHERE status='PENDING'
- ExecutionQueueRepository gains claimPendingIdentityMatchJobs() and findStaleClaimedIdentityMatchItems() native queries filtered exclusively on IDENTITY_MATCH job_type
- IdentityMatchTriggerListener applies two-stage filter: IDENTIFIER attribute presence check then change detection on update, uses @Transactional(REQUIRES_NEW) mandatory for post-AFTER_COMMIT writes

## Task Commits

Each task was committed atomically:

1. **Task 1: Event class, classification service, queue service, and repository queries** - `7ee27e856` (feat)
2. **Task 2: Identity match trigger listener with IDENTIFIER filtering and change detection** - `05f31bfaa` (feat)

_Note: Both tasks used TDD (failing tests written first, implementation second)_

## Files Created/Modified

- `src/main/kotlin/riven/core/models/identity/IdentityMatchTriggerEvent.kt` - Domain event with pre/post IDENTIFIER attribute maps for change detection
- `src/main/kotlin/riven/core/service/identity/EntityTypeClassificationService.kt` - ConcurrentHashMap IDENTIFIER attribute ID cache with invalidation
- `src/main/kotlin/riven/core/service/identity/IdentityMatchQueueService.kt` - Enqueue IDENTITY_MATCH jobs with dedup exception swallowing
- `src/main/kotlin/riven/core/service/entity/IdentityMatchTriggerListener.kt` - @TransactionalEventListener(AFTER_COMMIT) + @Transactional(REQUIRES_NEW) with two-stage filter logic
- `src/main/kotlin/riven/core/repository/workflow/ExecutionQueueRepository.kt` - Added claimPendingIdentityMatchJobs() and findStaleClaimedIdentityMatchItems() native queries
- `src/test/kotlin/riven/core/service/identity/EntityTypeClassificationServiceTest.kt` - Cache hit/miss, invalidation, and attribute ID set tests
- `src/test/kotlin/riven/core/service/identity/IdentityMatchQueueServiceTest.kt` - Enqueue field assertions and dedup silence test
- `src/test/kotlin/riven/core/service/entity/IdentityMatchTriggerListenerTest.kt` - All 5 decision paths: create, no-identifiers, update-changed, update-unchanged, create-empty-values

## Decisions Made

- Cache stores `Set<UUID>` of IDENTIFIER attribute IDs rather than a Boolean — the set enables both `hasIdentifierAttributes()` (isEmpty check) and `getIdentifierAttributeIds()` without requiring a second repository query.
- Listener placed in `service.entity` package — it listens to entity domain events, consistent with `WorkspaceAnalyticsListener` living in `service.analytics`.
- On create, always enqueue when entity type has IDENTIFIER attributes even if `newIdentifierAttributes` is empty — attribute values may be populated in a separate step; the entity type configuration is the signal, not the current attribute values.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Trigger infrastructure is complete. Plan 02 can now add the `IdentityMatchDispatcherService` and `IdentityMatchQueueProcessorService` to claim IDENTITY_MATCH jobs and start Temporal workflows.
- `EntityService` still needs to publish `IdentityMatchTriggerEvent` (also Plan 02 scope per RESEARCH.md).
- `claimPendingIdentityMatchJobs()` and `findStaleClaimedIdentityMatchItems()` queries are ready for the dispatcher.
- `EntityTypeClassificationService.invalidate()` should be called from `EntityTypeAttributeService` when attributes are added/removed from an entity type (future integration point).

---
*Phase: 03-trigger-and-dispatch*
*Completed: 2026-03-17*
