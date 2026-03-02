---
tags:
  - layer/controller
  - component/active
  - architecture/component
Created: 2026-02-09
Updated: 2026-03-01
Domains:
  - "[[Entities]]"
---
# EntityTypeController

Part of [[Type Definitions]]

## Purpose

REST controller for entity type schema management — exposes endpoints for type CRUD, attribute/relationship definition management, and enriched entity type queries with relationship definitions and semantic metadata.

---

## Responsibilities

- Expose entity type CRUD endpoints at `/api/v1/entity/schema/`
- Delegate all business logic to [[EntityTypeService]]
- Return enriched `EntityType` models with relationship definitions and semantic metadata bundles embedded
- Swagger documentation via `@Operation` and `@ApiResponses` annotations

---

## Dependencies

- [[EntityTypeService]] — all entity type business logic
- [[EntityTypeSemanticMetadataService]] — semantic metadata bundle construction (accessed via EntityTypeService)

## Used By

- REST API consumers — entity type management
- Frontend — entity type schema editor with optional semantic metadata display

---

## Key Logic

**Enriched entity type queries:**

The `getEntityTypesForWorkspace` and `getEntityTypeByKeyForWorkspace` endpoints delegate to `EntityTypeService.getWorkspaceEntityTypesWithIncludes()` and `getEntityTypeByKeyWithIncludes()` respectively. These methods always return `EntityType` models enriched with relationship definitions and semantic metadata — no opt-in query parameter required.

**`EntityType` model includes:**
- `relationships` — relationship definitions for the entity type
- `semantics` — `SemanticMetadataBundle` with entity type, attribute, and relationship semantic metadata
- `semanticGroup` — the entity type's `SemanticGroup` classification (replaces the previous `description` field)

---

## Public Methods

### `getEntityTypesForWorkspace(workspaceId): ResponseEntity<List<EntityType>>`

Lists all entity types for workspace, enriched with relationship definitions and semantic metadata.

### `getEntityTypeByKeyForWorkspace(workspaceId, key): ResponseEntity<EntityType>`

Gets single entity type by key, enriched with relationship definitions and semantic metadata.

### `publishEntityType(workspaceId, request): ResponseEntity<EntityType>`

Creates and publishes a new entity type with initial schema.

### `saveEntityTypeConfiguration(workspaceId, request): ResponseEntity<EntityType>`

Updates entity type metadata (name, semantic group, icon).

### `saveEntityTypeDefinition(workspaceId, request, impactConfirmed): ResponseEntity<EntityTypeImpactResponse>`

Saves attribute or relationship definitions with impact analysis.

### `removeEntityTypeDefinition(workspaceId, request, impactConfirmed): ResponseEntity<EntityTypeImpactResponse>`

Removes definitions with impact analysis.

### `deleteEntityType(workspaceId, key, impactConfirmed): ResponseEntity<EntityTypeImpactResponse>`

Deletes entity type with cascade handling.

---

## Gotchas

> [!warning] API contract change — `description` replaced with `semanticGroup`
> The `CreateEntityTypeRequest` and `UpdateEntityTypeConfigurationRequest` no longer accept a `description` field. Entity types are now classified using the `semanticGroup` enum (defaults to `UNCATEGORIZED`).

> [!warning] API contract change — GET responses no longer wrapped
> GET endpoint return types changed from `EntityTypeWithSemanticsResponse` back to `EntityType`. Relationship definitions and semantic metadata are now embedded directly in the `EntityType` model rather than wrapped in a separate response object. The `?include=semantics` query parameter has been removed.

---

## Related

- [[EntityTypeService]] — business logic
- [[EntityTypeSemanticMetadataService]] — semantic metadata operations
- [[Type Definitions]] — parent subdomain
