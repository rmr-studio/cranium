# Phase 1: Storage Foundation - Research

**Researched:** 2026-03-06
**Domain:** File storage abstraction with local filesystem backend, content validation, signed URLs
**Confidence:** HIGH

## Summary

Phase 1 builds a workspace-scoped file storage system with a provider abstraction interface, local filesystem adapter, content-type validation via Apache Tika magic bytes, HMAC-signed download URLs, and full CRUD through REST endpoints. The codebase already has established patterns for layered architecture, soft-delete entities, activity logging, workspace security, and exception handling -- this phase follows all of them and introduces one new pattern (`@ConditionalOnProperty` for provider selection).

The technical risks are low. Apache Tika `tika-core` is mature and lightweight for MIME detection. HMAC-SHA256 signing uses JDK standard library (`javax.crypto.Mac`). SVG sanitization has a dedicated Java library. The local filesystem adapter is straightforward. The main complexity is in designing the `StorageProvider` interface to be general enough for future S3/Supabase adapters while being concrete enough for Phase 1.

**Primary recommendation:** Follow the existing codebase patterns exactly -- data class entity extending `AuditableSoftDeletableEntity`, service with `@PreAuthorize`, controller delegating to service, exceptions mapped in `ExceptionHandler`. The only new concepts are the provider interface with `@ConditionalOnProperty` wiring and the HMAC-signed URL endpoint.

<user_constraints>

## User Constraints (from CONTEXT.md)

### Locked Decisions
- System-defined domains via a `StorageDomain` enum -- not user-defined paths
- v1 domain: `AVATAR` only (additional domains like BLOCK, DOCUMENT, ATTACHMENT added in future phases as needed)
- Storage key format: `{workspaceId}/{domain}/{uuid}.{ext}` -- UUID only in path, no original filename
- Original filename preserved only in the metadata database table
- Each domain carries its own validation rules (allowed content types, max file size) as enum properties
- Apache Tika `tika-core` only (MIME detection from magic bytes, ~2MB) -- not full Tika with parsers
- Validate before storage: read magic bytes from multipart stream before writing to provider. Nothing stored on failure.
- Content type allowlist hardcoded in `StorageDomain` enum properties (not env-var configurable)
- AVATAR domain: images only (JPEG, PNG, WebP, GIF), small size limit (e.g. 2MB)
- SVGs are allowed but must be sanitized (strip script tags, event handlers, embedded JS) before storage
- Local adapter uses self-signed endpoint: backend generates HMAC-signed time-limited tokens, dedicated `/api/v1/storage/download/{token}` endpoint validates and streams the file
- Default expiry is configurable, but callers can specify custom duration per request (within bounds)
- Upload response includes a signed download URL (saves a round trip for upload-then-display flow)
- File listing returns metadata only -- no signed URLs per file. Clients request URLs individually as needed.
- Blocking (non-suspend) interface -- S3 adapter will internally use `runBlocking` to bridge coroutines. Consistent with synchronous Spring MVC codebase.
- Interface + `@ConditionalOnProperty` wiring: `StorageProvider` is an interface, each adapter is a `@Service` with `@ConditionalOnProperty("storage.provider", havingValue="local")`. Only one adapter bean is active.
- Existing `StorageService` (currently injects `SupabaseClient` directly) will be replaced/refactored to inject `StorageProvider` interface
- New `StorageException` hierarchy: `StorageException` base with subtypes mapped to HTTP status codes in `ExceptionHandler`
- Interface includes a `healthCheck()` method for Spring Actuator integration

### Claude's Discretion
- Exact HMAC signing implementation for local signed URLs
- SVG sanitization library choice
- Default signed URL expiry duration (15 min or 1 hour)
- Max bounds for custom expiry durations
- File metadata entity column design beyond the requirements spec (DATA-01)
- StorageProvider interface method signatures and return types
- Configuration properties structure (`storage.*` namespace)

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope.

</user_constraints>

