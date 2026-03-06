# Feature Research

**Domain:** Provider-agnostic file storage abstraction (backend service API + client-side interface)
**Researched:** 2026-03-05
**Confidence:** HIGH

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume exist. Missing these = product feels incomplete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Upload files (proxied multipart) | Core operation -- users need to get files into the system | MEDIUM | Client sends multipart to backend, backend streams to provider. Already partially exists in WorkspaceController for avatars. Must handle streaming to avoid loading full file into memory. |
| Download / retrieve files | Users need to get files back out | LOW | Stream from provider through backend, or redirect to signed URL |
| Delete files | Basic lifecycle management | LOW | Soft-delete consideration: delete from provider immediately but track in metadata table for audit |
| List files by path/prefix | Users need to browse what's stored in a context | LOW | Scoped to workspace + domain path (e.g., `{workspaceId}/blocks/`) |
| Signed (time-limited) download URLs | Security baseline -- permanent public URLs are unacceptable for workspace-scoped content | MEDIUM | All three target providers (Supabase, S3, local FS) need different implementations. Local FS requires a token-based endpoint that validates expiry. |
| File metadata storage (DB table) | Users expect to see filename, size, content type, upload date | MEDIUM | Requires a `storage_file` database table tracking provider-agnostic metadata. This is the source of truth for listing, not the provider. |
| Content-type detection and validation | Security requirement per OWASP -- cannot trust client-provided Content-Type | LOW | Validate magic bytes against allowlist (images + documents). Use Apache Tika or simple magic byte checking. |
| File size limits | Prevents DoS via disk/bandwidth exhaustion | LOW | Configurable per-environment. Spring's `spring.servlet.multipart.max-file-size` + application-level check. |
| Filename sanitization | Path traversal prevention, collision avoidance | LOW | Generate UUID-based storage keys. Store original filename in metadata only. Never use user-provided names as storage paths. |
| Workspace-scoped file isolation | Multi-tenant platform -- files must respect workspace boundaries | MEDIUM | Path convention `{workspaceId}/{domain}/{filename}`. Service-layer `@PreAuthorize` enforcement matching existing patterns. |
| File type allowlist | Security -- restrict to known-safe types (images, PDFs, office docs) | LOW | Reject at upload time. Validate both extension and magic bytes. |
| Provider interface / adapter pattern (backend) | The entire point of the abstraction -- swap providers without code changes | MEDIUM | Kotlin interface with `upload()`, `download()`, `delete()`, `list()`, `generateSignedUrl()`, `generateUploadUrl()`. One implementation per provider. |
| Provider configuration via environment variables | Self-hosters configure once, platform handles the rest | LOW | `STORAGE_PROVIDER=s3\|supabase\|local` + provider-specific vars. Matches 12-factor app pattern. |
| Supabase Storage adapter | Current platform already uses Supabase for auth -- natural first provider | MEDIUM | Uses Supabase Storage REST API. Leverage existing Supabase credentials. |
| S3-compatible adapter | Industry standard, covers AWS S3, MinIO, Cloudflare R2, DigitalOcean Spaces | MEDIUM | AWS SDK v2 for Kotlin. Endpoint + credentials config covers all S3-compatible providers. |
| Local filesystem adapter | Development and simple self-hosted deployments | LOW | Store files under a configurable base directory. Simplest adapter but needs its own signed-URL mechanism (token-validated serving endpoint). |

### Differentiators (Competitive Advantage)

