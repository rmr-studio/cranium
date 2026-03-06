# Architecture Patterns

**Domain:** Provider-agnostic storage abstraction (Kotlin/Spring Boot backend + TypeScript/Next.js frontend)
**Researched:** 2026-03-05

## Recommended Architecture

The storage abstraction follows the **Strategy Pattern** on both backend and frontend: a provider-agnostic interface defines operations, concrete adapters implement them per provider, and a factory selects the active adapter at startup based on environment configuration. This mirrors the established `AuthProvider` pattern already in the frontend codebase.

### System Overview

```
Frontend (Next.js)                          Backend (Spring Boot)
========================                    ========================

StorageProvider interface                   StorageProvider interface
  |                                           |
  +-- SupabaseStorageAdapter                  +-- SupabaseStorageAdapter
  +-- (future adapters)                       +-- S3StorageAdapter
                                              +-- LocalFilesystemAdapter
StorageFactory                              StorageConfiguration
  (env var -> adapter)                        (@ConditionalOnProperty -> bean)

                                            StorageService (business logic)
                                              |
                                            StorageController (/api/v1/storage)
```

**Two upload flows exist:**

1. **Proxied upload** (default) -- Client sends multipart to backend controller, backend stores via active provider. Simple, secure, works for all providers including local filesystem.
2. **Presigned URL upload** (large files) -- Client requests a presigned URL from backend, backend generates it via provider, client uploads directly to storage. Reduces backend bandwidth. Only available for providers that support presigned URLs (S3, Supabase -- not local filesystem).

### Component Boundaries

| Component | Responsibility | Communicates With |
|-----------|---------------|-------------------|
| `StorageProvider` (interface) | Defines contract: upload, download, delete, list, generateSignedUrl, generatePresignedUploadUrl | Implemented by adapters |
| `SupabaseStorageAdapter` | Translates StorageProvider calls to supabase-kt Storage SDK | Supabase Storage API |
| `S3StorageAdapter` | Translates StorageProvider calls to AWS SDK v2 S3Client/S3Presigner | S3-compatible API (AWS, MinIO, R2) |
| `LocalFilesystemAdapter` | Stores files on local disk, serves via controller endpoint | Local filesystem |
| `StorageConfiguration` | Creates the correct adapter bean based on `STORAGE_PROVIDER` env var | Spring `@Configuration` |
| `StorageProperties` | Typed config for each provider (bucket, region, credentials, paths) | Spring `@ConfigurationProperties` |
| `StorageService` | Business logic: workspace scoping, path construction, metadata, validation | StorageProvider, StorageMetadataRepository |
| `StorageController` | REST endpoints for upload/download/delete/list/presign | StorageService |
| `StorageMetadataEntity` | Persists file metadata (path, provider, content type, size, uploader) | PostgreSQL |
| Frontend `StorageProvider` | Client-side interface for presigned uploads and URL resolution | Backend API via fetch |
| Frontend `StorageFactory` | Selects frontend adapter from env var | Environment |

### Data Flow

**Flow 1: Proxied Upload (default, all providers)**

```
Client                    Backend                         Storage
  |                         |                               |
  |-- POST /api/v1/storage  |                               |
  |   multipart/form-data   |                               |
  |   + workspaceId         |                               |
  |   + domain (avatars,    |                               |
  |     blocks, documents)  |                               |
  |                         |-- validate auth + workspace   |
  |                         |-- construct path:             |
  |                         |   {workspaceId}/{domain}/{id} |
  |                         |-- storageProvider.upload()  --|-->  Store file
  |                         |-- save StorageMetadata to DB  |
  |                         |                               |
  |<-- 201 { fileId, path } |                               |
```

**Flow 2: Presigned URL Upload (large files, S3/Supabase only)**

