---
phase: 03-postgres-adapter-schema-mapping
plan: 00
subsystem: testing
tags: [kotlin, junit5, test-factories, wave-0, scaffolding, postgres]

requires:
  - phase: 01-adapter-foundation
    provides: SchemaIntrospectionResult / TableSchema / ColumnSchema models used by PostgresIntrospectionFactory
  - phase: 02-secure-connection-management
    provides: DataConnectorConnectionEntityFactory precedent + connector/ package layout

provides:
  - CustomSourceTableMappingEntityFactory (Phase 3 test factory)
  - CustomSourceFieldMappingEntityFactory (Phase 3 test factory)
  - PostgresIntrospectionFactory (column/table/result builders for adapter tests)
  - CustomSourceTableMappingEntity shell (full JPA annotations land in plan 03-01)
  - CustomSourceFieldMappingEntity shell (full JPA annotations land in plan 03-01)
  - Eight @Disabled placeholder test classes covering every Phase 3 SUT

affects: [03-01, 03-02, 03-03, 03-04]

tech-stack:
  added: []
  patterns:
    - "Wave-0 @Disabled placeholder scaffolds with private placeholder() no-op body (mirrors Phase 2 02-00)"
    - "Entity-shell-now, JPA-annotate-later split between Wave 0 and owning Wave 1 plan"

key-files:
  created:
    - core/src/main/kotlin/riven/core/entity/connector/CustomSourceTableMappingEntity.kt
    - core/src/main/kotlin/riven/core/entity/connector/CustomSourceFieldMappingEntity.kt
    - core/src/test/kotlin/riven/core/service/util/factory/CustomSourceTableMappingEntityFactory.kt
    - core/src/test/kotlin/riven/core/service/util/factory/CustomSourceFieldMappingEntityFactory.kt
    - core/src/test/kotlin/riven/core/service/util/factory/PostgresIntrospectionFactory.kt
    - core/src/test/kotlin/riven/core/service/connector/postgres/PgTypeMapperTest.kt
    - core/src/test/kotlin/riven/core/service/connector/postgres/SchemaHashTest.kt
    - core/src/test/kotlin/riven/core/service/connector/postgres/PostgresAdapterTest.kt
    - core/src/test/kotlin/riven/core/service/connector/pool/WorkspaceConnectionPoolManagerTest.kt
    - core/src/test/kotlin/riven/core/service/connector/mapping/CustomSourceSchemaInferenceServiceTest.kt
    - core/src/test/kotlin/riven/core/service/connector/mapping/CustomSourceFieldMappingServiceTest.kt
    - core/src/test/kotlin/riven/core/service/connector/mapping/NlMappingSuggestionServiceTest.kt
    - core/src/test/kotlin/riven/core/controller/connector/CustomSourceMappingControllerTest.kt
    - .planning/phases/03-postgres-adapter-schema-mapping/deferred-items.md
  modified:
    - core/src/main/kotlin/riven/core/service/dev/DevSeedService.kt
    - core/src/main/kotlin/riven/core/service/dev/DevSeedDataGenerator.kt

key-decisions:
  - "Wave-0 entity shells carry the full field list declared in plan 03-00 <interfaces> (no JPA annotations yet) so factories compile today and plan 03-01 only has to annotate + DDL"
  - "Factories live flat under service/util/factory/ (not a customsource/ subfolder) to match plan 03-00's <files> list and keep the Phase 3 factory surface discoverable"
  - "Pre-existing riven.core.lifecycle.* imports in service/dev/ auto-fixed under Rule 3 (blocking) — compileTestKotlin was red before any Phase 3 test scaffold existed"
  - "DataConnectorConnectionServiceTest failures (13 tests, pre-existing on postgres-ingestion HEAD) deferred to phase-scoped deferred-items.md — out of scope for Wave-0 scaffolding"

patterns-established:
  - "Wave-0 placeholder: @Disabled(\"populated by plan 03-XX\") at class level + named @Test fun per must-have row + private placeholder() no-op body"
  - "Downstream plans discover their owned test class via grep '@Disabled(\"populated by plan 03-' — exact pattern documented in plan 03-00 frontmatter key_links"