Features that set the product apart. Not required, but valuable.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Presigned URL uploads for large files | Client uploads directly to provider, bypassing backend bandwidth/memory constraints. Critical for files over 10-50MB. | MEDIUM | S3 and Supabase both support this natively. Local FS adapter would need a token-validated upload endpoint. Frontend needs upload progress tracking. |
| Frontend StorageProvider interface (mirroring AuthProvider) | Consistent abstraction pattern across the client app. Developers learn one pattern for all provider-agnostic concerns. | MEDIUM | Interface defines `uploadFile()`, `getSignedUrl()`, `deleteFile()`. Factory creates adapter based on env var. Mirrors the existing `AuthProvider` pattern in `lib/auth/`. |
| Domain-scoped file contexts | Typed upload contexts (avatar, block-attachment, document) with per-context validation rules (e.g., avatars must be images and under 2MB, documents allow PDFs up to 20MB). | MEDIUM | Enum-based contexts with configurable constraints. Prevents misuse of upload endpoints. |
| Upload progress tracking (client-side) | Large file uploads need progress feedback for good UX | LOW | XMLHttpRequest or fetch with ReadableStream for proxied uploads. For presigned uploads, track via XHR `upload.onprogress`. |
| Batch upload/delete operations | Upload or delete multiple files in one request, reducing round trips | MEDIUM | Important for block content with multiple attachments. Can be built on top of single-file operations. |

### Anti-Features (Commonly Requested, Often Problematic)

Features that seem good but create problems.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Client-side image processing (thumbnails, compression) | Reduce upload size, faster perceived performance | Increases client bundle size, inconsistent across browsers, cannot guarantee output quality. Moves trust boundary to untrusted client. | Server-side processing after upload. For v1, serve originals with appropriate caching headers. |
| Per-workspace provider configuration (database-driven) | Different workspaces might want different storage backends | Massive operational complexity -- connection pooling per provider, credential management in DB, migration between providers. Over-engineering for v1. | Global provider via env vars. If multi-provider is ever needed, it is a v3+ concern. |
| Move/copy files between paths | Seems like basic filesystem operation | Most object stores do not support native move (it is copy + delete). Adds complexity to interface for a rarely needed operation. Path is set at upload time. | If path changes are needed, re-upload to new path and delete old. Keep it out of the abstraction interface. |
| Video file support | Users want to upload videos | Storage costs balloon, bandwidth concerns, transcoding requirements, streaming infrastructure needed. Completely different problem domain. | Defer entirely. If video is ever needed, integrate a dedicated video platform (Mux, Cloudflare Stream) rather than storing raw video files. |
| Azure Blob / GCS adapters in v1 | "Support all clouds" completeness | Each adapter is maintenance burden. S3-compatible API covers the vast majority of providers (MinIO, R2, Spaces, Backblaze B2). Two adapters (S3 + Supabase) plus local covers 95% of use cases. | Ship S3 + Supabase + Local. Document how to add new adapters. Community can contribute Azure/GCS if demand exists. |
| Image transformation / thumbnails on upload | Serve optimized images automatically | Server-side processing is expensive, requires ImageMagick/Sharp or similar, per-provider implementation differences (Supabase has built-in transforms, S3 does not). | Store originals. Add image optimization as a separate phase when there are concrete performance requirements. |
| Resumable uploads (TUS protocol) | Reliability for large files on poor connections | Complex protocol, significant abstraction surface area increase. Supabase supports TUS natively but S3 uses different multipart upload API. Breaks interface uniformity. | Presigned URLs handle the large file case adequately. Revisit only if users need 500MB+ uploads on unreliable connections. |
| CDN integration in the abstraction | Serve files faster via CDN edge | CDN is an infrastructure concern, not an application concern. Mixing CDN logic into the storage abstraction couples deployment topology to code. | Use signed URLs that can be fronted by CDN at the infrastructure level (CloudFront + S3, Supabase built-in CDN). Keep CDN transparent to the application. |
| Virus/malware scanning | Enterprise security requirement | Requires external service integration (ClamAV, cloud scanning APIs), adds latency to upload path, operational overhead. Not needed for a workspace productivity tool in v1. | If needed later, add as a middleware/hook in the upload pipeline, not as a core abstraction feature. |

## Feature Dependencies

