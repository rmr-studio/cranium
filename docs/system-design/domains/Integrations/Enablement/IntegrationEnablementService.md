---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2025-07-17
Updated: 2026-03-18
Domains:
  - "[[riven/docs/system-design/domains/Integrations/Integrations]]"
---
# IntegrationEnablementService

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Enablement/Enablement]]

## Purpose

Orchestrates the disable lifecycle for workspace integrations. Soft-deletes integration entity types and relationships, disconnects the Nango connection, snapshots sync state for gap recovery, and soft-deletes the installation record. Integration enablement (connection creation, installation tracking, materialization) has moved to [[riven/docs/system-design/domains/Integrations/Webhook Authentication/NangoWebhookService]] in the [[riven/docs/system-design/domains/Integrations/Webhook Authentication/Webhook Authentication]] subdomain.

---

## Responsibilities

- Disable integrations for a workspace (soft-delete entity types, disconnect Nango, snapshot sync state, soft-delete installation)
- Transactional DB mutations separated from external Nango disconnect
- Log activity for disable operations

---

## Dependencies

- `WorkspaceIntegrationInstallationRepository` — Installation record persistence
- `IntegrationDefinitionRepository` — Integration definition lookup
- [[riven/docs/system-design/domains/Integrations/Connection Management/IntegrationConnectionService]] — Nango connection disconnect
- [[riven/docs/system-design/domains/Entities/Type Definitions/EntityTypeService]] — Soft-delete entity types by integration on disable
- `AuthTokenService` — JWT user extraction
- `ActivityService` — Audit logging

## Used By

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Enablement/IntegrationController]] — REST endpoint for disable

---

## Key Logic

**Disable flow:**

1. Retrieve `userId` from JWT
2. Look up integration definition by ID (throws `NotFoundException` if missing)
3. Find active installation — throw `NotFoundException` if integration is not enabled
4. Soft-delete all entity types and relationships created by the integration via `EntityTypeService.softDeleteByIntegration()`
5. Snapshot `lastSyncedAt` on installation record for gap recovery on future re-enable
6. Soft-delete the installation record
7. Log disable activity
8. Steps 1-7 run in a single transaction (`disableIntegrationTransactional`)
9. Disconnect Nango connection outside the transaction (best-effort, exceptions caught)
10. Return `IntegrationDisableResponse` with soft-delete counts

**Transaction separation:**

The disable flow separates DB mutations from the Nango API call:
- `disableIntegrationTransactional()` — `@Transactional`, handles all DB writes
- `disconnectIfConnected()` — runs after transaction commits, catches all exceptions

This avoids holding a DB transaction open during the potentially slow Nango disconnect call.

---

## Public Methods

### `disableIntegration(workspaceId: UUID, request: DisableIntegrationRequest): IntegrationDisableResponse`

Disables an integration for a workspace. Soft-deletes entity types, disconnects Nango, snapshots sync state, and soft-deletes the installation. Requires ADMIN workspace role.

### `disableIntegrationTransactional(workspaceId: UUID, request: DisableIntegrationRequest): DisableTransactionResult`

Transactional method performing the DB mutations for disable. Separated from the Nango disconnect to avoid holding a transaction during external API calls. Returns the definition, installation, and delete result for the caller.

---

## Gotchas

- **Admin-only operation:** Disable requires `WorkspaceRoles.ADMIN` via `@PreAuthorize` — this is stricter than standard workspace member access.
- **Nango disconnect is best-effort:** `disconnectIfConnected()` catches all exceptions. The disable operation succeeds even if Nango cleanup fails, to avoid leaving the installation in an inconsistent half-disabled state.
- **Unique constraint on installation:** The `workspace_integration_installations` table has a unique constraint on `(workspace_id, integration_definition_id)`. The soft-delete pattern means only one row per pair ever exists.
- **Transaction separation is intentional:** The `disableIntegrationTransactional` method is `@Transactional` and handles all DB writes. The Nango disconnect runs AFTER the transaction commits. This is the same pattern used in `IntegrationConnectionService.disconnectConnection`.
- **No enable functionality:** Enable has moved to [[riven/docs/system-design/domains/Integrations/Webhook Authentication/NangoWebhookService]]. This service no longer handles connection creation, installation creation, or template materialization.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Enablement/IntegrationController]] — REST API surface
- [[riven/docs/system-design/domains/Integrations/Connection Management/IntegrationConnectionService]] — Nango connection lifecycle
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Enablement/TemplateMaterializationService]] — Template-to-entity-type materialization
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Enablement/WorkspaceIntegrationInstallationEntity]] — Installation tracking entity
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Enablement/WorkspaceIntegrationInstallationRepository]] — Installation persistence
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Enablement/Enablement]] — Parent subdomain

---

## Changelog

### 2025-07-17

- Initial implementation — enable/disable lifecycle with idempotent enable, soft-delete restore, Nango connection management, and template materialization.

### 2026-03-18

- Removed `enableIntegration()` method — enable lifecycle moved to [[riven/docs/system-design/domains/Integrations/Webhook Authentication/NangoWebhookService]] in [[riven/docs/system-design/domains/Integrations/Webhook Authentication/Webhook Authentication]] subdomain
- Service now handles disable only
- Separated transactional DB mutations from Nango disconnect call
- Updated purpose, responsibilities, and documentation to reflect disable-only scope