```
Client                    Backend                         Storage
  |                         |                               |
  |-- POST /api/v1/storage  |                               |
  |   /presigned-upload     |                               |
  |   { filename, type,     |                               |
  |     workspaceId, domain}|                               |
  |                         |-- validate auth + workspace   |
  |                         |-- construct path              |
  |                         |-- provider.presignUpload() --|-->  Generate URL
  |                         |-- save pending metadata       |
  |<-- { uploadUrl, fileId }|                               |
  |                         |                               |
  |-- PUT uploadUrl --------|-------------------------------|-->  Direct upload
  |                         |                               |
  |-- POST /api/v1/storage  |                               |
  |   /confirm/{fileId}     |                               |
  |                         |-- verify file exists in store |
  |                         |-- mark metadata as confirmed  |
  |<-- 200 { fileId, path } |                               |
```

**Flow 3: File Access (signed download URL)**

```
Client                    Backend                         Storage
  |                         |                               |
  |-- GET /api/v1/storage   |                               |
  |   /{fileId}/url         |                               |
  |                         |-- validate auth + workspace   |
  |                         |-- provider.signedUrl()     --|-->  Generate URL
  |<-- { url, expiresAt }   |                               |
  |                         |                               |
  |-- GET url --------------|-------------------------------|-->  Direct download
```

**Flow 3b: Local Filesystem Access (no signed URLs)**

```
Client                    Backend
  |                         |
  |-- GET /api/v1/storage   |
  |   /{fileId}/download    |
  |                         |-- validate auth + workspace
  |                         |-- read file from disk
  |<-- binary stream        |
```

## Backend Architecture Detail

### Package Structure (following existing conventions)

```
riven.core.
  configuration.storage/
    StorageConfiguration.kt          -- @ConditionalOnProperty bean selection
    StorageProperties.kt             -- @ConfigurationProperties for all providers

  service.storage/
    StorageService.kt                -- Business logic, workspace scoping
    StorageProvider.kt               -- Interface (strategy contract)
    adapter/
      SupabaseStorageAdapter.kt      -- supabase-kt implementation
      S3StorageAdapter.kt            -- AWS SDK v2 implementation
      LocalFilesystemAdapter.kt     -- java.nio.file implementation

  controller.storage/
    StorageController.kt             -- REST API

  entity.storage/
    StorageMetadataEntity.kt         -- JPA entity for file metadata

  repository.storage/
    StorageMetadataRepository.kt     -- JPA repository

  models.storage/
    StorageMetadata.kt               -- Domain model
    StorageUploadResult.kt           -- Upload response model
    StorageSignedUrl.kt              -- Signed URL response model

  enums.storage/
    StorageProviderType.kt           -- SUPABASE, S3, LOCAL
    StorageDomain.kt                 -- AVATAR, BLOCK, DOCUMENT, etc.
```

### StorageProvider Interface (core contract)

```kotlin
interface StorageProvider {

    // ------ Upload ------

    /** Upload file content to the given path. Returns the stored path. */
    fun upload(path: String, content: ByteArray, contentType: String): String

    /** Upload from InputStream (for large files via proxied upload). */
    fun upload(path: String, inputStream: InputStream, contentType: String, contentLength: Long): String

    // ------ Download ------

    /** Download file content as ByteArray. */
    fun download(path: String): ByteArray

    /** Generate a time-limited signed URL for direct download. */
    fun generateSignedUrl(path: String, expiresIn: Duration): String

    // ------ Presigned Upload ------

    /**
     * Generate a presigned URL for direct client upload.
     * Returns null if the provider does not support presigned uploads (e.g., local filesystem).
     */
    fun generatePresignedUploadUrl(path: String, contentType: String, expiresIn: Duration): String?

    // ------ Delete ------

    fun delete(path: String)
    fun deleteMany(paths: List<String>)

    // ------ List ------

    /** List files under a given prefix/path. */
    fun list(prefix: String): List<StorageFileInfo>

    // ------ Metadata ------

    /** Check if a file exists at the given path. */
    fun exists(path: String): Boolean

    /** Get the provider type identifier. */
    fun providerType(): StorageProviderType
}
```

### Bean Selection Pattern

Use Spring's `@ConditionalOnProperty` to wire the correct adapter as a singleton bean. This is the idiomatic Spring approach -- no factory method needed at runtime.

