---
phase: 05-phonetic-matching-method-consolidation
plan: "01"
subsystem: identity
tags: [postgresql, fuzzystrmatch, dmetaphone, kotlin, spring-jpa, identity-matching]

# Dependency graph
requires:
  - phase: 04-email-domain-matching
    provides: EmailMatcher, MatchSource.EMAIL_DOMAIN, findEmailDomainCandidates pattern

provides:
  - fuzzystrmatch PostgreSQL extension in schema
  - findPhoneticCandidates private method using dmetaphone() via EXISTS/regexp_split_to_table SQL
  - computePhoneticCodes private method using JDBC scalar dmetaphone queries per token
  - findNameCandidates, findPhoneCandidates, findEmailCandidates, findDefaultCandidates orchestrators
  - when(signalType) expression dispatch replacing inline if-chains in findCandidates()
  - 5-level mergeCandidates tiebreaker: NICKNAME > PHONETIC > EMAIL_DOMAIN > EXACT_NORMALIZED > TRIGRAM
  - 6 new unit tests for phonetic candidate behaviour and tiebreaker ordering

affects: [identity-matching, candidate-discovery, phase-05-plan-02-integration-tests]

# Tech tracking
tech-stack:
  added: [fuzzystrmatch PostgreSQL extension (dmetaphone function)]
  patterns:
    - JDBC scalar query per trigger token to compute dmetaphone codes (avoids Kotlin phonetic library)
    - Per-type orchestrator methods (findNameCandidates et al.) with when(signalType) expression dispatch
    - Early return on empty phoneticCodes set to prevent empty-collection SQL parameter error

key-files:
  created: []
  modified:
    - db/schema/00_extensions/extensions.sql
    - src/main/kotlin/riven/core/service/identity/IdentityMatchCandidateService.kt
    - src/test/kotlin/riven/core/service/identity/IdentityMatchCandidateServiceTest.kt
    - src/test/kotlin/riven/core/service/identity/IdentityMatchPipelineIntegrationTest.kt

key-decisions:
  - "dmetaphone computed via JDBC scalar queries per token — no Kotlin phonetic library, same algorithm both sides"
  - "when(signalType) used as expression (not statement) to enforce exhaustiveness at compile time"
  - "Early return in findPhoneticCandidates when phoneticCodes is empty — prevents empty-collection SQL error"
  - "computePhoneticCodes filters tokens < 2 chars before issuing queries — single-char initials unreliable"
  - "fuzzystrmatch installed in integration test BeforeAll alongside pg_trgm to enable dmetaphone()"

patterns-established:
  - "Pattern: per-type orchestrator + when(signalType) dispatch — add new signal types by extending when() and adding orchestrator method"
  - "Pattern: dmetaphone scalar query for Kotlin-side phonetic code computation without library dependency"

requirements-completed: [CAND-07, CAND-08, DB-01]

# Metrics
duration: 10min
completed: 2026-03-31
---

# Phase 5 Plan 01: Phonetic Matching + Method Consolidation Summary

**Phonetic NAME candidate discovery via PostgreSQL dmetaphone() with when(signalType) dispatch consolidation replacing inline if-chains**

## Performance

- **Duration:** ~10 min
- **Started:** 2026-03-31T09:27:11Z
- **Completed:** 2026-03-31T09:37:00Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- Added `findPhoneticCandidates` for NAME signals using dmetaphone() — "Smith" and "Smythe" now produce PHONETIC candidates with fixed score 0.85
- Consolidated all candidate dispatch into `when(signalType)` expression with four per-type orchestrators, enforcing exhaustiveness at compile time
- Updated `mergeCandidates` tiebreaker to full 5-level ordering: NICKNAME > PHONETIC > EMAIL_DOMAIN > EXACT_NORMALIZED > TRIGRAM
- Added 6 new unit tests covering phonetic candidates, empty-token early returns, and tiebreaker ordering

## Task Commits

Each task was committed atomically:

1. **Task 1: Add fuzzystrmatch extension, findPhoneticCandidates, and when() dispatch consolidation** - `e55f4fd93` (feat)
2. **Task 2: Unit tests for phonetic candidate query and consolidation** - `a9cf03ff9` (test)

