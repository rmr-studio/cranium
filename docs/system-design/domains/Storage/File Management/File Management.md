---
Created: 2026-03-07
Domains:
  - "[[Storage]]"
tags:
  - architecture/subdomain
---
# Subdomain: File Management

## Overview

File Management owns the full lifecycle of workspace-scoped files: upload, download, delete, list, and metadata operations. It coordinates content validation (MIME detection, size limits, SVG sanitization), delegates physical I/O to a pluggable [[StorageProvider]], persists file metadata to PostgreSQL, generates signed download URLs with HMAC-SHA256 tokens, and logs all mutations to the activity audit trail.

Files are always scoped to a workspace and categorised by a `StorageDomain` enum that defines per-domain content type allowlists and file size limits. The only exception is user-scoped uploads (e.g. avatars via `uploadUserFile`), which bypass metadata persistence and activity logging.

Two upload flows are supported:

1. **Direct upload** -- the client sends the file to the API, which validates and stores it in a single request.
2. **Presigned upload** -- the client requests a presigned URL, uploads directly to the provider (S3/Supabase), then confirms the upload so the API can validate content and persist metadata.

Downloads are authorized via signed URL tokens rather than workspace JWT, allowing files to be served without requiring the consumer to hold a workspace session.

---

## Components

| Component | Purpose | Type |
|---|---|---|
| [[StorageService]] | Orchestrates upload, download, delete, list, metadata, and batch operations | Service |
| [[AvatarService]] | Resolves and serves avatar images for workspaces and users via StorageProvider | Service |
| [[StorageController]] | Thin REST controller exposing 11 endpoints under `/api/v1/storage` | Controller |
| [[AvatarController]] | Thin REST controller serving avatar images at `/api/v1/avatars` (unauthenticated) | Controller |
| [[ContentValidationService]] | MIME detection, content type/size validation, SVG sanitization, storage key generation | Service |
| [[SignedUrlService]] | HMAC-SHA256 signed token generation and validation for secure download URLs | Service |
| [[AvatarUrlResolver]] | Stateless utility converting stored avatar storage keys to API-relative URLs at read time | Utility |
| [[FileMetadataEntity]] | JPA entity for `file_metadata` table with JSONB custom metadata | Entity |
| [[FileMetadataRepository]] | Spring Data JPA repository with workspace- and domain-scoped queries | Repository |

---

## Technical Debt

- **Single StorageDomain variant** -- only `AVATAR` is defined. New domains (e.g. attachments, exports) will need to be added to the enum with appropriate allowlists and size limits as the platform grows.
- **No pagination on `listFiles`** -- returns all files in a workspace/domain as a flat list. Will need cursor or offset pagination once file counts grow.

---

## Recent Changes

| Date | Change |
|---|---|
| 2026-03-16 | Added avatar serving components: [[AvatarService]], [[AvatarController]], [[AvatarUrlResolver]]. Avatar URLs in API responses now point to dedicated unauthenticated endpoints instead of exposing raw storage keys. |
| 2026-03-07 | Initial implementation of File Management subdomain with full upload/download/delete/list/batch/metadata/presigned-upload flows. |
