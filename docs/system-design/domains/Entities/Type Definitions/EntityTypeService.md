---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-02-08
Updated: 2026-02-21
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
---
# EntityTypeService

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Type Definitions/Type Definitions]]

## Purpose

Primary entry point for entity type lifecycle operations including creation, attribute/relationship management, definition publishing, and impact-aware deletion.

---

## Responsibilities

- Create and publish new entity types with initial schema
- Update entity type metadata (name, semantic group, icon)
- Add/modify/remove attribute and relationship definitions
- Reorder entity type columns (attributes and relationships)
- Perform impact analysis before breaking changes
- Delete entity types with relationship cascade handling
- Retrieve entity types by ID, key, or workspace
- Batch soft-delete and restore entity types by integration ID for integration lifecycle management
- Enforce readonly guards on integration-sourced entity types
- Log activity for audit trail

---

## Dependencies

- `EntityTypeRepository` — Type persistence
- [[riven/docs/system-design/domains/Entities/Relationships/EntityTypeRelationshipService]] — Relationship definition management
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Type Definitions/EntityTypeAttributeService]] — Attribute schema operations
- `RelationshipDefinitionRepository` — Direct repository access for checking definition existence during save/delete
- `EntityRelationshipRepository` — Counts active links for impact analysis during delete
- `AuthTokenService` — JWT user extraction
- `ActivityService` — Audit logging
- [[riven/docs/system-design/domains/Entities/Entity Semantics/EntityTypeSemanticMetadataService]] — Lifecycle hooks for semantic metadata initialization and cascade deletion
- [[riven/docs/system-design/domains/Integrations/Enablement/IntegrationEnablementService]] — (via reverse dependency) calls `softDeleteByIntegration` and `restoreByIntegration` for integration lifecycle operations

## Used By

- Entity Type API controllers — REST endpoints for type management
- [[riven/docs/system-design/domains/Entities/Entity Management/EntityService]] — Fetches type definitions for instance validation

---

## Key Logic

**Publish entity type:**

1. Create `EntityTypeEntity` with initial schema containing single identifier attribute
2. Identifier is UUID-based, required, unique, protected STRING field
3. Initialize empty relationships list and single-column ordering
4. Log CREATE activity
5. Create fallback CONNECTED_ENTITIES definition via `entityTypeRelationshipService.createFallbackDefinition(workspaceId, savedId)` — ensures every published entity type has a system-managed connection definition
6. Initialize semantic metadata via [[riven/docs/system-design/domains/Entities/Entity Semantics/EntityTypeSemanticMetadataService]].initializeForEntityType() — creates ENTITY_TYPE metadata record plus one ATTRIBUTE metadata record per initial attribute

**Save definition (attribute or relationship):**

1. Parse request to determine attribute vs. relationship
2. For attributes: delegate to [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Type Definitions/EntityTypeAttributeService]] (validates breaking changes)
3. For relationships: check if definition exists via `definitionRepository.findByIdAndWorkspaceId()`
   - Not found → call `entityTypeRelationshipService.createRelationshipDefinition()`
   - Found → call `entityTypeRelationshipService.updateRelationshipDefinition()`
4. Update column ordering (insert at specified index)
5. Return `EntityTypeImpactResponse` with updated types

No diff calculation or impact analysis is performed on save — the create/update dispatch is a straight delegation.

**Remove definition:**

- For attributes: delegate to [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Type Definitions/EntityTypeAttributeService]]
- For relationships: delegate to `entityTypeRelationshipService.deleteRelationshipDefinition()`, which returns a `DeleteDefinitionImpact?`
  - If impact returned: wrap in `EntityTypeImpactResponse` and return (two-pass confirmation flow)
  - Otherwise: proceed with column removal and return updated types
- Relationship removals specify `DeleteAction` (DELETE_RELATIONSHIP or REMOVE_ENTITY_TYPE)

**Delete entity type:**

1. Fetch all relationship definitions for the entity type via `definitionRepository.findByWorkspaceIdAndSourceEntityTypeId()`
2. If `!impactConfirmed` and any definition has active links (counted via `entityRelationshipRepository`): return `EntityTypeImpactResponse.impact` without deleting
3. If confirmed or no links: iterate definitions and call `entityTypeRelationshipService.deleteRelationshipDefinition(impactConfirmed=true)` for each
4. Soft-delete semantic metadata via `semanticMetadataService.softDeleteForEntityType()`
5. Soft-delete the entity type itself

