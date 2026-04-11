---
tags:
  - layer/repository
  - component/active
  - architecture/component
Created: 2026-02-09
Updated: 2026-03-01
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
---
# EntityTypeRepository

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Type Definitions/Type Definitions]]

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

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Type Definitions/EntityTypeService]] — type CRUD, workspace listing, key-based lookups
- [[riven/docs/system-design/domains/Entities/Entity Management/EntityRelationshipService]] — `findSemanticGroupsByIds` for batch semantic group resolution during relationship validation
- [[riven/docs/system-design/domains/Entities/Relationships/EntityTypeRelationshipService]] — entity type lookups during relationship definition management
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Type Definitions/EntityTypeAttributeService]] — entity type lookups during attribute schema operations
- [[riven/docs/system-design/domains/Integrations/Enablement/IntegrationEnablementService]] — integration-scoped entity type queries for lifecycle operations
- [[riven/docs/system-design/domains/Integrations/Enablement/TemplateMaterializationService]] — entity type lookups during template materialization

---

## Key Logic

**Semantic group projection:**

`findSemanticGroupsByIds` uses a JPQL projection query to return only `(id, semanticGroup)` pairs for a set of entity type IDs. This avoids loading full `EntityTypeEntity` rows (which include JSONB schema, columns, etc.) when only the semantic classification is needed. Used by `EntityRelationshipService.resolveSemanticGroups()` during relationship validation.

**Native SQL for soft-deleted integration types:**

`findSoftDeletedBySourceIntegrationIdAndWorkspaceId` uses native SQL (`nativeQuery = true`) to bypass the `@SQLRestriction("deleted = false")` filter, enabling the integration lifecycle to find and restore previously soft-deleted entity types.

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

### `findBySourceIntegrationIdAndWorkspaceId(integrationId, workspaceId): List<EntityTypeEntity>`

JPQL query returning all entity types belonging to a specific integration within a workspace. Used by integration lifecycle operations.

### `findSoftDeletedBySourceIntegrationIdAndWorkspaceId(integrationId, workspaceId): List<EntityTypeEntity>`

Native SQL query (`nativeQuery = true`) returning soft-deleted entity types for a specific integration within a workspace. Bypasses `@SQLRestriction("deleted = false")` to find previously disabled integration types for restore operations.

---

## Gotchas

> [!warning] Method naming convention
> `findByworkspaceId` uses lowercase 'w' in `workspaceId` — this is a Spring Data derived query naming convention quirk. The method name must match the entity field name casing for Spring Data to derive the query correctly.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Type Definitions/EntityTypeService]] — primary consumer for type CRUD
- [[riven/docs/system-design/domains/Entities/Entity Management/EntityRelationshipService]] — consumes `findSemanticGroupsByIds` for semantic group resolution
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Type Definitions/Type Definitions]] — parent subdomain

---

## Changelog

### 2025-07-17

- Added integration-scoped queries: `findBySourceIntegrationIdAndWorkspaceId` (JPQL) and `findSoftDeletedBySourceIntegrationIdAndWorkspaceId` (native SQL, bypasses soft-delete filter).