requirements-completed:
  - PG-01
  - PG-02
  - PG-03
  - PG-04
  - PG-05
  - PG-06
  - PG-07
  - MAP-01
  - MAP-02
  - MAP-06
  - MAP-08

duration: ~8min
completed: 2026-04-13
---

# Phase 3 Plan 00: Wave-0 Test Scaffolds & Factories Summary

**Eight @Disabled placeholder JUnit 5 classes + three test factories + two entity shells land so every Phase 3 downstream plan (03-01..04) has concrete test files to populate and factories to call — no scavenger hunts.**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-04-13T03:17:00Z
- **Completed:** 2026-04-13T03:25:40Z
- **Tasks:** 2
- **Files created:** 14 (5 factory/entity files + 8 test classes + 1 deferred-items log)
- **Files modified:** 2 (DevSeed import fix)

## Accomplishments

- Three test factories (`CustomSourceTableMappingEntityFactory`, `CustomSourceFieldMappingEntityFactory`, `PostgresIntrospectionFactory`) ready for Wave-1 plans to call without inline entity construction (CLAUDE.md rule)
- Two entity shells (`CustomSourceTableMappingEntity`, `CustomSourceFieldMappingEntity`) compile today; plan 03-01 will replace with full JPA annotations + DDL + repository + `toModel()`
- Eight `@Disabled` test classes with named methods per must-have row — downstream plans flip `@Disabled` off and fill in assertions
- `compileTestKotlin` green; new test classes skipped cleanly (0 failures in Wave-0 scope)
- Pre-existing `riven.core.lifecycle.*` import rot in `service/dev/` unblocked so downstream plans inherit a green baseline

## Task Commits

