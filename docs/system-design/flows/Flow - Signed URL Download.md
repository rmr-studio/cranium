---
tags:
  - flow/user-facing
  - architecture/flow
Created: 2026-03-06
Updated: 2026-03-06
Domains:
  - "[[riven/docs/system-design/domains/Storage/Storage]]"
---
# Flow: Signed URL Download

---

## Overview

Downloads a file using an HMAC-signed token that embeds the storage key and expiry timestamp. This flow crosses a security boundary -- the download endpoint is **unauthenticated**. Authorization is the signed token itself, not a JWT. This enables inline image rendering in rich text, avatar display via `<img>` tags, and link sharing without requiring the browser to attach authentication headers.

The flow has two phases:
1. **Authenticated phase** (prior flow): A user with JWT auth and workspace access calls `POST /api/v1/storage/workspace/{wId}/files/{fileId}/signed-url` to generate a time-limited signed URL. This is covered by the upload flow (which returns a signed URL) and the generate signed URL endpoint.
2. **Unauthenticated phase** (this flow): The signed URL is used to download the file without any JWT. The token is the sole authorization mechanism.

---

## Trigger

**What initiates this flow:**

|Trigger Type|Source|Condition|
|---|---|---|
|Browser Request|`<img>` tag, `<a>` download link, or direct URL navigation|Client has a signed URL token (obtained during authenticated phase)|

**Entry Point:** `StorageController.downloadFile`

---

## Preconditions

- A valid signed URL token exists (generated during the authenticated phase)
- The token has not expired (timestamp in token is in the future)
- The HMAC signature in the token matches the recomputed signature
- The referenced file exists in the storage provider

---

## Actors

|Actor|Role in Flow|
|---|---|
|Client (browser/user)|Requests file download using signed URL|
|`StorageController`|API entry point, constructs StreamingResponseBody|
|`StorageService`|Orchestrates token validation, file download, and filename lookup|
|`SignedUrlService`|Validates HMAC-SHA256 token and extracts storage key|
|`LocalStorageProvider`|Reads file bytes from local filesystem|
|`FileMetadataRepository`|Looks up original filename for Content-Disposition header|

---

## Flow Steps

### Happy Path: Download File via Signed Token

```mermaid
sequenceDiagram
    autonumber
    participant C as Client (Browser)
    participant SC as StorageController
    participant SS as StorageService
    participant SUS as SignedUrlService
    participant LSP as LocalStorageProvider
    participant Repo as FileMetadataRepository

    C->>SC: GET /api/v1/storage/download/{token}
    Note over SC: No JWT auth required<br/>(SecurityConfig permits this path)

    SC->>SS: downloadFile(token)

    SS->>SUS: validateToken(token)
    SUS->>SUS: Base64URL decode token
    SUS->>SUS: Extract storageKey, expiresAt, signature
    SUS->>SUS: Check expiresAt > now()
    SUS->>SUS: Recompute HMAC-SHA256(storageKey:expiresAt, secret)
    SUS->>SUS: MessageDigest.isEqual(expected, actual)
    SUS-->>SS: storageKey

    SS->>LSP: download(storageKey)
    LSP->>LSP: Resolve path, read file
    LSP-->>SS: InputStream + contentType + contentLength

    SS->>Repo: findByStorageKey(storageKey)
    Repo-->>SS: FileMetadataEntity (for original filename)

    SS-->>SC: DownloadResult(content, contentType, contentLength, originalFilename)

    SC->>SC: Build StreamingResponseBody
    SC->>SC: Set Content-Type header
    SC->>SC: Set Content-Disposition: attachment; filename="originalFilename"
    SC-->>C: 200 OK (streaming file bytes)
```

### Step-by-Step Breakdown

#### 1. Receive Request

- **Component:** `StorageController`
- **Action:** Receives GET request with signed token as path variable
- **Input:** Token string (Base64URL-encoded)
- **Output:** Passes token to `StorageService`
- **Security note:** This endpoint is explicitly permitted in `SecurityConfig` without JWT authentication. No `@PreAuthorize` annotation.

#### 2. Token Validation

- **Component:** `SignedUrlService`
- **Action:** Decodes token, checks expiry, verifies HMAC signature
- **Input:** Base64URL-encoded token string
- **Output:** Storage key string (if valid)
- **Validation steps:**
  1. Base64URL decode the token
  2. Split on `:` delimiter to extract `storageKey`, `expiresAt`, `signature`
  3. Parse `expiresAt` as epoch seconds, verify it is in the future
  4. Recompute `HMAC-SHA256(storageKey + ":" + expiresAt, secret)` using the configured HMAC secret
  5. Compare computed signature with token signature using `MessageDigest.isEqual()` (constant-time)
