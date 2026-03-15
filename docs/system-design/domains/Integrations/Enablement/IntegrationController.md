---
tags:
  - layer/controller
  - component/active
  - architecture/component
Created: 2025-07-17
Domains:
  - "[[Integrations]]"
---
# IntegrationController

Part of [[Enablement]]

## Purpose

REST controller providing the Integrations domain's HTTP API surface — four endpoints for listing available integrations, checking workspace integration status, and enabling/disabling integrations.

---

## Responsibilities

- Expose `GET /api/v1/integrations` for listing available integration definitions
- Expose `GET /api/v1/integrations/{workspaceId}/status` for workspace integration connection status
- Expose `POST /api/v1/integrations/{workspaceId}/enable` for enabling an integration
- Expose `POST /api/v1/integrations/{workspaceId}/disable` for disabling an integration
- Delegate all business logic to [[IntegrationEnablementService]] (enable/disable), [[IntegrationDefinitionService]] (listing), and [[IntegrationConnectionService]] (status)

---

## Dependencies

- [[IntegrationEnablementService]] — Enable/disable orchestration
- [[IntegrationDefinitionService]] — Active integration definition listing
- [[IntegrationConnectionService]] — Workspace connection status queries

---

## Key Logic

Thin controller — delegates entirely to services. No business logic. Uses `@Valid` on enable and disable request bodies.

---

## Endpoints

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/api/v1/integrations` | List all available integration definitions | Authenticated |
| GET | `/api/v1/integrations/{workspaceId}/status` | Get integration connection status for workspace | Workspace access |
| POST | `/api/v1/integrations/{workspaceId}/enable` | Enable an integration for a workspace | Admin role |
| POST | `/api/v1/integrations/{workspaceId}/disable` | Disable an integration for a workspace | Admin role |

---

## Related

- [[IntegrationEnablementService]] — enable/disable business logic
- [[IntegrationDefinitionService]] — integration catalog queries
- [[IntegrationConnectionService]] — connection status queries
- [[Enablement]] — parent subdomain

---

## Changelog

### 2025-07-17

- Initial implementation — four endpoints for integration listing, status, enable, and disable.
