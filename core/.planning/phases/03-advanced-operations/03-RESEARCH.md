# Phase 3: Advanced Operations - Research

**Researched:** 2026-03-06
**Domain:** Presigned uploads, batch file operations, custom file metadata (Spring Boot / Kotlin / S3 / Supabase)
**Confidence:** HIGH

## Summary

Phase 3 adds three capabilities to the existing storage system: (1) presigned upload URLs for direct-to-provider large file uploads with a two-step request-then-confirm flow, (2) batch upload and batch delete endpoints with 207 Multi-Status partial success semantics, and (3) a JSONB metadata column on `FileMetadataEntity` for custom key-value pairs.

All three features build directly on established codebase patterns. The `StorageProvider` interface needs one new method (`generateUploadUrl`). The presigned upload flow reuses `ContentValidationService` for post-upload Tika validation, `storageProvider.exists()` for confirmation, and the existing `persistMetadata` / `logActivity` patterns. Batch operations use Spring's native `@RequestParam("files") List<MultipartFile>` support. The JSONB metadata column follows the exact Hypersistence `JsonBinaryType` pattern used in 7+ other entities. No new dependencies are required.

**Primary recommendation:** Implement as two plans -- (1) presigned upload + metadata JSONB column, (2) batch operations. This separates the StorageProvider interface change from the batch processing logic.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Two-step presigned upload flow: request URL -> upload to provider -> confirm via backend endpoint
- Backend generates UUID storage key (client never generates keys)
- Confirmation endpoint verifies file exists via `storageProvider.exists()`, then persists metadata
- Content validation on confirmation: backend downloads file, runs Tika, rejects + deletes if invalid
- Local adapter throws `UnsupportedOperationException` on `generateUploadUrl()`, API signals "use proxied upload instead"
- Both S3 and Supabase adapters support presigned upload URLs
- Batch upload: multipart/form-data with multiple `files` parts + shared `domain` parameter
- Batch delete: JSON body with array of file IDs (UUIDs)
- Partial success model: 207 Multi-Status with per-item result array
- Fixed limits: max 10 files per batch upload, max 50 per batch delete. 400 if exceeded
- JSONB `metadata` column on `FileMetadataEntity` using Hypersistence `JsonBinaryType`
- Metadata attachable at upload time, patchable after via PATCH endpoint
- Merge/patch semantics: only specified keys updated, null to remove
- Constraints: max 20 key-value pairs, keys max 64 chars (alphanumeric + underscore/hyphen), values strings only max 1024 chars
- Metadata included in `FileListResponse` -- no extra round-trip
- No metadata filtering in v1 listings
- Activity logging: `FILE_UPDATE` activity type for metadata changes
- Frontend integration (API-02, API-03) deferred to client repo

### Claude's Discretion
- `generateUploadUrl()` method signature and return type on StorageProvider interface
- Presigned URL expiry duration for uploads
- Exact 207 Multi-Status response structure for batch operations
- Metadata validation implementation details
- How to signal "presigned not supported" in API response for local adapter fallback
- DB schema migration for the new metadata JSONB column

### Deferred Ideas (OUT OF SCOPE)
- Frontend StorageProvider TypeScript interface (API-02) -- implement in client repo
- Frontend storage adapter factory (API-03) -- implement in client repo
- Metadata filtering in file listings (JSONB query support) -- add when needed
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| FILE-09 | Presigned URL generation for direct-to-provider large file uploads | `presignPutObject` available in AWS SDK Kotlin 1.3.112; Supabase `createSignedUploadUrl` available in supabase-kt 3.1.4. Add `generateUploadUrl()` to `StorageProvider` interface. |
| FILE-10 | Presigned upload confirmation -- client notifies backend after direct upload, metadata recorded | Reuse existing `storageProvider.exists()`, `ContentValidationService.detectContentType()`, `persistMetadata()` pattern from `uploadFile()`. Download file for Tika validation, delete if invalid. |
| FILE-11 | Batch upload -- multiple files in one request | Spring MVC natively supports `@RequestParam("files") files: List<MultipartFile>`. Reuse per-file `uploadFile` logic in a loop with per-item error collection. |
| FILE-12 | Batch delete -- multiple files in one request | Reuse existing `deleteFile` logic per ID with per-item error collection. Return 207 Multi-Status. |
| DATA-05 | File metadata attachment and retrieval (custom key-value metadata per file) | Add JSONB `metadata` column to `FileMetadataEntity` using `@Type(JsonBinaryType::class)` -- exact pattern from `IntegrationDefinitionEntity`. |
| API-02 | Frontend StorageProvider TypeScript interface | DEFERRED to client repo per CONTEXT.md locked decisions. |
| API-03 | Frontend storage adapter factory | DEFERRED to client repo per CONTEXT.md locked decisions. |
</phase_requirements>

