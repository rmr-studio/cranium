---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-03-16
Domains:
  - "[[riven/docs/system-design/domains/Storage/Storage]]"
---
# AvatarService

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/File Management]]

## Purpose

Resolves avatar images for workspaces and users by looking up the entity's stored storage key and delegating the download to the [[riven/docs/system-design/domains/Storage/Provider Adapters/StorageProvider]]. Provides a read-only serving path that complements the upload path handled by [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/StorageService]].

---

## Dependencies

- `KLogger` — Structured logging
- [[riven/docs/system-design/domains/Storage/Provider Adapters/StorageProvider]] — Physical file download
- `WorkspaceRepository` — Workspace entity lookup (reads `avatarUrl` storage key)
- `UserRepository` — User entity lookup (reads `avatarUrl` storage key)

## Used By

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/AvatarController]] — REST endpoints for avatar serving

---

## Key Logic

Both methods follow the same pattern:

1. Look up the entity by ID via `ServiceUtil.findOrThrow` (throws `NotFoundException` if missing)
2. Extract the `avatarUrl` field (the stored storage key)
3. Throw `NotFoundException` if `avatarUrl` is null (no avatar set)
4. Delegate to `storageProvider.download(storageKey)` and return the `DownloadResult`

---

## Public Methods

### `getWorkspaceAvatar(workspaceId): DownloadResult`

Downloads the avatar for a workspace by resolving its stored storage key.

### `getUserAvatar(userId): DownloadResult`

Downloads the avatar for a user by resolving their stored storage key.

---

## Error Handling

| Exception | HTTP Status | Trigger |
|---|---|---|
| `NotFoundException` | 404 | Entity not found by ID, or entity has no avatar set |

---

## Gotchas

- **No `@PreAuthorize`** — avatar endpoints are public (added to `permitAll` in [[riven/docs/system-design/domains/Workspaces & Users/Auth & Authorization/SecurityConfig]]). The entity ID in the URL is the only lookup key, matching the pattern used by signed URL downloads.
- **Cross-domain read** — this service reads from `WorkspaceRepository` and `UserRepository` (owned by [[riven/docs/system-design/domains/Workspaces & Users/Workspaces & Users]]). This is a narrow, read-only dependency — it only extracts the `avatarUrl` field.
- **No metadata persistence** — unlike [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/StorageService]], this service does not interact with [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/FileMetadataEntity]] or log activity. It is a pure read path.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/AvatarController]] — REST endpoint layer
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/AvatarUrlResolver]] — Generates the URLs that route to this service
- [[riven/docs/system-design/domains/Storage/Provider Adapters/StorageProvider]] — Provider interface for physical file download
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/StorageService]] — Handles the upload side of avatar files
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/File Management]] — Parent subdomain
