---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-03-07
Updated: 2026-03-12
Domains:
  - "[[riven/docs/system-design/domains/Storage/Storage]]"
---
# StorageService

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/File Management]]

## Purpose

Central orchestrator for all file storage operations. Coordinates content validation, provider I/O, metadata persistence, signed URL generation, and activity logging across upload, download, delete, list, metadata update, and batch flows.

---

## Responsibilities

- Upload files with MIME detection, content type/size validation, SVG sanitization, and metadata persistence
- Support user-scoped uploads (no metadata persistence or activity logging)
- Issue presigned upload URLs and confirm uploads after direct-to-provider transfer
- Update custom metadata with merge semantics (add, update, remove keys)
- Batch upload (max 10) and batch delete (max 50) with per-item success/failure
- Generate signed download URLs with provider-native fallback to HMAC tokens
- Soft-delete metadata then physically remove files from provider
- Stream file downloads authorized by signed token (no JWT required)
- Enforce workspace access control via `@PreAuthorize`
- Log activity for all create, update, and delete mutations

---

## Dependencies

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/ContentValidationService]] -- MIME detection, content type/size validation, SVG sanitization, storage key generation
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/SignedUrlService]] -- HMAC token generation/validation, download URL construction
- [[riven/docs/system-design/domains/Storage/Provider Adapters/StorageProvider]] -- Pluggable provider interface for physical file I/O (upload, download, delete, exists, signed URLs)
- [[riven/docs/system-design/domains/Storage/Provider Adapters/StorageConfigurationProperties]] -- Presigned upload expiry, signed URL secret and expiry config
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/FileMetadataRepository]] -- File metadata persistence
- [[riven/docs/system-design/domains/Workspaces & Users/User Management/ActivityService]] -- Audit trail logging
- [[riven/docs/system-design/domains/Workspaces & Users/Auth & Authorization/AuthTokenService]] -- JWT user ID extraction

## Used By

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/StorageController]] -- REST endpoints for all storage operations
- `UserService` -- Avatar uploads via `uploadUserFile`
- [[riven/docs/system-design/domains/Workspaces & Users/Workspace Management/WorkspaceService]] -- Workspace avatar upload during creation via `uploadFileInternal`
- [[riven/docs/system-design/domains/Workspaces & Users/Onboarding/OnboardingService]] -- File uploads during onboarding flow via `uploadFileInternal`

---

## Key Logic

### Upload flow

`uploadFile` delegates to `uploadFileInternal` (which contains the actual logic). `uploadFileInternal` is also callable directly without `@PreAuthorize` for onboarding and internal service use.

1. Detect MIME type via Tika magic bytes
2. Validate content type against `StorageDomain` allowlist
3. Validate file size against `StorageDomain` limit
4. Sanitize SVG content if detected type is `image/svg+xml`
5. Generate storage key: `{workspaceId}/{domain}/{uuid}.{ext}`
6. Upload to provider
7. Persist `FileMetadataEntity`
8. Log `FILE_UPLOAD` / `CREATE` activity
9. Return metadata with a signed download URL

### User file upload

Simplified flow for user-scoped files (e.g. avatars). Performs steps 1--6 with a user-scoped key (`users/{userId}/{domain}/{uuid}.{ext}`) but skips metadata persistence and activity logging. Returns the storage key directly.

### Presigned upload flow

Two-phase flow for direct-to-provider upload:

1. **Request phase** (`requestPresignedUpload`): generates a storage key and asks the provider for an upload URL. Returns `supported=false` for providers that don't support it (e.g. local filesystem).
2. **Confirm phase** (`confirmPresignedUpload`): verifies the file exists at the storage key, downloads it to validate content type via Tika, deletes from provider if validation fails, then persists metadata and logs activity.

### Signed URL generation

`generateProviderSignedUrl()` tries the provider's native signed URL first. On `UnsupportedOperationException` (e.g. local filesystem), falls back to [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/SignedUrlService]] HMAC-based tokens routed through the download endpoint.

### Download (token-authorized)

`downloadFile` does NOT use `@PreAuthorize` -- the signed token IS the authorization. Validates the HMAC token, downloads from the provider, and enriches the result with the original filename from metadata (if metadata exists).

### Metadata update

Merge semantics: new keys are added, existing keys are updated, keys with `null` values are removed. Validation enforces max 20 keys, key pattern `^[a-zA-Z0-9_-]{1,64}$`, and value max 1024 characters.

### Soft delete

`deleteFile` soft-deletes metadata first (sets `deleted=true`, `deletedAt=now()`), then attempts physical deletion from the provider. If physical deletion fails, the error is logged but the soft-delete is preserved -- metadata consistency takes priority over storage cleanup.

### Batch operations

