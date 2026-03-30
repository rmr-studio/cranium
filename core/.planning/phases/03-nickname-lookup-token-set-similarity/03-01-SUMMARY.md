---
phase: 03-nickname-lookup-token-set-similarity
plan: 01
subsystem: identity
tags: [kotlin, nickname, name-matching, similarity, overlap-coefficient, token-set]

# Dependency graph
requires: []
provides:
  - "NicknameExpander Kotlin object with ~150 bidirectional English name groups"
  - "TokenSimilarity Kotlin object with overlap coefficient computation"
  - "Unit tests for both utilities covering all edge cases"
affects:
  - 03-nickname-lookup-token-set-similarity  # Plan 02 wires these into CandidateService

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Kotlin object singleton for pure stateless utilities (not Spring beans)"
    - "Bidirectional index built at object init from GROUPS list"
    - "Overlap coefficient: |A ∩ B| / min(|A|, |B|) for subset-aware similarity"

key-files:
  created:
    - src/main/kotlin/riven/core/service/identity/NicknameExpander.kt
    - src/main/kotlin/riven/core/service/identity/TokenSimilarity.kt
    - src/test/kotlin/riven/core/service/identity/NicknameExpanderTest.kt
    - src/test/kotlin/riven/core/service/identity/TokenSimilarityTest.kt
  modified:
    - src/test/kotlin/riven/core/service/ingestion/ProjectionPipelineIntegrationTestBase.kt

key-decisions:
  - "NicknameExpander as Kotlin object (not Spring bean) — pure stateless lookup, no dependencies, testable without Spring context"
  - "Bidirectional INDEX built at init from GROUPS list — each name variant maps to its full group"
  - "TokenSimilarity uses overlap coefficient not Jaccard — enables subset containment score of 1.0"

patterns-established:
  - "Kotlin object singletons for pure computation utilities in service.identity package"
  - "Name group list with bidirectional reverse index for O(1) lookup"

requirements-completed: [UTIL-01, UTIL-02, TEST-02, TEST-03]

# Metrics
duration: 3min
completed: 2026-03-31
---

# Phase 03 Plan 01: NicknameExpander and TokenSimilarity Summary

**Bidirectional English nickname lookup with ~150 name groups and overlap coefficient similarity computation for subset-aware name token matching**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-30T22:12:29Z
- **Completed:** 2026-03-30T22:15:39Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- NicknameExpander Kotlin object with ~150 English name groups, bidirectional INDEX, and expand()/areEquivalent() methods
- TokenSimilarity Kotlin object computing overlap coefficient — scores "John" vs "John Smith" as 1.0 (subset containment)
- Comprehensive unit tests for both: bidirectionality, case insensitivity, unknown names, empty inputs, partial overlap, whitespace handling

## Task Commits

Each task was committed atomically:

1. **Task 1: Create NicknameExpander and TokenSimilarity utility objects** - `0aba38c3e` (feat)
2. **Task 2: Unit tests for NicknameExpander and TokenSimilarity** - `0d22fbd55` (test)

## Files Created/Modified
- `src/main/kotlin/riven/core/service/identity/NicknameExpander.kt` - Bidirectional nickname lookup with ~150 name groups
- `src/main/kotlin/riven/core/service/identity/TokenSimilarity.kt` - Overlap coefficient on word token sets
- `src/test/kotlin/riven/core/service/identity/NicknameExpanderTest.kt` - Full unit test coverage
- `src/test/kotlin/riven/core/service/identity/TokenSimilarityTest.kt` - Full unit test coverage
- `src/test/kotlin/riven/core/service/ingestion/ProjectionPipelineIntegrationTestBase.kt` - Fixed stale extra argument call to resolveManifest

## Decisions Made
- Kotlin object singletons (not Spring beans) — no Spring context overhead, directly testable
- Bidirectional INDEX built eagerly at object init — O(1) lookup in both directions from any variant
- Names in multiple groups get merged sets in the INDEX (edge case handling)
- Overlap coefficient chosen over Jaccard — returns 1.0 for "John" vs "John Smith" (subset containment, critical for partial name matching)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed pre-existing compilation error in ProjectionPipelineIntegrationTestBase**
- **Found during:** Task 2 (running tests)
- **Issue:** `resolveManifest(scanned, emptyMap())` called with two arguments but service signature only accepts one — blocked test compilation
- **Fix:** Removed stale second argument to match current service signature
- **Files modified:** `src/test/kotlin/riven/core/service/ingestion/ProjectionPipelineIntegrationTestBase.kt`
- **Verification:** `./gradlew test` passes successfully
- **Committed in:** `0d22fbd55` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking pre-existing compilation error)
**Impact on plan:** Fix was necessary to unblock test compilation. No scope change.

## Issues Encountered
None beyond the pre-existing compilation error documented above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- NicknameExpander and TokenSimilarity are fully tested and ready for wiring into IdentityMatchCandidateService
- Plan 02 can directly call NicknameExpander.expand() in findNicknameCandidates() and TokenSimilarity.overlap() for re-scoring
- No blockers

---
*Phase: 03-nickname-lookup-token-set-similarity*
*Completed: 2026-03-31*
