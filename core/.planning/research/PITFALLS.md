# Pitfalls Research

**Domain:** Provider-agnostic file storage abstraction (Spring Boot/Kotlin backend, TypeScript frontend)
**Researched:** 2026-03-05
**Confidence:** HIGH

## Critical Pitfalls

### Pitfall 1: Leaky Abstraction — Treating Object Storage as a Filesystem

**What goes wrong:**
The storage interface assumes filesystem semantics (directories, rename, move, atomic writes) that do not exist in S3-compatible object stores. S3 has no directories — only key prefixes. There is no atomic rename. Listing is prefix-based, not directory-based. The local filesystem adapter works perfectly, then the S3 adapter silently breaks or behaves differently in production.

**Why it happens:**
Developers design the interface against the local filesystem first (the easiest adapter to build), then discover S3 does not support operations they baked into the contract. The interface ends up with methods like `listDirectory()` or `moveFile()` that have no clean S3 equivalent.

**How to avoid:**
Design the interface against the **lowest common denominator** — S3's object storage model. The core interface should only support: `put(key, stream)`, `get(key)`, `delete(key)`, `list(prefix)`, `generatePresignedUrl(key, expiry)`, and `exists(key)`. No directory operations, no rename, no move. The local filesystem adapter adapts *up* to this model (keys become paths), not the other way around. This aligns with the PROJECT.md out-of-scope decision to exclude move/copy for v1.

**Warning signs:**
- Interface methods that mention "directory", "folder", "rename", or "move"
- Tests that pass on local filesystem but fail on S3/MinIO
- Adapter implementations that require multi-step workarounds (copy + delete to simulate rename)

**Phase to address:**
Phase 1 (Interface Design). Get the interface right before writing any adapter. The interface contract is the hardest thing to change later.

---

### Pitfall 2: Multipart Upload Memory Exhaustion in Spring Boot

**What goes wrong:**
Spring Boot's default multipart handling loads the entire file into memory before the controller receives it. With the default `file-size-threshold` of 0 (or misconfigured), a 200MB upload consumes 200MB of heap. Three concurrent uploads from different users crash the JVM with `OutOfMemoryError`. Spring Boot 3.5+ (which this project uses at 3.5.3) also introduced stricter Tomcat `maxPartsCount` limits that reject legitimate multipart requests.

**Why it happens:**
The default Spring Boot multipart config is designed for small form submissions, not file storage. Developers test with small files locally, never hit the memory ceiling, then deploy to production where real users upload real files.

**How to avoid:**
Configure multipart explicitly in `application.yml`:
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 50MB        # or whatever limit you want
      max-request-size: 55MB     # slightly above max-file-size
      file-size-threshold: 1MB   # write to temp disk above this
      location: /tmp/uploads     # temp file location
server:
  tomcat:
    max-http-form-post-size: -1  # disable Tomcat's own limit
    connection:
      max-parts: 20              # Spring Boot 3.5+ Tomcat limit
