---
tags:
  - layer/repository
  - component/active
  - architecture/component
Created: 2026-02-09
Domains:
  - "[[Entities]]"
---
# EntityRepository

Part of [[Entity Management]]

## Purpose

JPA repository for `EntityEntity` persistence — provides workspace-scoped entity lookups, batch operations, soft-delete by IDs, and integration deduplication queries.

---

## Responsibilities

- Persist and retrieve `EntityEntity` rows from `entities`
- Workspace-scoped entity lookups by ID, type, and batch IDs
- Soft-delete entities by ID list with workspace scoping (native SQL with `RETURNING *`)
- Integration entity deduplication — find existing entities by workspace + integration source + external IDs

---

## Dependencies

- `EntityEntity` — JPA entity mapping for `entities` table

## Used By

- [[EntityService]] — entity instance CRUD
- Future integration sync services — deduplication during data ingestion

---

## Key Logic

**Integration deduplication query:**

`findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn` enables the sync engine to check for existing entities before creating duplicates. It queries against the `source_integration_id` and `source_external_id` columns, which are backed by a unique partial index (`idx_entities_integration_dedup`) on `(workspace_id, source_integration_id, source_external_id) WHERE deleted = false`. This enforces at the DB level that a given integration source can only create one entity per external ID per workspace.

```kotlin
@Query("""
    SELECT e FROM EntityEntity e
    WHERE e.workspaceId = :workspaceId
      AND e.sourceIntegrationId = :sourceIntegrationId
      AND e.sourceExternalId IN :sourceExternalIds
""")
fun findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(
    workspaceId: UUID,
    sourceIntegrationId: UUID,
    sourceExternalIds: Collection<String>
): List<EntityEntity>
```

---

## Public Methods

### `findByIdIn(ids: Collection<UUID>): List<EntityEntity>`

Batch lookup by entity IDs.

### `findByWorkspaceId(workspaceId: UUID): List<EntityEntity>`

Lists all entities for a workspace.

### `findByTypeId(typeId: UUID): List<EntityEntity>`

Lists all entities of a given type.

### `findByTypeIdIn(typeIds: List<UUID>): List<EntityEntity>`

Lists all entities matching any of the given type IDs.

### `findByIdAndWorkspaceId(id: UUID, workspaceId: UUID): Optional<EntityEntity>`

Finds a single entity by ID within a workspace scope.

### `deleteByIds(ids: Array<UUID>, workspaceId: UUID): List<EntityEntity>`

Soft-deletes entities by ID list within a workspace. Uses a native SQL query with `RETURNING *` to return the deleted entities. Sets `deleted = true` and `deleted_at = CURRENT_TIMESTAMP`.

### `findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(workspaceId: UUID, sourceIntegrationId: UUID, sourceExternalIds: Collection<String>): List<EntityEntity>`

Finds existing entities by integration source for deduplication during sync. Returns all non-deleted entities matching the workspace, integration source, and any of the given external IDs. Backed by the `idx_entities_integration_dedup` partial unique index.

---

## Gotchas

- **Native SQL for soft-delete:** `deleteByIds` uses `nativeQuery = true` because JPQL does not support `RETURNING *`. The native query bypasses `@SQLRestriction` but manually filters on `deleted = false`.
- **Partial unique index for dedup:** The `idx_entities_integration_dedup` index only covers non-deleted rows (`WHERE deleted = false`). Soft-deleted entities won't conflict, allowing re-sync after entity deletion.

---

## Related

- [[EntityService]] — primary consumer for entity CRUD
- [[Entity Management]] — parent subdomain