**Plan metadata:** (docs commit follows)

## Files Created/Modified
- `db/schema/00_extensions/extensions.sql` - Added fuzzystrmatch extension declaration
- `src/main/kotlin/riven/core/service/identity/IdentityMatchCandidateService.kt` - computePhoneticCodes, findPhoneticCandidates, four orchestrators, when(signalType) dispatch, updated tiebreaker
- `src/test/kotlin/riven/core/service/identity/IdentityMatchCandidateServiceTest.kt` - 6 new tests in FindPhoneticCandidatesTests, dmetaphoneScalarQuery() helper, updated existing NAME tests for phonetic call counts
- `src/test/kotlin/riven/core/service/identity/IdentityMatchPipelineIntegrationTest.kt` - Added fuzzystrmatch install in BeforeAll

## Decisions Made
- dmetaphone computed via JDBC scalar queries per token (`SELECT dmetaphone(:token)`) — avoids adding a Kotlin phonetic library and ensures both sides use the same PostgreSQL algorithm implementation
- `when(signalType)` used as an expression (assigned to val) to enforce compile-time exhaustiveness — adding a new MatchSignalType will cause a compiler error, not a silent no-op
- Early return in `findPhoneticCandidates` when `phoneticCodes` is empty — mirrors the pattern from `findNicknameCandidates` and prevents Hibernate empty-collection SQL parameter error
- Tokens shorter than 2 characters filtered before issuing dmetaphone queries — single-char initials produce unreliable phonetic codes

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added fuzzystrmatch extension install to integration test BeforeAll**
- **Found during:** Task 1 verification (full test run)
- **Issue:** Integration test `IdentityMatchPipelineIntegrationTest` failed with `ERROR: function dmetaphone(character varying) does not exist` — fuzzystrmatch extension not installed in Testcontainer
- **Fix:** Added `jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS fuzzystrmatch")` to `installExtensionsAndSeedData` alongside the existing pg_trgm line
- **Files modified:** src/test/kotlin/riven/core/service/identity/IdentityMatchPipelineIntegrationTest.kt
- **Verification:** Full test suite green after fix
- **Committed in:** e55f4fd93 (Task 1 commit)

**2. [Rule 1 - Bug] Updated existing NAME signal unit tests for phonetic query call counts**
- **Found during:** Task 1 verification
- **Issue:** 2 existing unit tests failed with `TooManyActualInvocations` because NAME signals now issue additional dmetaphone scalar queries — tests expected 3 `createNativeQuery` calls but received 5 (2 tokens) or 4 (1 token)
- **Fix:** Updated mock chains with `dmetaphoneScalarQuery("")` mocks and updated `verify(times(N))` assertions to match new call counts
- **Files modified:** src/test/kotlin/riven/core/service/identity/IdentityMatchCandidateServiceTest.kt
- **Verification:** All 29 unit tests pass
- **Committed in:** e55f4fd93 (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (1 missing critical, 1 bug)
**Impact on plan:** Both fixes were necessary and anticipated by the RESEARCH.md (Pitfall 2 for integration, expected behavior change for unit tests). No scope creep.

## Issues Encountered
- PHONETIC/TRIGRAM tiebreaker test initially designed with "smith" as both trigger and candidate value — token overlap of "smith" vs "smith" is 1.0 which bumped trigram score to 1.0, causing TRIGRAM to win on score over PHONETIC at 0.85. Fixed by using "smythe" as the candidate value (zero token overlap with "smith"), ensuring both strategies produce equal 0.85 scores where the tiebreaker fires correctly.

## Next Phase Readiness
- Phonetic candidate discovery complete — NAME signals now run all 4 strategies: trigram + token re-score + nickname + phonetic
- Phase 05 Plan 02 (integration tests TEST-10 and TEST-12) can now add phonetic pipeline integration tests against the installed fuzzystrmatch extension

---
*Phase: 05-phonetic-matching-method-consolidation*
*Completed: 2026-03-31*
