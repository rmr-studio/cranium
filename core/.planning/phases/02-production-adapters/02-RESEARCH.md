# Phase 2: Production Adapters - Research

**Researched:** 2026-03-06
**Domain:** Storage provider adapters (Supabase Storage, AWS S3 / S3-compatible)
**Confidence:** HIGH

## Summary

Phase 2 implements two new `StorageProvider` adapters -- `SupabaseStorageProvider` and `S3StorageProvider` -- and refactors `StorageService.generateSignedUrl()` to try the provider first before falling back to `SignedUrlService`. The existing interface, exception hierarchy, and configuration patterns are all in place from Phase 1. The Supabase client is already wired as a Spring bean via `SupabaseConfiguration`. AWS SDK for Kotlin needs to be added as a dependency.

Both adapters follow the same pattern as `LocalStorageProvider`: `@Service` with `@ConditionalOnProperty`, constructor injection of KLogger + SDK client + config, and method implementations that delegate to the respective SDK. The key integration challenge is bridging suspend functions (both supabase-kt and AWS SDK for Kotlin are coroutine-based) to the blocking `StorageProvider` interface via `runBlocking {}`.

**Primary recommendation:** Implement both adapters following the established `LocalStorageProvider` pattern exactly. Use `runBlocking {}` per call for coroutine bridging. Unit test with mocked SDK clients following the existing `LocalStorageProviderTest` structure.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Single bucket per provider (configured via `storage.supabase.bucket` / `storage.s3.bucket`)
- Storage keys used directly as object paths inside the bucket
- Supabase bucket must be private; auto-create on `healthCheck()` if missing
- Use `runBlocking {}` per call to bridge suspend functions
- Use Supabase native `createSignedUrl()` for signed URLs
- S3 optional `storage.s3.endpoint-url` for S3-compatible services; if unset, defaults to AWS S3
- One S3 adapter handles all S3-compatible services via endpoint URL override
- Explicit credential config: `storage.s3.access-key-id` and `storage.s3.secret-access-key`
- S3 auto-create bucket on `healthCheck()` if missing
- `storage.s3.region` required for AWS, often ignored by S3-compatible services
- Provider-first signed URL strategy: `StorageService` tries `storageProvider.generateSignedUrl()` first; if `UnsupportedOperationException`, falls back to `SignedUrlService`
- Supabase signed URLs use `createSignedUrl()` from supabase-kt
- S3 signed URLs use `S3Client.presignGetObject()` presigned URLs
- Direct downloads for Supabase/S3 -- signed URLs point directly to provider, not through backend
- `/download/{token}` endpoint remains local-adapter-only
- Each adapter catches SDK exceptions and wraps in `StorageNotFoundException` / `StorageProviderException`
- Map 404 / NoSuchKey to `StorageNotFoundException`, all others to `StorageProviderException`
- Original provider exception preserved as `cause`
- No retries -- throw immediately
- Provider error details never leak into exception messages

### Claude's Discretion
- Exact supabase-kt API usage for upload/download/delete/list/signed URL calls
- AWS SDK for Kotlin client builder configuration details
- S3Presigner setup and usage
- Config property validation and defaults
- Test strategy for production adapters (mock vs testcontainers)
- Whether to add `storage.supabase.url` / `storage.supabase.key` to StorageConfigurationProperties or reuse existing ApplicationConfigurationProperties

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| ADPT-01 | Supabase Storage adapter implementing StorageProvider interface | supabase-kt Storage API researched: upload, downloadAuthenticated, delete, list, createSignedUrl, createBucket. SupabaseClient bean already exists. |
| ADPT-02 | S3-compatible adapter implementing StorageProvider (supports AWS S3, MinIO, R2, Spaces via custom endpoint) | AWS SDK for Kotlin S3Client researched: putObject, getObject, deleteObject, listObjectsV2, presignGetObject, createBucket, headBucket. Custom endpoint via `endpointUrl`, `forcePathStyle` for MinIO. |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| supabase-kt (storage-kt) | 3.1.4 (BOM) | Supabase Storage operations | Already in build.gradle.kts, project standard |
| aws.sdk.kotlin:s3 | 1.6.30 | S3 operations (put, get, delete, list, presign) | AWS SDK for Kotlin -- project decision to use Kotlin SDK over Java SDK |
| io.ktor:ktor-client-cio | 3.0.0 | HTTP engine for supabase-kt | Already in build.gradle.kts |
| kotlinx-coroutines-reactor | (managed) | `runBlocking {}` for coroutine bridging | Already in build.gradle.kts |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| AWS SDK for Kotlin | AWS SDK for Java v2 | Java SDK is more mature but requires more boilerplate; Kotlin SDK provides native suspend functions and DSL builders. Project already decided on Kotlin SDK. |
| Explicit credentials in config | AWS default credential chain | Explicit config is simpler and more predictable for multi-environment deployment. Default chain can be enabled later. |