```kotlin
@Configuration
class StorageConfiguration {

    @Bean
    @ConditionalOnProperty(name = ["riven.storage.provider"], havingValue = "supabase")
    fun supabaseStorageAdapter(
        supabaseClient: SupabaseClient,
        properties: StorageProperties
    ): StorageProvider = SupabaseStorageAdapter(supabaseClient, properties)

    @Bean
    @ConditionalOnProperty(name = ["riven.storage.provider"], havingValue = "s3")
    fun s3StorageAdapter(properties: StorageProperties): StorageProvider =
        S3StorageAdapter(properties)

    @Bean
    @ConditionalOnProperty(name = ["riven.storage.provider"], havingValue = "local")
    fun localStorageAdapter(properties: StorageProperties): StorageProvider =
        LocalFilesystemAdapter(properties)
}
```

### Configuration Properties

```kotlin
@ConfigurationProperties(prefix = "riven.storage")
data class StorageProperties(
    val provider: StorageProviderType,           // SUPABASE, S3, LOCAL
    val bucket: String = "riven-storage",        // Default bucket name
    val signedUrlExpiry: Duration = Duration.ofHours(1),
    val maxFileSize: Long = 52_428_800,          // 50MB default
    val allowedContentTypes: List<String> = listOf(
        "image/jpeg", "image/png", "image/webp", "image/gif", "image/svg+xml",
        "application/pdf", "application/vnd.openxmlformats-officedocument.*"
    ),
    // S3-specific
    val s3: S3Properties? = null,
    // Local-specific
    val local: LocalProperties? = null
)

data class S3Properties(
    val region: String,
    val accessKeyId: String,
    val secretAccessKey: String,
    val endpoint: String? = null,   // For MinIO, R2, etc.
    val pathStyleAccess: Boolean = false  // Required for MinIO
)

data class LocalProperties(
    val basePath: String = "./storage"  // Filesystem root for stored files
)
```

### Path Construction Strategy

The `StorageService` (not adapters) constructs all paths. Adapters receive fully-qualified paths and are path-agnostic. This keeps path logic centralized and testable.

```
Path format: {workspaceId}/{domain}/{filename-with-uuid}
Example:     550e8400-e29b/avatars/a3f1b2c4-profile.jpg
Example:     550e8400-e29b/blocks/7d8e9f01-hero-image.png
Example:     550e8400-e29b/documents/b2c3d4e5-report.pdf
```

UUID prefix on filenames prevents collisions. The workspace ID as the top-level directory enables future per-workspace operations (quota calculation, bulk delete on workspace removal).

### Metadata Entity

```kotlin
@Entity
@Table(name = "storage_metadata")
data class StorageMetadataEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "workspace_id", nullable = false)
    val workspaceId: UUID,

    @Column(name = "path", nullable = false, unique = true)
    val path: String,                    // Full path in storage

    @Column(name = "original_filename")
    val originalFilename: String,        // User's original filename

    @Column(name = "content_type")
    val contentType: String,

    @Column(name = "size_bytes")
    val sizeBytes: Long,

    @Column(name = "domain")
    @Enumerated(EnumType.STRING)
    val domain: StorageDomain,           // AVATAR, BLOCK, DOCUMENT

    @Column(name = "provider")
    @Enumerated(EnumType.STRING)
    val provider: StorageProviderType,   // Which provider stored this file

    @Column(name = "uploaded_by", nullable = false)
    val uploadedBy: UUID,                // User ID

    @Column(name = "confirmed")
    val confirmed: Boolean = true,       // false for pending presigned uploads

    @Type(JsonBinaryType::class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    val metadata: Map<String, Any>? = null  // Extensible metadata
) : AuditableSoftDeletableEntity()
```

## Frontend Architecture Detail

### Mirror the AuthProvider Pattern

The frontend storage abstraction is simpler than the backend because the client primarily interacts with the backend API -- it does not talk to storage providers directly (except for presigned uploads where it PUTs directly to the storage URL).

```
apps/client/lib/storage/
  storage-provider.interface.ts     -- StorageProvider interface
  storage.types.ts                  -- Domain types
  factory.ts                        -- createStorageProvider()
  index.ts                          -- Re-exports
  adapters/
    api/
      api-storage-adapter.ts        -- Default: talks to backend /api/v1/storage
```

