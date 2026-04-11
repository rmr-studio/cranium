---
tags:
  - architecture/domain
Created: 2026-03-07
---
# Domain: Storage

---

## Overview

The Storage domain provides provider-agnostic file storage capabilities for the application. It manages the complete lifecycle of uploaded files — content validation, storage provider delegation, workspace-scoped metadata persistence, HMAC-signed download URLs, and batch operations. The domain uses a strategy pattern to support multiple storage backends (local filesystem, AWS S3, Supabase Storage) with runtime selection via configuration.

---

## Boundaries

### This Domain Owns

- File upload/download/delete lifecycle
- Content type validation via Apache Tika (magic-byte detection, not extension-based)
- File size validation per storage domain
- SVG sanitization
- Storage key generation (`{workspaceId}/{domain}/{uuid}.{ext}`)
- File metadata persistence (FileMetadataEntity with JSONB custom metadata)
- Signed URL generation and validation (HMAC-based for local, provider-native for S3/Supabase)
- Batch upload/delete operations with per-item error isolation
- Presigned direct-to-provider upload flow with post-upload validation

### This Domain Does NOT Own

- User avatar assignment (owned by [[riven/docs/system-design/domains/Workspaces & Users/Workspaces & Users]], which calls StorageService)
- Workspace-specific business logic around files
- Database-level RLS policies
- External storage provider infrastructure

---

## Sub-Domains

| Sub-Domain | Purpose |
|---|---|
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/File Management]] | Upload, download, delete, list operations with validation, metadata, and signed URLs |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/Provider Adapters]] | Pluggable storage backends (Local, S3, Supabase) with configuration |

### Integrations

| Component | External System |
|---|---|
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/SupabaseStorageProvider]] | Supabase Storage API |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/S3StorageProvider]] | AWS S3 / S3-compatible services |

---

## Flows

| Flow | Type | Description |
|---|---|---|
| [[riven/docs/system-design/flows/Flow - File Upload]] | User-facing | Multipart upload -> validation -> storage -> metadata persistence |
| [[riven/docs/system-design/flows/Flow - Signed URL Download]] | User-facing | Token-authorized download bypassing JWT authentication |
| Avatar Serving | User-facing | Entity ID -> storage key lookup -> provider download -> streamed response (unauthenticated) |

---

## Data

### Owned Entities

| Entity | Purpose | Key Fields |
|---|---|---|
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/FileMetadataEntity]] | Workspace-scoped file metadata with JSONB custom metadata | id, workspaceId, domain, storageKey, originalFilename, contentType, fileSize, uploadedBy, metadata |

### Database Tables

| Table | Entity | Notes |
|---|---|---|
| file_metadata | [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/FileMetadataEntity]] | Indexed on workspace_id, (workspace_id, domain), storage_key (unique) |

---

## External Dependencies

| Dependency | Purpose | Failure Impact |
|---|---|---|
| Apache Tika | MIME type detection from file magic bytes | Validation fails — files rejected with wrong content type |
| AWS S3 SDK | S3 storage operations (when provider=s3) | Upload/download fails — 502 StorageProviderException |
| Supabase Storage | Supabase storage operations (when provider=supabase) | Upload/download fails — 502 StorageProviderException |

---

## Domain Interactions

### Depends On

| Domain | What We Consume | Via Component | Related Flow |
|--------|----------------|---------------|--------------|
| [[riven/docs/system-design/domains/Workspaces & Users/Workspaces & Users]] | User identity (JWT extraction) | [[riven/docs/system-design/domains/Workspaces & Users/Auth & Authorization/AuthTokenService]] | [[riven/docs/system-design/flows/Flow - File Upload]] |
| [[riven/docs/system-design/domains/Workspaces & Users/Workspaces & Users]] | Workspace authorization | [[riven/docs/system-design/domains/Workspaces & Users/Auth & Authorization/WorkspaceSecurity]] | All workspace-scoped operations |
| [[riven/docs/system-design/domains/Workspaces & Users/Workspaces & Users]] | Audit trail logging | [[riven/docs/system-design/domains/Workspaces & Users/User Management/ActivityService]] | [[riven/docs/system-design/flows/Flow - File Upload]], delete, metadata update |

### Consumed By

| Consumer | What They Consume | Via Component | Related Flow |
|----------|------------------|---------------|--------------|
| [[riven/docs/system-design/domains/Workspaces & Users/Workspaces & Users]] | File upload for workspace and user avatars | [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/StorageService]] (direct injection from UserService, WorkspaceService) | Avatar upload |
| Self (Storage) | Workspace and user entity lookups for avatar serving | [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/AvatarService]] reads from WorkspaceRepository and UserRepository ([[riven/docs/system-design/domains/Workspaces & Users/Workspaces & Users]]) | Avatar serving |

---

## Service Summary

| Subdomain | Service | Purpose |
|---|---|---|
| File Management | [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/StorageService]] | Orchestrates upload, download, delete, list, batch, and presigned flows |
| File Management | [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/StorageController]] | REST endpoints for all file operations |
| File Management | [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/AvatarService]] | Resolves and serves avatar images for workspaces and users |
| File Management | [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/AvatarController]] | Unauthenticated REST endpoints for avatar image serving |
| File Management | [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/AvatarUrlResolver]] | Converts stored avatar storage keys to API-relative URLs at read time |
| File Management | [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/ContentValidationService]] | Tika-based MIME detection, content type/size validation, SVG sanitization |
| File Management | [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/SignedUrlService]] | HMAC-based signed URL generation and validation for local provider |
| Provider Adapters | [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/StorageProvider]] | Interface defining the storage backend contract |
| Provider Adapters | [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/LocalStorageProvider]] | Filesystem-based storage with path traversal prevention |
| Provider Adapters | [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/S3StorageProvider]] | AWS S3 / S3-compatible storage with presigned URL support |
| Provider Adapters | [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/SupabaseStorageProvider]] | Supabase Storage API implementation |
| Provider Adapters | [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/StorageConfigurationProperties]] | Typed configuration for all providers |
| Provider Adapters | [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/S3Configuration]] | Conditional S3Client bean creation |

---

## Key Decisions

| Decision | Summary |
|---|---|
| Strategy pattern for providers | `@ConditionalOnProperty` activates exactly one [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/StorageProvider]] at runtime |
| HMAC-signed download URLs | Downloads bypass JWT auth — signed token IS the authorization |
| Content validation via Tika | MIME type detected from magic bytes, not file extension — prevents spoofing |
| Soft-delete before physical delete | Metadata marked deleted first; physical deletion failure is tolerated |
| Per-item error isolation in batches | Batch upload/delete process each item independently; one failure does not block others |

---

## Technical Debt

| Issue | Impact | Effort |
|---|---|---|
| StorageService at ~510 lines | Some long methods could benefit from further extraction | Med |
| `runBlocking` in S3/Supabase providers | Blocks servlet threads; could use coroutine support | Med |

---

## Recent Changes

| Date | Change | Feature/ADR |
|---|---|---|
| 2026-03-16 | Added avatar serving: AvatarService, AvatarController, AvatarUrlResolver. API responses now contain usable avatar URLs instead of raw storage keys. New cross-domain read dependency on WorkspaceRepository/UserRepository. | Avatar URL Resolution |
| 2026-03-07 | Storage domain created | Provider-Agnostic File Storage |