1. **Task 1: Add factories under service/util/factory/** — `60399e689` (test)
2. **Task 2: Create @Disabled placeholder test scaffolds for every Phase 3 SUT** — `794520364` (test)

**Plan metadata:** _(created by final_commit step)_

## Files Created/Modified

### Created
- `core/src/main/kotlin/riven/core/entity/connector/CustomSourceTableMappingEntity.kt` — Wave-0 shell (fields only)
- `core/src/main/kotlin/riven/core/entity/connector/CustomSourceFieldMappingEntity.kt` — Wave-0 shell (fields only)
- `core/src/test/kotlin/riven/core/service/util/factory/CustomSourceTableMappingEntityFactory.kt` — test factory
- `core/src/test/kotlin/riven/core/service/util/factory/CustomSourceFieldMappingEntityFactory.kt` — test factory
- `core/src/test/kotlin/riven/core/service/util/factory/PostgresIntrospectionFactory.kt` — adapter fixture factory (column/table/result)
- `core/src/test/kotlin/riven/core/service/connector/postgres/PgTypeMapperTest.kt` — 12 methods (owned by 03-01)
- `core/src/test/kotlin/riven/core/service/connector/postgres/SchemaHashTest.kt` — 5 methods (owned by 03-01)
- `core/src/test/kotlin/riven/core/service/connector/postgres/PostgresAdapterTest.kt` — 9 methods (owned by 03-02)
- `core/src/test/kotlin/riven/core/service/connector/pool/WorkspaceConnectionPoolManagerTest.kt` — 6 methods (owned by 03-02)
- `core/src/test/kotlin/riven/core/service/connector/mapping/CustomSourceSchemaInferenceServiceTest.kt` — 7 methods (owned by 03-03)
- `core/src/test/kotlin/riven/core/service/connector/mapping/CustomSourceFieldMappingServiceTest.kt` — 10 methods (owned by 03-03)
- `core/src/test/kotlin/riven/core/service/connector/mapping/NlMappingSuggestionServiceTest.kt` — 6 methods (owned by 03-04)
- `core/src/test/kotlin/riven/core/controller/connector/CustomSourceMappingControllerTest.kt` — 5 methods (owned by 03-03)
- `.planning/phases/03-postgres-adapter-schema-mapping/deferred-items.md` — tracks out-of-scope test failures

### Modified (Rule-3 blocking auto-fix)
- `core/src/main/kotlin/riven/core/service/dev/DevSeedService.kt` — `riven.core.lifecycle.*` → `riven.core.models.core.*`
- `core/src/main/kotlin/riven/core/service/dev/DevSeedDataGenerator.kt` — `riven.core.lifecycle.*` → `riven.core.models.core.*`

## Decisions Made

- **Flat factory layout.** Plan 03-00 lists factories at `service/util/factory/<Name>.kt` — chose that over the Phase 2 `customsource/` subfolder so the Phase 3 factory surface is greppable by test class name.
- **Entity-shell fields now, JPA later.** Shells expose the exact ctor shape the factories need. Plan 03-01 annotates + adds DDL in one sweep without touching call sites.
- **Pre-existing DevSeed import rot fixed inline.** `compileTestKotlin` could not succeed until these imports were corrected — this is a Rule-3 blocking fix, not scope creep.
- **`DataConnectorConnectionServiceTest` failures deferred.** 13 pre-existing failures unrelated to Wave-0 scaffolding; logged in `deferred-items.md` for Phase-2 or Phase-3-owner triage.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed stale `riven.core.lifecycle.*` imports in `service/dev/`**
- **Found during:** Task 1 verification (`./gradlew compileTestKotlin`)
- **Issue:** `DevSeedService.kt` and `DevSeedDataGenerator.kt` imported `riven.core.lifecycle.CoreModelSet` / `CoreModelDefinition` / `CoreModelAttribute` — these classes actually live at `riven.core.models.core.*`. The mis-import existed on `postgres-ingestion` HEAD and prevented the main module from compiling, which in turn blocked `compileTestKotlin` — no Phase 3 plan could be verified without this fix.
- **Fix:** Rewrote the three offending imports in both files. No call-site changes required.
- **Files modified:** `DevSeedService.kt`, `DevSeedDataGenerator.kt`
- **Verification:** `./gradlew compileTestKotlin` → BUILD SUCCESSFUL.
- **Committed in:** `60399e689` (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Fix was essential to verify Wave-0 scaffolds compile. No scope creep — strictly unblocking.

## Issues Encountered

- **`./gradlew test --tests "riven.core.service.connector.*"` reported 13 failures** in `DataConnectorConnectionServiceTest`. These are pre-existing, unrelated to Plan 03-00, and out-of-scope per the Scope Boundary rule. Logged to `deferred-items.md`. Narrowed-glob verification (`service.connector.postgres.*` + `service.connector.pool.*` + `service.connector.mapping.*` + `controller.connector.CustomSourceMappingControllerTest`) is BUILD SUCCESSFUL and confirms every Wave-0 test class is skipped cleanly.

## User Setup Required

None — Wave-0 scaffolding adds no new environment variables, infrastructure, or external services.

## Next Phase Readiness

- **Downstream plans 03-01..04 can proceed in parallel per the Phase 3 ROADMAP.** Each plan:
  1. Locates its owned test class via `grep '@Disabled("populated by plan 03-XX")' core/src/test/`.
  2. Removes the class-level `@Disabled`.
  3. Replaces `placeholder()` bodies with real assertions calling the factories under `service/util/factory/`.
- **Plan 03-01 owes:** JPA annotations + SQL DDL + repositories + `toModel()` for the two entity shells.
- **Known concern:** `DataConnectorConnectionServiceTest` regression must be diagnosed before Phase 3 can declare the connector test suite fully green — not a Phase 3 scaffolding problem.

## Self-Check: PASSED

- [x] `core/src/main/kotlin/riven/core/entity/connector/CustomSourceTableMappingEntity.kt` — FOUND
- [x] `core/src/main/kotlin/riven/core/entity/connector/CustomSourceFieldMappingEntity.kt` — FOUND
- [x] `core/src/test/kotlin/riven/core/service/util/factory/CustomSourceTableMappingEntityFactory.kt` — FOUND
- [x] `core/src/test/kotlin/riven/core/service/util/factory/CustomSourceFieldMappingEntityFactory.kt` — FOUND
- [x] `core/src/test/kotlin/riven/core/service/util/factory/PostgresIntrospectionFactory.kt` — FOUND
- [x] 8 `@Disabled` test class files under `service/connector/{postgres,pool,mapping}/` + `controller/connector/` — FOUND
- [x] Commit `60399e689` — FOUND
- [x] Commit `794520364` — FOUND

---
*Phase: 03-postgres-adapter-schema-mapping*
*Completed: 2026-04-13*
