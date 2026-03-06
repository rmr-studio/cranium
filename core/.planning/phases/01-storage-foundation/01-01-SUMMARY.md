---
phase: 01-storage-foundation
plan: 01
subsystem: storage
tags: [storage, provider-abstraction, tika, svg-sanitizer, jpa, postgresql, hmac]

# Dependency graph
requires: []
provides:
  - StorageProvider interface with 6 blocking methods (upload, download, delete, exists, generateSignedUrl, healthCheck)
  - StorageDomain enum with per-domain validation rules (AVATAR: 5 image types, 2MB limit)
  - FileMetadataEntity with JPA persistence and toModel() mapping
  - FileMetadataRepository with workspace+domain query methods
  - Storage exception hierarchy (5 types) with ExceptionHandler mappings
  - StorageConfigurationProperties binding storage.* namespace
  - Request/response types for upload, list, and signed URL operations
  - DB schema for file_metadata table with indexes
affects: [01-02, 01-03, 01-04, 01-05]

# Tech tracking
tech-stack:
  added: [apache-tika-core-3.2.0, svg-sanitizer-0.3.1]
  patterns: ["@ConditionalOnProperty for provider selection", "StorageDomain enum with validation rules as properties"]

key-files:
  created:
    - src/main/kotlin/riven/core/models/storage/StorageProvider.kt
    - src/main/kotlin/riven/core/models/storage/FileMetadata.kt
    - src/main/kotlin/riven/core/models/storage/StorageResult.kt
    - src/main/kotlin/riven/core/models/storage/DownloadResult.kt
    - src/main/kotlin/riven/core/enums/storage/StorageDomain.kt
    - src/main/kotlin/riven/core/entity/storage/FileMetadataEntity.kt
    - src/main/kotlin/riven/core/repository/storage/FileMetadataRepository.kt
    - src/main/kotlin/riven/core/configuration/storage/StorageConfigurationProperties.kt
    - src/main/kotlin/riven/core/models/request/storage/StorageRequests.kt
    - src/main/kotlin/riven/core/models/response/storage/StorageResponses.kt
    - db/schema/01_tables/storage.sql
    - db/schema/02_indexes/storage_indexes.sql
  modified:
    - src/main/kotlin/riven/core/enums/activity/Activity.kt
    - src/main/kotlin/riven/core/enums/core/ApplicationEntityType.kt
    - src/main/kotlin/riven/core/exceptions/ArgumentExceptions.kt
    - src/main/kotlin/riven/core/exceptions/ExceptionHandler.kt
    - src/main/kotlin/riven/core/configuration/storage/SupabaseConfiguration.kt
    - build.gradle.kts
    - src/main/resources/application.yml
    - src/test/resources/application-test.yml

key-decisions:
  - "StorageProvider interface is blocking (non-suspend) to match synchronous Spring MVC codebase"
  - "SupabaseConfiguration gated by @ConditionalOnProperty to allow local-only startup"
  - "Separate HMAC secret (storage.signed-url.secret) instead of reusing JWT secret"

patterns-established:
  - "@ConditionalOnProperty(storage.provider) for provider bean selection -- one active provider at runtime"
  - "StorageDomain enum carries allowedContentTypes and maxFileSize as properties with validation helpers"
  - "StorageException hierarchy with typed subtypes mapped to HTTP status codes in ExceptionHandler"

requirements-completed: [PROV-01, PROV-02, PROV-03, PROV-04, DATA-01, DATA-02, FILE-08]

# Metrics
duration: 3min
completed: 2026-03-06
---

# Phase 1 Plan 1: Storage Contracts and Data Layer Summary

**StorageProvider interface, StorageDomain enum with AVATAR validation rules, FileMetadataEntity with JPA persistence, storage exception hierarchy, and conditional Supabase configuration**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-05T21:03:05Z
- **Completed:** 2026-03-05T21:06:30Z
- **Tasks:** 3
- **Files modified:** 20

## Accomplishments
- StorageProvider interface with 6 blocking methods establishing the provider abstraction contract
- StorageDomain.AVATAR enum with 5 image content types and 2MB limit, plus validation helpers
- FileMetadataEntity extending AuditableSoftDeletableEntity with toModel() and full repository
- 5 storage exception types mapped to HTTP status codes (415, 413, 404, 403, 502) in ExceptionHandler
- SupabaseConfiguration gated by @ConditionalOnProperty enabling local-only startup

## Task Commits

Each task was committed atomically:

1. **Task 1: Define StorageProvider interface, domain models, enums, and request/response types** - `2526387` (feat)
2. **Task 2: Create FileMetadataEntity, repository, DB schema, and exception hierarchy** - `eeab4d6` (feat)
3. **Task 3: Configure storage properties, update SupabaseConfiguration, and add Gradle dependencies** - `f039499` (feat)

## Files Created/Modified
- `src/main/kotlin/riven/core/models/storage/StorageProvider.kt` - Provider interface with 6 blocking methods
- `src/main/kotlin/riven/core/models/storage/FileMetadata.kt` - Domain model for file metadata
- `src/main/kotlin/riven/core/models/storage/StorageResult.kt` - Upload result data class
- `src/main/kotlin/riven/core/models/storage/DownloadResult.kt` - Download result with InputStream
- `src/main/kotlin/riven/core/enums/storage/StorageDomain.kt` - Domain enum with validation rules
- `src/main/kotlin/riven/core/entity/storage/FileMetadataEntity.kt` - JPA entity with toModel()
- `src/main/kotlin/riven/core/repository/storage/FileMetadataRepository.kt` - Repository with workspace+domain queries
- `src/main/kotlin/riven/core/configuration/storage/StorageConfigurationProperties.kt` - storage.* config binding
- `src/main/kotlin/riven/core/models/request/storage/StorageRequests.kt` - GenerateSignedUrlRequest
- `src/main/kotlin/riven/core/models/response/storage/StorageResponses.kt` - UploadFileResponse, FileListResponse, SignedUrlResponse
- `db/schema/01_tables/storage.sql` - file_metadata table DDL
- `db/schema/02_indexes/storage_indexes.sql` - Workspace, domain, and storage key indexes
- `src/main/kotlin/riven/core/enums/activity/Activity.kt` - Added FILE_UPLOAD, FILE_DELETE
- `src/main/kotlin/riven/core/enums/core/ApplicationEntityType.kt` - Added FILE
- `src/main/kotlin/riven/core/exceptions/ArgumentExceptions.kt` - 5 storage exception classes
- `src/main/kotlin/riven/core/exceptions/ExceptionHandler.kt` - 5 new exception handlers
- `src/main/kotlin/riven/core/configuration/storage/SupabaseConfiguration.kt` - Added @ConditionalOnProperty
- `build.gradle.kts` - Added tika-core 3.2.0 and svg-sanitizer 0.3.1
- `src/main/resources/application.yml` - Storage config defaults
- `src/test/resources/application-test.yml` - Storage test config

## Decisions Made
- StorageProvider interface is blocking (non-suspend) to match synchronous Spring MVC codebase
- SupabaseConfiguration gated by @ConditionalOnProperty(storage.provider=supabase) to allow local-only startup without Supabase credentials
- Separate HMAC secret (storage.signed-url.secret) instead of reusing JWT secret for security isolation

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- All contracts and types compile successfully
- Every subsequent plan (01-02 through 01-05) can import these types without modification
- Application can start with storage.provider=local without requiring Supabase credentials

## Self-Check: PASSED

All 10 created files verified on disk. All 3 task commits verified in git history.

---
*Phase: 01-storage-foundation*
*Completed: 2026-03-06*
