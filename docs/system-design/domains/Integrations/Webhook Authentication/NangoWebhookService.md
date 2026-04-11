---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-03-18
Domains:
  - "[[riven/docs/system-design/domains/Integrations/Integrations]]"
---
# NangoWebhookService

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Webhook Authentication/Webhook Authentication]]

## Purpose

Routes and processes inbound Nango webhook events. Auth events (successful OAuth completion) trigger connection creation, installation tracking, and template materialization. Sync events trigger integration sync workflow dispatch via Temporal. All exceptions are caught internally — the controller must always return 200 to Nango regardless of processing outcome.

---

## Responsibilities

- Route webhook payloads to the correct handler based on `type` field (`auth` or `sync`)
- Parse Nango webhook tags to extract workspace context (userId, workspaceId, integrationDefinitionId)
- Validate UUID format of all tag values
- Create or reconnect integration connections after successful OAuth
- Find or create workspace integration installations (including soft-deleted restoration)
- Trigger template materialization with error isolation — materialization failures mark installation as FAILED but preserve the CONNECTED connection
- Log connection activity for audit trail
- Queue integration sync workflows when sync events are received
- Swallow all exceptions to guarantee 200 response to Nango

---

## Dependencies

- `IntegrationConnectionRepository` — Connection persistence and lookup
- `IntegrationDefinitionRepository` — Integration definition validation
- `WorkspaceIntegrationInstallationRepository` — Installation persistence, soft-delete queries
- [[riven/docs/system-design/domains/Integrations/Enablement/TemplateMaterializationService]] — Creates workspace-scoped entity types from catalog templates
- `ActivityService` — Audit logging
- `TransactionTemplate` — Programmatic transaction management (Spring AOP cannot intercept private methods)
- `KLogger` — Structured logging

## Used By

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Webhook Authentication/NangoWebhookController]] — Delegates all webhook processing to this service

---

## Key Logic

### Webhook routing

The `handleWebhook()` entry point inspects `payload.type` and routes to the appropriate handler:

- `"auth"` -> `handleAuthEvent()` — processes OAuth completion
- `"sync"` -> `handleSyncEvent()` — queues integration sync workflow via Temporal
- Unknown -> logged and ignored

The entire routing is wrapped in a try-catch that swallows all exceptions. Nango requires a 200 response regardless of processing outcome.

### Auth event processing

The auth handler follows these steps:

1. **Parse tags** — Extracts `userId`, `workspaceId`, and `integrationDefinitionId` from `NangoWebhookTags`. Each field is validated as a UUID; missing or malformed tags cause an early return with error logging.
2. **Validate connectionId** — The `payload.connectionId` (Nango's connection identifier) must be present.
3. **Check success** — Only `success == true` payloads are processed; failures are logged and skipped.
4. **Execute in transaction** — Calls `handleAuthWebhookTransaction()` via `TransactionTemplate`:
   - Look up the integration definition (early return if not found)
   - Create or reconnect the connection via `createOrReconnectConnection()`
   - Find or create the installation via `findOrCreateInstallation()`
   - Trigger materialization with fallback error handling
   - Log connection activity

### Connection creation (createOrReconnectConnection)

Handles 4 scenarios based on existing connection state:

| Existing State | Action |
|---|---|
| No existing connection | Create new with status `CONNECTED` |
| `DISCONNECTED` or `FAILED` | Reconnect: set status to `CONNECTED`, update `nangoConnectionId` |
| `CONNECTED` | Idempotent: update `nangoConnectionId` if changed, otherwise no-op |
| Any other state | Warning logged, `nangoConnectionId` updated, existing status preserved |

### Installation tracking (findOrCreateInstallation)

Three-tier lookup with restore logic:

1. **Active installation exists** — Set status to `ACTIVE` and return
2. **Soft-deleted installation exists** — Restore by clearing `deleted`/`deletedAt`, set status to `ACTIVE`
3. **No installation** — Create new with `ACTIVE` status

### Materialization with fallback

`triggerMaterializationWithFallback()` wraps materialization in a try-catch:

- **Success:** Logs success message
- **Failure:** Sets installation status to `FAILED`, saves, and does NOT rethrow. The transaction commits with the FAILED installation but the CONNECTED connection is preserved. This design allows the connection to remain usable — the user can retry materialization or the system can attempt recovery.

### Tag mapping

Nango webhook tags map to Riven concepts through repurposed fields:

| Nango Field | JSON Key | Riven Concept |
|---|---|---|
| `endUserId` | `end_user_id` | userId (UUID) |
| `organizationId` | `organization_id` | workspaceId (UUID) |
| `endUserEmail` | `end_user_email` | integrationDefinitionId (UUID) |

The `endUserEmail` field is pragmatically repurposed because Nango only provides three tag fields.

---

## Public Methods

### `handleWebhook(payload: NangoWebhookPayload)`

Routes an inbound Nango webhook payload to the correct handler. Swallows all exceptions to guarantee the caller (controller) can return 200. Called by [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Webhook Authentication/NangoWebhookController]].

---

## Gotchas

> **All exceptions are swallowed.** The service MUST NOT throw — Nango interprets non-200 responses as delivery failures and retries, which could cause duplicate processing.

> **Programmatic transactions required.** Spring AOP proxies cannot intercept private method calls within the same bean, so `TransactionTemplate` is used instead of `@Transactional` on the private handler method.

> **Materialization failure does not roll back connection.** By design, a FAILED installation with a CONNECTED connection is a valid state. The connection represents successful OAuth; the installation failure means workspace setup is incomplete.

> **Tag validation is defensive.** Missing tags, malformed UUIDs, or null fields cause early returns with error logging — not exceptions. This prevents partial processing from leaving orphaned state.

> **Sync events now dispatch Temporal workflows.** The `handleSyncEvent()` method queues an `IntegrationSyncWorkflow` for the connection, triggering the full fetch-process-project pipeline.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Webhook Authentication/NangoWebhookController]] — REST endpoint
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Webhook Authentication/NangoWebhookHmacFilter]] — HMAC signature verification
- [[riven/docs/system-design/domains/Integrations/Connection Management/IntegrationConnectionService]] — Also manages connections; `createOrReconnect` is the internal (webhook-only) creation path
- [[riven/docs/system-design/domains/Integrations/Enablement/TemplateMaterializationService]] — Materialization engine
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Webhook Authentication/Webhook Authentication]] — Parent subdomain

---

## Changelog

### 2026-03-18

- Initial implementation — auth event handling with connection creation, installation tracking, and materialization with error isolation
- Sync event stub for Phase 3

### 2026-04-11

- `handleSyncEvent()` now dispatches Temporal integration sync workflow instead of logging (Phase 3 stub removed)
