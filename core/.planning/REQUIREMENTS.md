# Requirements: Abstract Storage Implementation

**Defined:** 2025-03-05
**Core Value:** Any file operation in the platform works identically regardless of which storage provider is configured

## v1 Requirements

### Provider Abstraction

- [x] **PROV-01**: StorageProvider interface defines upload, download, delete, list, generateSignedUrl, generateUploadUrl operations
- [x] **PROV-02**: Provider selection via environment variable (`STORAGE_PROVIDER`) with `@ConditionalOnProperty` bean wiring
- [x] **PROV-03**: Provider-specific configuration via environment variables (bucket, region, credentials, base path)
- [x] **PROV-04**: Domain exception hierarchy for storage errors (normalize provider-specific exceptions)

### Adapters

- [x] **ADPT-01**: Supabase Storage adapter implementing StorageProvider interface
- [x] **ADPT-02**: S3-compatible adapter implementing StorageProvider (supports AWS S3, MinIO, R2, Spaces via custom endpoint)
- [x] **ADPT-03**: Local filesystem adapter implementing StorageProvider with configurable base directory

### File Operations

- [x] **FILE-01**: Proxied multipart upload — client sends file to backend, backend streams to provider
- [x] **FILE-02**: Content-type validation via magic bytes (Apache Tika), reject files not matching allowlist
- [x] **FILE-03**: File size limit enforcement (configurable per environment)
- [x] **FILE-04**: UUID-based storage key generation (never use user-provided filenames as storage paths)
- [x] **FILE-05**: Signed (time-limited) download URL generation for file access
- [x] **FILE-06**: File deletion from provider with metadata cleanup
- [x] **FILE-07**: File listing by workspace and domain path (query metadata table)
- [x] **FILE-08**: File type allowlist — images (JPEG, PNG, WebP, GIF, SVG) and documents (PDF, DOCX, XLSX)
- [x] **FILE-09**: Presigned URL generation for direct-to-provider large file uploads
- [x] **FILE-10**: Presigned upload confirmation — client notifies backend after direct upload, metadata recorded
- [x] **FILE-11**: Batch upload — multiple files in one request
- [x] **FILE-12**: Batch delete — multiple files in one request

### Data & Security

- [x] **DATA-01**: File metadata entity in PostgreSQL (id, workspaceId, domain, storageKey, originalFilename, contentType, size, uploadedBy, timestamps)
- [x] **DATA-02**: Workspace + domain scoped file organization (`{workspaceId}/{domain}/{uuid-filename}`)
- [x] **DATA-03**: Workspace-scoped access control via `@PreAuthorize` on all storage service methods
- [x] **DATA-04**: Activity logging for all storage mutations (upload, delete)
- [x] **DATA-05**: File metadata attachment and retrieval (custom key-value metadata per file)

### API & Client

- [ ] **API-01**: REST endpoints under `/api/v1/storage/` for upload, download, delete, list, signed URL, presigned URL, batch operations
- [ ] **API-02**: Frontend StorageProvider TypeScript interface mirroring AuthProvider pattern
- [ ] **API-03**: Frontend storage adapter factory with environment-based provider selection

## v2 Requirements

### Client Enhancements

- **CLNT-01**: Upload progress tracking on client for large file uploads
- **CLNT-02**: Domain-scoped file contexts with per-context validation rules (avatars: images only <2MB, documents: PDFs <20MB)

### Platform

- **PLAT-01**: Image optimization / thumbnail generation on upload
- **PLAT-02**: Per-workspace storage quotas with usage tracking
- **PLAT-03**: File versioning for block content editing history

## Out of Scope

| Feature | Reason |
|---------|--------|
| Video file support | Storage/bandwidth costs, transcoding complexity — different problem domain |
| Move/copy files between paths | Most object stores don't support native move; rarely needed |
| Azure Blob / GCS adapters | S3-compatible API covers most providers (MinIO, R2, Spaces). Add on demand. |
| Client-side file processing | Moves trust boundary to untrusted client. Backend handles all processing. |
| Per-workspace provider config (DB-driven) | Massive operational complexity. Global env-var config sufficient for v1. |
| Resumable uploads (TUS) | Complex protocol, breaks interface uniformity. Presigned URLs handle large files. |
| CDN integration | Infrastructure concern, not application concern. Signed URLs can be fronted by CDN transparently. |
| Virus/malware scanning | Requires external service integration. Not needed for workspace productivity tool in v1. |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| PROV-01 | Phase 1 | Complete |
| PROV-02 | Phase 1 | Complete |
| PROV-03 | Phase 1 | Complete |
| PROV-04 | Phase 1 | Complete |
| ADPT-01 | Phase 2 | Complete |
| ADPT-02 | Phase 2 | Complete |
| ADPT-03 | Phase 1 | Complete |
| FILE-01 | Phase 1 | Complete |
| FILE-02 | Phase 1 | Complete |
| FILE-03 | Phase 1 | Complete |
| FILE-04 | Phase 1 | Complete |
| FILE-05 | Phase 1 | Complete |
| FILE-06 | Phase 1 | Complete |
| FILE-07 | Phase 1 | Complete |
| FILE-08 | Phase 1 | Complete |
| FILE-09 | Phase 3 | Complete |
| FILE-10 | Phase 3 | Complete |
| FILE-11 | Phase 3 | Complete |
| FILE-12 | Phase 3 | Complete |
| DATA-01 | Phase 1 | Complete |
| DATA-02 | Phase 1 | Complete |
| DATA-03 | Phase 1 | Complete |
| DATA-04 | Phase 1 | Complete |
| DATA-05 | Phase 3 | Complete |
| API-01 | Phase 1 | Pending |
| API-02 | Phase 3 | Pending |
| API-03 | Phase 3 | Pending |

**Coverage:**
- v1 requirements: 27 total
- Mapped to phases: 27
- Unmapped: 0

---
*Requirements defined: 2025-03-05*
*Last updated: 2026-03-05 after roadmap creation*
