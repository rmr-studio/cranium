---
phase: 01-storage-foundation
plan: 03
subsystem: storage
tags: [storage, hmac, signed-url, upload, download, delete, activity-logging, workspace-security]

# Dependency graph
requires:
  - phase: 01-01
    provides: StorageProvider interface, FileMetadataEntity/Repository, StorageConfigurationProperties, storage exceptions, request/response types
  - phase: 01-02
    provides: ContentValidationService with Tika MIME detection, SVG sanitization, storage key generation
provides:
  - SignedUrlService with HMAC-SHA256 token generation, constant-time validation, and download URL generation
  - StorageService orchestrating upload/download/delete/list with validation, provider I/O, metadata persistence, signed URLs, and activity logging
affects: [01-04]

# Tech tracking
tech-stack:
  added: []
  patterns: ["HMAC-SHA256 signed tokens with Base64URL encoding and constant-time validation", "Validate-then-store orchestration pattern in StorageService", "Token-based download authorization (no @PreAuthorize) for signed URLs"]

key-files:
  created:
    - src/main/kotlin/riven/core/service/storage/SignedUrlService.kt
    - src/test/kotlin/riven/core/service/storage/SignedUrlServiceTest.kt
    - src/test/kotlin/riven/core/service/storage/StorageServiceTest.kt
    - src/test/kotlin/riven/core/service/util/factory/storage/StorageFactory.kt
  modified:
    - src/main/kotlin/riven/core/service/storage/StorageService.kt

key-decisions:
  - "Token format: Base64URL(storageKey:expiresAt:hmacSignature) -- simple, self-contained, no DB lookup needed"
  - "downloadFile uses signed token as authorization (no @PreAuthorize) per CONTEXT.md decision"
  - "Physical file deletion failures are logged but don't roll back metadata soft-delete for consistency"

patterns-established:
  - "SignedUrlService: generate/validate token pair with HMAC-SHA256 and MessageDigest.isEqual() constant-time comparison"
  - "StorageService: detect -> validate -> sanitize(SVG) -> store -> persist -> log activity -> return with signed URL"
  - "StorageFactory test utility for reusable FileMetadataEntity and FileMetadata builders"

requirements-completed: [FILE-01, FILE-05, FILE-06, FILE-07, DATA-03, DATA-04]

# Metrics
duration: 4min
completed: 2026-03-06
---

# Phase 1 Plan 3: Signed URL Service and Storage Orchestration Summary

**HMAC-SHA256 signed download tokens with constant-time validation, and StorageService orchestrating validate-then-store upload, token-authorized download, soft-delete, and workspace-scoped listing with activity logging**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-05T21:15:10Z
- **Completed:** 2026-03-05T21:19:30Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- SignedUrlService generating HMAC-SHA256 tokens with Base64URL encoding, constant-time validation, and expiry clamping
- StorageService orchestrating the full upload flow: MIME detection, type/size validation, SVG sanitization, provider storage, metadata persistence, activity logging, signed URL return
- Token-authorized download without workspace security (signed token IS the authorization)
- Soft-delete with resilient physical file cleanup (provider failures logged, not propagated)
- 25 unit tests across SignedUrlService (11) and StorageService (14)

## Task Commits

Each task was committed atomically (TDD: test then feat):

1. **Task 1: SignedUrlService** - `3e74e07` (test: failing), `c901f9d` (feat: implementation)
2. **Task 2: StorageService** - `6132077` (test: failing), `372de9f` (feat: implementation)

## Files Created/Modified
- `src/main/kotlin/riven/core/service/storage/SignedUrlService.kt` - HMAC-SHA256 token generation, constant-time validation, download URL generation
- `src/main/kotlin/riven/core/service/storage/StorageService.kt` - Full storage orchestration: upload, download, delete, list with security and logging
- `src/test/kotlin/riven/core/service/storage/SignedUrlServiceTest.kt` - 11 tests for token round-trip, expiry, tampering, URL format
- `src/test/kotlin/riven/core/service/storage/StorageServiceTest.kt` - 14 tests for all CRUD operations, validation ordering, error resilience
- `src/test/kotlin/riven/core/service/util/factory/storage/StorageFactory.kt` - Test data builders for FileMetadataEntity and FileMetadata

## Decisions Made
- Token format uses Base64URL(storageKey:expiresAt:hmacSignature) -- self-contained, no database lookup needed for validation
- downloadFile method has no @PreAuthorize annotation -- the signed token IS the authorization (per CONTEXT.md)
- Physical file deletion failures are caught and logged but do not roll back the metadata soft-delete, ensuring metadata consistency even when the provider is temporarily unavailable

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- StorageService ready for controller wiring (Plan 04)
- SignedUrlService ready for download endpoint in controller
- All 25 unit tests pass, full build succeeds
- StorageFactory available for future test development

## Self-Check: PASSED

All 5 created/modified files verified on disk. All 4 task commits verified in git history.

---
*Phase: 01-storage-foundation*
*Completed: 2026-03-06*
