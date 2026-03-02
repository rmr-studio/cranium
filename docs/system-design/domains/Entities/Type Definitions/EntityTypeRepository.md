---
tags:
  - layer/repository
  - component/active
  - architecture/component
Created: 2026-02-09
Updated: 2026-03-01
Domains:
  - "[[Entities]]"
---
# EntityTypeRepository

Part of [[Type Definitions]]

## Purpose

JPA repository for `EntityTypeEntity` persistence — provides workspace-scoped lookups, key-based retrieval, and a lightweight projection query for semantic group resolution.

---

## Responsibilities

- Persist and retrieve `EntityTypeEntity` rows from `entity_types`
- Workspace-scoped listing and key-based lookups
- Lightweight projection query for semantic group resolution (avoids loading full entity rows)

---

## Dependencies

- `EntityTypeEntity` — JPA entity mapping for `entity_types` table

## Used By

- [[EntityTypeService]] — type CRUD, workspace listing, key-based lookups
- [[EntityRelationshipService]] — `findSemanticGroupsByIds` for batch semantic group resolution during relationship validation
- [[EntityTypeRelationshipService]] — entity type lookups during relationship definition management
- [[EntityTypeAttributeService]] — entity type lookups during attribute schema operations

---

## Key Logic

**Semantic group projection:**

`findSemanticGroupsByIds` uses a JPQL projection query to return only `(id, semanticGroup)` pairs for a set of entity type IDs. This avoids loading full `EntityTypeEntity` rows (which include JSONB schema, columns, etc.) when only the semantic classification is needed. Used by `EntityRelationshipService.resolveSemanticGroups()` during relationship validation.

---

## Public Methods

### `findByworkspaceId(id): List<EntityTypeEntity>`

Lists all entity types for a workspace.

### `findByworkspaceIdAndKey(workspaceId, key): Optional<EntityTypeEntity>`

Finds a single entity type by workspace and key. Returns `Optional` — mutable pattern means only one row per workspace+key.

### `findByworkspaceIdAndKeyIn(workspaceId, keys): List<EntityTypeEntity>`

Batch lookup by workspace and multiple keys.

### `findSemanticGroupsByIds(ids): List<Array<Any>>`

JPQL projection query returning `(id, semanticGroup)` pairs without loading full entity rows. Used for batch semantic group resolution during relationship target validation.

```kotlin
@Query("SELECT e.id, e.semanticGroup FROM EntityTypeEntity e WHERE e.id IN :ids")
fun findSemanticGroupsByIds(@Param("ids") ids: Collection<UUID>): List<Array<Any>>
```

---

## Gotchas

> [!warning] Method naming convention
> `findByworkspaceId` uses lowercase 'w' in `workspaceId` — this is a Spring Data derived query naming convention quirk. The method name must match the entity field name casing for Spring Data to derive the query correctly.

---

## Related

- [[EntityTypeService]] — primary consumer for type CRUD
- [[EntityRelationshipService]] — consumes `findSemanticGroupsByIds` for semantic group resolution
- [[Type Definitions]] — parent subdomain
