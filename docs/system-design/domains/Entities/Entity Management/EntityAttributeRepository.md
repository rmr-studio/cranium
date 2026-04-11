---
tags:
  - layer/repository
  - component/active
  - architecture/component
Created: 2026-03-09
Updated: 2026-03-09
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
---
# EntityAttributeRepository

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/Entity Management]]

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

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/EntityAttributeService]] — All attribute CRUD operations
- [[IdentityResolutionService]] — Identifier key matching (Check 2) during projection identity resolution

---

## Key Logic

**Derived query methods:**
- `findByEntityId(entityId)` — Standard Spring Data derived query
- `findByEntityIdIn(entityIds)` — JPQL query: `SELECT a FROM EntityAttributeEntity a WHERE a.entityId IN :entityIds`

**Identifier value lookup (native SQL):**

`findByIdentifierValuesForEntityType` uses a native SQL query with JSONB text extraction (`ea.value ->> 'value'`) to find attributes matching candidate identifier values. This is the only query in this repository that performs JSONB field extraction — all other queries treat the `value` column as an opaque blob. The query joins against the `entities` table to filter by entity type, since `entity_attributes` does not store `type_id` directly.

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

### `findByIdentifierValuesForEntityType(workspaceId: UUID, entityTypeId: UUID, attributeIds: Collection<UUID>, textValues: Collection<String>): List<EntityAttributeEntity>`

Batch identifier value lookup for projection identity resolution (Check 2). Native SQL query that joins `entity_attributes` with `entities` (filtered by entity type and workspace), then matches on `attribute_id IN (:attributeIds)` and `value ->> 'value' IN (:textValues)`. The JSONB `value ->> 'value'` extraction pulls the text value from the normalized attribute storage format.

---

## Gotchas

- **Hard-delete bypasses soft-delete:** `hardDeleteByEntityId` uses native SQL specifically to bypass `@SQLRestriction("deleted = false")`. This is intentional for the save flow — old rows (including soft-deleted) must be fully removed before re-insertion.
- **Array parameter:** `softDeleteByEntityIds` takes `Array<UUID>` (not `Collection`) because the native SQL uses PostgreSQL's `ANY(:entityIds)` syntax.
- **JSONB extraction in native SQL:** `findByIdentifierValuesForEntityType` uses `ea.value ->> 'value'` (PostgreSQL JSONB text extraction). This assumes the attribute value follows the normalized `{"value": "..."}` structure. If the structure changes, this query will silently return no results.
- **Joins entities table:** Unlike other methods that query `entity_attributes` alone, this method joins against `entities` to filter by `type_id` and `deleted = false`. The join is necessary because `entity_attributes` doesn't store the entity type directly.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/EntityAttributeEntity]] — JPA entity
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/EntityAttributeService]] — Service consumer
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/Entity Management]] — Parent subdomain
