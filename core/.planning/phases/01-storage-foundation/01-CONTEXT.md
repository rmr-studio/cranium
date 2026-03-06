# Phase 1: Storage Foundation - Context

**Gathered:** 2026-03-05
**Status:** Ready for planning

<domain>
## Phase Boundary

A fully functional storage system with local filesystem backend. Files can be uploaded, downloaded, deleted, listed, and queried through the REST API with workspace-scoped security. Only the local filesystem adapter is implemented in this phase -- Supabase and S3 adapters come in Phase 2.

</domain>

<decisions>
## Implementation Decisions

### Domain path design
- System-defined domains via a `StorageDomain` enum -- not user-defined paths
- v1 domain: `AVATAR` only (additional domains like BLOCK, DOCUMENT, ATTACHMENT added in future phases as needed)
- Storage key format: `{workspaceId}/{domain}/{uuid}.{ext}` -- UUID only in path, no original filename
- Original filename preserved only in the metadata database table
- Each domain carries its own validation rules (allowed content types, max file size) as enum properties

### Content validation
- Apache Tika `tika-core` only (MIME detection from magic bytes, ~2MB) -- not full Tika with parsers
- Validate before storage: read magic bytes from multipart stream before writing to provider. Nothing stored on failure.
- Content type allowlist hardcoded in `StorageDomain` enum properties (not env-var configurable)
- AVATAR domain: images only (JPEG, PNG, WebP, GIF), small size limit (e.g. 2MB)
- SVGs are allowed but must be sanitized (strip script tags, event handlers, embedded JS) before storage

### Signed URL behavior
- Local adapter uses self-signed endpoint: backend generates HMAC-signed time-limited tokens, dedicated `/api/v1/storage/download/{token}` endpoint validates and streams the file
- Default expiry is configurable, but callers can specify custom duration per request (within bounds)
- Upload response includes a signed download URL (saves a round trip for upload-then-display flow)
- File listing returns metadata only -- no signed URLs per file. Clients request URLs individually as needed.

### StorageProvider contract
- Blocking (non-suspend) interface -- S3 adapter will internally use `runBlocking` to bridge coroutines. Consistent with synchronous Spring MVC codebase.
- Interface + `@ConditionalOnProperty` wiring: `StorageProvider` is an interface, each adapter is a `@Service` with `@ConditionalOnProperty("storage.provider", havingValue="local")`. Only one adapter bean is active.
- Existing `StorageService` (currently injects `SupabaseClient` directly) will be replaced/refactored to inject `StorageProvider` interface
- New `StorageException` hierarchy: `StorageException` base with subtypes (`StorageNotFoundException`, `StorageProviderException`, `ContentTypeNotAllowedException`, `FileSizeLimitExceededException`, etc.) mapped to HTTP status codes in `ExceptionHandler`
- Interface includes a `healthCheck()` method for Spring Actuator integration

### Claude's Discretion
- Exact HMAC signing implementation for local signed URLs
- SVG sanitization library choice
- Default signed URL expiry duration (15 min or 1 hour)
- Max bounds for custom expiry durations
- File metadata entity column design beyond the requirements spec (DATA-01)
- StorageProvider interface method signatures and return types
- Configuration properties structure (`storage.*` namespace)

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ActivityService`: Activity logging for mutations -- use `activityService.log()` convenience wrapper for upload/delete tracking
- `AuditableSoftDeletableEntity`: Base entity for file metadata (soft delete, audit columns)
- `ExceptionHandler` in `riven.core.exceptions`: Add new storage exceptions here for HTTP status mapping
- `Activity` enum: Needs new `FILE_UPLOAD`, `FILE_DELETE` (or similar) variants for activity logging
- `ApplicationEntityType` enum: Needs new `FILE` variant for activity entity type
- `WorkspaceSecurity`: Existing `@PreAuthorize` pattern for workspace-scoped access control
- `ServiceUtil.findOrThrow`: Repository lookup pattern for file metadata

### Established Patterns
- Constructor injection with KLogger via `LoggerConfig` prototype bean
- `@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")` on service methods
- Data classes for JPA entities extending `AuditableSoftDeletableEntity`
- `toModel()` mapping methods on entity classes
- Layered architecture: Controller -> Service -> Repository
- `@ConditionalOnProperty` used for bean wiring (new pattern for this codebase -- will establish the convention)

### Integration Points
- `SupabaseConfiguration` in `configuration/storage/`: Currently creates a `SupabaseClient` bean. Will need refactoring -- the Supabase bean should only exist when `storage.provider=supabase` (Phase 2)
- `StorageService` in `service/storage/`: Currently a stub injecting `SupabaseClient`. Will be replaced with the new abstraction.
- `build.gradle.kts`: Apache Tika dependency needs adding. Supabase storage dependency already present.
- `db/schema/01_tables/`: New `storage.sql` file for file metadata table
- `ExceptionHandler`: Add storage exception mappings

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

*Phase: 01-storage-foundation*
*Context gathered: 2026-03-05*
