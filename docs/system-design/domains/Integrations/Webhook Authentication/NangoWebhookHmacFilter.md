---
tags:
  - layer/filter
  - component/active
  - architecture/component
Created: 2026-03-18
Domains:
  - "[[riven/docs/system-design/domains/Integrations/Integrations]]"
---
# NangoWebhookHmacFilter

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Webhook Authentication/Webhook Authentication]]

## Purpose

Servlet filter that validates the HMAC-SHA256 signature of incoming Nango webhook requests. Prevents unauthorized webhook submissions by verifying the `X-Nango-Hmac-Sha256` header against the shared secret key.

---

## Responsibilities

- Read and cache the raw request body for signature computation
- Enforce a configurable maximum body size (`maxWebhookBodySize`, default 1MB) to prevent payload abuse
- Compute HMAC-SHA256 using the Nango secret key and raw body bytes
- Compare signatures using constant-time `MessageDigest.isEqual()` to prevent timing attacks
- Wrap the request in `CachedBodyHttpServletRequest` so downstream handlers can re-read the body
- Return 401 on missing or invalid signatures; return 413 on oversized payloads

---

## Dependencies

- `NangoConfigurationProperties` — Provides `secretKey` and `maxWebhookBodySize`
- `KLogger` — Warning logging for rejected requests

## Used By

- `NangoWebhookFilterConfiguration` — Registers the filter at order=1 scoped to `/api/v1/webhooks/nango`

---

## Key Logic

### Validation flow

1. Check `Content-Length` against `maxWebhookBodySize` -> 413 if exceeded
2. Read body bytes from input stream (limited to `maxWebhookBodySize`)
3. Extract `X-Nango-Hmac-Sha256` header -> 401 if missing or blank
4. Compute expected HMAC-SHA256 signature from secret key + body bytes
5. Compare expected vs provided signature using `MessageDigest.isEqual()` -> 401 if mismatch
6. Wrap request in `CachedBodyHttpServletRequest` and continue filter chain

### CachedBodyHttpServletRequest

Inner class that wraps `HttpServletRequest` with a cached byte array. Overrides `getInputStream()` and `getReader()` to return fresh streams from the cached bytes, allowing the body to be read multiple times (once by this filter, once by Spring's `@RequestBody` deserialization).

---

## Gotchas

> **Constant-time comparison is security-critical.** Using `String.equals()` instead of `MessageDigest.isEqual()` would leak signature bytes through timing side-channels.

> **Body is read once and cached.** The servlet input stream can only be read once. Without the `CachedBodyHttpServletRequest` wrapper, the controller would receive an empty body.

> **Filter is not a Spring bean.** It is instantiated manually in `NangoWebhookFilterConfiguration` and registered via `FilterRegistrationBean`, not component-scanned.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Webhook Authentication/NangoWebhookController]] — Protected by this filter
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Webhook Authentication/NangoWebhookService]] — Processes the validated webhook payload
- `NangoConfigurationProperties` — Secret key and body size configuration
- `NangoWebhookFilterConfiguration` — Filter registration bean
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Webhook Authentication/Webhook Authentication]] — Parent subdomain

---

## Changelog

### 2026-03-18

- Initial implementation — HMAC-SHA256 verification with constant-time comparison, body caching, and size limit enforcement