- **Side Effects:** Throws `SignedUrlExpiredException` if expired or signature mismatch

#### 3. File Download from Provider

- **Component:** `LocalStorageProvider`
- **Action:** Reads file bytes from local filesystem
- **Input:** Storage key
- **Output:** `InputStream` of file bytes, content type, content length
- **Side Effects:** File system read. Path traversal check executed before read.

#### 4. Filename Lookup

- **Component:** `FileMetadataRepository`
- **Action:** Looks up original filename from metadata table
- **Input:** Storage key
- **Output:** `FileMetadataEntity` containing `originalFilename`
- **Purpose:** The `Content-Disposition` header uses the original filename so the browser saves the file with a human-readable name, not the UUID-based storage key.

#### 5. Stream Response

- **Component:** `StorageController`
- **Action:** Constructs `StreamingResponseBody` that streams file bytes to the HTTP response
- **Output:** HTTP 200 with `Content-Type`, `Content-Disposition`, and streaming body
- **Side Effects:** File bytes streamed to client without buffering entire file in memory

---

## Failure Modes

### Failure Point: Token Expired

|Failure|Cause|Detection|User Experience|Recovery|
|---|---|---|---|---|
|Token expired|`expiresAt` timestamp is in the past|`SignedUrlService.validateToken` checks timestamp|HTTP 401 Unauthorized|Client requests a new signed URL (requires JWT auth)|

### Failure Point: Invalid Signature

|Failure|Cause|Detection|User Experience|Recovery|
|---|---|---|---|---|
|Signature mismatch|Token tampered with or wrong HMAC secret|`MessageDigest.isEqual()` returns false|HTTP 401 Unauthorized|Client requests a new signed URL|

### Failure Point: Malformed Token

|Failure|Cause|Detection|User Experience|Recovery|
|---|---|---|---|---|
|Token decode failure|Invalid Base64URL or missing `:` delimiters|Exception during decode/split|HTTP 401 Unauthorized|Client requests a new signed URL|

### Failure Point: File Not Found

|Failure|Cause|Detection|User Experience|Recovery|
|---|---|---|---|---|
|Physical file missing|File deleted from storage but token still valid|`LocalStorageProvider.download` throws `StorageNotFoundException`|HTTP 404 Not Found|File is gone — no recovery via this path|

### Failure Point: Metadata Not Found

|Failure|Cause|Detection|User Experience|Recovery|
|---|---|---|---|---|
|Metadata soft-deleted|File metadata was soft-deleted but physical file still exists|`FileMetadataRepository.findByStorageKey` returns empty|Download may proceed with fallback filename, or HTTP 404|Depends on implementation — file may still be downloadable without original filename|

---

## Security Considerations

- **No JWT authentication on this endpoint** — this is intentional and by design. The signed token IS the authorization. See [[riven/docs/system-design/decisions/ADR-006 HMAC-Signed Download Tokens for File Access]].
- **Constant-time signature comparison** — `MessageDigest.isEqual()` prevents timing attacks that could be used to forge valid signatures incrementally.
- **Separate HMAC secret** — the signing secret (`storage.signed-url.secret`) is separate from the JWT secret. Compromising one does not compromise the other.
- **Time-limited access** — tokens expire after the configured duration (default 1 hour, max 24 hours). Leaked URLs have a bounded window of exposure.
- **No per-download audit trail** — the download endpoint does not know which user is downloading (unauthenticated). The audit trail exists at token generation time (which authenticated user requested the signed URL).
- **Token cannot be revoked** — once generated, a token is valid until expiry. Mitigated by short default expiry.

---

## Related

- [[riven/docs/system-design/feature-design/2. Planned/Provider-Agnostic File Storage]] -- Feature design for the storage system
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/flows/Flow - File Upload]] -- Upload flow that generates the signed URLs consumed here
- [[riven/docs/system-design/decisions/ADR-006 HMAC-Signed Download Tokens for File Access]] -- Architecture decision for this mechanism
- [[riven/docs/system-design/decisions/ADR-005 Strategy Pattern with Conditional Bean Selection for Storage Providers]] -- Provider abstraction
- [[riven/docs/system-design/feature-design/_Sub-Domain Plans/File Storage]] -- Sub-domain plan

---

## Changelog

|Date|Change|Reason|
|---|---|---|
|2026-03-06|Initial documentation|Phase 1: Storage Foundation implementation|
