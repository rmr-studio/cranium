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
# SignedUrlService

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/File Management]]

## Purpose

Generates and validates HMAC-SHA256 signed tokens for secure file download URLs. Acts as the fallback authorization mechanism when the storage provider does not support native signed URLs (e.g. local filesystem). Tokens encode a storage key and expiry timestamp, signed with a configurable secret.

---

## Dependencies

- `KLogger` -- Structured logging
- [[riven/docs/system-design/domains/Storage/Provider Adapters/StorageConfigurationProperties]] -- Secret key (`signedUrl.secret`), default expiry (`signedUrl.defaultExpirySeconds`), max expiry (`signedUrl.maxExpirySeconds`)

## Used By

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/StorageService]] -- HMAC fallback URL generation via `generateDownloadUrl`, token validation via `validateToken`

---

## Key Logic

### Token format

Tokens are Base64URL-encoded strings (no padding) containing: `{storageKey}:{expiresAtEpochSecond}:{hmacSignature}`

The HMAC is computed over the payload `{storageKey}:{expiresAtEpochSecond}` using the configured secret and `HmacSHA256`.

### Token generation

1. Clamp the requested expiry to the configured maximum (default 86400 seconds / 24 hours)
2. Compute `expiresAt` as current epoch second + clamped duration
3. Build payload: `{storageKey}:{expiresAt}`
4. Compute HMAC-SHA256 signature over the payload
5. Base64URL-encode the full string: `{payload}:{signature}`

### Token validation

1. Base64URL-decode the token
2. Split on the last colon to separate payload from signature
3. Recompute HMAC over the payload
4. Compare signatures using `MessageDigest.isEqual()` for constant-time comparison (timing attack prevention)
5. Parse storage key and expiry from the payload
6. Check that current time has not exceeded `expiresAt`
7. Return `(storageKey, expiresAt)` on success, `null` on any failure

### Download URL format

Generated URLs follow the pattern: `/api/v1/storage/download/{token}`

These are relative paths, routed to [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/StorageController]]'s download endpoint.

### Expiry clamping

User-requested expiry durations are clamped to `signedUrl.maxExpirySeconds` (default 86400s). This prevents tokens with excessively long lifetimes.

---

## Public Methods

### `generateToken(storageKey, expiresIn): String`

Generate a Base64URL-encoded signed token containing the storage key and expiry.

### `validateToken(token): Pair<String, Long>?`

Validate a signed token. Returns `(storageKey, expiresAtEpochSecond)` if valid and not expired, `null` otherwise.

### `generateDownloadUrl(storageKey, expiresIn): String`

Generate a download URL path containing a signed token: `/api/v1/storage/download/{token}`.

### `getDefaultExpiry(): Duration`

Return the configured default expiry duration.

---

## Gotchas

- **Constant-time comparison is critical** -- `MessageDigest.isEqual()` prevents timing attacks that could leak signature bytes. Do not replace with `==` or `String.equals()`.
- **Tokens are path-embedded, not query parameters** -- the token is part of the URL path (`/download/{token}`), not a `?token=` query parameter. This simplifies routing but means tokens must be URL-safe (Base64URL encoding handles this).
- **Secret rotation requires coordination** -- changing `signedUrl.secret` invalidates all outstanding tokens. There is no dual-key validation for graceful rotation.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/StorageService]] -- Primary consumer for URL generation and token validation
- [[riven/docs/system-design/domains/Storage/Provider Adapters/StorageConfigurationProperties]] -- Configuration source
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/File Management/File Management]] -- Parent subdomain
