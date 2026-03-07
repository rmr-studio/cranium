---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-03-07
Domains:
  - "[[Storage]]"
---
# StorageProvider

Part of [[Provider Adapters]]

## Purpose

Interface defining the contract all storage backends must implement. Activated via `@ConditionalOnProperty` — exactly one implementation is active at runtime.

---

## Responsibilities

- Define a uniform API for file upload, download, delete, and existence checks
- Define presigned URL generation for direct-to-provider uploads and signed downloads
- Define a health check contract for provider readiness verification

---

## Dependencies

- None (interface only)

## Used By

- [[StorageService]] — delegates all I/O to the active provider

---

## Key Logic

**Key format:** All methods operate on storage keys in the format `{workspaceId}/{domain}/{uuid}.{ext}`. Key generation is handled by [[ContentValidationService]], not the provider.

**Blocking contract:** All methods are blocking (non-suspend) to match the synchronous Spring MVC codebase. Providers wrapping async SDKs (S3, Supabase) use `runBlocking` internally.

**Presigned URL support:** Not all providers support presigned URLs. [[LocalStorageProvider]] throws `UnsupportedOperationException` for `generateUploadUrl()` and `generateSignedUrl()`, which [[StorageService]] catches and falls back to HMAC-based [[SignedUrlService]].

---

## Public Methods

### `upload(key: String, content: InputStream, contentType: String, contentLength: Long): StorageResult`
Store file content at the given key. Returns metadata including the storage key and content info.

### `download(key: String): DownloadResult`
Retrieve file content and metadata. Throws `StorageNotFoundException` if the key does not exist.

### `delete(key: String)`
Remove a file. All implementations are idempotent — no error if the file is already absent.

### `exists(key: String): Boolean`
Check whether a file exists at the given key.

### `generateUploadUrl(key: String, contentType: String, expiresIn: Duration): String`
Generate a time-limited presigned URL for direct upload. Throws `UnsupportedOperationException` if the provider does not support it.

### `generateSignedUrl(key: String, expiresIn: Duration): String`
Generate a time-limited signed URL for direct download. Throws `UnsupportedOperationException` if the provider does not support it.

### `healthCheck(): Boolean`
Verify the storage backend is healthy and reachable.

---

## Gotchas

- **UnsupportedOperationException is expected:** [[StorageService]] explicitly catches this for local provider fallback. Do not treat it as a bug.
- **Lives in `models.storage`, not `service.storage`:** The interface is in the models package because it defines a domain contract, not a Spring service. Concrete implementations are in `service.storage`.

---

## Related

- [[LocalStorageProvider]]
- [[S3StorageProvider]]
- [[SupabaseStorageProvider]]
- [[StorageService]]