<phase_requirements>

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| PROV-01 | StorageProvider interface defines upload, download, delete, list, generateSignedUrl, generateUploadUrl operations | Interface design pattern with `@ConditionalOnProperty` wiring; blocking signatures; return types documented below |
| PROV-02 | Provider selection via environment variable (`STORAGE_PROVIDER`) with `@ConditionalOnProperty` bean wiring | Spring `@ConditionalOnProperty` pattern documented; property namespace `storage.provider` |
| PROV-03 | Provider-specific configuration via environment variables (bucket, region, credentials, base path) | `@ConfigurationProperties(prefix = "storage")` with nested classes per provider |
| PROV-04 | Domain exception hierarchy for storage errors | `StorageException` base class with typed subtypes; `ExceptionHandler` mappings documented |
| ADPT-03 | Local filesystem adapter implementing StorageProvider with configurable base directory | `java.nio.file` API usage; directory structure `{basePath}/{workspaceId}/{domain}/{uuid}.{ext}` |
| FILE-01 | Proxied multipart upload -- client sends file to backend, backend streams to provider | Spring `MultipartFile` handling; validate-then-store flow |
| FILE-02 | Content-type validation via magic bytes (Apache Tika), reject files not matching allowlist | Apache Tika `tika-core` 3.2.0 MIME detection from `InputStream` |
| FILE-03 | File size limit enforcement (configurable per environment) | Spring `spring.servlet.multipart.max-file-size` + per-domain validation in `StorageDomain` enum |
| FILE-04 | UUID-based storage key generation (never use user-provided filenames as storage paths) | `UUID.randomUUID()` for key generation; extension derived from detected MIME type |
| FILE-05 | Signed (time-limited) download URL generation for file access | HMAC-SHA256 token generation using JDK `javax.crypto.Mac`; dedicated download endpoint |
| FILE-06 | File deletion from provider with metadata cleanup | Soft-delete metadata entity + physical file deletion from provider |
| FILE-07 | File listing by workspace and domain path (query metadata table) | JPQL query on `FileMetadataEntity` filtered by `workspaceId` and `domain` |
| FILE-08 | File type allowlist -- images (JPEG, PNG, WebP, GIF, SVG) and documents (PDF, DOCX, XLSX) | `StorageDomain` enum properties with allowed MIME types; AVATAR domain: images only for Phase 1 |
| DATA-01 | File metadata entity in PostgreSQL | `FileMetadataEntity` with columns documented below |
| DATA-02 | Workspace + domain scoped file organization | Storage key format `{workspaceId}/{domain}/{uuid}.{ext}` |
| DATA-03 | Workspace-scoped access control via `@PreAuthorize` on all storage service methods | Existing `@workspaceSecurity.hasWorkspace` pattern |
| DATA-04 | Activity logging for all storage mutations (upload, delete) | New `FILE_UPLOAD`, `FILE_DELETE` activity types; new `FILE` entity type in `ApplicationEntityType` |
| API-01 | REST endpoints under `/api/v1/storage/` for upload, download, delete, list, signed URL | `StorageController` with standard endpoint structure |

</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Apache Tika Core | 3.2.0+ | MIME type detection from magic bytes | De facto standard for content detection in JVM; `tika-core` is ~2MB, no parsers needed |
| svg-sanitizer | 0.3.1+ | SVG XSS sanitization | Only dedicated SVG sanitizer on Maven Central for Java; strips script tags, event handlers, external resources |
| JDK `javax.crypto.Mac` | (JDK 21) | HMAC-SHA256 for signed URL tokens | Standard library -- no external dependency needed |
| JDK `java.nio.file` | (JDK 21) | Local filesystem operations | Standard library for file I/O |

