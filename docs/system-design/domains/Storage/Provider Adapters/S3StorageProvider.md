---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-03-07
Domains:
  - "[[Storage]]"
---
# S3StorageProvider

Part of [[Provider Adapters]]

## Purpose

S3-compatible storage implementation supporting AWS S3 and compatible services (MinIO, R2, DigitalOcean Spaces). Active when `storage.provider=s3`.

---

## Responsibilities

- Upload, download, delete, and check existence of files in S3
- Generate presigned PUT URLs for direct client upload
- Generate presigned GET URLs for direct client download
- Bridge suspend AWS SDK calls to blocking via `runBlocking`
- Auto-create bucket on health check if missing
- Detect not-found conditions from various S3 error formats

---

## Dependencies

- `KLogger` — structured logging
- `S3Client` (from [[S3Configuration]]) — AWS SDK S3 client
- [[StorageConfigurationProperties]] — `s3.bucket` config

## Used By

- [[StorageService]] — via [[StorageProvider]] interface

---

## Key Logic

**Upload:** Reads entire `InputStream` into memory via `readAllBytes()`, wraps as `ByteStream.fromBytes()`, and calls `putObject`. This means the full file is held in JVM heap during upload.

**Download:** Consumes the response body inside the `getObject` lambda to avoid closed-stream issues (the S3 SDK closes the stream when the lambda exits). Returns bytes wrapped in a `ByteArrayInputStream`.

**Presigned upload URL:** Intentionally omits `contentType` from the `PutObjectRequest` to avoid S3 403 errors when the client uploads with a different Content-Type than what was signed. Content type validation happens post-upload via Tika detection in [[ContentValidationService]].

**Not-found detection:** `isNotFound()` checks for `NoSuchKey` and `NotFound` exception types, plus message-based matching for `"nosuchkey"`, `"404"`, and `"not found"`. This covers various S3-compatible services that report not-found differently.

**Health check:** Calls `headBucket`; if it throws, creates the bucket via `createBucket`. Returns false only if both operations fail.

---

## Public Methods

### `upload(key, content, contentType, contentLength): StorageResult`
Upload file bytes to S3 via `putObject`.

### `download(key): DownloadResult`
Download file from S3. Throws `StorageNotFoundException` if key does not exist.

### `delete(key)`
Delete file from S3 via `deleteObject`. Idempotent — S3 does not error on missing keys.

### `exists(key): Boolean`
Check existence via `headObject`. Returns false on not-found, throws `StorageProviderException` on other errors.

### `generateUploadUrl(key, contentType, expiresIn): String`
Presigned PUT URL via `presignPutObject`. Content type omitted from signature.

### `generateSignedUrl(key, expiresIn): String`
Presigned GET URL via `presignGetObject`.

### `healthCheck(): Boolean`
Verify bucket exists, auto-create if missing.

---

## Gotchas

- **Full file in memory:** Upload reads all bytes into heap. Large files will consume proportional memory.
- **`runBlocking` on servlet threads:** All S3 SDK calls are suspend functions bridged with `runBlocking`, which blocks the calling servlet thread. Under high concurrency this can exhaust the thread pool.
- **ContentType omitted from presigned PUT:** This is intentional to avoid 403 errors. The trade-off is that content type enforcement happens after upload, not during.
- **Duration conversion:** Uses `kotlin.time.toKotlinDuration()` to bridge `java.time.Duration` to Kotlin Duration for the SDK presigner.

---

## Related

- [[StorageProvider]]
- [[S3Configuration]]
- [[StorageConfigurationProperties]]
