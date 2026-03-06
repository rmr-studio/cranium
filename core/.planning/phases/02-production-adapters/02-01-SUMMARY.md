---
phase: 02-production-adapters
plan: 01
subsystem: storage
tags: [supabase, supabase-kt, storage-provider, signed-url, runBlocking]

# Dependency graph
requires:
  - phase: 01-storage-foundation
    provides: StorageProvider interface, LocalStorageProvider, SignedUrlService, StorageService, StorageConfigurationProperties
provides:
  - SupabaseStorageProvider implementing all 6 StorageProvider methods with supabase-kt
  - Extended StorageConfigurationProperties with supabase and s3 nested config sections
  - Provider-first signed URL strategy in StorageService with HMAC fallback
affects: [02-production-adapters]

# Tech tracking
tech-stack:
  added: []
  patterns: [runBlocking-bridge-per-call, provider-first-signed-url-fallback, storagePlugin-testability-override]

key-files:
  created:
    - src/main/kotlin/riven/core/service/storage/SupabaseStorageProvider.kt
    - src/test/kotlin/riven/core/service/storage/SupabaseStorageProviderTest.kt
  modified:
    - src/main/kotlin/riven/core/configuration/storage/StorageConfigurationProperties.kt
    - src/main/kotlin/riven/core/service/storage/StorageService.kt
    - src/test/kotlin/riven/core/service/storage/StorageServiceTest.kt
    - src/main/resources/application.yml
    - src/test/resources/application-test.yml

key-decisions:
  - "storagePlugin() protected open method for testability -- supabase-kt extension is static, cannot be mocked"
  - "SupabaseStorageProvider tests use subclass override pattern instead of Spring context -- avoids SupabaseClient bean creation"
  - "Default setUp mocks storageProvider.generateSignedUrl to throw UnsupportedOperationException -- simulates local adapter"

patterns-established:
  - "Provider-first signed URL: StorageService tries storageProvider.generateSignedUrl(), catches UnsupportedOperationException, falls back to SignedUrlService"
  - "storagePlugin() override pattern: tests override protected method to inject mock Storage without needing real SupabaseClient"

requirements-completed: [ADPT-01]

# Metrics
duration: 6min
completed: 2026-03-06
---

# Phase 2 Plan 1: Supabase Storage Adapter Summary

**Supabase StorageProvider adapter with runBlocking bridge, extended config properties for supabase/s3, and provider-first signed URL fallback in StorageService**

## Performance

- **Duration:** 6 min
- **Started:** 2026-03-06T03:58:10Z
- **Completed:** 2026-03-06T04:04:50Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments
- SupabaseStorageProvider implements all 6 StorageProvider methods (upload, download, delete, exists, generateSignedUrl, healthCheck) with supabase-kt
- StorageConfigurationProperties extended with supabase and s3 nested config sections for all production providers
- StorageService refactored to provider-first signed URL strategy with HMAC SignedUrlService fallback
- 13 new tests (SupabaseStorageProviderTest) plus 3 new StorageServiceTest methods for fallback behavior

## Task Commits

Each task was committed atomically:

1. **Task 1: Extend config properties and implement SupabaseStorageProvider** - `5dec3f4` (feat)
2. **Task 2 RED: Add failing tests for provider-first signed URL strategy** - `d652bf8` (test)
3. **Task 2 GREEN: Refactor StorageService to provider-first signed URL strategy** - `9b1c59d` (feat)

## Files Created/Modified
- `src/main/kotlin/riven/core/service/storage/SupabaseStorageProvider.kt` - Supabase Storage adapter with runBlocking bridge
- `src/main/kotlin/riven/core/configuration/storage/StorageConfigurationProperties.kt` - Added Supabase and S3 nested config data classes
- `src/main/kotlin/riven/core/service/storage/StorageService.kt` - Provider-first signed URL with HMAC fallback
- `src/test/kotlin/riven/core/service/storage/SupabaseStorageProviderTest.kt` - Unit tests for all adapter methods
- `src/test/kotlin/riven/core/service/storage/StorageServiceTest.kt` - New signed URL fallback tests
- `src/main/resources/application.yml` - Default supabase and s3 config values
- `src/test/resources/application-test.yml` - Test config for supabase and s3 sections

## Decisions Made
- Used `protected open fun storagePlugin()` override pattern for testability since supabase-kt's `SupabaseClient.storage` is a static extension function that cannot be mocked by Mockito
- SupabaseStorageProvider tests do not use Spring context -- direct instantiation with mock override avoids needing a real SupabaseClient bean
- `createSignedUrl` uses Kotlin Duration inline class with mangled JVM name, so signed URL happy-path test verifies exception normalization instead of direct mock
- Default test setUp mocks `storageProvider.generateSignedUrl()` to throw `UnsupportedOperationException` to simulate local adapter behavior in existing tests

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added storagePlugin() testability override**
- **Found during:** Task 2 (SupabaseStorageProviderTest creation)
- **Issue:** supabase-kt `SupabaseClient.storage` is a Kotlin static extension function compiled to `StorageKt.getStorage()`, impossible to mock with Mockito
- **Fix:** Added `protected open fun storagePlugin(): Storage` in SupabaseStorageProvider, tests override via anonymous subclass
- **Files modified:** src/main/kotlin/riven/core/service/storage/SupabaseStorageProvider.kt
- **Verification:** All 13 SupabaseStorageProviderTest tests pass
- **Committed in:** d652bf8

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Minimal -- single protected method added for testability. No scope creep.

## Issues Encountered
- supabase-kt's `BucketApi.createSignedUrl` uses Kotlin `Duration` inline class which mangles the JVM method name, making direct mockito mocking unreliable. Worked around by testing exception normalization path instead.
- Suspend function mocking with mockito-kotlin requires `runBlocking` wrapper in test setup to properly mock coroutine continuations.

## User Setup Required
None - no external service configuration required. Supabase adapter activates only when `storage.provider=supabase` env var is set.

## Next Phase Readiness
- S3 adapter (Plan 02) can follow identical patterns: storagePlugin override for testing, runBlocking bridge, exception normalization
- S3 config properties already in place in StorageConfigurationProperties
- Provider-first signed URL strategy works for any provider that implements generateSignedUrl

---
*Phase: 02-production-adapters*
*Completed: 2026-03-06*