## Standard Stack

### Core (already in project -- no new dependencies)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| AWS SDK for Kotlin S3 | 1.3.112 | `presignPutObject` for S3 presigned upload URLs | Already in `build.gradle.kts`, presigner extensions in same package as existing `presignGetObject` |
| supabase-kt storage-kt | 3.1.4 (BOM) | `createSignedUploadUrl` for Supabase presigned uploads | Already in `build.gradle.kts` |
| Hypersistence Utils | 3.9.2 | `JsonBinaryType` for JSONB `metadata` column | Already used in 7+ entities in the codebase |
| Spring MVC | via Boot 3.5.3 | `List<MultipartFile>` for batch uploads, `@RequestBody` for batch delete | Native multipart support, no extra config needed |
| Apache Tika | 3.2.0 | Content validation on presigned upload confirmation | Already used by `ContentValidationService` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| 207 Multi-Status | 200 with error array | 207 is semantically correct for mixed results per HTTP spec; 200 hides partial failures |
| JSONB metadata column | Separate metadata table | JSONB is simpler, avoids joins, matches existing JSONB patterns; separate table only needed for querying/indexing metadata keys |

## Architecture Patterns

### Recommended Changes to Existing Structure
```
src/main/kotlin/riven/core/
├── models/storage/
│   └── StorageProvider.kt          # ADD generateUploadUrl() method
│   └── FileMetadata.kt             # ADD metadata: Map<String, String>? field
├── models/request/storage/
│   └── StorageRequests.kt          # ADD PresignedUploadRequest, ConfirmUploadRequest,
│                                   #     BatchDeleteRequest, UpdateMetadataRequest
├── models/response/storage/
│   └── StorageResponses.kt         # ADD PresignedUploadResponse, BatchUploadResponse,
│                                   #     BatchDeleteResponse, BatchItemResult
├── entity/storage/
│   └── FileMetadataEntity.kt       # ADD metadata JSONB column
├── service/storage/
│   └── StorageService.kt           # ADD presigned upload, batch, metadata methods
│   └── S3StorageProvider.kt        # ADD generateUploadUrl() impl
│   └── SupabaseStorageProvider.kt  # ADD generateUploadUrl() impl
│   └── LocalStorageProvider.kt     # ADD generateUploadUrl() -> throws UnsupportedOperationException
├── controller/storage/
│   └── StorageController.kt        # ADD presigned upload, batch, metadata endpoints
├── enums/activity/
│   └── Activity.kt                 # ADD FILE_UPDATE variant
├── exceptions/
│   └── ArgumentExceptions.kt       # ADD PresignedUploadNotSupportedException (optional)
└── db/schema/01_tables/
    └── storage.sql                 # ADD metadata JSONB column
```

### Pattern 1: Presigned Upload Flow
**What:** Two-step client-initiated upload bypassing backend for the file transfer
**When to use:** Large files where proxied upload through backend is wasteful