**Semantic metadata lifecycle:**

- On publish: `semanticMetadataService.initializeForEntityType(entityTypeId, workspaceId, attributeIds)` — creates empty metadata records
- On delete: `semanticMetadataService.softDeleteForEntityType(entityTypeId)` — cascade soft-delete preserves metadata for audit

**Enriched entity type queries:**

Methods `getWorkspaceEntityTypesWithIncludes()` and `getEntityTypeByKeyWithIncludes()` always load relationship definitions and semantic metadata alongside entity types:
1. Fetch entity types for workspace, then filter out integration-sourced types unless `includeInternal` is true
2. Batch-fetch relationship definitions via `entityTypeRelationshipService.getDefinitionsForEntityTypes()`
3. Batch-fetch semantic metadata via `semanticMetadataService.getMetadataForEntityTypes()`
4. Build `SemanticMetadataBundle` per entity type
5. Return enriched `EntityType` models with `relationships` and `semantics` fields populated

**Column assembly for readonly types:**

`assembleColumns()` accepts a `readonly` parameter (default `false`). When `readonly = true` (integration-sourced entity types), relationship columns are excluded — the `relationshipIds` set is empty. This prevents integration entity types from displaying inverse/relationship columns in the UI, since their relationships are system-managed and not user-editable.

**Integration lifecycle operations:**

- `softDeleteByIntegration(workspaceId, integrationId)`: Finds all entity types belonging to the integration via `findBySourceIntegrationIdAndWorkspaceId`. Batch-fetches their relationship definitions, soft-deletes the relationships first, then soft-deletes the entity types with activity logging (reason: `integration_disabled`). Returns `IntegrationSoftDeleteResult` with counts of entity types and relationships deleted.
- `restoreByIntegration(workspaceId, integrationId)`: Finds soft-deleted entity types via native SQL query `findSoftDeletedBySourceIntegrationIdAndWorkspaceId` (bypasses `@SQLRestriction`). Clears `deleted` flag and `deletedAt` timestamp on each. Returns count of restored entity types.

**Readonly guards:**

The following methods check `type.readonly` before proceeding and throw `IllegalArgumentException` for integration-sourced entity types:
- `updateEntityTypeConfiguration` — readonly types only allow column configuration changes (name, icon, semantic group changes are silently skipped)
- `saveEntityTypeDefinition` — blocks all attribute and relationship definition modifications
- `removeEntityTypeDefinition` — blocks all definition removals
- `deleteEntityType` — blocks deletion of readonly types

**Column reordering:**

- Maintains ordered list of `EntityTypeAttributeColumn` (key + type)
- When adding: inserts at specified index (or end if null)
- When moving: removes from old position, inserts at new position
- Coerces indices to valid bounds

---

## Public Methods

### `publishEntityType(workspaceId, request): EntityType`

Creates new entity type with initial identifier attribute. Returns published type model.

### `updateEntityTypeConfiguration(workspaceId, type): EntityType`

Updates metadata only (name, semantic group, icon, columns). Does NOT modify schema or relationships.

### `saveEntityTypeDefinition(workspaceId, request, impactConfirmed): EntityTypeImpactResponse`

Unified save for attributes and relationships. Performs impact analysis if not confirmed. Returns impacts OR updated types.

### `removeEntityTypeDefinition(workspaceId, request, impactConfirmed): EntityTypeImpactResponse`

Removes attribute or relationship definition with impact analysis flow.

### `reorderEntityTypeColumns(order, key, prev, new): List<EntityTypeAttributeColumn>`

Pure function for column reordering. Handles both insertions (prev=null) and moves.

### `deleteEntityType(workspaceId, key, impactConfirmed): EntityTypeImpactResponse`

Deletes entity type with cascade to all relationships. Impact analysis required if type has relationships.

### `getWorkspaceEntityTypes(workspaceId): List<EntityType>`

Retrieves all entity types for workspace.

### `getByKey(key, workspaceId): EntityTypeEntity`

Retrieves entity type by unique key within workspace.

### `getById(id): EntityTypeEntity`

Retrieves entity type by ID (no workspace scope — used for system operations).

### `getByIds(ids): List<EntityTypeEntity>`

Batch retrieval by IDs.

### `getWorkspaceEntityTypesWithIncludes(workspaceId: UUID, includeInternal: Boolean = false): List<EntityType>`