### Supporting (already in project)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Spring Boot Web | 3.5.3 | Multipart upload handling, REST endpoints | `MultipartFile` for uploads, `StreamingResponseBody` for downloads |
| Spring Data JPA | 3.5.3 | File metadata persistence | `FileMetadataRepository` with JPQL queries |
| Hypersistence Utils | 3.9.2 | JSONB column support | If metadata entity needs a JSONB details column |
| Spring Security | 6.x | `@PreAuthorize` workspace access control | All storage service methods |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| svg-sanitizer | OWASP Java HTML Sanitizer | OWASP is more mature but not SVG-specific; would need custom policy. svg-sanitizer is purpose-built. |
| svg-sanitizer | Manual XML parsing + stripping | Error-prone, easy to miss attack vectors. Use a library. |
| JDK HMAC | JWT for signed tokens | JWT adds a dependency and is heavier than needed for simple time-limited tokens |

**Installation (Gradle):**
```kotlin
// Add to build.gradle.kts dependencies
implementation("org.apache.tika:tika-core:3.2.0")
implementation("io.github.borewit:svg-sanitizer:0.3.1")
```

## Architecture Patterns

### Recommended Package Structure
```
src/main/kotlin/riven/core/
├── configuration/
│   └── storage/
│       ├── SupabaseConfiguration.kt        # Existing -- add @ConditionalOnProperty
│       └── StorageConfigurationProperties.kt # NEW -- storage.* namespace
├── controller/
│   └── storage/
│       └── StorageController.kt             # NEW -- REST endpoints
├── entity/
│   └── storage/
│       └── FileMetadataEntity.kt            # NEW -- JPA entity
├── enums/
│   └── storage/
│       └── StorageDomain.kt                 # NEW -- domain enum with validation rules
├── exceptions/
│   └── StorageExceptions.kt                 # NEW -- or add to ArgumentExceptions.kt
├── models/
│   └── storage/
│       ├── FileMetadata.kt                  # NEW -- domain model
│       ├── StorageProvider.kt               # NEW -- provider interface
│       └── SignedUrlToken.kt                # NEW -- HMAC token model
├── repository/
│   └── storage/
│       └── FileMetadataRepository.kt        # NEW
└── service/
    └── storage/
        ├── StorageService.kt                # REFACTOR -- inject StorageProvider
        ├── ContentValidationService.kt      # NEW -- Tika + SVG sanitization
        ├── SignedUrlService.kt              # NEW -- HMAC token generation/validation
        └── LocalStorageProvider.kt          # NEW -- @ConditionalOnProperty("storage.provider", havingValue="local")
```

### Pattern 1: Provider Interface with Conditional Wiring
**What:** `StorageProvider` is a plain Kotlin interface. Each adapter is a `@Service` with `@ConditionalOnProperty`. Only one bean is active at runtime.
**When to use:** Always -- this is the core abstraction.
**Example:**
```kotlin
// StorageProvider interface (in models/storage/)
interface StorageProvider {
    fun upload(key: String, content: InputStream, contentType: String, contentLength: Long): StorageResult
    fun download(key: String): DownloadResult
    fun delete(key: String)
    fun exists(key: String): Boolean
    fun generateSignedUrl(key: String, expiresIn: Duration): String
    fun healthCheck(): Boolean
}

// Local adapter (in service/storage/)
@Service
@ConditionalOnProperty(name = ["storage.provider"], havingValue = "local")
class LocalStorageProvider(
    private val logger: KLogger,
    private val storageConfig: StorageConfigurationProperties
) : StorageProvider {
    // Implementation using java.nio.file
}
```

### Pattern 2: HMAC-Signed Download Token
**What:** Generate a time-limited HMAC-SHA256 token encoding the storage key and expiration. A dedicated public endpoint validates and streams the file.
**When to use:** For the local adapter's signed URL implementation.
**Example:**
```kotlin
// Token format: Base64URL(storageKey:expiresAtEpochSeconds:hmacSignature)
// The download endpoint is unauthenticated (the token IS the auth)

fun generateToken(storageKey: String, expiresIn: Duration): String {
    val expiresAt = Instant.now().plus(expiresIn).epochSecond
    val payload = "$storageKey:$expiresAt"
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
    val signature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.toByteArray()))
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString("$payload:$signature".toByteArray())
}

fun validateToken(token: String): TokenPayload? {
    val decoded = String(Base64.getUrlDecoder().decode(token))
    val parts = decoded.split(":")
    // Verify: 3 parts, not expired, HMAC matches
}
```

