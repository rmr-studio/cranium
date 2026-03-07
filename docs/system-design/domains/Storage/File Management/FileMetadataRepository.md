---
tags:
  - layer/repository
  - component/active
  - architecture/component
Created: 2026-03-07
Updated: 2026-03-07
Domains:
  - "[[Storage]]"
---
# FileMetadataRepository

Part of [[File Management]]

## Purpose

Spring Data JPA repository for [[FileMetadataEntity]]. Provides workspace-scoped and domain-filtered queries for file metadata. All derived queries automatically exclude soft-deleted rows via the entity's `@SQLRestriction("deleted = false")`.

---

## Interface

Extends `JpaRepository<FileMetadataEntity, UUID>`.

## Query Methods

| Method | Returns | Description |
|---|---|---|
| `findByWorkspaceIdAndDomain(workspaceId, domain)` | `List<FileMetadataEntity>` | All files in a workspace filtered by storage domain |
| `findByWorkspaceId(workspaceId)` | `List<FileMetadataEntity>` | All files in a workspace |
| `findByStorageKey(storageKey)` | `Optional<FileMetadataEntity>` | Lookup by unique storage key (used in download flow) |
| `findByIdAndWorkspaceId(id, workspaceId)` | `Optional<FileMetadataEntity>` | Workspace-scoped lookup by ID (primary access pattern) |

---

## Used By

- [[StorageService]] -- All file metadata reads and writes

---

## Related

- [[FileMetadataEntity]] -- Managed entity
- [[File Management]] -- Parent subdomain
