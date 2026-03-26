---
tags:
  - flow/background
  - architecture/flow
  - domain/integration
Created: 2025-07-17
Updated: 2026-03-18
Domains:
  - "[[Integrations]]"
---
# Flow: Integration Enable (Deprecated)

> **This flow has been replaced.** As of Phase 2 (2026-03-18), integration enablement is webhook-driven. See [[Flow - Auth Webhook]] for the current flow.

## What Changed

The original `POST /api/v1/integrations/{workspaceId}/enable` endpoint has been removed. Connections are now created exclusively by the Nango auth webhook handler after successful OAuth completion.

**Previous flow (removed):**
1. Frontend called `POST /enable` with integration definition ID and Nango connection ID
2. `IntegrationEnablementService.enableIntegration()` orchestrated connection creation, materialization, and installation tracking
3. Response returned materialization counts

**Current flow:**
1. Frontend opens Nango Connect UI, passing tags (userId, workspaceId, integrationDefinitionId)
2. Nango completes OAuth and sends a signed webhook to `POST /api/v1/webhooks/nango`
3. [[NangoWebhookService]] creates the connection, installation, and triggers materialization
4. Frontend polls `GET /api/v1/integrations/{workspaceId}/status` to detect the new connection

See [[Flow - Auth Webhook]] for the complete current flow documentation.

## Why It Changed

The webhook-driven approach eliminates client-side coordination of OAuth completion and connection creation. The frontend no longer needs to capture the Nango connection ID and pass it to a backend endpoint — Nango delivers it directly via webhook. This is more reliable and removes a class of timing-related edge cases where the frontend might call `POST /enable` before or after the OAuth flow fully completes on Nango's side.

---

## Components Involved (Current)

- [[NangoWebhookHmacFilter]]
- [[NangoWebhookController]]
- [[NangoWebhookService]]
- [[TemplateMaterializationService]]
