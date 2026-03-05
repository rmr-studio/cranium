---
tags:
  - adr/accepted
  - architecture/decision
Created: 2026-03-06
Updated: 2026-03-06
---
# ADR-006: HMAC-Signed Download Tokens for File Access

---

## Context

Files stored in the platform need to be downloadable in contexts where JWT authentication is not available or practical:

- **Inline image rendering in rich text**: When a block contains an `<img src="...">` tag, the browser fetches the image URL directly. There is no opportunity to attach a JWT `Authorization` header to these requests.
- **Avatar display**: Workspace and user avatars are rendered as `<img>` tags across the UI. Every avatar display would require a custom fetch-with-headers implementation instead of native browser image loading.
- **Link sharing**: Users may need to share file download links with external parties who do not have platform accounts.

The download endpoint must be accessible without JWT authentication, but files must not be permanently publicly accessible. Access must be time-limited and cryptographically verifiable without requiring a database lookup on every download request.

---

## Decision

Use **HMAC-SHA256 signed tokens** for file download authorization:

1. **Token generation**: `SignedUrlService` computes `HMAC-SHA256(storageKey + ":" + expiresAt, secret)` and encodes the result as `Base64URL(storageKey:expiresAt:hmacSignature)`.

2. **Token validation**: On download request, `SignedUrlService` decodes the token, extracts `storageKey` and `expiresAt`, recomputes the HMAC, and compares using `MessageDigest.isEqual()` (constant-time comparison). If the signature matches and the timestamp has not passed, the `storageKey` is returned.

3. **Separate HMAC secret**: The signing secret (`storage.signed-url.secret`) is a dedicated configuration property, separate from the JWT secret. This decouples file access tokens from the user authentication system.

4. **SecurityConfig bypass**: The download endpoint (`GET /api/v1/storage/download/{token}`) is explicitly permitted in `SecurityConfig` without JWT authentication. The signed token itself is the authorization mechanism.

5. **Configurable expiry**: Default token expiry is 3600 seconds (1 hour). Maximum allowed expiry is 86400 seconds (24 hours). Both are configurable via `StorageConfigurationProperties`.

Token format:

```
Base64URL(storageKey:expiresAt:hmacSignature)
```

Example decoded token:

```
avatar/a1b2c3d4-e5f6-7890-abcd-ef1234567890.png:1709726400:xK9mPq2rS...
```

Download URL:

```
/api/v1/storage/download/YXZhdGFyL2ExYjJjM2Q0LWU1ZjYtNzg5MC...
```

---

## Rationale

- **No database lookup per download**: The token is self-contained — validation requires only the HMAC secret and the token contents. This is critical for performance when serving files at scale (e.g., a page with 20 inline images generates 20 concurrent download requests).
- **Time-limited access**: Tokens expire, preventing permanent unauthorized access from leaked URLs. A shared link becomes invalid after the configured expiry.
- **Constant-time comparison**: `MessageDigest.isEqual()` prevents timing attacks that could be used to forge valid signatures incrementally.
- **Separate secret limits blast radius**: Compromising the HMAC secret allows generating file download tokens but does not grant JWT-level access (user impersonation, workspace access, mutation operations). Conversely, compromising the JWT secret does not enable file downloads.
- **Two-phase access model**: Generating a signed URL requires JWT authentication + workspace access (authenticated phase). Using the signed URL to download requires only the token (unauthenticated phase). This ensures that file access is always traceable to an authorized user who generated the URL.

---

## Alternatives Considered

### Option 1: JWT-Based File Tokens

Issue short-lived JWTs specifically for file access, with the storage key encoded in the token payload. The download endpoint validates the JWT using the existing JWT infrastructure.

- **Pros:** Reuses existing JWT validation infrastructure. Token format is standardized.
- **Cons:** Couples file access to user session lifecycle — if the JWT secret rotates, all outstanding file tokens become invalid. JWT tokens are larger than HMAC tokens (header + payload + signature vs. key + expiry + signature). Requires either sharing the JWT secret (conflating auth scopes) or maintaining a second JWT key pair (similar complexity to a separate HMAC secret, but with more overhead).
- **Why rejected:** The coupling to user auth lifecycle is the primary concern. File download URLs should remain valid independently of user session changes, JWT secret rotations, or auth infrastructure modifications.

### Option 2: Database-Stored Tokens

Generate random tokens, store them in a `file_access_tokens` table with storage key, expiry, and creator, and validate by database lookup.

- **Pros:** Tokens can be revoked individually by deleting the database row. Full audit trail of who generated which tokens.
- **Cons:** Every download request requires a database query — unacceptable for inline image rendering (N images = N DB queries before any file I/O). Adds a new table and repository. Token generation requires a database write. Stale tokens must be cleaned up periodically.
- **Why rejected:** The per-download database lookup is a performance blocker. HMAC tokens achieve the same security properties (time-limited, verifiable) without any database involvement.

### Option 3: Permanent Public URLs

Store files in a publicly accessible location (e.g., public S3 bucket, public Supabase Storage bucket) and return direct URLs.

- **Pros:** Simplest implementation — no token generation, no validation, no custom download endpoint. Browsers load images natively.
- **Cons:** No access control. Anyone with the URL can access the file permanently. Files cannot be made private after sharing. Violates the principle of least privilege. Exposes internal storage structure in URLs.
- **Why rejected:** Unacceptable security posture. Files may contain sensitive data (user avatars, entity attachments). Permanent public access with no expiry and no authentication is not appropriate.

---

## Consequences

### Positive

- Download requests are validated in O(1) time with zero database queries
- File access is decoupled from user session lifecycle and JWT infrastructure
- Tokens are compact and URL-safe (Base64URL encoding)
- Constant-time signature comparison prevents timing attacks
- Two-phase access model (authenticated generation, unauthenticated download) provides auditability without impeding file serving performance

### Negative

- Tokens cannot be individually revoked — once generated, a token is valid until its expiry. Mitigated by keeping default expiry short (1 hour). If a token is compromised, the window of exposure is bounded.
- HMAC secret rotation invalidates all outstanding tokens. Mitigated by coordinating secret rotation with short token lifetimes — rotate during a period when most tokens have expired.
- No per-download audit trail — the download endpoint does not log which user downloaded a file (because it is unauthenticated). The audit trail exists at token generation time (which user requested the signed URL).

### Neutral

- `LocalStorageProvider.generateSignedUrl()` throws `UnsupportedOperationException` because HMAC-signed URL generation is handled by `SignedUrlService`, not by the storage provider. Cloud providers (Phase 2) may implement native signed URLs, in which case `SignedUrlService` can delegate to the provider's `generateSignedUrl` method instead of computing HMAC tokens.
- The token format is not standardized (not JWT, not a known spec). This is intentional — the format is minimal and purpose-built for this use case. Interoperability with external systems is not a requirement.

---

## Related

- [[Provider-Agnostic File Storage]] -- Feature design using this signed URL mechanism
- [[ADR-005 Strategy Pattern with Conditional Bean Selection for Storage Providers]] -- Storage provider abstraction that this decision complements
- [[File Storage]] -- Sub-domain plan for the Storage domain
- [[Flow - Signed URL Download]] -- Detailed flow for the download path
