---
tags:
  - layer/controller
  - component/active
  - architecture/component
Created: 2026-03-18
Domains:
  - "[[Integrations]]"
---
# NangoWebhookController

Part of [[Webhook Authentication]]

## Purpose

Thin REST controller that receives inbound Nango webhook events and delegates processing to [[NangoWebhookService]]. Always returns 200 OK regardless of processing outcome — Nango requires a 200 response to acknowledge delivery.

---

## Responsibilities

- Expose `POST /api/v1/webhooks/nango` for Nango webhook delivery
- Deserialize `NangoWebhookPayload` from the request body
- Delegate processing to [[NangoWebhookService]]
- Always return `ResponseEntity<Void>` with 200 status

---

## Dependencies

- [[NangoWebhookService]] — Webhook processing

## Used By

- Nango (external) — Sends webhook events to this endpoint after OAuth completion and sync execution

---

## Endpoints

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/api/v1/webhooks/nango` | Handle Nango webhook events (auth and sync) | HMAC signature via [[NangoWebhookHmacFilter]] |

---

## Gotchas

> **No JWT authentication.** This endpoint is secured by HMAC signature verification via [[NangoWebhookHmacFilter]], not by JWT — Nango is an external system, not an authenticated user.

> **Always returns 200.** The service swallows all exceptions internally. If the service were to throw, the controller would still need to return 200 to prevent Nango retries.

---

## Related

- [[NangoWebhookService]] — Processing logic
- [[NangoWebhookHmacFilter]] — HMAC verification
- [[Webhook Authentication]] — Parent subdomain

---

## Changelog

### 2026-03-18

- Initial implementation — single POST endpoint for Nango webhook delivery
