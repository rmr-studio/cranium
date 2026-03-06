# Phase 3: Advanced Operations - Context

**Gathered:** 2026-03-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Add presigned upload URLs (direct-to-provider uploads for large files), batch upload/delete operations, and custom file metadata (key-value pairs). Backend-only -- frontend StorageProvider TypeScript integration is deferred to the client repo. Phase renamed from "Advanced Operations and Frontend" to "Advanced Operations" to reflect actual scope.

</domain>

<decisions>
## Implementation Decisions

### Presigned upload flow
- Two-step flow: client requests presigned upload URL -> uploads directly to provider -> confirms upload via backend endpoint
- Backend generates the UUID-based storage key and includes it in the presigned URL response (client never generates keys)
- Confirmation endpoint accepts storage key + original filename; backend verifies file exists in provider via `storageProvider.exists()`, then persists metadata
- Content validation on confirmation: backend downloads the file from provider, runs Tika validation, and rejects (deletes from provider) if content type is invalid for the domain
- Local adapter: throws `UnsupportedOperationException` on `generateUploadUrl()`. API response signals "use proxied upload instead" so client falls back to standard multipart upload
- Both S3 and Supabase adapters support presigned upload URLs -- add `generateUploadUrl()` to `StorageProvider` interface

### Batch operations
- Batch upload: standard multipart/form-data with multiple `files` parts + shared `domain` parameter. Spring's `@RequestParam("files") List<MultipartFile>` support
- Batch delete: JSON body with array of file IDs (UUIDs), consistent with single-delete using fileId
- Partial success model: return 207 Multi-Status with per-item result array (success/failure + error details per item). Successfully processed items are committed; failed items include error details for client retry
- Fixed limits: max 10 files per batch upload, max 50 file IDs per batch delete. Returns 400 if exceeded

### File metadata (DATA-05)
- JSONB column (`metadata`) on `FileMetadataEntity` using Hypersistence `JsonBinaryType` -- same pattern as other JSONB columns in the codebase
- Attachable at upload time (optional `metadata` JSON parameter on upload endpoint) and patchable after via PATCH endpoint
- Update semantics: merge/patch -- only specified keys are updated, unspecified keys preserved, send null to remove a key
- Light constraints: max 20 key-value pairs, keys max 64 chars (alphanumeric + underscore/hyphen), values are strings only (max 1024 chars)
- Metadata included in file listing responses (FileListResponse) -- no extra round-trip needed
- No metadata filtering in v1 listings -- defer JSONB query support to future if needed
- Activity logging: FILE_UPDATE activity type for metadata changes, with old/new values in activity details

### Frontend integration
- Deferred to client repo -- API-02 (frontend StorageProvider TypeScript interface) and API-03 (frontend adapter factory) are not implemented in this backend repo
- This phase delivers the backend API endpoints that the frontend will consume

### Claude's Discretion
- `generateUploadUrl()` method signature and return type on StorageProvider interface
- Presigned URL expiry duration for uploads
- Exact 207 Multi-Status response structure for batch operations
- Metadata validation implementation details
- How to signal "presigned not supported" in the API response for local adapter fallback
- DB schema migration for the new metadata JSONB column

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `StorageProvider` interface (`models/storage/StorageProvider.kt`): needs `generateUploadUrl()` method added
- `StorageService`: orchestrates upload flow -- presigned upload confirmation will follow similar pattern (validate, persist metadata, log activity)
- `StorageController`: existing endpoints to extend with batch and presigned upload routes
- `FileMetadataEntity`: add JSONB `metadata` column
- `ContentValidationService`: reuse for post-upload validation on presigned flow
- `ActivityService.log()`: existing activity logging pattern for FILE_UPDATE

### Established Patterns
- `@ConditionalOnProperty` for adapter activation -- presigned upload follows same pattern (provider supports or throws UnsupportedOperationException)
- Provider-first fallback: `try provider, catch UnsupportedOperationException, fallback` -- same pattern for generateUploadUrl
- Hypersistence `@Type(JsonBinaryType::class)` with `columnDefinition = "jsonb"` for JSONB columns
- `@PreAuthorize` on all workspace-scoped service methods
- Multipart file handling in `StorageController.uploadFile()` -- extend for batch

### Integration Points
- `StorageProvider` interface: add `generateUploadUrl()` method -- all 3 adapters need implementation
- `StorageController`: add presigned upload request/confirm endpoints, batch upload/delete endpoints, metadata PATCH endpoint
- `StorageService`: add presigned upload orchestration, batch processing, metadata update methods
- `FileMetadataEntity` + DB schema: add `metadata` JSONB column
- `Activity` enum: add `FILE_UPDATE` variant
- Response models: new batch result response, presigned URL response models

</code_context>

<specifics>
## Specific Ideas

No specific requirements -- open to standard approaches.

</specifics>

<deferred>
## Deferred Ideas

- Frontend StorageProvider TypeScript interface (API-02) -- implement in client repo
- Frontend storage adapter factory (API-03) -- implement in client repo
- Metadata filtering in file listings (JSONB query support) -- add when needed

</deferred>

---

*Phase: 03-advanced-operations*
*Context gathered: 2026-03-06*
