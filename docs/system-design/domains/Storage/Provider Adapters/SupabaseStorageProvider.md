---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-03-07
Domains:
  - "[[riven/docs/system-design/domains/Storage/Storage]]"
---
# SupabaseStorageProvider

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/Provider Adapters]]

## Purpose

Supabase Storage API implementation using supabase-kt client. Active when `storage.provider=supabase`.

---

## Responsibilities

- Upload, download, delete, and check existence of files in Supabase Storage
- Generate presigned upload URLs via `createSignedUploadUrl()`
- Generate signed download URLs via `createSignedUrl()`
- Bridge suspend supabase-kt calls to blocking via `runBlocking`
- Auto-create private bucket on health check if missing
- Parse MIME types into Ktor `ContentType` for the Supabase SDK

---

## Dependencies

- `KLogger` — structured logging
- `SupabaseClient` — supabase-kt client
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/StorageConfigurationProperties]] — `supabase.bucket` config

## Used By

- [[riven/docs/system-design/domains/Storage/File Management/StorageService]] — via [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/StorageProvider]] interface

---

## Key Logic

**Upload:** Uses upsert mode (`upsert = true`), which overwrites existing files with the same key. MIME type is parsed into Ktor `ContentType` via `parseContentType()`, falling back to `Application.OctetStream` on parse failure.

**Download:** Uses `downloadAuthenticated()` which returns raw bytes. Content type defaults to `"application/octet-stream"` because the Supabase download response does not include content type metadata.

**Presigned upload URL:** Uses `createSignedUploadUrl()` which returns an `UploadSignedUrl` object. The `.url` property contains the full presigned URL. Note: `upsert = false` is passed, meaning presigned uploads will fail if the key already exists.

**Signed download URL:** Uses `createSignedUrl()` with Kotlin Duration conversion via `toKotlinDuration()`.

**Health check:** Calls `retrieveBucketById()` to verify the bucket exists. If it throws, creates the bucket as private (`public = false`).

**`storagePlugin()` method:** Marked `protected open` to allow test subclasses to override the Storage plugin access, working around the fact that supabase-kt uses static extension functions that are difficult to mock.

---

## Public Methods

### `upload(key, content, contentType, contentLength): StorageResult`
Upload file bytes to Supabase Storage with upsert semantics.

### `download(key): DownloadResult`
Download file via `downloadAuthenticated()`. Throws `StorageNotFoundException` if absent.

### `delete(key)`
Delete file from Supabase Storage. Idempotent.

### `exists(key): Boolean`
Check file existence via Supabase SDK `exists()`.

### `generateUploadUrl(key, contentType, expiresIn): String`
Presigned upload URL via `createSignedUploadUrl()`.

### `generateSignedUrl(key, expiresIn): String`
Signed download URL via `createSignedUrl()`.

### `healthCheck(): Boolean`
Verify bucket exists, auto-create as private if missing.

---

## Gotchas

- **Content type lost on download:** `downloadAuthenticated()` does not return content type. All downloads report `application/octet-stream`. The actual content type is available from [[riven/docs/system-design/domains/Storage/File Management/FileMetadataEntity]] metadata.
- **`runBlocking` on servlet threads:** Same thread-blocking concern as [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/S3StorageProvider]].
- **`storagePlugin()` is `protected open`:** This exists solely for testability. The supabase-kt `storage` extension is a static call that cannot be mocked directly.
- **Not-found detection is message-based:** Checks for `"not found"`, `"404"`, and `"object not found"` in exception messages. Fragile if Supabase changes error messages.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/StorageProvider]]
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/StorageConfigurationProperties]]
