---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-03-07
Domains:
  - "[[Storage]]"
---
# LocalStorageProvider

Part of [[Provider Adapters]]

## Purpose

Filesystem-based [[StorageProvider]] for development and self-hosted deployments. Active when `storage.provider=local`.

---

## Responsibilities

- Store files at `{basePath}/{key}` where basePath defaults to `./storage`
- Prevent path traversal attacks via resolved path validation
- Create parent directories on upload
- Detect content type on download via `Files.probeContentType()`
- Report health based on basePath writability

---

## Dependencies

- `KLogger` — structured logging
- [[StorageConfigurationProperties]] — `local.basePath` config

## Used By

- [[StorageService]] — via [[StorageProvider]] interface

---

## Key Logic

**Path traversal prevention:** `resolveAndValidate()` resolves the storage key against the base path, normalizes the result, then verifies it starts with the base path. If the resolved path escapes the base directory, throws `StorageProviderException`. This prevents keys like `../../etc/passwd` from accessing arbitrary filesystem locations.

**Upload:** Creates parent directories with `Files.createDirectories()`, then copies the input stream to the resolved path with `REPLACE_EXISTING`.

**Download:** Checks file existence first, throws `StorageNotFoundException` if absent. Probes content type via `Files.probeContentType()`, falling back to `application/octet-stream`.

**Delete:** Uses `Files.deleteIfExists()` — idempotent by design.

**Health check:** Returns `Files.isWritable(basePath)`. Catches exceptions and returns false.

---

## Public Methods

### `upload(key, content, contentType, contentLength): StorageResult`
Write file to `{basePath}/{key}`. Creates directories as needed.

### `download(key): DownloadResult`
Read file from `{basePath}/{key}`. Throws `StorageNotFoundException` if absent.

### `delete(key)`
Delete file at `{basePath}/{key}`. No-op if already absent.

### `exists(key): Boolean`
Check if file exists at `{basePath}/{key}`.

### `generateUploadUrl(key, contentType, expiresIn): String`
Always throws `UnsupportedOperationException`. Local storage does not support presigned upload URLs.

### `generateSignedUrl(key, expiresIn): String`
Always throws `UnsupportedOperationException`. [[StorageService]] falls back to HMAC-based [[SignedUrlService]].

### `healthCheck(): Boolean`
Returns true if basePath is writable.

---

## Gotchas

- **No presigned URLs:** Local provider cannot generate presigned URLs. [[StorageService]] catches `UnsupportedOperationException` and uses [[SignedUrlService]] for HMAC-based signed download URLs instead.
- **Path traversal guard:** `resolveAndValidate()` is called on every operation. A key containing `..` segments that would escape basePath throws `StorageProviderException`.
- **basePath is a computed property:** Re-evaluated on each call via `Paths.get(storageConfig.local.basePath).toAbsolutePath().normalize()`, so config changes take effect without restart.

---

## Related

- [[StorageProvider]]
- [[SignedUrlService]]
- [[StorageConfigurationProperties]]
