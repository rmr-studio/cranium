---
tags:
  - layer/repository
  - component/active
  - architecture/component
Created: 2025-07-17
Domains:
  - "[[Integrations]]"
---
# WorkspaceIntegrationInstallationRepository

Part of [[Enablement]]

## Purpose

JPA repository for `WorkspaceIntegrationInstallationEntity` persistence — provides workspace-scoped lookups for active installations and a native query to bypass `@SQLRestriction` for soft-deleted record recovery.

---

## Responsibilities

- Persist and retrieve `WorkspaceIntegrationInstallationEntity` rows from `workspace_integration_installations`
- Workspace-scoped lookups by integration definition ID
- List all active installations for a workspace
- Bypass soft-delete filter to find previously disabled installations for re-enable

---

## Dependencies

- `WorkspaceIntegrationInstallationEntity` — JPA entity mapping for `workspace_integration_installations` table

## Used By

- [[IntegrationEnablementService]] — installation CRUD during enable/disable lifecycle

---

## Key Logic

**Soft-delete bypass for re-enable:**

`findSoftDeletedByWorkspaceIdAndIntegrationDefinitionId` uses a native SQL query (`nativeQuery = true`) to bypass the entity's `@SQLRestriction("deleted = false")`. This allows the enablement service to find a previously soft-deleted installation and restore it rather than creating a new row, preserving `lastSyncedAt` for gap recovery.

The native query explicitly filters for `deleted = true` and uses `LIMIT 1` since the unique constraint guarantees at most one row per workspace/definition pair.

---

## Public Methods

### `findByWorkspaceIdAndIntegrationDefinitionId(workspaceId: UUID, integrationDefinitionId: UUID): WorkspaceIntegrationInstallationEntity?`

Finds an active (non-deleted) installation for a specific workspace and integration definition. Returns `null` if no active installation exists. Uses Spring Data derived query — `@SQLRestriction` automatically filters soft-deleted rows.

### `findByWorkspaceId(workspaceId: UUID): List<WorkspaceIntegrationInstallationEntity>`

Lists all active installations for a workspace.

### `findSoftDeletedByWorkspaceIdAndIntegrationDefinitionId(workspaceId: UUID, integrationDefinitionId: UUID): WorkspaceIntegrationInstallationEntity?`

Finds a soft-deleted installation for re-enable scenarios. Native SQL query that bypasses `@SQLRestriction` to query `deleted = true` rows. Returns `null` if no soft-deleted installation exists.

```kotlin
@Query(
    value = """
        SELECT * FROM workspace_integration_installations
        WHERE workspace_id = :workspaceId
          AND integration_definition_id = :integrationDefinitionId
          AND deleted = true
        LIMIT 1
    """,
    nativeQuery = true
)
fun findSoftDeletedByWorkspaceIdAndIntegrationDefinitionId(
    @Param("workspaceId") workspaceId: UUID,
    @Param("integrationDefinitionId") integrationDefinitionId: UUID
): WorkspaceIntegrationInstallationEntity?
```

---

## Gotchas

- **Native SQL for soft-delete bypass:** `findSoftDeletedByWorkspaceIdAndIntegrationDefinitionId` must use `nativeQuery = true` because JPQL queries always have `@SQLRestriction("deleted = false")` appended by Hibernate. There is no way to bypass this with JPQL or derived queries.
- **Unique constraint scope includes deleted rows:** The unique constraint on `(workspace_id, integration_definition_id)` applies to all rows regardless of `deleted` status. This is why the restore pattern (un-delete existing row) is used instead of creating a new row on re-enable.

---

## Related

- [[WorkspaceIntegrationInstallationEntity]] — JPA entity
- [[IntegrationEnablementService]] — primary consumer for installation lifecycle
- [[Enablement]] — parent subdomain
