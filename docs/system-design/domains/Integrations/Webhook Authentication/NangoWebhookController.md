---
tags:
  - layer/controller
  - component/active
  - architecture/component
Created: 2026-03-18
Domains:
  - "[[riven/docs/system-design/domains/Integrations/Integrations]]"
---
# NangoWebhookController

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Webhook Authentication/Webhook Authentication]]

## Purpose

Thin REST controller that receives inbound Nango webhook events and delegates processing to [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Webhook Authentication/NangoWebhookService]]. Always returns 200 OK regardless of processing outcome — Nango requires a 200 response to acknowledge delivery.

---

## Responsibilities

- Expose `POST /api/v1/webhooks/nango` for Nango webhook delivery
- Deserialize `NangoWebhookPayload` from the request body
- Delegate processing to [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Webhook Authentication/NangoWebhookService]]
- Always return `ResponseEntity<Void>` with 200 status

---

## Dependencies

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Webhook Authentication/NangoWebhookService]] — Webhook processing

## Used By

- Nango (external) — Sends webhook events to this endpoint after OAuth completion and sync execution

---

## Endpoints

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/api/v1/webhooks/nango` | Handle Nango webhook events (auth and sync) | HMAC signature via [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Webhook Authentication/NangoWebhookHmacFilter]] |

---

## Gotchas

> **No JWT authentication.** This endpoint is secured by HMAC signature verification via [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Webhook Authentication/NangoWebhookHmacFilter]], not by JWT — Nango is an external system, not an authenticated user.

> **Always returns 200.** The service swallows all exceptions internally. If the service were to throw, the controller would still need to return 200 to prevent Nango retries.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Webhook Authentication/NangoWebhookService]] — Processing logic
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Webhook Authentication/NangoWebhookHmacFilter]] — HMAC verification
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Webhook Authentication/Webhook Authentication]] — Parent subdomain

---

## Changelog

### 2026-03-18

- Initial implementation — single POST endpoint for Nango webhook delivery