**Installation (new dependency only):**
```kotlin
// build.gradle.kts
implementation("aws.sdk.kotlin:s3:1.6.30")
```

## Architecture Patterns

### Recommended Project Structure
```
src/main/kotlin/riven/core/
├── configuration/storage/
│   ├── StorageConfigurationProperties.kt  # ADD: supabase + s3 nested sections
│   ├── SupabaseConfiguration.kt           # EXISTS: already creates SupabaseClient bean
│   └── S3Configuration.kt                 # NEW: creates S3Client bean, @ConditionalOnProperty
├── service/storage/
│   ├── LocalStorageProvider.kt            # EXISTS: reference implementation
│   ├── SupabaseStorageProvider.kt         # NEW: supabase-kt adapter
│   ├── S3StorageProvider.kt              # NEW: AWS SDK adapter
│   ├── StorageService.kt                 # MODIFY: signed URL fallback logic
│   ├── SignedUrlService.kt               # EXISTS: HMAC fallback (no changes)
│   └── ContentValidationService.kt       # EXISTS: no changes
└── models/storage/
    └── StorageProvider.kt                 # EXISTS: interface (no changes)
```

### Pattern 1: Provider Adapter Pattern (follow LocalStorageProvider exactly)
**What:** Each adapter is a `@Service` with `@ConditionalOnProperty`, implements `StorageProvider`, uses constructor injection.
**When to use:** Every new storage provider.
**Example:**
```kotlin
// Source: existing LocalStorageProvider.kt pattern
@Service
@ConditionalOnProperty(name = ["storage.provider"], havingValue = "supabase")
class SupabaseStorageProvider(
    private val logger: KLogger,
    private val supabaseClient: SupabaseClient,
    private val storageConfig: StorageConfigurationProperties
) : StorageProvider {
    // ...
}
```

### Pattern 2: Coroutine Bridging with runBlocking
**What:** Wrap each suspend call in `runBlocking {}` since StorageProvider is blocking.
**When to use:** Every method in both adapters (supabase-kt and AWS SDK for Kotlin are both suspend-based).
**Example:**
```kotlin
override fun upload(key: String, content: InputStream, contentType: String, contentLength: Long): StorageResult {
    return runBlocking {
        bucket.upload(key, content.readAllBytes()) {
            upsert = true
        }
    }
    // ... return StorageResult
}
```

### Pattern 3: Exception Normalization
**What:** Catch provider SDK exceptions and wrap in domain exceptions.
**When to use:** Every method in both adapters.
**Example:**
```kotlin
try {
    runBlocking { /* SDK call */ }
} catch (e: Exception) {
    when {
        isNotFound(e) -> throw StorageNotFoundException("File not found: $key")
        else -> throw StorageProviderException("Upload failed for key: $key", e)
    }
}
```

### Pattern 4: Signed URL Fallback in StorageService
**What:** Try `storageProvider.generateSignedUrl()` first; if `UnsupportedOperationException`, fall back to `SignedUrlService`.
**When to use:** `StorageService.generateSignedUrl()` and upload response URL generation.
**Example:**
```kotlin
private fun generateProviderSignedUrl(storageKey: String, expiry: Duration): String {
    return try {
        storageProvider.generateSignedUrl(storageKey, expiry)
    } catch (e: UnsupportedOperationException) {
        signedUrlService.generateDownloadUrl(storageKey, expiry)
    }
}
```