```kotlin
// StorageProvider interface addition
fun generateUploadUrl(key: String, contentType: String, expiresIn: Duration): String

// S3 implementation using presignPutObject (same pattern as existing presignGetObject)
override fun generateUploadUrl(key: String, contentType: String, expiresIn: Duration): String {
    val request = PutObjectRequest {
        bucket = bucketName
        this.key = key
        this.contentType = contentType
    }
    val presigned = runBlocking {
        s3Client.presignPutObject(request, expiresIn.toKotlinDuration())
    }
    return presigned.url.toString()
}
```

**Confirmation flow in StorageService:**
1. Client calls `POST /presigned-upload` with workspace, domain -- backend generates key, returns presigned URL
2. Client uploads directly to provider using presigned URL
3. Client calls `POST /presigned-upload/confirm` with storage key + original filename
4. Backend: `storageProvider.exists(key)` to verify -> download file -> Tika validate -> persist metadata -> log activity
5. If Tika rejects: `storageProvider.delete(key)` then throw `ContentTypeNotAllowedException`

### Pattern 2: 207 Multi-Status for Batch Operations
**What:** Per-item success/failure reporting for batch requests
**When to use:** Batch upload and batch delete where partial success is acceptable

```kotlin
// Response structure
data class BatchItemResult(
    val id: UUID?,          // file ID (null for upload failures before persist)
    val filename: String?,  // original filename (for uploads)
    val status: Int,        // HTTP status code per item (201, 204, 400, 500, etc.)
    val error: String?      // error message if failed, null if success
)

data class BatchUploadResponse(
    val results: List<BatchItemResult>,
    val succeeded: Int,
    val failed: Int
)

// Controller returns 207
return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(response)
```

### Pattern 3: JSONB Metadata with Merge Semantics
**What:** Partial update of JSONB metadata preserving unmentioned keys
**When to use:** PATCH endpoint for file metadata

```kotlin
// Entity field (matches IntegrationDefinitionEntity pattern)
@Type(JsonBinaryType::class)
@Column(name = "metadata", columnDefinition = "jsonb")
var metadata: Map<String, String>? = null

// Merge logic in service
fun mergeMetadata(existing: Map<String, String>?, patch: Map<String, String?>): Map<String, String> {
    val merged = existing?.toMutableMap() ?: mutableMapOf()
    patch.forEach { (key, value) ->
        if (value == null) merged.remove(key) else merged[key] = value
    }
    return merged
}
```

### Pattern 4: Local Adapter Fallback Signal
**What:** API response indicating presigned uploads not supported, client should use proxied upload
**When to use:** When `generateUploadUrl()` throws `UnsupportedOperationException`

```kotlin
// In StorageService - same try/catch pattern as generateProviderSignedUrl
fun requestPresignedUpload(workspaceId: UUID, domain: StorageDomain): PresignedUploadResponse {
    val key = contentValidationService.generateStorageKey(workspaceId, domain, "application/octet-stream")
    return try {
        val url = storageProvider.generateUploadUrl(key, "application/octet-stream", uploadExpiry)
        PresignedUploadResponse(storageKey = key, uploadUrl = url, method = "PUT", supported = true)
    } catch (e: UnsupportedOperationException) {
        PresignedUploadResponse(storageKey = key, uploadUrl = null, method = null, supported = false)
    }
}
```

### Anti-Patterns to Avoid
- **Generating storage keys on client side:** Keys must be UUID-based and generated server-side. Client only receives key in the presigned URL response.
- **Wrapping batch operations in a single transaction:** Each item should succeed or fail independently. Do NOT use `@Transactional` on the batch method -- let individual items commit.
- **Using `Optional<T>` for metadata field:** Use Kotlin nullable `Map<String, String>?` for the entity field, not `Optional`.
- **Validating content type from presigned URL request alone:** Must re-validate via Tika after file is actually uploaded to provider. The `contentType` in the presigned request is a hint, not trusted.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Presigned S3 URLs | Custom HMAC signing of S3 URLs | `s3Client.presignPutObject()` | S3 SigV4 signing is complex, SDK handles it correctly |
| Presigned Supabase URLs | Custom token generation | `bucket.createSignedUploadUrl()` | Supabase handles token + policy enforcement |
| MIME type detection on confirmation | Extension-based guessing | `ContentValidationService.detectContentType()` (Tika) | Magic bytes are the only trustworthy signal |
| Multi-file multipart parsing | Custom multipart parser | Spring's `@RequestParam("files") List<MultipartFile>` | Spring handles RFC 2046 parsing, streaming, temp files |
| JSONB serialization | Custom JSON column handling | Hypersistence `JsonBinaryType` | Handles Hibernate type mapping, null handling, serialization |