### Frontend StorageProvider Interface

```typescript
export interface StorageProvider {
    /** Upload a file via the backend proxy. */
    upload(file: File, options: UploadOptions): Promise<UploadResult>;

    /** Request a presigned URL for direct upload (large files). */
    requestPresignedUpload(options: PresignedUploadOptions): Promise<PresignedUploadResult>;

    /** Get a signed download/preview URL for a file. */
    getSignedUrl(fileId: string): Promise<SignedUrlResult>;

    /** Delete a file. */
    delete(fileId: string): Promise<void>;

    /** List files in a workspace domain. */
    list(options: ListOptions): Promise<FileListResult>;
}
```

The default (and likely only) frontend adapter is `ApiStorageAdapter` which calls the backend REST API. The frontend abstraction exists not to swap storage providers (that happens on the backend) but to:

1. Keep a consistent pattern with AuthProvider for developer familiarity.
2. Allow future alternative flows (e.g., a mock adapter for testing/storybook).
3. Encapsulate the two-step presigned upload flow (request URL, upload, confirm).

## Patterns to Follow

### Pattern 1: Conditional Bean Selection

**What:** Use `@ConditionalOnProperty` to wire the correct `StorageProvider` implementation at startup.
**When:** Provider is determined by environment variable, only one provider active at a time.
**Why over factory:** Idiomatic Spring. The DI container handles lifecycle, the adapter is a singleton, and services just inject `StorageProvider` without knowing which implementation they get. Testable via `@TestConfiguration` overrides.

### Pattern 2: Service Owns Path Logic, Adapter Owns Storage Logic

**What:** `StorageService` constructs workspace-scoped paths, validates content types, enforces size limits, manages metadata. Adapters only handle raw storage operations (put bytes, get bytes, generate URL).
**When:** Always. Adapters should be thin translators between the interface and the SDK.
**Why:** Keeps adapters simple and interchangeable. Path construction, validation, and metadata are consistent regardless of provider.

### Pattern 3: Metadata Entity as Source of Truth

**What:** Every stored file gets a `StorageMetadataEntity` row in PostgreSQL. This is the system of record for what files exist, who uploaded them, and where they live.
**When:** Every upload (proxied or presigned).
**Why:** Enables workspace-scoped queries ("list all files in workspace X"), activity logging, orphan cleanup, future quota enforcement. The storage provider itself may not have efficient listing or metadata querying.

### Pattern 4: Presigned Upload with Confirmation

**What:** For presigned uploads, create metadata with `confirmed = false`, return presigned URL to client, client uploads directly, then client calls a confirm endpoint which verifies the file exists in storage and marks metadata as confirmed.
**When:** Presigned upload flow only.
**Why:** Prevents orphaned metadata rows for uploads that never complete. A scheduled job can clean up unconfirmed uploads older than N hours.

## Anti-Patterns to Avoid

### Anti-Pattern 1: Provider-Specific Logic in StorageService

