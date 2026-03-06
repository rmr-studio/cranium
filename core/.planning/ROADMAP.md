# Roadmap: Abstract Storage Implementation

## Overview

Deliver a provider-agnostic storage layer in three phases: first build the complete storage system end-to-end with a local filesystem adapter (proving the abstraction works), then implement production adapters (Supabase and S3), then add advanced operations (presigned uploads, batch ops) and frontend integration. Each phase delivers a working, testable increment.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Storage Foundation** - StorageProvider interface, local adapter, service layer, REST API, and all core file operations
- [x] **Phase 2: Production Adapters** - Supabase Storage and S3-compatible adapter implementations (completed 2026-03-06)
- [ ] **Phase 3: Advanced Operations** - Presigned uploads, batch operations, file metadata (frontend integration deferred to client repo)

## Phase Details

### Phase 1: Storage Foundation
**Goal**: A fully functional storage system exists with local filesystem backend -- files can be uploaded, downloaded, deleted, listed, and queried through the REST API with workspace-scoped security
**Depends on**: Nothing (first phase)
**Requirements**: PROV-01, PROV-02, PROV-03, PROV-04, ADPT-03, FILE-01, FILE-02, FILE-03, FILE-04, FILE-05, FILE-06, FILE-07, FILE-08, DATA-01, DATA-02, DATA-03, DATA-04, API-01
**Success Criteria** (what must be TRUE):
  1. A multipart file upload to `/api/v1/storage/` stores the file and returns file metadata including a UUID-based storage key
  2. Uploading a file with a disallowed content type (e.g., .exe) is rejected based on magic byte detection, not file extension
  3. A signed download URL can be generated for a stored file, and the URL expires after its time limit
  4. Files are listed and filtered by workspace and domain path, and soft-deleted files do not appear in listings
  5. All storage mutations (upload, delete) are recorded in the activity log with workspace and user context
**Plans:** 4 plans

Plans:
- [ ] 01-01-PLAN.md -- Contracts, data layer, config: StorageProvider interface, StorageDomain enum, FileMetadataEntity, exceptions, configuration properties, DB schema
- [ ] 01-02-PLAN.md -- Content validation and local adapter: Tika MIME detection, SVG sanitization, LocalStorageProvider filesystem operations
- [ ] 01-03-PLAN.md -- Signed URLs and storage service: HMAC-SHA256 token service, StorageService orchestrating upload/download/delete/list with security and activity logging
- [ ] 01-04-PLAN.md -- REST API and verification: StorageController with 6 endpoints, SecurityConfig update, end-to-end checkpoint

### Phase 2: Production Adapters
**Goal**: The storage system works identically against Supabase Storage and any S3-compatible provider (AWS S3, MinIO, R2, Spaces) -- switching providers requires only changing environment variables
**Depends on**: Phase 1
**Requirements**: ADPT-01, ADPT-02
**Success Criteria** (what must be TRUE):
  1. Switching `STORAGE_PROVIDER` from `local` to `supabase` and providing Supabase credentials results in all storage operations working without code changes
  2. Switching `STORAGE_PROVIDER` to `s3` with a MinIO endpoint URL results in all storage operations working against the S3-compatible service
  3. Provider-specific errors (Supabase API errors, S3 SDK exceptions) are normalized into the domain exception hierarchy -- no provider details leak to API responses
**Plans:** 2/2 plans complete

Plans:
- [ ] 02-01-PLAN.md -- Supabase adapter, config extensions, signed URL refactor: SupabaseStorageProvider with supabase-kt, extended StorageConfigurationProperties, provider-first signed URL strategy
- [ ] 02-02-PLAN.md -- S3 adapter: AWS SDK for Kotlin dependency, S3Configuration with S3Client bean, S3StorageProvider with presigned URLs

### Phase 3: Advanced Operations
**Goal**: Large files can be uploaded directly to the storage provider via presigned URLs, batch operations reduce round trips, and custom file metadata can be attached and updated on stored files. Frontend TypeScript integration (API-02, API-03) deferred to client repo.
**Depends on**: Phase 2
**Requirements**: FILE-09, FILE-10, FILE-11, FILE-12, DATA-05, API-02, API-03
**Success Criteria** (what must be TRUE):
  1. A client can request a presigned upload URL, upload directly to the provider, then confirm the upload -- and the file appears in metadata with correct workspace scoping
  2. Multiple files can be uploaded or deleted in a single API request via batch endpoints
  3. Custom key-value metadata can be attached to and retrieved from any stored file
  4. The frontend StorageProvider TypeScript interface mirrors the AuthProvider pattern, with a factory that selects the adapter based on environment configuration
**Plans:** 2/3 plans executed

Plans:
- [ ] 03-01-PLAN.md -- Contracts and data layer: StorageProvider interface update (generateUploadUrl), all adapter implementations, FileMetadataEntity JSONB metadata column, request/response types, FILE_UPDATE enum
- [ ] 03-02-PLAN.md -- Presigned upload and metadata: StorageService presigned upload flow (request + confirm), metadata PATCH with merge semantics, StorageController endpoints
- [ ] 03-03-PLAN.md -- Batch operations: StorageService batch upload/delete with per-item error collection, StorageController 207 Multi-Status endpoints

## Progress

**Execution Order:**
Phases execute in numeric order: 1 -> 2 -> 3

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Storage Foundation | 0/4 | Planned | - |
| 2. Production Adapters | 2/2 | Complete   | 2026-03-06 |
| 3. Advanced Operations | 2/3 | In Progress|  |