### Anti-Patterns to Avoid
- **Sharing S3Client across threads without proper lifecycle:** S3Client must be a Spring bean (singleton), properly closed on shutdown via `@PreDestroy` or `DisposableBean`.
- **Catching generic Exception without re-checking type:** Always check for specific not-found indicators before wrapping.
- **Leaking provider details in exception messages:** Message should be generic; provider exception goes in `cause` only.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| S3 presigned URLs | Custom HMAC URL signing for S3 | `S3Client.presignGetObject()` | AWS handles signature computation, STS tokens, region-specific signing |
| Supabase signed URLs | Custom token generation | `bucket.createSignedUrl()` | Supabase handles auth context, RLS integration |
| S3 credential management | Custom credential parsing/caching | `StaticCredentialsProvider` from AWS SDK | Handles credential refresh, STS, etc. |
| Content-type on S3 download | Manual content-type inference | S3 `GetObjectResponse.contentType` | S3 stores and returns the content-type set during upload |

## Common Pitfalls

### Pitfall 1: supabase-kt `downloadAuthenticated` returns ByteArray, not InputStream
**What goes wrong:** Attempting to stream large files fails because supabase-kt loads entire file into memory.
**Why it happens:** supabase-kt is designed for mobile/multiplatform where ByteArray is standard.
**How to avoid:** Wrap returned `ByteArray` in `ByteArrayInputStream` for the `DownloadResult`. Accept the memory cost for v1 -- files have size limits enforced during upload.
**Warning signs:** OOM errors on large file downloads.

### Pitfall 2: S3 `getObject` response body must be consumed inside the lambda
**What goes wrong:** Trying to return `response.body` outside the `getObject` lambda results in a closed stream.
**Why it happens:** AWS SDK for Kotlin uses a scoped lambda pattern: `s3Client.getObject(request) { response -> /* must consume here */ }`.
**How to avoid:** Read all bytes inside the lambda, return them as part of `DownloadResult` with `ByteArrayInputStream`.
**Warning signs:** `IOException: stream closed` or empty responses.

### Pitfall 3: S3-compatible endpoints require `forcePathStyle = true`
**What goes wrong:** MinIO, LocalStack, and some S3-compatible services fail with DNS resolution errors.
**Why it happens:** Default AWS SDK uses virtual-hosted-style URLs (`bucket.endpoint.com`), which don't work with custom endpoints.
**How to avoid:** Set `forcePathStyle = true` in S3Client config when a custom `endpointUrl` is provided.
**Warning signs:** DNS resolution failures, "bucket not found" errors against known-good buckets.

### Pitfall 4: supabase-kt `delete` method is named `delete` and accepts vararg paths
**What goes wrong:** Calling the wrong method name or passing incorrect argument format.
**Why it happens:** API naming differs from other SDKs (some use `remove`, supabase-kt uses `delete`).
**How to avoid:** Use `bucket.delete(key)` -- the method accepts vararg `String` paths.

### Pitfall 5: S3Client lifecycle management
**What goes wrong:** Resource leaks if S3Client is not properly closed.
**Why it happens:** AWS SDK for Kotlin S3Client implements `Closeable`.
**How to avoid:** Create S3Client as a Spring `@Bean` and implement `DisposableBean` or `@PreDestroy` on the configuration class to call `s3Client.close()`.

### Pitfall 6: SupabaseClient already exists as a bean -- do not create a second one
**What goes wrong:** Creating a new SupabaseClient in the adapter leads to duplicate beans or configuration mismatch.
**Why it happens:** `SupabaseConfiguration` already creates the `SupabaseClient` bean with Storage plugin installed.
**How to avoid:** Inject the existing `SupabaseClient` bean into `SupabaseStorageProvider`. Access storage via `supabaseClient.storage.from(bucket)`.

## Code Examples

