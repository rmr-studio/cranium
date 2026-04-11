---
tags:
  - layer/entity
  - component/active
  - architecture/component
Created: 2026-03-07
Updated: 2026-03-07
Domains:
  - "[[riven/docs/system-design/domains/Storage/Storage]]"
---
# FileMetadataEntity

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/File Management]]

## Purpose

JPA entity representing a stored file's metadata. Extends `AuditableSoftDeletableEntity` for audit columns (`createdAt`, `updatedAt`) and soft-delete support (`deleted`, `deletedAt`). Maps to the `file_metadata` table.

---

## Table: `file_metadata`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | UUID | PK, auto-generated | Unique file identifier |
| `workspace_id` | UUID | NOT NULL | Owning workspace |
| `domain` | VARCHAR (enum) | NOT NULL | `StorageDomain` enum value (e.g. `AVATAR`) |
| `storage_key` | VARCHAR | NOT NULL, UNIQUE | Provider-specific path to the physical file |
| `original_filename` | VARCHAR | NOT NULL | Original filename from the upload |
| `content_type` | VARCHAR | NOT NULL | Detected MIME type |
| `file_size` | BIGINT | NOT NULL | File size in bytes |
| `uploaded_by` | UUID | NOT NULL | User who uploaded the file |
| `metadata` | JSONB | nullable | Custom key-value metadata (max 20 keys) |
| `created_at` | TIMESTAMP | NOT NULL (audit) | Creation timestamp |
| `updated_at` | TIMESTAMP | NOT NULL (audit) | Last update timestamp |
| `deleted` | BOOLEAN | NOT NULL (soft-delete) | Soft-delete flag |
| `deleted_at` | TIMESTAMP | nullable (soft-delete) | Soft-delete timestamp |

## Indexes

| Name | Columns | Unique |
|---|---|---|
| `idx_file_metadata_workspace_id` | `workspace_id` | No |
| `idx_file_metadata_workspace_domain` | `workspace_id, domain` | No |
| `uq_file_metadata_storage_key` | `storage_key` | Yes |

---

## Key Details

- **JSONB metadata** uses Hypersistence `@Type(JsonBinaryType::class)` with `columnDefinition = "jsonb"`. Stores arbitrary `Map<String, String>?` for user-defined file metadata.
- **`domain` is stored as a string** via `@Enumerated(EnumType.STRING)`, mapping directly to `StorageDomain` enum names.
- **`toModel(): FileMetadata`** maps the entity to the domain model, asserting non-null on `id`, `createdAt`, and `updatedAt` (which are always populated after persistence).
- **Soft-delete filtering** is automatic via `@SQLRestriction("deleted = false")` inherited from `AuditableSoftDeletableEntity`. Soft-deleted files are invisible to all JPQL and derived queries.

---

## Used By

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/FileMetadataRepository]] -- Persistence layer
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/StorageService]] -- Creates, updates, and soft-deletes entities

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/FileMetadataRepository]] -- Repository interface
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/File Management]] -- Parent subdomain