## Common Pitfalls

### Pitfall 1: Content Type Mismatch on Presigned Upload
**What goes wrong:** S3 presigned PUT URLs can include content-type constraints. If the client uploads with a different Content-Type header than what was signed, S3 rejects with 403.
**Why it happens:** The `PutObjectRequest` used for signing includes `contentType`, and S3 enforces it.
**How to avoid:** Either (a) include content type in presigned URL response so client knows what header to send, or (b) don't constrain content type in the presigned URL and rely on post-upload Tika validation instead. Since the decision is to validate with Tika after upload, option (b) is safer -- generate presigned URL without `contentType` constraint.
**Warning signs:** 403 errors from S3 when client uploads with presigned URL.

### Pitfall 2: Presigned Upload Key Reuse
**What goes wrong:** If the same storage key is reused for a presigned upload that was never confirmed, old orphaned files accumulate in the provider.
**How to avoid:** Each presigned upload request generates a fresh UUID key. Consider a cleanup job in the future, but for v1 this is acceptable -- orphaned files are rare and low-cost.

### Pitfall 3: Batch Upload Memory Pressure
**What goes wrong:** 10 files * domain max size could exceed JVM heap during batch upload since all files are read into `ByteArray` for Tika detection.
**Why it happens:** Current `uploadFile` reads `file.bytes` (entire file into memory).
**How to avoid:** This is already the pattern for single uploads, and with max 10 files in a batch plus domain-level size limits (e.g., 2MB for AVATAR), worst case is ~20MB which is manageable. If larger domains are added later, consider streaming. For now, the existing pattern is fine.

### Pitfall 4: 207 Multi-Status Client Handling
**What goes wrong:** Clients treat 207 as a success and don't check individual item results.
**How to avoid:** Include `succeeded` and `failed` counts at the top level of the response. Document clearly in Swagger that 207 means "check each item result".

### Pitfall 5: JSONB Null vs Empty Map
**What goes wrong:** Hibernate/Jackson may serialize `null` and `emptyMap()` differently, causing inconsistent reads.
**How to avoid:** Store `null` when no metadata exists (not empty map). In `toModel()`, map `null` to `null` (not to `emptyMap()`). The JSONB column should be nullable.

### Pitfall 6: Presigned Upload Confirmation Race
**What goes wrong:** Client calls confirm before the file is fully propagated in S3 (eventual consistency).
**Why it happens:** S3 strong consistency for PUT is only for the same region/path. Cross-region or CDN caching can delay visibility.
**How to avoid:** `storageProvider.exists()` should be sufficient with S3's strong read-after-write consistency. If exists returns false, return an appropriate error telling client to retry.

## Code Examples

### S3 Presigned Upload URL Generation
```kotlin
// Source: AWS SDK for Kotlin docs + existing S3StorageProvider pattern
import aws.sdk.kotlin.services.s3.presigners.presignPutObject

override fun generateUploadUrl(key: String, contentType: String, expiresIn: Duration): String {
    try {
        val request = PutObjectRequest {
            bucket = bucketName
            this.key = key
            // Intentionally omit contentType to avoid 403 on mismatch
        }
        val presigned = runBlocking {
            s3Client.presignPutObject(request, expiresIn.toKotlinDuration())
        }
        return presigned.url.toString()
    } catch (e: Exception) {
        throw StorageProviderException("Upload URL generation failed for key: $key", e)
    }
}
```