### Pattern 3: Validate-Before-Store Flow
**What:** Read magic bytes from the multipart stream, validate content type against domain allowlist, sanitize SVGs, THEN store. Nothing persists on validation failure.
**When to use:** Every upload.
**Example:**
```kotlin
// In StorageService.uploadFile():
val detectedType = contentValidationService.detectContentType(file.inputStream, file.originalFilename)
contentValidationService.validateContentType(domain, detectedType)
contentValidationService.validateFileSize(domain, file.size)
val content = if (detectedType == "image/svg+xml") {
    contentValidationService.sanitizeSvg(file.inputStream)
} else {
    file.inputStream
}
val storageKey = generateStorageKey(workspaceId, domain, detectedType)
val result = storageProvider.upload(storageKey, content, detectedType, file.size)
// Then persist metadata entity
```

### Pattern 4: StorageDomain Enum with Validation Rules
**What:** Each domain carries its own allowed content types and max file size as enum properties.
**When to use:** For content validation per domain.
**Example:**
```kotlin
enum class StorageDomain(
    val allowedContentTypes: Set<String>,
    val maxFileSize: Long  // bytes
) {
    AVATAR(
        allowedContentTypes = setOf(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/svg+xml"
        ),
        maxFileSize = 2 * 1024 * 1024  // 2MB
    );

    fun isContentTypeAllowed(contentType: String): Boolean =
        contentType in allowedContentTypes

    fun isFileSizeAllowed(size: Long): Boolean =
        size <= maxFileSize
}
```

### Anti-Patterns to Avoid
- **Trusting file extensions:** Never use the extension from the user-provided filename for content type. Always use Tika magic byte detection.
- **Storing files before validation:** Always validate content type and size BEFORE writing to the provider. Rollback complexity is avoided.
- **Coupling provider logic into StorageService:** StorageService orchestrates (validate, store, persist metadata, log activity). Provider-specific logic lives in the adapter.
- **Returning signed URLs in list responses:** Per CONTEXT.md decision, listing returns metadata only. Clients request signed URLs individually.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| MIME type detection | Custom magic byte parser | Apache Tika `tika-core` | Thousands of MIME types, complex magic byte rules, constantly updated |
| SVG sanitization | Regex-based script stripping | `svg-sanitizer` library | SVG XSS vectors are numerous and subtle (event handlers, data URIs, CSS expressions, entity expansion) |
| HMAC computation | Custom crypto | `javax.crypto.Mac` (JDK) | Standard, audited, constant-time comparison available via `MessageDigest.isEqual` |
| Multipart parsing | Manual stream parsing | Spring `MultipartFile` | Handles encoding, boundaries, temp files, size limits |
| File extension from MIME | Manual mapping | Tika `MimeTypes.forName(type).extension` | Comprehensive mapping, handles edge cases |

**Key insight:** Content validation and SVG sanitization are security-critical. Using audited libraries prevents the inevitable bypass that comes from hand-rolled solutions.

## Common Pitfalls

### Pitfall 1: InputStream Exhaustion
**What goes wrong:** Reading the `MultipartFile` input stream for Tika detection consumes it, leaving nothing to write to storage.
**Why it happens:** `InputStream` can only be read once.
**How to avoid:** Either: (a) use `MultipartFile.getBytes()` and wrap in `ByteArrayInputStream` for both detection and storage, or (b) use Tika's `detect()` which reads only the first N bytes and doesn't close/reset the stream (if the stream supports mark/reset). Spring's `MultipartFile.getInputStream()` is repeatable -- call it twice.
**Warning signs:** Empty files being stored, or "Stream closed" exceptions.

### Pitfall 2: Race Condition on Directory Creation
**What goes wrong:** Two concurrent uploads to the same workspace create the directory simultaneously, one fails.
**Why it happens:** `Files.createDirectories()` is not atomic.
**How to avoid:** `Files.createDirectories()` is actually safe -- it's a no-op if the directory already exists (unlike `createDirectory()` which throws). Just use `createDirectories()` and ignore `FileAlreadyExistsException`.
**Warning signs:** Sporadic `FileAlreadyExistsException` in logs.

