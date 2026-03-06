---
phase: 03-advanced-operations
plan: 01
subsystem: api
tags: [s3, supabase, presigned-urls, jsonb, storage]

requires:
  - phase: 02-production-adapters
    provides: StorageProvider interface with S3 and Supabase adapters
provides:
  - generateUploadUrl method on StorageProvider interface
  - Presigned upload URL generation for S3 (presignPutObject) and Supabase (createSignedUploadUrl)
  - JSONB metadata column on FileMetadataEntity
  - Request/response types for presigned upload, confirm upload, batch delete, metadata update
  - FILE_UPDATE Activity enum variant
affects: [03-02, 03-03]

tech-stack:
  added: []
  patterns: [presigned-upload-url-generation, jsonb-metadata-column]

key-files:
  created:
    - src/main/kotlin/riven/core/models/request/storage/StorageRequests.kt (new request types)
    - src/main/kotlin/riven/core/models/response/storage/StorageResponses.kt (new response types)
  modified:
    - src/main/kotlin/riven/core/models/storage/StorageProvider.kt
    - src/main/kotlin/riven/core/service/storage/S3StorageProvider.kt
    - src/main/kotlin/riven/core/service/storage/SupabaseStorageProvider.kt
    - src/main/kotlin/riven/core/service/storage/LocalStorageProvider.kt
    - src/main/kotlin/riven/core/entity/storage/FileMetadataEntity.kt
    - src/main/kotlin/riven/core/models/storage/FileMetadata.kt
    - src/main/kotlin/riven/core/enums/activity/Activity.kt
    - db/schema/01_tables/storage.sql

key-decisions:
  - "S3 presignPutObject omits contentType to avoid 403 mismatch -- rely on post-upload Tika validation"
  - "Supabase createSignedUploadUrl returns UploadSignedUrl with full URL property"
  - "metadata column is Map<String, String>? -- null means no metadata, not empty map"
  - "UpdateMetadataRequest uses Map<String, String?> -- nullable values mean remove key"

patterns-established:
  - "Presigned upload URL pattern: generate key, get URL, client uploads directly, confirm via API"
  - "JSONB metadata on file entities: nullable Map<String, String> with Hypersistence JsonBinaryType"

requirements-completed: [FILE-09, DATA-05]

duration: 4min
completed: 2026-03-06
---

# Phase 3 Plan 1: Foundation Contracts Summary

**StorageProvider extended with presigned upload URLs (S3/Supabase), JSONB metadata column, and all Phase 3 request/response types**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-06T10:41:08Z
- **Completed:** 2026-03-06T10:45:40Z
- **Tasks:** 2
- **Files modified:** 14

## Accomplishments
- StorageProvider interface extended with generateUploadUrl -- S3 uses presignPutObject, Supabase uses createSignedUploadUrl, Local throws UnsupportedOperationException
- FileMetadataEntity and domain model extended with nullable JSONB metadata column
- All request/response types for Phase 3 endpoints created (PresignedUploadRequest, ConfirmUploadRequest, BatchDeleteRequest, UpdateMetadataRequest, PresignedUploadResponse, BatchItemResult, BatchUploadResponse, BatchDeleteResponse)
- FILE_UPDATE added to Activity enum for metadata update tracking

## Task Commits

Each task was committed atomically:

1. **Task 1: Extend StorageProvider interface, implement generateUploadUrl in all adapters** - `2b27103` (feat)
2. **Task 2: Add metadata JSONB column, request/response types, FILE_UPDATE enum** - `6c558e0` (feat)

## Files Created/Modified
- `src/main/kotlin/riven/core/models/storage/StorageProvider.kt` - Added generateUploadUrl method
- `src/main/kotlin/riven/core/service/storage/S3StorageProvider.kt` - presignPutObject implementation
- `src/main/kotlin/riven/core/service/storage/SupabaseStorageProvider.kt` - createSignedUploadUrl implementation
- `src/main/kotlin/riven/core/service/storage/LocalStorageProvider.kt` - UnsupportedOperationException
- `src/main/kotlin/riven/core/entity/storage/FileMetadataEntity.kt` - JSONB metadata column
- `src/main/kotlin/riven/core/models/storage/FileMetadata.kt` - metadata field
- `src/main/kotlin/riven/core/enums/activity/Activity.kt` - FILE_UPDATE variant
- `src/main/kotlin/riven/core/models/request/storage/StorageRequests.kt` - 4 new request types
- `src/main/kotlin/riven/core/models/response/storage/StorageResponses.kt` - 4 new response types
- `db/schema/01_tables/storage.sql` - metadata JSONB column
- `src/test/kotlin/riven/core/service/util/factory/storage/StorageFactory.kt` - metadata parameter
- `src/test/kotlin/riven/core/service/storage/LocalStorageProviderTest.kt` - generateUploadUrl test
- `src/test/kotlin/riven/core/service/storage/S3StorageProviderTest.kt` - generateUploadUrl test
- `src/test/kotlin/riven/core/service/storage/SupabaseStorageProviderTest.kt` - generateUploadUrl test

## Decisions Made
- S3 presignPutObject intentionally omits contentType from PutObjectRequest to avoid 403 signature mismatch when client uploads with different Content-Type
- Supabase createSignedUploadUrl returns UploadSignedUrl data class with url, path, and token -- we use the url property directly
- metadata is nullable Map<String, String>? -- null means no metadata (not empty map)
- UpdateMetadataRequest uses Map<String, String?> where nullable values signal key removal

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All contracts and data layer changes for Plans 02 and 03 are in place
- StorageProvider has 7 methods (6 original + generateUploadUrl)
- All request/response types ready for endpoint implementation

---
*Phase: 03-advanced-operations*
*Completed: 2026-03-06*
