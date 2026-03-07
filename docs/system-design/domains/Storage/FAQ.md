This document outlines common questions for understanding the Storage domain's architecture and key design decisions.

### How does the storage provider get selected at runtime

The system uses the strategy pattern with Spring's `@ConditionalOnProperty("storage.provider")`. Exactly one `StorageProvider` implementation is active at runtime based on the `storage.provider` configuration value: `local`, `s3`, or `supabase`. Each provider class — `LocalStorageProvider`, `S3StorageProvider`, `SupabaseStorageProvider` — is annotated with `@ConditionalOnProperty(name = ["storage.provider"], havingValue = "...")`. The `SupabaseConfiguration` bean (which creates the Supabase client) is also conditional on `storage.provider=supabase`, and `S3Configuration` (which creates the S3Client) is conditional on `storage.provider=s3`. This means provider-specific beans are only created when needed.

`StorageService` depends on the `StorageProvider` interface. It doesn't know which implementation is active — it delegates all physical I/O through the interface.

### How are files validated before upload

Validation happens in `ContentValidationService` and is orchestrated by `StorageService.uploadFile()`:

1. **MIME detection via Tika magic bytes** — Apache Tika reads the file's byte signature to detect the true MIME type. The original filename is used only as a hint when magic bytes are ambiguous (e.g. SVG). This prevents content type spoofing via renamed files (e.g. a `.jpg` that is actually a PDF).
2. **Content type allowlist** — Each `StorageDomain` enum variant defines an `allowedContentTypes` set. If the detected type is not in the set, `ContentTypeNotAllowedException` is thrown (HTTP 415).
3. **File size limit** — Each `StorageDomain` defines a `maxFileSize` in bytes. If exceeded, `FileSizeLimitExceededException` is thrown (HTTP 413).
4. **SVG sanitization** — If the detected type is `image/svg+xml`, the file is passed through `SVGSanitizer.sanitize()` which strips `<script>` tags, event handlers (`onload`, `onclick`, etc.), and embedded JavaScript. The sanitized bytes replace the original content before storage.

For presigned uploads, validation happens *after* the client uploads directly to the provider. `confirmPresignedUpload` downloads the file from the provider, runs Tika detection, and if the content type is rejected, deletes the file from the provider before throwing.

### How do signed download URLs work

There are two mechanisms, and `StorageService` selects automatically:

1. **Provider-native signed URLs** — S3 and Supabase providers can generate time-limited presigned GET URLs directly. `StorageService.generateProviderSignedUrl()` tries this first.
2. **HMAC-based fallback** — When the provider throws `UnsupportedOperationException` (as `LocalStorageProvider` does), `SignedUrlService` generates an HMAC-SHA256 token. The token encodes `{storageKey}:{expiresAtEpochSeconds}:{hmacSignature}` as a Base64URL string, embedded in a URL path: `/api/v1/storage/download/{token}`.

The download endpoint (`GET /api/v1/storage/download/{token}`) is `permitAll()` in SecurityConfig — no JWT required. The signed token IS the authorization. `SignedUrlService.validateToken()` uses `MessageDigest.isEqual()` for constant-time comparison to prevent timing attacks. Expiry is checked against the current epoch second.

Provider-native URLs point directly to S3/Supabase, bypassing the API server entirely. HMAC fallback URLs route through the API server, which streams the file via `StreamingResponseBody`.

### What happens when a file is deleted

`StorageService.deleteFile()` follows a two-step process:

1. **Soft-delete metadata first** — sets `deleted=true` and `deletedAt=now()` on the `FileMetadataEntity` and saves. The `@SQLRestriction("deleted = false")` on the entity makes it invisible to all subsequent queries.
2. **Physical delete second** — calls `storageProvider.delete(storageKey)`. If this fails, the error is logged but the soft-delete is preserved.

This ordering ensures metadata consistency: a file can never appear in queries after deletion starts, even if the physical file lingers in storage. Orphaned physical files are a lower-cost failure than orphaned metadata pointing to deleted files.

All three providers implement idempotent delete — no error is thrown if the file is already absent.

### What is the difference between uploadFile and uploadUserFile

| Aspect | `uploadFile` | `uploadUserFile` |
|--------|-------------|-----------------|
| Scope | Workspace-scoped | User-scoped |
| `@PreAuthorize` | Yes (`workspaceSecurity.hasWorkspace`) | No |
| Storage key format | `{workspaceId}/{domain}/{uuid}.{ext}` | `users/{userId}/{domain}/{uuid}.{ext}` |
| Metadata persistence | Yes (`FileMetadataEntity` saved) | No |
| Activity logging | Yes (`FILE_UPLOAD` / `CREATE`) | No |
| Returns | `UploadFileResponse` (metadata + signed URL) | `String` (storage key only) |
| Used by | `StorageController` | `UserService` (avatar uploads) |