### Pitfall 3: HMAC Token Timing Attack
**What goes wrong:** Using `String.equals()` to compare HMAC signatures allows timing-based attacks.
**Why it happens:** `String.equals()` short-circuits on first mismatch.
**How to avoid:** Use `MessageDigest.isEqual(expected, actual)` for constant-time comparison.
**Warning signs:** No visible symptoms -- this is a silent security vulnerability.

### Pitfall 4: Incomplete Cleanup on Delete
**What goes wrong:** Metadata is soft-deleted but file remains on disk, or file is deleted but metadata persists.
**Why it happens:** Non-transactional operation across two systems (DB + filesystem).
**How to avoid:** Soft-delete metadata first (transactional), then delete physical file. If physical delete fails, log an error but don't roll back the soft-delete. Orphaned files are less harmful than orphaned metadata pointing to missing files.
**Warning signs:** Disk usage growing despite deletions; 404s when accessing "existing" files.

### Pitfall 5: Path Traversal in Storage Keys
**What goes wrong:** A crafted storage key like `../../etc/passwd` escapes the base directory.
**Why it happens:** Trusting user input in file paths.
**How to avoid:** Storage keys are UUID-based (generated server-side), never from user input. Additionally, resolve the full path and verify it starts with the configured base directory: `require(resolvedPath.startsWith(basePath))`.
**Warning signs:** Files appearing outside the storage base directory.

### Pitfall 6: Supabase Bean Failure When Provider is Local
**What goes wrong:** Application fails to start because `SupabaseConfiguration` creates a `SupabaseClient` bean that requires Supabase credentials, even when using the local provider.
**Why it happens:** `SupabaseConfiguration` is unconditionally loaded.
**How to avoid:** Add `@ConditionalOnProperty(name = ["storage.provider"], havingValue = "supabase")` to `SupabaseConfiguration` (or make it conditional on the Supabase properties being present). This change enables running locally without Supabase credentials.
**Warning signs:** Application startup failure with missing Supabase config when `storage.provider=local`.

## Code Examples

### Apache Tika MIME Detection
```kotlin
// Source: Apache Tika 3.x API docs
import org.apache.tika.Tika
import org.apache.tika.metadata.Metadata
import org.apache.tika.metadata.TikaCoreProperties

private val tika = Tika()

fun detectContentType(inputStream: InputStream, filename: String?): String {
    val metadata = Metadata()
    filename?.let { metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, it) }
    return tika.detect(inputStream, metadata)
}
```

### SVG Sanitization
```kotlin
// Source: svg-sanitizer README
import io.github.borewit.sanitize.SVGSanitizer

fun sanitizeSvg(input: InputStream): InputStream {
    return SVGSanitizer.sanitize(input)
}
```

### HMAC-SHA256 Token Generation
```kotlin
// Source: JDK javax.crypto.Mac API
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

fun generateSignedToken(storageKey: String, expiresIn: Duration, secret: String): String {
    val expiresAt = Instant.now().plus(expiresIn).epochSecond
    val payload = "$storageKey:$expiresAt"
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    val signature = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(mac.doFinal(payload.toByteArray(Charsets.UTF_8)))
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString("$payload:$signature".toByteArray(Charsets.UTF_8))
}

fun validateSignedToken(token: String, secret: String): Pair<String, Long>? {
    val decoded = String(Base64.getUrlDecoder().decode(token))
    val lastColon = decoded.lastIndexOf(':')
    if (lastColon == -1) return null
    val payloadPart = decoded.substring(0, lastColon)
    val signaturePart = decoded.substring(lastColon + 1)

    // Recompute HMAC
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    val expectedSignature = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(mac.doFinal(payloadPart.toByteArray(Charsets.UTF_8)))

    // Constant-time comparison
    if (!MessageDigest.isEqual(
            expectedSignature.toByteArray(Charsets.UTF_8),
            signaturePart.toByteArray(Charsets.UTF_8)
        )) return null

    val parts = payloadPart.split(":")
    if (parts.size < 2) return null
    val storageKey = parts.dropLast(1).joinToString(":")
    val expiresAt = parts.last().toLongOrNull() ?: return null
    if (Instant.now().epochSecond > expiresAt) return null

    return storageKey to expiresAt
}
```