```
[Provider Interface (backend)]
    +--requires--> [Provider Configuration (env vars)]
    +--requires--> [File Metadata Table]
    +--enables---> [Supabase Adapter]
    +--enables---> [S3 Adapter]
    +--enables---> [Local FS Adapter]

[Upload Files (proxied)]
    +--requires--> [Provider Interface]
    +--requires--> [Content-Type Validation]
    +--requires--> [File Size Limits]
    +--requires--> [Filename Sanitization]
    +--requires--> [Workspace Scoping]

[Signed Download URLs]
    +--requires--> [Provider Interface]
    +--requires--> [File Metadata Table]

[Delete Files]
    +--requires--> [Provider Interface]
    +--requires--> [File Metadata Table]

[List Files]
    +--requires--> [File Metadata Table]

[Presigned URL Uploads]
    +--requires--> [Provider Interface]
    +--requires--> [File Metadata Table]
    +--enhances--> [Upload Files (proxied)]

[Frontend StorageProvider]
    +--requires--> [Backend REST API]
    +--requires--> [Signed Download URLs]
    +--optionally-uses--> [Presigned URL Uploads]

[Upload Progress Tracking]
    +--enhances--> [Frontend StorageProvider]

[Domain-Scoped Contexts]
    +--enhances--> [Content-Type Validation]
    +--enhances--> [File Size Limits]

[Batch Operations]
    +--requires--> [Upload Files (proxied)]
    +--requires--> [Delete Files]
```

### Dependency Notes

- **All operations require Provider Interface:** The abstraction layer is the foundation. No file operations work without it.
- **File Metadata Table is the source of truth:** Even though files live in providers, the application metadata table tracks what exists, who uploaded it, and where it belongs. Listing files queries the database, not the provider -- enabling richer queries and workspace-scoped filtering.
- **Presigned URL uploads enhance proxied uploads:** Proxied upload is the baseline. Presigned URLs are an optimization for large files. Both write to the same metadata table. Presigned uploads require a callback/confirmation step to record metadata after direct upload completes.
- **Frontend StorageProvider requires backend REST API:** The client never talks to storage providers directly (except for presigned URL uploads to provider endpoints). All metadata operations go through the backend API.
- **Upload validation (content-type, size, filename) must exist before any upload path works:** These are security controls, not optional enhancements.
- **Domain-Scoped Contexts enhance validation:** They layer per-context rules on top of the base validation. Can be added after basic validation is working.

## MVP Definition

### Launch With (v1)

Minimum viable product -- unblock the existing avatar upload and establish the abstraction pattern.

- [ ] Provider interface with `upload()`, `download()`, `delete()`, `list()`, `generateSignedUrl()` -- the core contract
- [ ] Supabase Storage adapter -- immediate value since Supabase is already in the stack
- [ ] S3-compatible adapter -- covers the widest range of self-hosted providers (MinIO, R2, etc.)
- [ ] Local filesystem adapter -- zero-dependency development and simple self-hosting
- [ ] Environment variable-based provider selection (`@ConditionalOnProperty` pattern)
- [ ] File metadata table (`storage_file`) tracking id, workspace, domain, path, original filename, content type, size, uploaded_by, timestamps
- [ ] Proxied multipart upload with content-type validation (magic bytes), size limits, and UUID-based storage key generation
- [ ] Signed download URL generation (time-limited)
- [ ] File deletion (remove from provider + mark metadata)
- [ ] File listing by workspace + domain path (query metadata table)
- [ ] Workspace-scoped access control (`@PreAuthorize`) on all storage service methods
- [ ] REST API endpoints under `/api/v1/storage/`
- [ ] Frontend `StorageProvider` interface + factory (mirroring AuthProvider pattern)
- [ ] Activity logging for all storage mutations

### Add After Validation (v1.x)

Features to add once core upload/download/delete flow is working end-to-end.

- [ ] Presigned URL uploads for large files -- add when users hit upload size/timeout issues with proxied uploads
- [ ] Upload progress tracking on client -- add alongside presigned URL support
- [ ] Domain-scoped file contexts with per-context validation rules -- add when multiple upload use cases exist (avatars, block attachments, documents)
- [ ] Batch upload/delete operations -- add when block content requires multiple file attachments

### Future Consideration (v2+)

Features to defer until core storage is battle-tested.