### Supabase Storage Adapter -- Upload
```kotlin
// Source: supabase.com/docs/reference/kotlin/storage-from-upload
override fun upload(key: String, content: InputStream, contentType: String, contentLength: Long): StorageResult {
    try {
        val bytes = content.readAllBytes()
        runBlocking {
            bucket.upload(key, bytes) {
                upsert = true // overwrite if exists
                contentType(contentType)
            }
        }
        logger.debug { "Uploaded file to Supabase Storage: $key ($contentLength bytes)" }
        return StorageResult(storageKey = key, contentType = contentType, contentLength = contentLength)
    } catch (e: Exception) {
        throw StorageProviderException("Supabase upload failed for key: $key", e)
    }
}
```

### Supabase Storage Adapter -- Download
```kotlin
// Source: supabase.com/docs/reference/kotlin/storage-from-download
override fun download(key: String): DownloadResult {
    try {
        val bytes = runBlocking { bucket.downloadAuthenticated(key) }
        return DownloadResult(
            content = ByteArrayInputStream(bytes),
            contentType = "application/octet-stream", // supabase-kt doesn't return content-type on download
            contentLength = bytes.size.toLong(),
            originalFilename = null
        )
    } catch (e: Exception) {
        if (isSupabaseNotFound(e)) throw StorageNotFoundException("File not found: $key")
        throw StorageProviderException("Supabase download failed for key: $key", e)
    }
}
```

### Supabase Storage Adapter -- Signed URL
```kotlin
// Source: supabase.com/docs/reference/kotlin/storage-from-createsignedurl
override fun generateSignedUrl(key: String, expiresIn: Duration): String {
    try {
        return runBlocking {
            bucket.createSignedUrl(path = key, expiresIn = expiresIn)
        }
    } catch (e: Exception) {
        throw StorageProviderException("Supabase signed URL generation failed for key: $key", e)
    }
}
```

### Supabase Storage Adapter -- Health Check with Auto-Create
```kotlin
override fun healthCheck(): Boolean {
    return try {
        runBlocking {
            try {
                supabaseClient.storage.retrieveBucketById(bucketName)
            } catch (e: Exception) {
                // Bucket doesn't exist -- create it
                supabaseClient.storage.createBucket(id = bucketName) {
                    public = false
                }
            }
        }
        true
    } catch (e: Exception) {
        logger.warn { "Supabase storage health check failed: ${e.message}" }
        false
    }
}
```

### S3 Client Configuration
```kotlin
// Source: docs.aws.amazon.com/sdk-for-kotlin/latest/developer-guide/examples-s3-objects.html
@Configuration
@ConditionalOnProperty(name = ["storage.provider"], havingValue = "s3")
class S3Configuration(
    private val storageConfig: StorageConfigurationProperties
) : DisposableBean {

    @Bean
    fun s3Client(): S3Client {
        return S3Client {
            region = storageConfig.s3.region
            endpointUrl = storageConfig.s3.endpointUrl?.let { Url.parse(it) }
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = storageConfig.s3.accessKeyId
                secretAccessKey = storageConfig.s3.secretAccessKey
            }
            forcePathStyle = storageConfig.s3.endpointUrl != null
        }
    }

    override fun destroy() {
        // S3Client.close() called by Spring on shutdown
        s3Client().close()
    }
}
```

### S3 Adapter -- Upload
```kotlin
// Source: docs.aws.amazon.com/sdk-for-kotlin/latest/developer-guide/examples-s3-objects.html
override fun upload(key: String, content: InputStream, contentType: String, contentLength: Long): StorageResult {
    try {
        val bytes = content.readAllBytes()
        runBlocking {
            s3Client.putObject {
                bucket = bucketName
                this.key = key
                this.contentType = contentType
                this.contentLength = contentLength
                body = ByteStream.fromBytes(bytes)
            }
        }
        logger.debug { "Uploaded file to S3: $key ($contentLength bytes)" }
        return StorageResult(storageKey = key, contentType = contentType, contentLength = contentLength)
    } catch (e: Exception) {
        throw StorageProviderException("S3 upload failed for key: $key", e)
    }
}
```

