---
tags:
  - layer/repository
  - component/active
  - architecture/component
Created: 2026-03-09
Updated: 2026-03-09
Domains:
  - "[[Entities]]"
---
# EntityAttributeRepository

Part of [[Entity Management]]

## Purpose

JPA repository for normalized entity attribute values stored in the `entity_attributes` table.

---

## Responsibilities

- Find attributes by entity ID (single and batch)
- Hard-delete attributes by entity ID (native SQL, bypasses soft-delete filter)
- Soft-delete attributes by entity IDs and workspace (native SQL)

---

## Dependencies

- `EntityAttributeEntity` — JPA entity for attribute rows

## Used By

- [[EntityAttributeService]] — All attribute CRUD operations

---

## Key Logic

**Derived query methods:**
- `findByEntityId(entityId)` — Standard Spring Data derived query
- `findByEntityIdIn(entityIds)` — JPQL query: `SELECT a FROM EntityAttributeEntity a WHERE a.entityId IN :entityIds`

**Native SQL methods:**
- `hardDeleteByEntityId(entityId)` — `DELETE FROM entity_attributes WHERE entity_id = :entityId`. Uses native SQL to bypass Hibernate's `@SQLRestriction("deleted = false")`, ensuring soft-deleted rows are also removed during the save flow.
- `softDeleteByEntityIds(entityIds, workspaceId)` — `UPDATE entity_attributes SET deleted = true, deleted_at = CURRENT_TIMESTAMP WHERE entity_id = ANY(:entityIds) AND workspace_id = :workspaceId AND deleted = false`. Uses native SQL for bulk update efficiency.

---

## Public Methods

### `findByEntityId(entityId): List<EntityAttributeEntity>`

Finds all non-deleted attributes for a single entity.

### `findByEntityIdIn(entityIds: Collection<UUID>): List<EntityAttributeEntity>`

Finds all non-deleted attributes for multiple entities in a single query.

### `hardDeleteByEntityId(entityId)`

Hard-deletes ALL attribute rows (including soft-deleted) for an entity. Used in the delete-all + re-insert save pattern.

### `softDeleteByEntityIds(entityIds: Array<UUID>, workspaceId: UUID)`

Soft-deletes all non-deleted attributes for the given entity IDs within a workspace.

---

## Gotchas

- **Hard-delete bypasses soft-delete:** `hardDeleteByEntityId` uses native SQL specifically to bypass `@SQLRestriction("deleted = false")`. This is intentional for the save flow — old rows (including soft-deleted) must be fully removed before re-insertion.
- **Array parameter:** `softDeleteByEntityIds` takes `Array<UUID>` (not `Collection`) because the native SQL uses PostgreSQL's `ANY(:entityIds)` syntax.

---

## Related

- [[EntityAttributeEntity]] — JPA entity
- [[EntityAttributeService]] — Service consumer
- [[Entity Management]] — Parent subdomain
