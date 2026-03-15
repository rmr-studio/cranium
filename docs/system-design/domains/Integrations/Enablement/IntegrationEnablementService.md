---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2025-07-17
Domains:
  - "[[Integrations]]"
---
# IntegrationEnablementService

Part of [[Enablement]]

## Purpose

Orchestrates the enable/disable lifecycle for workspace integrations. Coordinates definition validation, Nango connection management, catalog template materialization, and installation tracking into a single transactional flow.

---

## Responsibilities

- Enable integrations for a workspace (validate, connect, materialize, track)
- Disable integrations for a workspace (soft-delete entity types, disconnect, snapshot sync state, soft-delete installation)
- Idempotent enable — return existing result if already enabled
- Restore soft-deleted installations on re-enable, preserving `lastSyncedAt` for gap recovery
- Track installation records per workspace/integration pair
- Log activity for both enable and disable operations

---

## Dependencies

- `WorkspaceIntegrationInstallationRepository` — Installation record persistence
- `IntegrationDefinitionRepository` — Integration definition lookup
- [[IntegrationConnectionService]] — Nango connection enable/disconnect
- [[TemplateMaterializationService]] — Catalog template materialization into workspace entity types
- [[EntityTypeService]] — Soft-delete entity types by integration on disable
- `AuthTokenService` — JWT user extraction
- `ActivityService` — Audit logging

## Used By

- [[IntegrationController]] — REST endpoints for enable/disable

---

## Key Logic

**Enable flow:**

1. Retrieve `userId` from JWT
2. Look up integration definition by ID (throws `NotFoundException` if missing)
3. Resolve sync configuration from request or use defaults
4. Check for existing active installation — if found, return idempotent already-enabled response with zero counts
5. Check for soft-deleted installation (for re-enable scenarios)
6. Enable Nango connection via [[IntegrationConnectionService]]
7. Materialize catalog templates into workspace entity types via [[TemplateMaterializationService]]
8. Track installation — restore soft-deleted record or create new one
9. Log enable activity
10. Return `IntegrationEnablementResponse` with materialization counts and created entity types

**Disable flow:**

1. Retrieve `userId` from JWT
2. Look up integration definition by ID
3. Find active installation — throw `NotFoundException` if integration is not enabled
4. Soft-delete all entity types and relationships created by the integration via `EntityTypeService.softDeleteByIntegration()`
5. Disconnect Nango connection gracefully (catches exceptions — disable succeeds even if Nango cleanup fails)
6. Snapshot `lastSyncedAt` on installation record for gap recovery on future re-enable
7. Soft-delete the installation record
8. Log disable activity
9. Return `IntegrationDisableResponse` with soft-delete counts

**Idempotency and restore logic:**

- If an active installation already exists for the workspace/definition pair, the enable call returns immediately with zero materialization counts — no duplicate work is performed.
- If a soft-deleted installation exists (previously disabled), the record is restored (`deleted = false`, `deletedAt = null`) rather than creating a new row. This preserves the original `lastSyncedAt` timestamp, enabling gap recovery on resume.

**Installation tracking:**

- `trackInstallation()` handles the restore-or-create decision:
  - Soft-deleted record found: clear `deleted`/`deletedAt` flags and save
  - No prior record: create new `WorkspaceIntegrationInstallationEntity` with workspace ID, definition ID, manifest key, installer, and sync config

---

## Public Methods

### `enableIntegration(workspaceId: UUID, request: EnableIntegrationRequest): IntegrationEnablementResponse`

Enables an integration for a workspace. Idempotent — returns existing result if already enabled. Restores soft-deleted installations on re-enable. Requires ADMIN workspace role.

### `disableIntegration(workspaceId: UUID, request: DisableIntegrationRequest): IntegrationDisableResponse`

Disables an integration for a workspace. Soft-deletes entity types, disconnects Nango, snapshots sync state, and soft-deletes the installation. Requires ADMIN workspace role.

---

## Gotchas

- **Admin-only operations:** Both enable and disable require `WorkspaceRoles.ADMIN` via `@PreAuthorize` — this is stricter than standard workspace member access.
- **Nango disconnect is best-effort:** `disconnectIfConnected()` catches all exceptions. The disable operation succeeds even if Nango cleanup fails, to avoid leaving the installation in an inconsistent half-disabled state.
- **Unique constraint on installation:** The `workspace_integration_installations` table has a unique constraint on `(workspace_id, integration_definition_id)`. The soft-delete restore pattern avoids violating this — only one row per pair ever exists.
- **Transactional boundary:** Both `enableIntegration` and `disableIntegration` are `@Transactional`. If template materialization or entity type soft-delete fails, the entire operation rolls back.

---

## Related

- [[IntegrationController]] — REST API surface
- [[IntegrationConnectionService]] — Nango connection lifecycle
- [[TemplateMaterializationService]] — Template-to-entity-type materialization
- [[WorkspaceIntegrationInstallationEntity]] — Installation tracking entity
- [[WorkspaceIntegrationInstallationRepository]] — Installation persistence
- [[Enablement]] — Parent subdomain

---

## Changelog

### 2025-07-17

- Initial implementation — enable/disable lifecycle with idempotent enable, soft-delete restore, Nango connection management, and template materialization.