### S3 Adapter -- Download
```kotlin
override fun download(key: String): DownloadResult {
    try {
        val result = runBlocking {
            s3Client.getObject(GetObjectRequest {
                bucket = bucketName
                this.key = key
            }) { response ->
                val bytes = response.body?.toByteArray() ?: ByteArray(0)
                Triple(bytes, response.contentType ?: "application/octet-stream", response.contentLength ?: 0L)
            }
        }
        return DownloadResult(
            content = ByteArrayInputStream(result.first),
            contentType = result.second,
            contentLength = result.third,
            originalFilename = null
        )
    } catch (e: Exception) {
        if (isS3NotFound(e)) throw StorageNotFoundException("File not found: $key")
        throw StorageProviderException("S3 download failed for key: $key", e)
    }
}
```

### S3 Adapter -- Presigned URL
```kotlin
// Source: docs.aws.amazon.com/sdk-for-kotlin/latest/developer-guide/presign-requests.html
override fun generateSignedUrl(key: String, expiresIn: Duration): String {
    try {
        val request = GetObjectRequest {
            bucket = bucketName
            this.key = key
        }
        val presigned = runBlocking {
            s3Client.presignGetObject(request, expiresIn.toKotlinDuration())
        }
        return presigned.url.toString()
    } catch (e: Exception) {
        throw StorageProviderException("S3 presigned URL generation failed for key: $key", e)
    }
}
```

### S3 Adapter -- Health Check with Auto-Create
```kotlin
override fun healthCheck(): Boolean {
    return try {
        runBlocking {
            try {
                s3Client.headBucket { bucket = bucketName }
            } catch (e: Exception) {
                s3Client.createBucket { bucket = bucketName }
            }
        }
        true
    } catch (e: Exception) {
        logger.warn { "S3 storage health check failed: ${e.message}" }
        false
    }
}
```