Both `batchUpload` and `batchDelete` process each item independently. A failure in one item does not prevent others from being processed. Neither method is `@Transactional`, ensuring each item commits independently. Return 207 Multi-Status with per-item results.

---

## Public Methods

### `uploadFile(workspaceId, domain, file, metadata?): UploadFileResponse`

Upload a file with full validation, storage, metadata persistence, and activity logging. Returns file metadata and signed download URL. Delegates to `uploadFileInternal`.

### `uploadFileInternal(workspaceId, domain, file, metadata?): UploadFileResponse`

Internal method without `@PreAuthorize` -- used by [[riven/docs/system-design/domains/Workspaces & Users/Workspace Management/WorkspaceService]] for avatar uploads during workspace creation and by [[riven/docs/system-design/domains/Workspaces & Users/Onboarding/OnboardingService]] when the workspace role is not yet in the JWT. Contains the full upload logic (identical to `uploadFile` but bypasses workspace access check).

### `uploadUserFile(userId, domain, file): String`

Upload a user-scoped file without metadata persistence or activity logging. Returns the storage key.

### `requestPresignedUpload(workspaceId, domain, contentType?): PresignedUploadResponse`

Request a presigned upload URL for direct-to-provider upload. Returns `supported=false` for local provider.

### `confirmPresignedUpload(workspaceId, request): UploadFileResponse`

Confirm a presigned upload by validating content and persisting metadata. Deletes from provider if content type is rejected.

### `updateMetadata(workspaceId, fileId, request): FileMetadata`

Update custom metadata with merge semantics. Logs `FILE_UPDATE` / `UPDATE` activity.

### `batchUpload(workspaceId, domain, files): BatchUploadResponse`

Upload up to 10 files with per-item success/failure results.

### `batchDelete(workspaceId, request): BatchDeleteResponse`

Delete up to 50 files with per-item success/failure results.

### `getFile(workspaceId, fileId): FileMetadata`

Retrieve file metadata by ID within a workspace.

### `generateSignedUrl(workspaceId, fileId, expiresInSeconds?): SignedUrlResponse`

Generate a signed download URL with optional custom expiry.

### `deleteFile(workspaceId, fileId)`

Soft-delete metadata, then physically remove file from provider.

### `listFiles(workspaceId, domain?): FileListResponse`

List files in a workspace, optionally filtered by storage domain.

### `downloadFile(token): DownloadResult`

Download a file using a signed URL token. No `@PreAuthorize` -- the token is the authorization.

---

## Error Handling

| Exception | HTTP Status | Trigger |
|---|---|---|
| `ContentTypeNotAllowedException` | 415 | MIME type not in domain allowlist |
| `FileSizeLimitExceededException` | 413 | File exceeds domain size limit |
| `StorageNotFoundException` | 404 | File not found at storage key (presigned confirm) |
| `NotFoundException` | 404 | File metadata not found by ID |
| `SignedUrlExpiredException` | 403 | Download token invalid or expired |
| `IllegalArgumentException` | 400 | Metadata validation failures, empty batch, batch size exceeded |

---

## Gotchas

- **`uploadFileInternal` has no `@PreAuthorize`** -- it is called by `WorkspaceService` for avatar uploads during workspace creation and by `OnboardingService` during onboarding flows where workspace role is not yet in the JWT. Unlike `uploadUserFile`, it performs full metadata persistence and activity logging.
- **`uploadUserFile` has no `@PreAuthorize`** -- it is called by `UserService` for avatar uploads where the user is authenticated but not necessarily in a workspace context. It also skips metadata persistence entirely.
- **`downloadFile` has no `@PreAuthorize`** -- the signed token replaces workspace-scoped auth. This is intentional so files can be served to contexts without workspace sessions.
- **Physical delete failure is non-fatal** -- if the provider fails to delete the file, the soft-delete on metadata is preserved. This avoids orphaned metadata but may leave orphaned files in storage.
- **Batch operations are not transactional** -- each item commits independently so partial failures don't roll back successful items.
- **Presigned upload validation downloads the file** -- `confirmPresignedUpload` downloads the file from the provider to run Tika detection. This means the file traverses the network twice (client-to-provider, provider-to-API) but ensures content type validation cannot be bypassed.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/ContentValidationService]] -- MIME detection and validation
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/SignedUrlService]] -- HMAC token generation
- [[riven/docs/system-design/domains/Storage/Provider Adapters/StorageProvider]] -- Provider interface for physical I/O
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/StorageController]] -- REST endpoint layer
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/File Management]] -- Parent subdomain
- [[riven/docs/system-design/flows/Flow - File Upload]] -- End-to-end upload flow
- [[riven/docs/system-design/flows/Flow - Signed URL Download]] -- End-to-end download flow