### FileMetadataEntity Design
```kotlin
// Following existing entity patterns (AuditableSoftDeletableEntity, toModel())
@Entity
@Table(
    name = "file_metadata",
    indexes = [
        Index(columnList = "workspace_id"),
        Index(columnList = "workspace_id, domain"),
        Index(columnList = "storage_key", unique = true),
    ]
)
data class FileMetadataEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
    val workspaceId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "domain", nullable = false)
    val domain: StorageDomain,

    @Column(name = "storage_key", nullable = false, unique = true)
    val storageKey: String,

    @Column(name = "original_filename", nullable = false)
    val originalFilename: String,

    @Column(name = "content_type", nullable = false)
    val contentType: String,

    @Column(name = "file_size", nullable = false)
    val fileSize: Long,

    @Column(name = "uploaded_by", nullable = false, columnDefinition = "uuid")
    val uploadedBy: UUID,
) : AuditableSoftDeletableEntity() {
    fun toModel(): FileMetadata { /* ... */ }
}
```

### Security Configuration for Download Endpoint
```kotlin
// The signed download endpoint MUST be unauthenticated (the token IS the auth)
// Add to SecurityConfig.securityFilterChain():
.requestMatchers("/api/v1/storage/download/**").permitAll()
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Tika 2.x (Java 8+) | Tika 3.x (Java 11+) | April 2025 (2.x EOL) | Must use 3.x; 2.x is end-of-life |
| Direct Supabase injection | Provider abstraction | This phase | Decouples storage from Supabase |
| `@ConditionalOnBean` | `@ConditionalOnProperty` | N/A (both valid) | Property-based is more explicit for provider selection |

**Deprecated/outdated:**
- Apache Tika 2.x: EOL as of April 2025. Use 3.x.
- The existing `StorageService` injecting `SupabaseClient` directly: Will be refactored to inject `StorageProvider`.

## Open Questions

1. **Signed URL secret key source**
   - What we know: Need an HMAC secret for signing download tokens
   - What's unclear: Should this reuse the JWT secret or be a separate key?
   - Recommendation: Use a separate `storage.signed-url.secret` property. Sharing the JWT secret creates unnecessary coupling and potential security risk if one is compromised.

2. **Download endpoint content disposition**
   - What we know: Signed URL endpoint streams the file back
   - What's unclear: Should it use `Content-Disposition: inline` (display in browser) or `attachment` (force download)?
   - Recommendation: Default to `inline` for images (AVATAR domain), allow `?download=true` query param to force `attachment`.

3. **svg-sanitizer version availability**
   - What we know: GitHub shows v0.4.0 released, but Maven Central search results show up to 0.3.1
   - What's unclear: Whether 0.4.0 is published to Maven Central yet
   - Recommendation: Use 0.3.1 (confirmed on Maven Central). Upgrade to 0.4.0 when confirmed available.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + mockito-kotlin |
| Config file | `src/test/resources/application-test.yml` (H2 in Postgres-compat mode) |
| Quick run command | `./gradlew test --tests "riven.core.service.storage.*"` |
| Full suite command | `./gradlew test` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PROV-01 | StorageProvider interface methods exist and are callable | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest" -x` | Wave 0 |
| PROV-02 | Only local provider bean created when `storage.provider=local` | unit | `./gradlew test --tests "riven.core.service.storage.LocalStorageProviderTest" -x` | Wave 0 |
| PROV-04 | Storage exceptions map to correct HTTP status codes | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest" -x` | Wave 0 |
| ADPT-03 | Local adapter reads/writes files to configured base directory | unit | `./gradlew test --tests "riven.core.service.storage.LocalStorageProviderTest" -x` | Wave 0 |
| FILE-01 | Multipart upload stores file and returns metadata | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest" -x` | Wave 0 |
| FILE-02 | Magic byte detection rejects disallowed content types | unit | `./gradlew test --tests "riven.core.service.storage.ContentValidationServiceTest" -x` | Wave 0 |
| FILE-03 | Oversized files rejected per domain limit | unit | `./gradlew test --tests "riven.core.service.storage.ContentValidationServiceTest" -x` | Wave 0 |
| FILE-04 | Storage key is UUID-based, no user filename | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest" -x` | Wave 0 |
| FILE-05 | Signed URL generation and validation (HMAC) | unit | `./gradlew test --tests "riven.core.service.storage.SignedUrlServiceTest" -x` | Wave 0 |
| FILE-06 | Delete soft-deletes metadata and removes physical file | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest" -x` | Wave 0 |
| FILE-07 | List files filtered by workspace and domain | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest" -x` | Wave 0 |
| FILE-08 | Allowlist enforcement per StorageDomain | unit | `./gradlew test --tests "riven.core.service.storage.ContentValidationServiceTest" -x` | Wave 0 |
| DATA-01 | FileMetadataEntity persists and maps to model | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest" -x` | Wave 0 |
| DATA-03 | Workspace access control enforced on service methods | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest" -x` | Wave 0 |
| DATA-04 | Activity logged for upload and delete | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest" -x` | Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "riven.core.service.storage.*"`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `src/test/kotlin/riven/core/service/storage/StorageServiceTest.kt` -- covers FILE-01, FILE-04, FILE-06, FILE-07, DATA-01, DATA-03, DATA-04
- [ ] `src/test/kotlin/riven/core/service/storage/ContentValidationServiceTest.kt` -- covers FILE-02, FILE-03, FILE-08
- [ ] `src/test/kotlin/riven/core/service/storage/SignedUrlServiceTest.kt` -- covers FILE-05
- [ ] `src/test/kotlin/riven/core/service/storage/LocalStorageProviderTest.kt` -- covers ADPT-03, PROV-02
- [ ] `src/test/kotlin/riven/core/service/util/factory/storage/StorageFactory.kt` -- test data factory for file metadata entities