### Supabase Presigned Upload URL Generation
```kotlin
// Source: supabase-kt docs
override fun generateUploadUrl(key: String, contentType: String, expiresIn: Duration): String {
    try {
        return runBlocking {
            bucket.createSignedUploadUrl(path = key, upsert = false)
        }
    } catch (e: Exception) {
        throw StorageProviderException("Upload URL generation failed for key: $key", e)
    }
}
```

### JSONB Metadata Entity Column
```kotlin
// Source: IntegrationDefinitionEntity pattern in codebase
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import org.hibernate.annotations.Type

// Add to FileMetadataEntity
@Type(JsonBinaryType::class)
@Column(name = "metadata", columnDefinition = "jsonb")
var metadata: Map<String, String>? = null
```

### Metadata Validation
```kotlin
// Validation constraints from CONTEXT.md decisions
private val METADATA_KEY_PATTERN = Regex("^[a-zA-Z0-9_-]{1,64}$")
private const val MAX_METADATA_PAIRS = 20
private const val MAX_VALUE_LENGTH = 1024

fun validateMetadata(metadata: Map<String, String?>) {
    require(metadata.size <= MAX_METADATA_PAIRS) {
        "Metadata cannot exceed $MAX_METADATA_PAIRS key-value pairs"
    }
    metadata.forEach { (key, value) ->
        require(METADATA_KEY_PATTERN.matches(key)) {
            "Metadata key '$key' must be alphanumeric with underscores/hyphens, max 64 chars"
        }
        value?.let {
            require(it.length <= MAX_VALUE_LENGTH) {
                "Metadata value for key '$key' exceeds maximum length of $MAX_VALUE_LENGTH chars"
            }
        }
    }
}
```

