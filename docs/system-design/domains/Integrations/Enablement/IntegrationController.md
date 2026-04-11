---
tags:
  - layer/controller
  - component/active
  - architecture/component
Created: 2025-07-17
Updated: 2026-03-18
Domains:
  - "[[riven/docs/system-design/domains/Integrations/Integrations]]"
---
# IntegrationController

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Enablement/Enablement]]

## Purpose

REST controller providing the Integrations domain's HTTP API surface — three endpoints for listing available integrations, checking workspace integration status, and disabling integrations. The `POST /enable` endpoint was removed in Phase 2 — integration enablement is now webhook-driven via [[riven/docs/system-design/domains/Integrations/Webhook Authentication/NangoWebhookController]].

---

## Responsibilities

- Expose `GET /api/v1/integrations` for listing available integration definitions
- Expose `GET /api/v1/integrations/{workspaceId}/status` for workspace integration connection status
- Expose `POST /api/v1/integrations/{workspaceId}/disable` for disabling an integration
- Delegate all business logic to [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Enablement/IntegrationEnablementService]] (disable), [[IntegrationDefinitionService]] (listing), and [[riven/docs/system-design/domains/Integrations/Connection Management/IntegrationConnectionService]] (status)

---

## Dependencies

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Enablement/IntegrationEnablementService]] — Disable orchestration
- [[IntegrationDefinitionService]] — Active integration definition listing
- [[riven/docs/system-design/domains/Integrations/Connection Management/IntegrationConnectionService]] — Workspace connection status queries

---

## Key Logic

Thin controller — delegates entirely to services. No business logic. Uses `@Valid` on the disable request body.

---

## Endpoints

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/api/v1/integrations` | List all available integration definitions | Authenticated |
| GET | `/api/v1/integrations/{workspaceId}/status` | Get integration connection status for workspace | Workspace access |
| POST | `/api/v1/integrations/{workspaceId}/disable` | Disable an integration for a workspace | Admin role |

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Enablement/IntegrationEnablementService]] — disable business logic
- [[IntegrationDefinitionService]] — integration catalog queries
- [[riven/docs/system-design/domains/Integrations/Connection Management/IntegrationConnectionService]] — connection status queries
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Enablement/Enablement]] — parent subdomain

---

## Changelog

### 2025-07-17

- Initial implementation — four endpoints for integration listing, status, enable, and disable.

### 2026-03-18

- Removed `POST /enable` endpoint — integration enablement is now webhook-driven via [[riven/docs/system-design/domains/Integrations/Webhook Authentication/NangoWebhookController]]
- Controller now has three endpoints: list, status, disable
