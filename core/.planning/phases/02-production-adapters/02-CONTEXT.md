# Phase 2: Production Adapters - Context

**Gathered:** 2026-03-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Implement Supabase Storage and S3-compatible adapter classes so the storage system works identically across providers. Switching providers requires only changing environment variables. No changes to StorageService, StorageController, or metadata layer -- only new StorageProvider implementations and configuration.

</domain>

<decisions>
## Implementation Decisions

### Supabase adapter behavior
- Single bucket (e.g. `riven-storage`), configured via `storage.supabase.bucket`
- Storage keys used directly as object paths inside the bucket -- same key format as local adapter
- Bucket must be private (not public)
- Auto-create bucket on `healthCheck()` if it doesn't exist (private, no public access)
- Use `runBlocking {}` per call to bridge supabase-kt suspend functions to blocking StorageProvider interface
- Use Supabase's native signed URLs via `createSignedUrl()` -- not HMAC tokens

### S3 adapter configuration
- Optional `storage.s3.endpoint-url` for S3-compatible services (MinIO, R2, Spaces) -- if unset, defaults to standard AWS S3
- One S3 adapter handles all S3-compatible services via endpoint URL override
- Explicit credential config: `storage.s3.access-key-id` and `storage.s3.secret-access-key` as config properties
- Single bucket via `storage.s3.bucket`, objects use full storage key path
- Auto-create bucket on `healthCheck()` if missing -- consistent with Supabase adapter
- `storage.s3.region` config property (required for AWS, often ignored by S3-compatible services)

### Signed URL strategy
- Provider-first, SignedUrlService as fallback: StorageService tries `storageProvider.generateSignedUrl()` first; if it throws `UnsupportedOperationException` (local adapter), falls back to `SignedUrlService`
- Supabase adapter: uses `createSignedUrl()` from supabase-kt
- S3 adapter: uses S3Presigner to generate presigned GetObject URLs
- Direct downloads for Supabase/S3 -- signed URLs point directly to the provider, not through backend
- `/download/{token}` endpoint remains local-adapter-only (HMAC token validation)

### Error normalization
- Each adapter catches provider SDK exceptions and wraps them in domain exceptions
- Map 404 / NoSuchKey to `StorageNotFoundException`, all other failures to `StorageProviderException`
- Original provider exception preserved as the `cause` of domain exceptions (accessible in logs, not exposed to API responses)
- No retries -- throw immediately on any provider error. Retry logic deferred to future if needed.
- Provider error details never leak into exception messages -- only in the wrapped cause

### Claude's Discretion
- Exact supabase-kt API usage for upload/download/delete/list/signed URL calls
- AWS SDK for Kotlin client builder configuration details
- S3Presigner setup and usage
- Config property validation and defaults
- Test strategy for production adapters (mock vs testcontainers)
- Whether to add a `storage.supabase.url` / `storage.supabase.key` to StorageConfigurationProperties or reuse existing ApplicationConfigurationProperties

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `StorageProvider` interface (`models/storage/StorageProvider.kt`): 6 methods -- upload, download, delete, exists, generateSignedUrl, healthCheck
- `StorageResult` / `DownloadResult` data classes: return types already defined
- `LocalStorageProvider`: reference implementation showing the pattern (resolveAndValidate, section organization, logging)
- `SupabaseConfiguration`: already exists with `@ConditionalOnProperty(havingValue = "supabase")` and supabase-kt client creation
- `StorageConfigurationProperties`: has `provider`, `local`, `signedUrl` sections -- needs `supabase` and `s3` sections added
- `StorageService`: already injects `StorageProvider` interface -- new adapters plug in without changes
- `SignedUrlService`: HMAC token generation for local adapter -- will be fallback for signed URLs

### Established Patterns
- `@ConditionalOnProperty(name = ["storage.provider"], havingValue = "...")` for adapter activation
- Constructor injection with KLogger
- `StorageNotFoundException` / `StorageProviderException` exception hierarchy already in place
- Blocking (non-suspend) interface methods

### Integration Points
- `build.gradle.kts`: supabase-kt `storage-kt` dependency already present. AWS SDK for Kotlin needs adding.
- `StorageConfigurationProperties`: add `supabase` and `s3` nested config sections
- `SupabaseConfiguration`: already creates SupabaseClient bean -- adapter injects this
- `StorageService.generateSignedUrl()`: currently calls `signedUrlService` directly -- needs refactoring to try provider first, fallback to SignedUrlService
- `application.yml` / env vars: new config properties for S3 and potentially Supabase bucket name

</code_context>

<specifics>
## Specific Ideas

No specific requirements -- open to standard approaches.

</specifics>

<deferred>
## Deferred Ideas

None -- discussion stayed within phase scope.

</deferred>

---

*Phase: 02-production-adapters*
*Context gathered: 2026-03-06*