- [ ] Image optimization / thumbnail generation -- requires per-provider strategy
- [ ] Resumable uploads (TUS) -- only if large file uploads on unreliable connections become a real problem
- [ ] File versioning -- only if block content editing requires version history
- [ ] Per-workspace storage quotas -- requires usage tracking infrastructure
- [ ] Additional provider adapters (Azure, GCS) -- only on demonstrated demand

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Provider interface + 3 adapters | HIGH | MEDIUM | P1 |
| Proxied file upload with validation | HIGH | MEDIUM | P1 |
| File metadata table | HIGH | LOW | P1 |
| Signed download URLs | HIGH | MEDIUM | P1 |
| File deletion | HIGH | LOW | P1 |
| File listing | MEDIUM | LOW | P1 |
| Workspace-scoped access control | HIGH | LOW | P1 |
| REST API endpoints | HIGH | MEDIUM | P1 |
| Frontend StorageProvider interface | HIGH | MEDIUM | P1 |
| Env-var provider configuration | HIGH | LOW | P1 |
| Presigned URL uploads | MEDIUM | MEDIUM | P2 |
| Upload progress tracking | MEDIUM | LOW | P2 |
| Domain-scoped file contexts | MEDIUM | MEDIUM | P2 |
| Batch operations | MEDIUM | MEDIUM | P2 |
| Image optimization | MEDIUM | HIGH | P3 |
| Resumable uploads | LOW | HIGH | P3 |
| File versioning | LOW | MEDIUM | P3 |
| Storage quotas | LOW | MEDIUM | P3 |

**Priority key:**
- P1: Must have for launch
- P2: Should have, add when needed
- P3: Nice to have, future consideration

## Competitor Feature Analysis

| Feature | Flysystem (PHP) | Flystorage (Node/TS) | Supabase Storage | Our Approach |
|---------|-----------------|----------------------|------------------|--------------|
| Core CRUD (read/write/delete) | Yes | Yes | Yes | Yes -- baseline |
| List contents | Yes (recursive + shallow) | Yes (deep + shallow) | Yes | Yes -- via metadata table for richer queries |
| Visibility (public/private) | Yes (first-class) | Yes (changeVisibility) | Yes (bucket-level) | Signed URLs only -- all files private by default. Simpler model. |
| Checksum / integrity | No | Yes (checksum method) | No | No in v1 -- add if data integrity validation needed |
| Move / copy | Yes | Yes (moveFile) | Yes (move, copy) | No -- anti-feature, not needed |
| Presigned URLs | N/A (server-side lib) | N/A | Yes (createSignedUrl) | Yes -- for large uploads (v1.x) |
| Image transforms | No (separate concern) | No | Yes (built-in) | Defer -- provider-specific, breaks abstraction purity |
| Resumable uploads | No | No | Yes (TUS) | Defer -- complexity vs. value tradeoff |
| File metadata in DB | No (filesystem only) | No (filesystem only) | Yes (Postgres-backed) | Yes -- key differentiator vs. pure filesystem abstractions |
| Multi-tenant scoping | No | No | Yes (RLS policies) | Yes -- workspace-scoped paths + service-layer auth |
| Frontend SDK | No | No | Yes (JS client) | Yes -- StorageProvider interface mirroring AuthProvider |

**Key insight:** Pure filesystem abstractions (Flysystem, Flystorage) handle provider switching but lack metadata storage, multi-tenancy, and auth. Supabase Storage has these but locks you into Supabase. Our abstraction combines provider-agnostic file operations with application-level metadata, workspace scoping, and auth -- bridging the gap between "filesystem library" and "storage platform."

## Sources

- [Flysystem documentation](https://flysystem.thephpleague.com/docs/) -- PHP storage abstraction, gold standard for interface design
- [Flystorage API](https://flystorage.dev/api/) -- TypeScript port of Flysystem concepts
- [Supabase Storage docs](https://supabase.com/docs/guides/storage) -- Current provider capabilities
- [AWS S3 presigned URLs](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html) -- Presigned URL best practices
- [Cloudflare R2 presigned URLs](https://developers.cloudflare.com/r2/api/s3/presigned-urls/) -- S3-compatible presigned URL reference
- [OWASP File Upload Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html) -- Security validation requirements

---
*Feature research for: Provider-agnostic file storage abstraction*
*Researched: 2026-03-05*
