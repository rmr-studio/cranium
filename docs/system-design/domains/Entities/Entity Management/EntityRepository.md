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
- [[EntityProjectionService]] — cross-integration identity resolution during projection
- [[IdentityResolutionService]] — batch sourceExternalId match (Check 1)

---

## Key Logic

**Integration deduplication query:**

`findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn` enables the sync engine to check for existing entities before creating duplicates. It queries against the `source_integration_id` and `source_external_id` columns, which are backed by a unique partial index (`idx_entities_integration_dedup`) on `(workspace_id, source_integration_id, source_external_id) WHERE deleted = false`. This enforces at the DB level that a given integration source can only create one entity per external ID per workspace.

**Cross-integration identity resolution query:**

`findByTypeIdAndWorkspaceIdAndSourceExternalIdIn` serves a different purpose from the dedup query. While dedup matches within a single integration source (`source_integration_id`), identity resolution matches across all integration sources by entity type. This allows an entity from Zendesk to match a projected entity originally created from Stripe data, if they share the same `sourceExternalId`. The query is backed by the `idx_entities_identity_resolution` index.

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

### `findByTypeIdAndWorkspaceIdAndSourceExternalIdIn(entityTypeId: UUID, workspaceId: UUID, sourceExternalIds: Collection<String>): List<EntityEntity>`

Batch sourceExternalId match on a specific entity type within a workspace. Used by the projection pipeline for identity resolution (Check 1). Unlike the dedup query `findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn`, this query matches by entity type, not integration source — enabling cross-integration identity resolution. Backed by the `idx_entities_identity_resolution` partial index on `(entity_type_id, workspace_id, source_external_id) WHERE deleted = false`.

---

## Gotchas

- **Native SQL for soft-delete:** `deleteByIds` uses `nativeQuery = true` because JPQL does not support `RETURNING *`. The native query bypasses `@SQLRestriction` but manually filters on `deleted = false`.
- **Partial unique index for dedup:** The `idx_entities_integration_dedup` index only covers non-deleted rows (`WHERE deleted = false`). Soft-deleted entities won't conflict, allowing re-sync after entity deletion.
- **Two distinct external ID indexes:** The dedup index `idx_entities_integration_dedup` includes `source_integration_id` (same-integration matching). The identity resolution index `idx_entities_identity_resolution` omits it (cross-integration matching by entity type). Both are partial indexes filtering on `deleted = false`.

---

## Related

- [[EntityService]] — primary consumer for entity CRUD
- [[Entity Management]] — parent subdomain
