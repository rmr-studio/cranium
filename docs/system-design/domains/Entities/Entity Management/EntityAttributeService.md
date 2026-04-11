---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-03-09
Updated: 2026-03-09
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
---
# EntityAttributeService

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/Entity Management]]

## Purpose

Service for managing normalized entity attribute values in the `entity_attributes` table. Handles CRUD operations with a delete-all + re-insert pattern for saves.

---

## Responsibilities

- Save entity attributes to normalized table using delete-all + re-insert pattern
- Soft-delete attributes when parent entities are soft-deleted
- Load attributes for a single entity
- Batch-load attributes for multiple entities (grouped by entity ID)

---

## Dependencies

- `EntityAttributeRepository` — Normalized attribute persistence
- `KLogger` — Logging

## Used By

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/EntityService]] — Attribute persistence during entity save and batch-loading during retrieval
- [[riven/docs/system-design/domains/Entities/Type Definitions/EntityTypeAttributeService]] — Loading attributes during breaking change validation
- [[riven/docs/system-design/domains/Entities/Querying/EntityQueryService]] — Hydrating query results with attribute data
- `BlockReferenceHydrationService` (Knowledge domain) — Loading attributes for block reference hydration
- `EntityContextService` (Workflows domain) — Loading attributes for workflow entity context

---

## Key Logic

**Save attributes (delete-all + re-insert):**

1. Hard-delete all existing attribute rows for the entity via native SQL (`DELETE FROM entity_attributes WHERE entity_id = :entityId`)
2. Skip insert if attributes map is empty
3. Filter out attributes with null values (only non-null values stored)
4. Create `EntityAttributeEntity` for each attribute with entityId, workspaceId, typeId, attributeId, schemaType, and value
5. Batch-save via `saveAll()`

**Why delete-all + re-insert:** Avoids merge/diff complexity. The hard-delete uses native SQL to bypass Hibernate's soft-delete filter (`@SQLRestriction`), ensuring previously soft-deleted rows are also cleaned up.

**Soft-delete attributes:**

Uses native SQL to set `deleted = true` and `deleted_at = CURRENT_TIMESTAMP` for all attributes matching entity IDs and workspace. Called when parent entities are soft-deleted.

**Batch-load attributes:**

- `getAttributes(entityId)` — Returns `Map<UUID, EntityAttributePrimitivePayload>` for one entity
- `getAttributesForEntities(entityIds)` — Returns `Map<UUID, Map<UUID, EntityAttributePrimitivePayload>>` (outer key = entity ID, inner key = attribute ID). Groups results from a single `findByEntityIdIn` query.

---

## Public Methods

### `saveAttributes(entityId, workspaceId, typeId, attributes: Map<UUID, EntityAttributePrimitivePayload>)`

Persists attributes using delete-all + re-insert. Annotated `@Transactional`. Null-valued attributes are skipped (not stored).

### `softDeleteByEntityIds(workspaceId, entityIds: Collection<UUID>)`

Soft-deletes all attributes for the given entity IDs within a workspace. Annotated `@Transactional`. No-op if entityIds is empty.

### `getAttributes(entityId): Map<UUID, EntityAttributePrimitivePayload>`

Loads all attributes for a single entity. Returns map keyed by attribute ID.

### `getAttributesForEntities(entityIds: Collection<UUID>): Map<UUID, Map<UUID, EntityAttributePrimitivePayload>>`

Batch-loads attributes for multiple entities in a single query. Returns nested map: entity ID → attribute ID → payload. Returns empty map if entityIds is empty.

---

## Gotchas

- **Hard-delete in save flow:** The save flow uses a native SQL hard-delete (bypassing `@SQLRestriction`) to clean up ALL rows including previously soft-deleted ones. This is intentional — re-inserting fresh rows is simpler than merging with existing soft-deleted state.
- **Null values not stored:** Attributes with null values are filtered out during save. IS_NULL queries in `AttributeSqlGenerator` check for absence of a row rather than a null value.
- **No workspace security annotation:** This service does not have `@PreAuthorize` — it's an internal service called by workspace-secured services (`EntityService`, `EntityQueryService`).

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/EntityAttributeRepository]] — Repository for attribute persistence
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/EntityAttributeEntity]] — JPA entity for attribute rows
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/EntityService]] — Primary consumer
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/Entity Management]] — Parent subdomain
