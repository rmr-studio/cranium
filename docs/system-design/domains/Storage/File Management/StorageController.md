---
tags:
  - layer/controller
  - component/active
  - architecture/component
Created: 2026-03-07
Updated: 2026-03-07
Domains:
  - "[[Storage]]"
---
# StorageController

Part of [[File Management]]

## Purpose

Thin REST controller exposing 11 endpoints under `/api/v1/storage`. Delegates all business logic to [[StorageService]] and returns `ResponseEntity` with appropriate HTTP status codes.

---

## Dependencies

- [[StorageService]] -- All storage operations
- `ObjectMapper` -- Parses metadata JSON string from multipart upload form parameter

## Used By

- External API consumers

---

## Endpoints

| Method | Path | Service Method | Status | Description |
|---|---|---|---|---|
| POST | `/workspace/{workspaceId}/upload` | `uploadFile` | 201 | Upload a file with optional metadata (multipart form) |
| POST | `/workspace/{workspaceId}/presigned-upload` | `requestPresignedUpload` | 200 | Request a presigned upload URL |
| POST | `/workspace/{workspaceId}/presigned-upload/confirm` | `confirmPresignedUpload` | 201 | Confirm a direct upload and persist metadata |
| PATCH | `/workspace/{workspaceId}/files/{fileId}/metadata` | `updateMetadata` | 200 | Update custom metadata on a file |
| GET | `/workspace/{workspaceId}/files` | `listFiles` | 200 | List files, optional `?domain=` filter |
| GET | `/workspace/{workspaceId}/files/{fileId}` | `getFile` | 200 | Get file metadata by ID |
| POST | `/workspace/{workspaceId}/files/{fileId}/signed-url` | `generateSignedUrl` | 200 | Generate a signed download URL |
| DELETE | `/workspace/{workspaceId}/files/{fileId}` | `deleteFile` | 204 | Soft-delete a file |
| POST | `/workspace/{workspaceId}/batch-upload` | `batchUpload` | 207 | Upload up to 10 files |
| POST | `/workspace/{workspaceId}/batch-delete` | `batchDelete` | 207 | Delete up to 50 files |
| GET | `/download/{token}` | `downloadFile` | 200 | Download a file via signed token (streaming) |

---

## Gotchas

- **Metadata on upload is a JSON string parameter** -- because the upload endpoint uses multipart form data, the `metadata` parameter is a raw JSON string that the controller parses via `ObjectMapper.readValue<Map<String, String>>()` before passing to the service.
- **Download uses `StreamingResponseBody`** -- the download endpoint streams the file content directly to the HTTP response output stream, avoiding buffering the entire file in memory. `Content-Disposition` is set to `attachment` when `?download=true`, `inline` otherwise.
- **Download endpoint has no workspace path** -- the route is `/api/v1/storage/download/{token}`, not under `/workspace/{workspaceId}`. The signed token is the sole authorization mechanism.

---

## Related

- [[StorageService]] -- Delegated business logic
- [[File Management]] -- Parent subdomain