Retrieves all entity types for workspace, enriched with relationship definitions and semantic metadata bundles. By default, filters out integration-sourced entity types (`sourceIntegrationId != null`). Pass `includeInternal = true` to include them.

### `getEntityTypeByKeyWithIncludes(workspaceId, key): EntityType`

Retrieves a single entity type by key, enriched with relationship definitions and semantic metadata bundle.

### `softDeleteByIntegration(workspaceId: UUID, integrationId: UUID): IntegrationSoftDeleteResult`

Soft-deletes all entity types and their relationship definitions for the given integration. Returns counts of deleted entity types and relationships.

### `restoreByIntegration(workspaceId: UUID, integrationId: UUID): Int`

Restores previously soft-deleted entity types for the given integration. Returns count of restored types.

### `buildSemanticBundle(metadataList): SemanticMetadataBundle`

Groups metadata records by targetType into structured bundle (entity type + attribute map + relationship map).

---

## Gotchas

- **Mutable types:** Unlike BlockTypeService (versioned), entity types update in place. Breaking changes validated against existing entities.
- **Impact confirmation required:** Destructive operations (delete definition, delete entity type) use a two-pass pattern — first call returns impact analysis, second call with `impactConfirmed=true` executes. Save operations do NOT use this pattern; create/update dispatch is immediate.
- **Workspace security:** All public methods use `@PreAuthorize` for access control.
- **Protected types:** `protected=false` on user-created types. System types (if any) cannot be modified/deleted.
- **Relationship impact vs. attribute impact:** Relationship definition deletes can affect OTHER entity types (bidirectional). Attribute changes only affect the same type's instances.
- **Cascade complexity:** Deleting entity type with relationships requires cascade handling via [[riven/docs/system-design/domains/Entities/Relationships/EntityTypeRelationshipService]]. Link counts are checked directly via `EntityRelationshipRepository` before proceeding.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Type Definitions/EntityTypeAttributeService]] — Attribute schema operations
- [[riven/docs/system-design/domains/Entities/Relationships/EntityTypeRelationshipService]] — Relationship definition management
- [[riven/docs/system-design/domains/Entities/Entity Management/EntityService]] — Consumes type definitions for instance validation
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Type Definitions/Type Definitions]] — Parent subdomain

---

## Changelog

### 2026-02-21

- Removed `EntityTypeRelationshipDiffService` and `EntityTypeRelationshipImpactAnalysisService` from dependencies — these services no longer exist.
- Added `RelationshipDefinitionRepository` and `EntityRelationshipRepository` as direct dependencies for save/delete dispatch and link-count checks.
- `saveEntityTypeDefinition`: Relationship path now does a simple create/update dispatch via `definitionRepository.findByIdAndWorkspaceId()` — no diff calculation or impact analysis on save.
- `removeEntityTypeDefinition`: Relationship path now delegates entirely to `entityTypeRelationshipService.deleteRelationshipDefinition()`, which owns the two-pass impact pattern.
- `deleteEntityType`: Cascade logic rewritten — fetches definitions via `definitionRepository`, counts links via `entityRelationshipRepository`, delegates each definition delete to `entityTypeRelationshipService.deleteRelationshipDefinition(impactConfirmed=true)`.

### 2026-03-01

- `description` field replaced with `semanticGroup: SemanticGroup` enum on entity type model and all request DTOs
- `?include=semantics` query parameter removed — semantic metadata and relationship definitions are now always loaded by `getWorkspaceEntityTypesWithIncludes()` and `getEntityTypeByKeyWithIncludes()`
- Method signatures simplified: `include: List<String>` parameter removed from both enriched query methods
- Return type changed from `EntityTypeWithSemanticsResponse` back to `EntityType` (semantics embedded in model)
- Publish flow now creates CONNECTED_ENTITIES fallback definition for each new entity type via `entityTypeRelationshipService.createFallbackDefinition()`.

### 2026-03-29

- `getWorkspaceEntityTypesWithIncludes()` gains `includeInternal` parameter — filters integration-sourced types by default
- `assembleColumns()` gains `readonly` parameter — skips relationship columns for readonly (integration-sourced) entity types

### 2025-07-17

- Added `softDeleteByIntegration()` and `restoreByIntegration()` for integration lifecycle. Added readonly guards on schema mutation methods.