### StorageConfigurationProperties -- Extended
```kotlin
@ConfigurationProperties(prefix = "storage")
@Validated
data class StorageConfigurationProperties(
    val provider: String = "local",
    val local: Local = Local(),
    val signedUrl: SignedUrl = SignedUrl(),
    val supabase: Supabase = Supabase(),
    val s3: S3 = S3()
) {
    data class Local(val basePath: String = "./storage")
    data class SignedUrl(
        val secret: String = "dev-secret-change-in-production",
        val defaultExpirySeconds: Long = 3600,
        val maxExpirySeconds: Long = 86400
    )
    data class Supabase(val bucket: String = "riven-storage")
    data class S3(
        val bucket: String = "riven-storage",
        val region: String = "us-east-1",
        val accessKeyId: String = "",
        val secretAccessKey: String = "",
        val endpointUrl: String? = null
    )
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| AWS SDK for Java v2 (blocking) | AWS SDK for Kotlin (suspend) | 2023+ | Need `runBlocking {}` bridge but get native Kotlin DSL |
| Supabase REST API direct calls | supabase-kt (Kotlin Multiplatform SDK) | 2024+ | Type-safe API, handles auth headers automatically |
| Virtual-hosted bucket URLs | `forcePathStyle` for S3-compatible | Always relevant | Must set `forcePathStyle = true` for MinIO/R2/custom endpoints |

## Open Questions

1. **supabase-kt content-type on download**
   - What we know: `downloadAuthenticated` returns `ByteArray` only, no metadata about content-type.
   - What's unclear: Whether there's a way to get content-type from the download response.
   - Recommendation: Look up content-type from file metadata entity (already stored in DB) or fall back to `application/octet-stream`. Since signed URLs bypass the download endpoint entirely for Supabase, this only matters for the `download()` method which is primarily used by the HMAC token flow (local adapter only).

2. **Supabase config property location**
   - What we know: `ApplicationConfigurationProperties` already has `supabaseUrl` and `supabaseKey`. `SupabaseConfiguration` already uses these.
   - What's unclear: Whether to duplicate supabase connection info in `StorageConfigurationProperties` or continue injecting `ApplicationConfigurationProperties` where needed.
   - Recommendation: Keep supabase URL/key in `ApplicationConfigurationProperties` (existing pattern). Only add `storage.supabase.bucket` to `StorageConfigurationProperties`. The adapter injects both config classes.

3. **S3 `getObject` response body consumption pattern**
   - What we know: AWS SDK for Kotlin uses a scoped lambda pattern where body must be consumed inside the lambda.
   - What's unclear: Exact API for reading bytes from `response.body` -- is it `toByteArray()`, `readAll()`, or something else.
   - Recommendation: Use `response.body?.toByteArray()` inside the lambda. LOW confidence on exact method name -- verify at implementation time.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + mockito-kotlin |
| Config file | `src/test/resources/application-test.yml` (H2 compat mode) |
| Quick run command | `./gradlew test --tests "riven.core.service.storage.*"` |
| Full suite command | `./gradlew test` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ADPT-01 | Supabase upload/download/delete/exists/signedUrl/healthCheck | unit (mocked SupabaseClient) | `./gradlew test --tests "riven.core.service.storage.SupabaseStorageProviderTest" -x` | Wave 0 |
| ADPT-02 | S3 upload/download/delete/exists/signedUrl/healthCheck | unit (mocked S3Client) | `./gradlew test --tests "riven.core.service.storage.S3StorageProviderTest" -x` | Wave 0 |
| ADPT-01/02 | StorageService signed URL fallback | unit (mocked StorageProvider) | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest" -x` | Exists (needs new test methods) |

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "riven.core.service.storage.*"`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `src/test/kotlin/riven/core/service/storage/SupabaseStorageProviderTest.kt` -- covers ADPT-01
- [ ] `src/test/kotlin/riven/core/service/storage/S3StorageProviderTest.kt` -- covers ADPT-02
- [ ] Test methods in existing `StorageServiceTest.kt` for signed URL fallback behavior

## Sources

### Primary (HIGH confidence)
- [Supabase Kotlin Storage API - Upload](https://supabase.com/docs/reference/kotlin/storage-from-upload) -- upload, upsert, progress tracking API
- [Supabase Kotlin Storage API - Create Signed URL](https://supabase.com/docs/reference/kotlin/storage-from-createsignedurl) -- `createSignedUrl(path, expiresIn)` API
- [Supabase Kotlin Storage API - Download](https://supabase.com/docs/reference/kotlin/storage-from-download) -- `downloadAuthenticated` returns ByteArray
- [Supabase Kotlin Storage API - Delete](https://supabase.com/docs/reference/kotlin/storage-from-remove) -- `delete(vararg paths)` API
- [Supabase Kotlin Storage API - Create Bucket](https://supabase.com/docs/reference/kotlin/storage-createbucket) -- `createBucket(id) { public = false }` API
- [AWS SDK for Kotlin - S3 Objects](https://docs.aws.amazon.com/sdk-for-kotlin/latest/developer-guide/examples-s3-objects.html) -- putObject, getObject, deleteObject, listObjectsV2 examples
- [AWS SDK for Kotlin - Presign Requests](https://docs.aws.amazon.com/sdk-for-kotlin/latest/developer-guide/presign-requests.html) -- `presignGetObject(request, duration)` API
- [AWS SDK for Kotlin - Endpoint Configuration](https://docs.aws.amazon.com/sdk-for-kotlin/latest/developer-guide/config-endpoint.html) -- custom endpoint URL, forcePathStyle

### Secondary (MEDIUM confidence)
- [Maven Central - aws.sdk.kotlin:s3](https://central.sonatype.com/artifact/aws.sdk.kotlin/s3) -- latest version 1.6.30
- [supabase-kt GitHub](https://github.com/supabase-community/supabase-kt) -- SDK overview, version 3.1.4

### Tertiary (LOW confidence)
- S3 `getObject` response body byte reading API -- exact method name (`toByteArray()` vs alternatives) needs implementation-time verification

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- both SDKs are already in use or well-documented, versions verified on Maven Central
- Architecture: HIGH -- follows established `LocalStorageProvider` pattern exactly, no architectural decisions needed
- Pitfalls: HIGH -- coroutine bridging, S3 scoped lambda, forcePathStyle are well-documented issues
- Code examples: MEDIUM -- supabase-kt upload/delete APIs verified, S3 APIs verified from official docs; exact download byte-reading API is LOW confidence

**Research date:** 2026-03-06
**Valid until:** 2026-04-06 (stable libraries, 30-day window)
