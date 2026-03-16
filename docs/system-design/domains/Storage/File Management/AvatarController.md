---
tags:
  - layer/controller
  - component/active
  - architecture/component
Created: 2026-03-16
Domains:
  - "[[Storage]]"
---
# AvatarController

Part of [[File Management]]

## Purpose

Thin REST controller serving avatar images for workspaces and users at `/api/v1/avatars`. Delegates to [[AvatarService]] for entity lookup and file download, then streams the image content directly to the HTTP response.

---

## Dependencies

- [[AvatarService]] — Entity lookup and storage provider download

## Used By

- External API consumers — avatar URLs embedded in API responses by [[AvatarUrlResolver]]

---

## Endpoints

| Method | Path | Service Method | Status | Description |
|---|---|---|---|---|
| GET | `/workspace/{workspaceId}` | `getWorkspaceAvatar` | 200 | Stream workspace avatar image |
| GET | `/user/{userId}` | `getUserAvatar` | 200 | Stream user avatar image |

Both endpoints are **unauthenticated** — added to `permitAll` in [[SecurityConfig]]. The entity ID is the lookup key.

---

## Key Logic

### `streamAvatar(result)` (private)

Shared helper that wraps a `DownloadResult` into a `StreamingResponseBody`:

- Streams file content directly to the output stream (no in-memory buffering)
- Sets `Content-Type` from the `DownloadResult.contentType`
- Sets `Content-Length` from the `DownloadResult.contentLength`
- Sets `Content-Disposition: inline` (display in browser, not download)
- Sets `Cache-Control: public, max-age=300` (5-minute browser cache)

---

## Gotchas

- **No JWT required** — these endpoints follow the same unauthenticated pattern as the signed URL download endpoint on [[StorageController]]. Anyone with the entity UUID can fetch the avatar.
- **5-minute cache** — `Cache-Control: public, max-age=300` means avatar changes may take up to 5 minutes to propagate to clients. This is a deliberate trade-off between freshness and load.
- **Streaming response** — uses `StreamingResponseBody` to avoid buffering the entire image in memory, identical to [[StorageController]]'s download endpoint.

---

## Related

- [[AvatarService]] — Delegated business logic
- [[AvatarUrlResolver]] — Generates the URLs that route to this controller
- [[StorageController]] — Sibling controller in the same package handling general file operations
- [[File Management]] — Parent subdomain