## Sources

### Primary (HIGH confidence)
- [Apache Tika official site](https://tika.apache.org/) - version info, MIME detection API, 3.x migration
- [Apache Tika 3.2.0 API docs](https://tika.apache.org/3.2.0/api/org/apache/tika/mime/MimeTypes.html) - MimeTypes API
- [Borewit svg-sanitizer GitHub](https://github.com/Borewit/svg-sanitizer) - API, usage, versions
- [Baeldung HMAC in Java](https://www.baeldung.com/java-hmac) - javax.crypto.Mac usage patterns
- Existing codebase patterns (read directly from source files)

### Secondary (MEDIUM confidence)
- [Maven Central tika-core](https://mvnrepository.com/artifact/org.apache.tika/tika-core) - latest version 3.2.3 available
- [Maven Central svg-sanitizer](https://mvnrepository.com/artifact/io.github.borewit/svg-sanitizer) - versions up to 0.3.1 confirmed
- [Spring Boot HMAC signed URL patterns](https://medium.com/@AlexanderObregon/signed-url-generation-in-spring-boot-for-private-file-access-65ae77515e99) - implementation approach

### Tertiary (LOW confidence)
- svg-sanitizer v0.4.0 -- shown on GitHub releases but not confirmed on Maven Central

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Apache Tika is mature, well-documented, verified on Maven Central. svg-sanitizer is the only purpose-built option.
- Architecture: HIGH - Follows established codebase patterns exactly. `@ConditionalOnProperty` is standard Spring.
- Pitfalls: HIGH - Common JVM file handling issues, well-documented in community.
- HMAC signing: HIGH - Standard JDK crypto APIs, well-documented patterns.

**Research date:** 2026-03-06
**Valid until:** 2026-04-06 (stable domain, 30-day validity)
