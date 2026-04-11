---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-03-07
Updated: 2026-03-07
Domains:
  - "[[riven/docs/system-design/domains/Storage/Storage]]"
---
# ContentValidationService

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/File Management]]

## Purpose

Validates file content before storage operations. Provides MIME type detection via Apache Tika magic bytes, content type and file size validation against per-domain rules, SVG sanitization to strip malicious content, and UUID-based storage key generation.

---

## Dependencies

- `KLogger` -- Structured logging

## Used By

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/StorageService]] -- Validation before all upload and presigned-confirm flows

---

## Key Logic

### MIME detection

Uses Apache Tika to detect MIME type from byte signatures (magic bytes), not file extension. The original filename is passed as a hint via `TikaCoreProperties.RESOURCE_NAME_KEY` but only influences detection when magic bytes are ambiguous. This prevents content type spoofing via renamed files.

### Content type validation

Each `StorageDomain` enum variant defines an allowlist of MIME types. `validateContentType` checks whether the detected type is in the domain's `allowedContentTypes` set and throws `ContentTypeNotAllowedException` if not.

### File size validation

Each `StorageDomain` defines a `maxFileSize` in bytes. `validateFileSize` checks whether the file size exceeds the domain's limit and throws `FileSizeLimitExceededException` if so.

### SVG sanitization

Uses `io.github.borewit:sanitize` (`SVGSanitizer.sanitize()`) to strip script tags, event handlers (`onload`, `onclick`, etc.), and embedded JavaScript from SVG files. Applied automatically by [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/StorageService]] when the detected MIME type is `image/svg+xml`.

### Storage key generation

Generates deterministic-path, UUID-named storage keys:

- **Workspace-scoped:** `{workspaceId}/{domain}/{uuid}.{ext}` -- e.g. `a1b2c3/avatar/d4e5f6.png`
- **User-scoped:** `users/{userId}/{domain}/{uuid}.{ext}` -- e.g. `users/a1b2c3/avatar/d4e5f6.png`

The file extension is derived from the detected MIME type using Tika's MIME type registry (`MimeTypes.getDefaultMimeTypes()`), not from the original filename.

### StorageDomain enum

Defines per-domain validation rules:

| Domain | Allowed Content Types | Max File Size |
|---|---|---|
| `AVATAR` | `image/jpeg`, `image/png`, `image/webp`, `image/gif`, `image/svg+xml` | 2 MB |

New domains are added by extending the enum with appropriate allowlists and size limits.

---

## Public Methods

### `detectContentType(inputStream, filename?): String`

Detect MIME type from stream content using Tika magic bytes. Filename is used only as a hint.

### `validateContentType(domain, contentType)`

Validate that the content type is in the domain's allowlist. Throws `ContentTypeNotAllowedException` on rejection.

### `validateFileSize(domain, fileSize)`

Validate that the file size is within the domain's limit. Throws `FileSizeLimitExceededException` on rejection.

### `sanitizeSvg(input: ByteArray): ByteArray`

Sanitize SVG content by stripping script tags, event handlers, and embedded JavaScript. Returns sanitized bytes.

### `generateStorageKey(workspaceId, domain, contentType): String`

Generate a workspace-scoped storage key with UUID filename and MIME-derived extension.

### `generateUserStorageKey(userId, domain, contentType): String`

Generate a user-scoped storage key with UUID filename and MIME-derived extension.

---

## Gotchas

- **Extension derivation can fail** -- if Tika's MIME registry doesn't recognise the content type, `deriveExtension` returns an empty string. The file is stored without an extension but is still valid.
- **SVG sanitization changes file size** -- the sanitized output may be smaller or larger than the input. The size stored in metadata reflects the original upload size, not the sanitized size. (Sanitization happens before `persistMetadata` in [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/StorageService]], so the stored size is the sanitized size.)

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/StorageService]] -- Primary consumer
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/File Management]] -- Parent subdomain