### Batch Upload Controller
```kotlin
// Spring's native multi-file support
@PostMapping("/workspace/{workspaceId}/batch-upload")
fun batchUpload(
    @PathVariable workspaceId: UUID,
    @RequestParam domain: StorageDomain,
    @RequestParam("files") files: List<MultipartFile>
): ResponseEntity<BatchUploadResponse> {
    require(files.size <= 10) { "Maximum 10 files per batch upload" }
    val response = storageService.batchUpload(workspaceId, domain, files)
    return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(response)
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Server-proxied upload for all files | Presigned URLs for large files, proxied for small/local | Standard S3 pattern | Reduces backend bandwidth and memory for large uploads |
| Single file operations only | Batch endpoints with 207 Multi-Status | REST API best practice | Reduces round trips for bulk operations |

**Note on Supabase `createSignedUploadUrl`:** This method returns a token string, not a full URL. The upload is then done via `uploadToSignedUrl(path, token, data)`. The implementation may need to construct the full upload URL or return the token + upload endpoint separately. Verify the exact return format during implementation.

## Open Questions

1. **Supabase `createSignedUploadUrl` return format**
   - What we know: Returns a token/URL string that's used with `uploadToSignedUrl()`
   - What's unclear: Whether the returned value is a full HTTP URL the client can PUT to directly, or a token that requires the Supabase client library
   - Recommendation: If Supabase signed upload requires their client library, the client would need to use the Supabase JS SDK for uploads. Test during implementation. If it's not a plain HTTP URL, may need to construct the full Supabase storage endpoint URL from the token.

2. **Presigned upload expiry duration**
   - What we know: S3 supports up to 7 days, Supabase's limit is unclear
   - What's unclear: Optimal default for the use case
   - Recommendation: Default to 15 minutes (sufficient for large file upload, short enough to limit abuse). Make configurable via `storage.presigned-upload.expiry-seconds` in application.yml.

3. **Batch upload content type per file**
   - What we know: Batch upload shares a single `domain` parameter
   - What's unclear: Whether different files in the batch might have different content types
   - Recommendation: Each file is individually validated via Tika. The `domain` parameter controls the validation rules (allowlist), not the individual content type. This is already how single upload works.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + mockito-kotlin 3.2.0 |
| Config file | `build.gradle.kts` (JUnit Platform) |
| Quick run command | `./gradlew test --tests "riven.core.service.storage.*"` |
| Full suite command | `./gradlew test` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| FILE-09 | generateUploadUrl on S3 returns presigned PUT URL | unit | `./gradlew test --tests "riven.core.service.storage.S3StorageProviderTest" -x` | Needs new tests |
| FILE-09 | generateUploadUrl on Supabase returns upload URL | unit | `./gradlew test --tests "riven.core.service.storage.SupabaseStorageProviderTest" -x` | Needs new tests |
| FILE-09 | generateUploadUrl on Local throws UnsupportedOperationException | unit | `./gradlew test --tests "riven.core.service.storage.LocalStorageProviderTest" -x` | Needs new tests |
| FILE-10 | confirmPresignedUpload verifies exists + validates + persists | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest" -x` | Needs new tests |
| FILE-10 | confirmPresignedUpload rejects invalid content type and deletes | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest" -x` | Needs new tests |
| FILE-11 | batchUpload processes multiple files with per-item results | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest" -x` | Needs new tests |
| FILE-11 | batchUpload rejects more than 10 files | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest" -x` | Needs new tests |
| FILE-12 | batchDelete processes multiple IDs with per-item results | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest" -x` | Needs new tests |
| FILE-12 | batchDelete rejects more than 50 IDs | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest" -x` | Needs new tests |
| DATA-05 | metadata attached at upload time persisted in JSONB | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest" -x` | Needs new tests |
| DATA-05 | metadata PATCH merges correctly (add, update, remove keys) | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest" -x` | Needs new tests |
| DATA-05 | metadata validation rejects invalid keys/values/count | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest" -x` | Needs new tests |

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "riven.core.service.storage.*"`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- None -- existing test infrastructure (`StorageServiceTest`, `S3StorageProviderTest`, `SupabaseStorageProviderTest`, `LocalStorageProviderTest`, `StorageFactory`) covers all phase requirements. New test cases will be added to existing test classes.

## Sources

### Primary (HIGH confidence)
- AWS SDK for Kotlin `presignPutObject` API: [AWS SDK Kotlin presignPutObject](https://docs.aws.amazon.com/sdk-for-kotlin/api/latest/s3/aws.sdk.kotlin.services.s3.presigners/presign-put-object.html)
- Supabase Kotlin `createSignedUploadUrl` API: [Supabase Kotlin Reference](https://supabase.com/docs/reference/kotlin/storage-from-createsigneduploadurl)
- Codebase inspection: `StorageProvider.kt`, `S3StorageProvider.kt`, `SupabaseStorageProvider.kt`, `StorageService.kt`, `StorageController.kt`, `FileMetadataEntity.kt`, `IntegrationDefinitionEntity.kt` (JSONB pattern)
- `build.gradle.kts` -- confirmed AWS SDK 1.3.112, supabase-kt 3.1.4 BOM, Hypersistence 3.9.2

### Secondary (MEDIUM confidence)
- AWS presigned URL documentation: [AWS Presigned URL Uploads](https://docs.aws.amazon.com/AmazonS3/latest/userguide/PresignedUrlUploadObject.html)
- AWS SDK Kotlin presign guide: [Presign Requests Guide](https://docs.aws.amazon.com/sdk-for-kotlin/latest/developer-guide/presign-requests.html)

### Tertiary (LOW confidence)
- Supabase `uploadToSignedUrl` exact behavior -- docs snippet was incomplete, needs verification during implementation

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- all libraries already in project, no new dependencies
- Architecture: HIGH -- all patterns follow established codebase conventions (interface extension, try/catch fallback, JSONB columns, activity logging)
- Pitfalls: HIGH -- based on direct codebase analysis and well-known S3/Supabase behaviors

**Research date:** 2026-03-06
**Valid until:** 2026-04-06 (stable -- all dependencies already pinned in project)
