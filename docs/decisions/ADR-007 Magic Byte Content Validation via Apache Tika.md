---
tags:
  - adr/accepted
  - architecture/decision
Created: 2026-03-06
Updated: 2026-05-13
---

# ADR-007: Magic Byte Content Validation via Apache Tika

> **Still in force after the 2026-05 architecture pivot** — see [[architecture-pivot]] §Reuse / Rework / Replace / Delete. The file pipeline is not in the v1 surface but is harmless to keep; this decision is unchanged.

---

## Context

The platform accepts file uploads from users and must validate that uploaded files are genuinely the content type they claim to be. This is a security requirement — without server-side content type verification:

- A user could upload a malicious executable renamed to `photo.png`, bypassing client-side extension checks
- A user could upload an HTML file as an "image", which when served could execute JavaScript in the browser (XSS)
- Client-provided `Content-Type` headers are trivially spoofable and cannot be trusted

The content validation system must:

1. **Detect the actual content type** of uploaded bytes, independent of filename extension or client-provided headers
2. **Enforce per-domain allowlists** — different storage domains (AVATAR, future: ATTACHMENT, DOCUMENT) allow different content types
3. **Enforce per-domain file size limits** — avatars have different size constraints than document attachments
4. **Handle SVG specially** — SVG files are XML-based and can contain embedded `<script>` tags, event handlers, and other XSS vectors that execute when the SVG is rendered in a browser

---

## Decision

Use **Apache Tika** for magic byte-based content type detection as the primary content type source:

1. **`ContentValidationService`** uses Tika's `detect(InputStream)` method to determine the MIME type from the file's magic bytes (the first few bytes that identify the file format).

2. **File extension is used only as a hint** when magic bytes are ambiguous (e.g., some text-based formats). Tika accepts an optional filename parameter that influences detection for ambiguous cases, but magic bytes take precedence.

3. **Domain-level allowlists** are defined in the `StorageDomain` enum. Each domain declares its `allowedContentTypes` set and `maxFileSize`. `ContentValidationService` validates the Tika-detected type against the domain's allowlist and the file size against the domain's limit.

4. **SVG sanitization** is performed via `io.github.borewit:sanitize` after content type detection. If the detected type is `image/svg+xml`, the SVG content is sanitized to remove `<script>` tags, inline event handlers (`onload`, `onclick`, etc.), and other XSS vectors before the file is stored.

5. **Storage key generation** uses the Tika-detected extension (including leading dot, e.g., `.png`) to generate UUID-based storage keys: `{workspaceId}/{domain}/{uuid}.{ext}`.

---

## Rationale

- **Magic bytes are not spoofable** — renaming a file does not change its magic bytes. A JPEG file starts with `FF D8 FF` regardless of its extension. Tika reads these bytes and identifies the format correctly even if the file is named `virus.exe.png`.
- **Tika maintains a comprehensive detection database** — Tika supports over 1,000 MIME types with regularly updated detection patterns. Maintaining a custom magic byte table would be a significant ongoing effort with lower coverage.
- **Domain-level validation is extensible** — adding a new domain (e.g., `DOCUMENT` with PDF/DOCX support) requires only adding a new enum value to `StorageDomain` with its allowlist and size limit. No code changes to `ContentValidationService`.
- **SVG is uniquely dangerous** — unlike binary image formats (PNG, JPEG, WebP), SVG is XML and can contain executable code. Serving an unsanitized SVG with `Content-Type: image/svg+xml` is equivalent to serving an HTML page. The `io.github.borewit:sanitize` library is purpose-built for SVG sanitization and handles the known XSS vectors.
- **Tika extension with leading dot** simplifies key generation — `ContentValidationService` can append the extension directly to the UUID without string manipulation.

---

## Alternatives Considered

### Option 1: Extension-Based Detection Only

Determine content type from the file's extension (e.g., `.png` maps to `image/png`). Validate against the domain allowlist using this mapped type.

- **Pros:** Zero dependencies. Trivial to implement. Fast (no byte reading).
- **Cons:** Trivially spoofable — renaming `malware.exe` to `malware.png` bypasses all validation. Relies on the client providing a correct filename, which is untrusted input.
- **Why rejected:** Provides no actual security. Extension-based detection is a convenience for the client, not a security mechanism.

### Option 2: Custom Magic Byte Tables

Maintain a hand-written map of magic byte signatures to MIME types for the specific content types the platform supports (JPEG, PNG, WebP, GIF, SVG).

- **Pros:** No external dependency. Full control over detection logic. Minimal code for a small set of supported types.
- **Cons:** Must be manually maintained as new content types are added. Edge cases in magic byte detection are numerous (e.g., JPEG variants, PNG with different chunk orderings, WebP container format). SVG detection via magic bytes is unreliable because SVG files are XML and may not start with a distinctive byte sequence. Risk of incomplete detection leading to false rejections or false acceptances.
- **Why rejected:** Reinventing a wheel that Tika already maintains with far greater coverage and correctness. The maintenance burden grows with each new domain and content type.

### Option 3: Client-Provided Content-Type Header

Trust the `Content-Type` header from the multipart upload request.

- **Pros:** Zero implementation effort. The browser sets this header based on the operating system's file type association.
- **Cons:** The header is set by the client and is untrusted. A malicious client can set any Content-Type. Browser detection is itself extension-based, so this is no better than Option 1.
- **Why rejected:** Untrusted input. Same fundamental problem as extension-based detection.

---

## Consequences

### Positive

- Uploaded files are validated against their actual content, not their claimed identity — prevents content type spoofing attacks
- SVG files are sanitized before storage, eliminating stored XSS vectors
- Adding new content types or domains requires only enum configuration, not code changes to the detection logic
- Tika's detection database is maintained by the Apache project and covers edge cases that custom implementations would miss

### Negative

- Apache Tika adds a dependency (~2MB for tika-core). The platform already uses a carefully scoped dependency set. Mitigated by using only `tika-core` (detection only), not `tika-parsers` (the much larger content extraction library).
- Tika detection reads bytes from the input stream, which means the stream must be buffered or re-readable. `ContentValidationService` handles this by reading bytes into a byte array for detection before passing to the storage provider.
- SVG sanitization via `io.github.borewit:sanitize` adds another dependency. Mitigated by the security necessity — serving unsanitized SVGs is not acceptable.

### Neutral

- Content type detection adds a small amount of latency to uploads (Tika reads the first few KB of the file). This is negligible compared to the I/O cost of storing the file.
- The detected content type is stored in `file_metadata.content_type` and used for the `Content-Type` response header on downloads. This means the download response type is based on actual file content, not the original upload header.

---

## Related

- [[cranium/docs/system-design/feature-design/2. Planned/Provider-Agnostic File Storage]] -- Feature design using this content validation approach
- [[2. Areas/2.1 Startup & Content/Cranium/2. System Design/decisions/ADR-005 Strategy Pattern with Conditional Bean Selection for Storage Providers]] -- Storage provider abstraction
- [[cranium/docs/system-design/feature-design/_Sub-Domain Plans/File Storage]] -- Sub-domain plan for the Storage domain