```
For proxied uploads, stream the `InputStream` directly to the storage provider — never call `multipartFile.getBytes()` or buffer in memory. For large files (>50MB), skip proxied upload entirely and use presigned URLs.

**Warning signs:**
- No explicit multipart config in `application.yml`
- Code calling `multipartFile.bytes` or `multipartFile.getBytes()` instead of `multipartFile.inputStream`
- No presigned URL path for large files
- Tests only use files under 1MB

**Phase to address:**
Phase 2 (Backend adapters / proxied upload implementation). Must be correct from the first line of upload handling code.

---

### Pitfall 3: Path Traversal Across Workspace Boundaries

**What goes wrong:**
A user uploads a file with a crafted filename like `../../other-workspace-id/secrets/credentials.json` or requests a file with a key containing `../`. Without path sanitization, the storage layer resolves the path relative to the workspace prefix and accesses another workspace's files. On the local filesystem adapter, this is a full path traversal vulnerability granting arbitrary file reads.

**Why it happens:**
The workspace-scoping logic constructs storage keys by concatenating user input: `"{workspaceId}/{domain}/{filename}"`. If the filename or any path segment is not sanitized, directory traversal escapes the workspace prefix. S3 is partially immune (keys are opaque strings, `..` has no special meaning), but the local filesystem adapter interprets `..` literally.

**How to avoid:**
1. **Sanitize all path components** at the service layer, before they reach any adapter. Strip leading slashes, reject any component containing `..`, reject null bytes.
2. **Canonicalize and verify** on the local filesystem adapter: resolve the full path, then verify it starts with the expected base directory.
3. **Generate storage keys server-side** — never let the client specify the full key. The client provides a filename; the server constructs `{workspaceId}/{domain}/{uuid}-{sanitizedFilename}`.
4. **Validate workspace ownership** on every read/delete — verify the requested file's workspace matches the authenticated user's workspace context.

**Warning signs:**
- Storage key construction uses raw user-provided filenames without sanitization
- No dedicated `sanitizePath()` or `sanitizeFilename()` utility
- Local filesystem adapter does not canonicalize paths
- Tests do not include path traversal attack vectors

**Phase to address:**
Phase 1 (Interface Design) for the sanitization contract, Phase 2 (Backend adapters) for adapter-specific canonicalization. This is a security boundary — get it right before any user-facing endpoint exists.

---

### Pitfall 4: Presigned URL Security Mismanagement

**What goes wrong:**
Presigned URLs are bearer tokens — anyone with the URL can access the file. Common failures: (1) URLs with excessively long expiry (hours/days instead of minutes), effectively creating permanent public links. (2) URLs that expire prematurely because the IAM role session expires before the URL's stated expiry. (3) Presigned upload URLs with no content-type or content-length constraints, letting users upload arbitrary large files of any type. (4) Generated URLs cached or logged, leaking access to server logs or CDN caches.

**Why it happens:**
Presigned URLs feel like "just a URL" but they are authorization tokens. Developers treat them casually — long expiry "for convenience", no upload constraints "because validation happens elsewhere", logging the full URL "for debugging".

**How to avoid:**
- **Download URLs:** 5-15 minute expiry maximum. Generate fresh on each request. Never cache.
- **Upload URLs:** Constrain content-type and content-length in the presigned URL policy. Validate the upload after completion (check file size, MIME type via magic bytes).
- **Never log** full presigned URLs. Log the key and expiry, not the signature.
- **Set `X-Content-Type-Options: nosniff`** on all served files to prevent MIME sniffing attacks.
- **Supabase-specific:** Supabase signed URLs use their own token format, not S3 presigned URLs. The abstraction must handle both URL generation patterns behind the same interface method.

**Warning signs:**
- Presigned URL expiry > 30 minutes for downloads
- No `Content-Type` or `Content-Length` conditions on upload URLs
- Presigned URLs appearing in application logs
- Frontend caching presigned URLs across page navigations

**Phase to address:**
Phase 3 (Presigned URL support). This is a distinct phase because it adds significant complexity beyond basic proxied upload/download.

---

### Pitfall 5: Inconsistent Provider Behavior in Error Cases

**What goes wrong:**
Each provider returns different errors for the same condition. S3 returns `NoSuchKey` for missing files. Supabase returns a 404 JSON response. Local filesystem throws `NoSuchFileException`. The abstraction layer leaks provider-specific exceptions to calling code, forcing services to handle multiple error types or, worse, catching generic `Exception`.

**Why it happens:**
The happy path is easy to abstract — `put`, `get`, `delete` all look similar across providers. The error path is where providers diverge, and it is typically the last thing implemented. Developers wrap provider calls and forget to normalize error handling.

**How to avoid:**
Define a sealed exception hierarchy for storage operations:
- `FileNotFoundException` — key does not exist
- `StorageQuotaExceededException` — provider rejected due to limits
- `StorageProviderException` — provider-level failure (network, auth, config)
- `InvalidFileException` — content validation failed

Each adapter catches provider-specific exceptions and maps them to these domain exceptions. The service layer never sees AWS SDK exceptions or Supabase HTTP errors.

**Warning signs:**
- Adapter methods that declare `throws Exception` or do not catch provider exceptions
- Service code with `try/catch` blocks referencing provider-specific exception classes
- Different HTTP status codes returned for the same logical error depending on which provider is configured

**Phase to address:**
Phase 1 (Interface Design) for the exception hierarchy definition, Phase 2 (Backend adapters) for the mapping implementation in each adapter.

---

### Pitfall 6: The "Interface Designed After the First Adapter" Trap

**What goes wrong:**
The team builds the Supabase adapter first (since it is the existing provider), extracts an interface from it, then discovers the interface does not fit S3 or the local filesystem. The Supabase adapter has methods like `getPublicUrl()` (Supabase has public buckets) that do not translate to S3 or local. Retrofitting the interface requires rewriting the first adapter.

**Why it happens:**
It is natural to start with the adapter you need immediately. But an interface extracted from a single implementation encodes that implementation's assumptions.

**How to avoid:**
Design the interface by writing **all three adapter signatures in pseudocode first** — Supabase, S3, and local filesystem. Identify what is common and what is provider-specific. The common subset becomes the interface. Provider-specific capabilities (like Supabase public buckets) are either excluded from v1 or handled via optional interface methods / capability flags.

**Warning signs:**
- Interface methods that only one adapter can implement naturally
- An adapter that implements interface methods as no-ops or throws `UnsupportedOperationException`
- Interface changes required when adding the second adapter

**Phase to address:**
Phase 1 (Interface Design). Spend time here. It is the single most important design decision in this project.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Buffering entire file in memory for upload | Simpler code, no streaming complexity | OOM crashes under concurrent uploads | Never — stream from day one |
| Hardcoding Supabase-specific paths in services | Fast integration with existing code | Every new adapter requires service changes | Never — services use the abstract interface only |
| Skipping file type validation on upload | Faster to ship, "we trust our users" | Malicious file uploads, XSS via SVG, stored malware | Never — validate magic bytes on every upload |
| Using filename as storage key directly | Human-readable storage paths | Filename collisions, path traversal, unicode issues | Only for local dev adapter, never in production |
| Single presigned URL expiry for all use cases | Simpler config | Upload URLs too short (user abandons), download URLs too long (security risk) | MVP only — split upload vs download expiry early |
| No file size metadata in database | One less column to maintain | Cannot enforce per-workspace quotas, cannot report usage | MVP only — add metadata table in Phase 2 |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| AWS S3 SDK v2 (Kotlin) | Using the synchronous S3Client for large files, blocking the thread pool | Use S3AsyncClient with streaming or presigned URLs for large files. Synchronous client is fine for metadata operations and small files. |
| Supabase Storage API | Assuming Supabase Storage API behaves identically to S3 | Supabase has its own auth model (service role key, user JWT), its own URL signing format, and bucket-level public/private policies. Test the adapter against actual Supabase, not just S3-compatibility assumptions. |
| MinIO (S3-compatible) | Assuming 100% S3 API compatibility | MinIO's presigned URL policy conditions and multipart upload behavior have minor differences. Test with MinIO explicitly if it is a target provider. |
| Local filesystem | Not handling OS-specific path separators, permissions, or symlinks | Use `java.nio.file.Path` APIs exclusively. Never construct paths with string concatenation. Set restrictive file permissions (600/700). Reject symlinks. |
| Spring Boot 3.5.3 multipart | Not accounting for Tomcat 10.1.39 stricter `maxPartsCount` | Explicitly configure `server.tomcat.max-http-form-post-size` and the parts limit. Test multipart uploads with metadata fields — each field counts as a "part". |
| Supabase Storage self-hosted | Assuming cloud Supabase and self-hosted Supabase Storage behave identically | Self-hosted defaults to local filesystem storage with a 50MB limit. CORS config requires matching `API_EXTERNAL_URL`. The storage API URL differs from the cloud version. |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Proxying all uploads through the backend | High CPU/memory usage, slow uploads, backend becomes bottleneck | Use presigned URLs for files > 10MB. Proxy only small files (avatars, thumbnails). | > 10 concurrent uploads of files > 5MB |
| Synchronous file listing on large prefixes | API timeout on `listFiles()`, UI hangs | Paginate listing results. Set a max-keys limit (1000). Never list an entire workspace recursively. | > 1000 files in a single prefix |
| Generating presigned URLs on every page render | Unnecessary latency on page loads, API rate limits on providers | Cache download URLs client-side for their validity period minus a buffer. Generate lazily on interaction, not on page mount. | > 50 files displayed per page |
| No connection pooling for S3 client | New HTTP connection per storage operation, high latency | Configure the S3 SDK HTTP client with connection pooling. Reuse the S3 client instance (Spring singleton bean). | > 100 storage operations per minute |
| Storing file metadata only in the provider | Listing files requires calling the provider API, no local query capability | Store file metadata (key, size, content type, workspace, upload timestamp) in PostgreSQL. Query locally, access provider only for content. | Any file listing or search feature |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Validating file type by Content-Type header only | Attackers set `Content-Type: image/png` on a `.html` file containing XSS payloads | Validate by reading magic bytes (file signature) from the first bytes of the file. Use Apache Tika or similar. Reject mismatches. |
| Serving user-uploaded files from the same origin | Stored XSS via SVG files, HTML files, or MIME-sniffed content | Serve files from a different origin (CDN subdomain or presigned URL). Set `Content-Disposition: attachment` for non-image types. Set `X-Content-Type-Options: nosniff`. |
| No per-workspace access check on file download | User in workspace A downloads files from workspace B by guessing/enumerating keys | Every file access (download, presigned URL generation, delete) must verify the requesting user has membership in the workspace that owns the file. Check at the service layer. |
| Presigned upload URLs with no content constraints | Users upload 10GB files or executable binaries via unconstrained presigned URLs | Set `Content-Length-Range` and `Content-Type` conditions in the presigned URL policy. Validate after upload completes. |
| Storing files with user-provided filenames as keys | Path traversal, filename collisions, unicode normalization attacks | Generate UUID-based keys server-side. Store the original filename as metadata in PostgreSQL, not as part of the storage key. |
| Not scanning or validating uploaded files | Malware distribution, polyglot files that are valid images AND valid HTML | Validate magic bytes match declared type. For images, attempt to decode with an image library (invalid images fail). Consider ClamAV integration for high-risk deployments. |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| No upload progress indication | User thinks upload failed, re-uploads, creates duplicates | For proxied uploads: use multipart chunked upload with progress events. For presigned URLs: `XMLHttpRequest` or `fetch` with upload progress tracking on the frontend. |
| Presigned URL expires during slow upload | User completes a long upload form, clicks submit, gets a cryptic 403 error | Generate presigned URLs on submit, not on page load. For uploads expected to take > 5 minutes, use multipart upload with per-chunk presigned URLs. |
| No file type feedback before upload starts | User selects a .exe file, waits for upload, then gets rejected | Validate file extension and MIME type client-side before upload begins. This is UX sugar, not a security boundary — server-side validation is still required. |
| Silent failure on upload/download errors | User does not know their file failed to save | Return structured error responses from the storage API. Frontend shows specific error messages: "File too large" (not "Upload failed"), "File type not allowed" (not "Error"). |
| No thumbnail/preview for uploaded images | Users cannot verify they uploaded the correct image | Generate thumbnails server-side on upload (or lazily on first access). Store thumbnail as a separate object at a predictable key pattern. |

## "Looks Done But Isn't" Checklist

- [ ] **Proxied upload:** Often missing streaming implementation — verify `InputStream` is piped directly to provider, not buffered with `getBytes()`
- [ ] **File deletion:** Often missing cleanup of metadata in PostgreSQL when provider file is deleted, or vice versa (orphaned metadata / orphaned files)
- [ ] **Presigned URLs:** Often missing expiry validation on the frontend — verify expired URLs trigger re-fetch, not a broken image/download
- [ ] **Local filesystem adapter:** Often missing directory creation on first write — verify `Files.createDirectories()` is called before `Files.write()`
- [ ] **Error handling:** Often missing provider-specific error mapping — verify a missing file returns 404, not 500
- [ ] **Multi-tenant isolation:** Often missing workspace validation on read/delete — verify a user cannot access files from another workspace
- [ ] **Content-Type:** Often missing proper Content-Type setting on upload — verify files are served with correct MIME type, not `application/octet-stream`
- [ ] **Cleanup on failed upload:** Often missing rollback when upload succeeds at provider but metadata write fails — verify no orphaned files on error
- [ ] **Configuration validation:** Often missing startup validation of provider config — verify the app fails fast with a clear error if `STORAGE_PROVIDER=s3` but no S3 credentials are set

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Leaky abstraction (filesystem semantics in interface) | HIGH | Redesign interface, rewrite all adapters, update all calling services. This is why getting the interface right in Phase 1 is critical. |
| Memory exhaustion from buffered uploads | LOW | Configuration change + refactor upload handler to use InputStream streaming. No interface changes needed. |
| Path traversal vulnerability | MEDIUM | Add sanitization utility, audit all existing storage keys in database, re-key any files with suspicious paths. |
| Presigned URL security issues | LOW | Reduce expiry time, add policy conditions. No data migration needed. |
| Provider-specific exceptions leaking | MEDIUM | Add exception mapping to each adapter, update service-layer catch blocks. Mechanical but touches many files. |
| Missing file metadata in database | HIGH | Requires schema migration, backfill job to scan provider and populate metadata, and service changes to write metadata on upload. |
| Same-origin file serving (XSS risk) | MEDIUM | Add `Content-Disposition` and `X-Content-Type-Options` headers. Move file serving to presigned URLs or a different subdomain. |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Leaky abstraction | Phase 1 (Interface Design) | Interface has no filesystem-specific methods. All three adapters can implement every method without `UnsupportedOperationException`. |
| Memory exhaustion | Phase 2 (Backend Adapters) | Load test with 50MB file upload. Monitor JVM heap during upload — should not spike by file size. |
| Path traversal | Phase 1 (Interface) + Phase 2 (Adapters) | Security test: upload file with `../../etc/passwd` as filename. Verify it is sanitized or rejected. |
| Presigned URL security | Phase 3 (Presigned URLs) | Verify download URL expires after configured time. Verify upload URL rejects wrong content type. Verify URLs are not logged. |
| Inconsistent error handling | Phase 2 (Backend Adapters) | Request a non-existent file via each adapter. All return the same exception type and HTTP status. |
| Interface-from-first-adapter trap | Phase 1 (Interface Design) | Write adapter signatures for all three providers before finalizing the interface. Review that no adapter needs workarounds. |
| Missing file metadata | Phase 2 (Backend Adapters) | Every upload creates a metadata record in PostgreSQL. File listing queries PostgreSQL, not the provider. |
| Content type validation | Phase 2 (Backend Adapters) | Upload a `.html` file with `Content-Type: image/png`. Verify it is rejected based on magic byte mismatch. |
| Same-origin file serving | Phase 3 (Presigned URLs) | Files are never served from the app's origin. All downloads go through presigned URLs or a separate domain. |
| Upload UX issues | Phase 4 (Frontend Integration) | Upload a 20MB file and verify progress bar. Let a presigned URL expire and verify graceful re-fetch. |

## Sources

- [S3 is files, but not a filesystem](https://calpaterson.com/s3.html) — on why S3 object semantics differ from filesystem semantics
- [OWASP File Upload Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html) — file upload security best practices
- [OWASP Path Traversal](https://owasp.org/www-community/attacks/Path_Traversal) — path traversal attack patterns
- [OWASP Multi-Tenant Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Multi_Tenant_Security_Cheat_Sheet.html) — tenant isolation patterns
- [AWS Presigned URL Documentation](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html) — presigned URL behavior and constraints
- [Securing S3 Presigned URLs](https://aws.amazon.com/blogs/compute/securing-amazon-s3-presigned-urls-for-serverless-applications/) — presigned URL security patterns
- [Pre-signed URLs in S3 - gotchas that got me](https://jsherz.com/aws/s3/simple%20storage%20service/2023/02/25/presigned-urls-in-aws-s3-gotchas.html) — real-world presigned URL issues
- [Supabase Storage File Limits](https://supabase.com/docs/guides/storage/uploads/file-limits) — Supabase upload constraints
- [Supabase S3 Compatibility](https://supabase.com/docs/guides/storage/s3/compatibility) — Supabase S3 protocol support
- [Spring Boot Large File Uploads](https://oneuptime.com/blog/post/2026-01-25-large-file-uploads-spring-boot/view) — streaming upload patterns
- [Spring Boot 3.5 Multipart Changes](https://medium.com/@keerthanacdurai/spring-boot-3-5-breaking-multipart-file-uploads-heres-the-fix-you-need-bcbfe50d0310) — Tomcat 10.1.39 breaking changes
- [File Upload Content Type Bypass](https://www.sourcery.ai/vulnerabilities/file-upload-content-type-bypass) — MIME validation bypass techniques

---
*Pitfalls research for: Provider-agnostic file storage abstraction*
*Researched: 2026-03-05*
