---
phase: 01-storage-foundation
plan: 02
subsystem: storage
tags: [storage, tika, mime-detection, svg-sanitizer, filesystem, local-storage, content-validation]

# Dependency graph
requires:
  - phase: 01-01
    provides: StorageProvider interface, StorageDomain enum, StorageResult/DownloadResult models, storage exceptions, StorageConfigurationProperties
provides:
  - ContentValidationService with Tika MIME detection, content type/size validation, SVG sanitization, storage key generation
  - LocalStorageProvider implementing StorageProvider for local filesystem with path traversal prevention
affects: [01-03, 01-04, 01-05]

# Tech tracking
tech-stack:
  added: []
  patterns: ["Tika magic byte detection for MIME type (not file extension)", "SVG sanitization via borewit before storage", "Path traversal prevention via normalize() + startsWith()", "Plain unit tests with @TempDir for filesystem operations"]

key-files:
  created:
    - src/main/kotlin/riven/core/service/storage/ContentValidationService.kt
    - src/main/kotlin/riven/core/service/storage/LocalStorageProvider.kt
    - src/test/kotlin/riven/core/service/storage/ContentValidationServiceTest.kt
    - src/test/kotlin/riven/core/service/storage/LocalStorageProviderTest.kt
  modified: []

key-decisions:
  - "Tika extension includes leading dot (e.g. '.png') -- used directly in key generation for simplicity"
  - "LocalStorageProvider.generateSignedUrl throws UnsupportedOperationException -- delegated to SignedUrlService in Plan 03"
  - "LocalStorageProvider tested as plain unit (no SpringBootTest) using @TempDir for filesystem isolation"

patterns-established:
  - "ContentValidationService: validate-before-store pattern -- MIME detect, validate type, validate size, sanitize if SVG"
  - "LocalStorageProvider: resolveAndValidate() private method pattern for path traversal prevention on every operation"

requirements-completed: [ADPT-03, FILE-02, FILE-03, FILE-04]

# Metrics
duration: 3min
completed: 2026-03-06
---

# Phase 1 Plan 2: Content Validation and Local Storage Adapter Summary

**Tika magic-byte MIME detection, SVG sanitization via borewit, and local filesystem StorageProvider with path traversal prevention**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-05T21:08:58Z
- **Completed:** 2026-03-05T21:12:25Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- ContentValidationService detecting MIME types from magic bytes (not file extension) using Apache Tika
- SVG sanitization stripping script tags and event handlers before storage
- LocalStorageProvider implementing full StorageProvider interface for local filesystem
- Path traversal prevention on all storage operations with normalize() + startsWith() validation
- 26 unit tests covering both services (12 for ContentValidation, 14 for LocalStorage)

## Task Commits

Each task was committed atomically (TDD: test then feat):

1. **Task 1: ContentValidationService** - `6b725e6` (test: failing), `0c0622d` (feat: implementation)
2. **Task 2: LocalStorageProvider** - `4cc753c` (test: failing), `1e4ba4c` (feat: implementation)

## Files Created/Modified
- `src/main/kotlin/riven/core/service/storage/ContentValidationService.kt` - MIME detection, validation, SVG sanitization, key generation
- `src/main/kotlin/riven/core/service/storage/LocalStorageProvider.kt` - Local filesystem StorageProvider with path traversal prevention
- `src/test/kotlin/riven/core/service/storage/ContentValidationServiceTest.kt` - 12 unit tests for content validation
- `src/test/kotlin/riven/core/service/storage/LocalStorageProviderTest.kt` - 14 unit tests for local storage adapter

## Decisions Made
- Tika's `mimeType.extension` returns with leading dot (e.g. `.png`) which is used directly in storage key generation -- no stripping needed
- LocalStorageProvider.generateSignedUrl() throws UnsupportedOperationException since signed URL generation will be handled by SignedUrlService (Plan 03)
- LocalStorageProvider tested as plain unit with manually constructed instance and @TempDir, not @SpringBootTest -- faster and no Spring context overhead

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- ContentValidationService ready for StorageService orchestration (Plan 03)
- LocalStorageProvider ready as the active provider when storage.provider=local
- All 26 unit tests pass, full build succeeds

## Self-Check: PASSED

All 4 created files verified on disk. All 4 task commits verified in git history.

---
*Phase: 01-storage-foundation*
*Completed: 2026-03-06*