**What:** Checking `if (provider is S3StorageAdapter)` in service code.
**Why bad:** Defeats the abstraction. Every new provider requires modifying the service.
**Instead:** If behavior differs per provider, define it in the interface (e.g., `generatePresignedUploadUrl` returns `null` for providers that don't support it, and the service checks for null).

### Anti-Pattern 2: Storing Provider URLs in the Database

**What:** Saving the full S3 URL or Supabase URL in the metadata entity.
**Why bad:** URLs change when you switch providers. Signed URLs expire.
**Instead:** Store the logical path (`{workspaceId}/{domain}/{filename}`). Generate URLs on demand via the active provider.

### Anti-Pattern 3: Letting Adapters Handle Auth/Workspace Scoping

**What:** Passing workspace ID into adapters to have them scope operations.
**Why bad:** Storage providers don't understand your multi-tenancy model.
**Instead:** Service constructs the full path including workspace ID. Adapter just uses the path.

### Anti-Pattern 4: Frontend Talking Directly to Storage Providers

**What:** Frontend using Supabase SDK or AWS SDK to upload/download.
**Why bad:** Leaks provider choice to the client. Client needs provider credentials. Breaks when provider changes.
**Instead:** Frontend always talks to the backend API. The only exception is the presigned URL upload where the client PUTs directly to a pre-authorized URL (provider-agnostic -- it's just an HTTP PUT to a URL).

## Scalability Considerations

| Concern | Small scale (< 1K files) | Medium scale (10K-100K files) | Large scale (1M+ files) |
|---------|--------------------------|-------------------------------|-------------------------|
| Upload throughput | Proxied uploads through backend are fine | Add presigned URL flow for large files to reduce backend bandwidth | Presigned URLs mandatory for most uploads |
| Metadata queries | Simple JPA queries | Add indexes on workspace_id + domain | Consider partitioning metadata table by workspace |
| File listing | Query metadata table | Paginated queries with cursor | Denormalized counts, async listing |
| Storage costs | Any provider works | S3-compatible (MinIO self-hosted) for cost control | Tiered storage, lifecycle policies |
| Cleanup | Manual or scheduled job for orphans | Scheduled job for unconfirmed presigned uploads | Event-driven cleanup, dead letter queue |

## Suggested Build Order (dependency chain)

The components have clear dependencies that dictate build order:

```
Phase 1: Foundation (no external dependencies between items)
  1. StorageProvider interface
  2. StorageProperties (@ConfigurationProperties)
  3. StorageMetadataEntity + Repository
  4. StorageDomain, StorageProviderType enums

Phase 2: First Adapter (proves the interface works)
  5. LocalFilesystemAdapter (simplest, no external service needed for dev)
  6. StorageConfiguration (conditional bean wiring)

Phase 3: Service Layer (depends on Phase 1 + 2)
  7. StorageService (path construction, validation, metadata management)
  8. StorageController (REST endpoints for proxied upload/download/delete/list)

Phase 4: Cloud Adapters (parallel, each independent)
  9a. SupabaseStorageAdapter (uses existing supabase-kt dependency)
  9b. S3StorageAdapter (new AWS SDK v2 dependency)

Phase 5: Presigned URL Flow (depends on Phase 3 + 4)
  10. Add presigned URL methods to adapters
  11. Presigned upload/confirm endpoints in controller
  12. Cleanup job for unconfirmed uploads

Phase 6: Frontend Integration
  13. StorageProvider interface + types (TypeScript)
  14. ApiStorageAdapter (calls backend API)
  15. StorageFactory
  16. Integration into existing UI components (avatar upload, etc.)
```

**Build order rationale:**
- Local adapter first because it requires zero external services and lets you test the full flow locally.
- Supabase adapter before S3 because the dependency already exists in the project.
- Presigned URLs are an optimization -- the system works fully without them via proxied uploads.
- Frontend last because it depends entirely on the backend API being stable.

## Sources

- Existing codebase: `apps/client/lib/auth/` (AuthProvider pattern), `riven.core.configuration.storage/` (existing Supabase config), `riven.core.service.storage/` (existing empty StorageService)
- [Supabase Kotlin Storage - createSignedUrl](https://supabase.com/docs/reference/kotlin/storage-from-createsignedurl) -- supabase-kt signed URL API
- [Supabase Kotlin Storage - createSignedUploadUrl](https://supabase.com/docs/reference/kotlin/storage-from-createsigneduploadurl) -- supabase-kt presigned upload API
- [AWS SDK v2 S3Presigner](https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/s3/presigner/S3Presigner.html) -- presigned URL generation
- [Working with Amazon S3 presigned URLs in Spring Boot](https://dev.to/aws-builders/working-with-amazon-s3-presigned-urls-in-spring-boot-383n) -- presigned URL patterns
- [Offloading File Transfers with Amazon S3 Presigned URLs](https://reflectoring.io/offloading-file-transfers-with-amazon-s3-presigned-urls-in-spring-boot/) -- proxy vs presigned architecture comparison
- [tweedegolf/storage-abstraction](https://github.com/tweedegolf/storage-abstraction) -- TypeScript storage abstraction reference implementation
- [Spring Boot with Kotlin, AWS S3](https://blog.codersee.com/spring-boot-with-kotlin-aws-s3-and-s3template/) -- S3 integration patterns
