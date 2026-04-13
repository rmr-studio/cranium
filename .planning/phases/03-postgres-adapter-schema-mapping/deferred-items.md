# Deferred Items — Phase 03

Out-of-scope issues discovered while executing Phase 3 plans. These were **not** fixed by the originating plan because they existed before Phase 3 work began or affect files outside the current plan's scope.

## Pre-existing failing tests in `DataConnectorConnectionServiceTest`

- **Discovered during:** Plan 03-00 Task 2 verification (`./gradlew test --tests "riven.core.service.connector.*"`)
- **Status:** Pre-existing failures — exist on `postgres-ingestion` HEAD before Plan 03-00 landed
- **Affected:** 13 tests in `core/src/test/kotlin/riven/core/service/connector/DataConnectorConnectionServiceTest.kt`
- **Symptom:** All tests fail (create/update/softDelete/listByWorkspace/getById/DataCorruptionException/CryptoException paths)
- **Scope decision:** Out of scope for Plan 03-00 (Wave-0 test scaffolding). Should be triaged under Phase 2 follow-up or the Phase 3 owner whose refactor breaks this suite.
- **Verification the scaffolds themselves are clean:** `./gradlew test --tests "riven.core.service.connector.postgres.*" --tests "riven.core.service.connector.pool.*" --tests "riven.core.service.connector.mapping.*" --tests "riven.core.controller.connector.CustomSourceMappingControllerTest"` → BUILD SUCCESSFUL (all @Disabled, 0 failures).

## Stale `riven.core.lifecycle.*` imports in `service/dev/`

- **Discovered during:** Plan 03-00 Task 1 `compileTestKotlin` verification
- **Status:** FIXED as Rule-3 blocking auto-fix (prevented the Wave-0 plan from compiling at all).
- **Files:** `DevSeedService.kt`, `DevSeedDataGenerator.kt` — imports now point at the canonical `riven.core.models.core.*` package.
- **Note:** Shipped as part of Plan 03-00 Task 1 commit so downstream Phase 3 plans have a green `compileTestKotlin` baseline.
