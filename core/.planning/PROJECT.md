# Abstract Storage Implementation

## What This Is

A provider-agnostic storage layer for the Riven platform that enables file upload, download, deletion, listing, and metadata management across multiple storage backends (Supabase Storage, AWS S3, local filesystem). Self-hosters configure their preferred provider via environment variables, and the application handles all storage operations through a unified abstraction on both the backend (Kotlin/Spring Boot) and frontend (TypeScript).

## Core Value

Any file operation in the platform works identically regardless of which storage provider is configured — self-hosters are never locked into a specific storage vendor.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Backend storage abstraction with pluggable provider interface
- [ ] Supabase Storage adapter implementation
- [ ] AWS S3 adapter implementation (compatible with S3-API providers like MinIO, R2)
- [ ] Local filesystem adapter for development and simple self-hosted setups
- [ ] Provider selection via environment variables (STORAGE_PROVIDER + provider-specific config)
- [ ] Proxied file upload — client sends multipart to backend, backend stores via provider
- [ ] Presigned URL generation for large file direct uploads
- [ ] Signed (time-limited) preview/download URL generation
- [ ] File deletion
- [ ] File listing within a path/prefix
- [ ] File metadata attachment and retrieval
- [ ] Workspace + domain scoped file organization (e.g., {workspace-id}/avatars/, {workspace-id}/blocks/)
- [ ] Support for images and documents (PDFs, office docs)
- [ ] Frontend StorageProvider interface mirroring the AuthProvider pattern for presigned URL uploads
- [ ] REST API endpoints under /api/v1/storage for all storage operations

### Out of Scope

- Move/copy files between paths — not needed for v1
- Azure Blob Storage / Google Cloud Storage adapters — can be added later following the same interface
- Video file support — storage/bandwidth cost concerns, defer to future
- Per-workspace storage provider overrides (database config) — env-var global config is sufficient for v1
- Client-side file processing (thumbnails, compression) — backend handles all processing

## Context

- The application already has a provider abstraction pattern established on the frontend for authentication (`apps/client/lib/auth/`), using interface → types → adapter → factory. The storage abstraction should mirror this pattern on the client side.
- The existing `WorkspaceController` already accepts multipart file uploads (avatar), but currently has no storage implementation. This is the primary use case driving the feature.
- The backend follows layered architecture: Controller → Service → Repository. Storage providers will be a new infrastructure concern injected into services.
- The platform is multi-tenant with workspace-scoped data. Storage must respect workspace boundaries.
- S3-compatible APIs (MinIO, Cloudflare R2, DigitalOcean Spaces) should work through the S3 adapter without additional code.

## Constraints

- **Tech stack**: Spring Boot 3.5.3 / Kotlin on backend, Next.js/TypeScript on frontend — no new frameworks
- **Auth**: All storage API endpoints must be authenticated and workspace-scoped, consistent with existing security patterns
- **File types**: Images (JPEG, PNG, WebP, GIF, SVG) and documents (PDF, DOCX, XLSX) for v1
- **Configuration**: Provider config via environment variables only — no database-driven config in v1

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Backend-proxied uploads as default, presigned URLs for large files | Keeps control server-side while allowing performance optimization for large uploads | — Pending |
| Environment variable configuration | Simplest for self-hosters, matches 12-factor app patterns, can add DB config later | — Pending |
| Workspace + domain path organization | Mirrors multi-tenant model, enables per-workspace quotas later, logical grouping | — Pending |
| Signed (time-limited) URLs for file access | More secure than permanent public URLs, prevents unauthorized access to files | — Pending |
| Mirror AuthProvider pattern on client | Consistent abstraction pattern across the app, developers learn one pattern | — Pending |

---
*Last updated: 2025-03-05 after initialization*
