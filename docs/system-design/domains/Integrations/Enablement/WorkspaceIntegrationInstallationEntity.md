---
tags:
  - layer/entity
  - component/active
  - architecture/component
Created: 2025-07-17
Domains:
  - "[[Integrations]]"
---
# WorkspaceIntegrationInstallationEntity

Part of [[Enablement]]

## Purpose

JPA entity tracking which integrations are enabled per workspace. Implements `SoftDeletable` so that disabling an integration sets `deleted = true` and re-enabling restores the same row, preserving sync state for gap recovery.

---

## Fields

| Column | Type | Purpose |
|--------|------|---------|
| `id` | `UUID` | Primary key, auto-generated |
| `workspace_id` | `UUID` | Owning workspace |
| `integration_definition_id` | `UUID` | Reference to the integration definition |
| `manifest_key` | `String` | Integration slug/key used for template materialization lookups |
| `installed_by` | `UUID` | User ID of the admin who enabled the integration |
| `installed_at` | `ZonedDateTime` | Timestamp of initial installation, defaults to `now()`, non-updatable |
| `sync_config` | `JSONB` | `SyncConfiguration` object — sync preferences stored as JSONB |
| `last_synced_at` | `ZonedDateTime?` | Last successful sync timestamp, snapshotted on disable for gap recovery |
| `status` | `InstallationStatus` | Installation lifecycle state — `PENDING_CONNECTION`, `ACTIVE`, or `FAILED` |
| `deleted` | `Boolean` | Soft-delete flag, filtered by `@SQLRestriction("deleted = false")` |
| `deleted_at` | `ZonedDateTime?` | Timestamp of soft-deletion |

### InstallationStatus Enum

| Value | Meaning | Can Transition To |
|-------|---------|-------------------|
| `PENDING_CONNECTION` | Installation created, waiting for OAuth connection to complete | `ACTIVE`, `FAILED` |
| `ACTIVE` | Connection established, integration fully operational | `FAILED` |
| `FAILED` | Connection or installation failed — can retry from scratch | `PENDING_CONNECTION` |

Transition validation is enforced by `InstallationStatus.canTransitionTo()` — invalid transitions are rejected before persistence.

---

## Key Design Decisions

- **Soft-deletable for re-enable:** Disabling an integration soft-deletes the installation rather than hard-deleting it. On re-enable, the same row is restored. This preserves `lastSyncedAt` so the sync engine can perform gap recovery instead of a full re-sync.
- **JSONB sync config:** `syncConfig` is stored as a `jsonb` column using Hypersistence `JsonBinaryType`. This allows the sync configuration structure to evolve without schema migrations.
- **Workspace-scoped with unique constraint:** A unique constraint on `(workspace_id, integration_definition_id)` ensures at most one installation per integration per workspace. The soft-delete restore pattern avoids violating this constraint.
- **Installation status lifecycle:** The `status` field tracks installation health independently of the Nango connection state machine. `PENDING_CONNECTION` is the initial state when an installation is created before OAuth completes. `ACTIVE` means the connection is established and the integration is ready. `FAILED` captures connection failures that require user intervention. The `canTransitionTo()` method on the enum enforces valid transitions.
- **Extends AuditableEntity:** Inherits `created_at` and `updated_at` audit columns from the base class.
- **@SQLRestriction:** All derived and JPQL queries automatically filter out soft-deleted rows. The repository uses a native query to bypass this restriction for re-enable lookups.

---

## Related

- [[WorkspaceIntegrationInstallationRepository]] — persistence layer
- [[IntegrationEnablementService]] — creates, restores, and soft-deletes installations
- [[Enablement]] — parent subdomain