`uploadUserFile` exists because user avatars are set outside of a workspace context — the user is authenticated but not necessarily operating within a workspace. It still performs full content validation (MIME detection, type/size checks, SVG sanitization).

### How does the presigned upload flow work

Presigned upload allows clients to upload files directly to the storage provider (S3 or Supabase) without proxying through the API server. This is a two-phase flow:

**Phase 1 — Request (`requestPresignedUpload`):**
- Generates a storage key via `ContentValidationService.generateStorageKey()`
- Asks the provider for a presigned PUT URL with a configurable expiry (default 900 seconds)
- For local provider, returns `supported=false` (client should use regular `uploadFile` instead)
- Returns the storage key and upload URL to the client

**Phase 2 — Confirm (`confirmPresignedUpload`):**
- Verifies the file exists at the storage key via `storageProvider.exists()`
- Downloads the file from the provider to validate content type via Tika
- If content type is rejected, deletes the file from the provider before throwing `ContentTypeNotAllowedException`
- Persists `FileMetadataEntity` and logs activity
- Returns metadata with a signed download URL

The double network hop (client→provider, then provider→API for validation) is intentional — it ensures content type validation cannot be bypassed by uploading directly to the provider.

### How is custom metadata associated with a file

Each `FileMetadataEntity` has an optional JSONB `metadata` column (`Map<String, String>?`). Custom metadata can be set at upload time (passed as a JSON string in the multipart form) or updated later via `updateMetadata`.

**Update semantics are merge-based:**
- New keys are added
- Existing keys are updated with new values
- Keys with `null` values are removed
- If the result is empty, the column is set to `null`

**Validation rules:**
- Maximum 20 key-value pairs
- Key pattern: `^[a-zA-Z0-9_-]{1,64}$`
- Value maximum: 1024 characters

Metadata updates are logged as `FILE_UPDATE` / `UPDATE` activity with both the previous and updated metadata in the activity details.

### What is the storage key format and why UUIDs

Storage keys follow the pattern `{workspaceId}/{domain}/{uuid}.{ext}` (or `users/{userId}/{domain}/{uuid}.{ext}` for user-scoped files). The key is the canonical identifier for a file in the storage backend — it maps 1:1 to a file in the provider.

UUIDs are used for filenames instead of original filenames to prevent collisions, avoid special character handling across providers, and eliminate information leakage in storage keys. The original filename is preserved in `FileMetadataEntity.originalFilename` and returned in download responses for `Content-Disposition`.

The file extension is derived from the detected MIME type using Tika's MIME type registry, not from the original filename. If Tika can't map the type to an extension, the file is stored without one.

### How do batch operations handle partial failures

Both `batchUpload` (max 10 files) and `batchDelete` (max 50 IDs) process items independently. Each item succeeds or fails on its own — a failure in one does not prevent others from processing.

Neither method is annotated with `@Transactional`, so each item commits independently. The response uses HTTP 207 Multi-Status with a `results` array of `BatchItemResult` objects, each containing an `id`, `filename`, `status` code, and optional `error` message. The response also includes `succeeded` and `failed` counts.

For batch upload, individual failures are caught and classified: `ContentTypeNotAllowedException` → 415, `FileSizeLimitExceededException` → 413, any other exception → 500. For batch delete, `NotFoundException` → 404, any other exception → 500.

### What tables make up the storage system

| Table | Purpose | Delete Strategy |
|-------|---------|-----------------|
| `file_metadata` | Workspace-scoped file metadata (domain, storage key, content type, size, uploader, custom JSONB metadata) | Soft-delete |

Indexes:
- `idx_file_metadata_workspace_id` — query by workspace
- `idx_file_metadata_workspace_domain` — query by workspace + domain
- `uq_file_metadata_storage_key` — unique constraint on storage key

The physical files themselves are stored outside the database — in the local filesystem, S3 bucket, or Supabase Storage bucket, depending on the active provider.

### How does workspace security interact with storage

All workspace-scoped `StorageService` methods use `@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")` to verify the authenticated user has membership in the target workspace. This is the same security pattern used across all other domains.

Two methods intentionally bypass workspace security:
- `uploadUserFile` — user-scoped, called by `UserService` for avatar uploads where workspace context doesn't apply
- `downloadFile` — uses signed token authorization instead of JWT/workspace checks. The download endpoint is `permitAll()` in SecurityConfig.

File metadata queries (`findByIdAndWorkspaceId`) inherently filter by workspace ID, providing data-level isolation even beyond the `@PreAuthorize` check.
