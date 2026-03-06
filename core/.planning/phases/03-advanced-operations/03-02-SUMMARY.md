---
phase: 03-advanced-operations
plan: 02
subsystem: api
tags: [presigned-upload, metadata, s3, supabase, rest, tika]

requires:
  - phase: 03-advanced-operations/01
    provides: StorageProvider interface with generateUploadUrl, FileMetadataEntity with metadata column, request/response models
provides:
  - Presigned upload flow (request URL + confirm with Tika validation)
  - Custom metadata PATCH with merge semantics (add, update, remove)
  - Metadata validation (max 20 pairs, key format, value length)
  - Upload with optional metadata attachment
affects: [03-advanced-operations/03, client-repo]

tech-stack:
  added: []
  patterns: [presigned-upload-with-fallback, metadata-merge-semantics, provider-first-try-catch]

key-files:
  created: []
  modified:
    - src/main/kotlin/riven/core/service/storage/StorageService.kt
    - src/main/kotlin/riven/core/controller/storage/StorageController.kt
    - src/main/kotlin/riven/core/configuration/storage/StorageConfigurationProperties.kt
    - src/test/kotlin/riven/core/service/storage/StorageServiceTest.kt
    - src/main/resources/application.yml
    - src/test/resources/application-test.yml

key-decisions:
  - "Parse domain from storage key format ({workspaceId}/{domain}/{uuid}.{ext}) instead of requiring domain in confirm request"
  - "Metadata validation: separate validateMetadata (String values) for upload and validateMetadataUpdate (String? values) for PATCH"
  - "ObjectMapper injected into StorageController for parsing multipart metadata string parameter"

patterns-established:
  - "Presigned upload fallback: try provider.generateUploadUrl, catch UnsupportedOperationException, return supported=false"
  - "Metadata merge: null values in patch map remove keys, non-null values add/update"

requirements-completed: [FILE-09, FILE-10, DATA-05]

duration: 4min
completed: 2026-03-06
---

# Phase 3 Plan 2: Presigned Upload and Metadata Operations Summary

**Presigned upload flow with provider fallback, metadata PATCH with merge semantics, and Tika validation on confirm**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-06T10:48:22Z
- **Completed:** 2026-03-06T10:52:40Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- Presigned upload request endpoint with S3/Supabase support and local fallback (supported=false)
- Presigned upload confirmation with exists check, Tika content type validation, and invalid file deletion
- Custom metadata PATCH with merge semantics (add, update, remove via null values)
- Metadata validation enforcing max 20 pairs, key pattern ^[a-zA-Z0-9_-]{1,64}$, value max 1024 chars
- Upload endpoint updated to accept optional metadata at upload time

## Task Commits

Each task was committed atomically:

1. **Task 1: Implement presigned upload and metadata service methods with tests** - `78e1ad6` (feat)
2. **Task 2: Add presigned upload and metadata REST endpoints to StorageController** - `7ba946b` (feat)

## Files Created/Modified
- `src/main/kotlin/riven/core/service/storage/StorageService.kt` - Added requestPresignedUpload, confirmPresignedUpload, updateMetadata, metadata validation/merge helpers
- `src/main/kotlin/riven/core/controller/storage/StorageController.kt` - Added presigned-upload, confirm, metadata PATCH endpoints + updated upload with metadata param
- `src/main/kotlin/riven/core/configuration/storage/StorageConfigurationProperties.kt` - Added PresignedUpload config with expirySeconds
- `src/test/kotlin/riven/core/service/storage/StorageServiceTest.kt` - Added 13 new tests for presigned upload and metadata flows
- `src/main/resources/application.yml` - Added presigned-upload.expiry-seconds config
- `src/test/resources/application-test.yml` - Added presigned-upload.expiry-seconds config

## Decisions Made
- Parse domain from storage key format instead of requiring in confirm request -- keeps API simple
- Separate validation functions for upload metadata (non-null values only) vs update metadata (nullable values for removal)
- ObjectMapper injected into controller for parsing multipart form metadata string to Map

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Presigned upload and metadata APIs ready for frontend integration (API-02/API-03 deferred to client repo)
- Ready for Plan 03 (batch operations or integration testing)

---
*Phase: 03-advanced-operations*
*Completed: 2026-03-06*
