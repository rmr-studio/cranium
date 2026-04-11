---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-02-19
Updated: 2026-02-19
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
---
# EntityTypeSemanticMetadataService

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Semantics/Entity Semantics]]

## Purpose

Central orchestrator for semantic metadata CRUD operations and lifecycle management — sole writer to the `entity_type_semantic_metadata` table, serving both the Knowledge API and internal lifecycle hooks from entity type mutations.

---

## Responsibilities

- Read semantic metadata by entity type, target type, and target ID
- Batch-read metadata for multiple entity types (efficient `?include=semantics` support)
- Upsert metadata with PUT semantics (full field replacement on every call)
- Bulk upsert attribute metadata to avoid N+1 patterns
- Initialize empty metadata records when entity types are published or attributes/relationships are added
- Hard-delete metadata when attributes or relationships are removed (prevents orphaned records)
- Soft-delete all metadata when entity types are deleted (preserves audit trail)
- Verify workspace ownership before mutations via EntityTypeRepository lookup

**Explicitly NOT responsible for:**
- Activity logging (per locked decision — metadata mutations are not logged)
- Restore of soft-deleted metadata (explicitly unimplemented — requires native query to bypass `@SQLRestriction`)

---

## Dependencies

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Semantics/EntityTypeSemanticMetadataRepository]] — data access for semantic metadata records
- `EntityTypeRepository` — workspace ownership verification for public-facing methods

## Used By

- [[riven/docs/system-design/domains/Knowledge/KnowledgeController]] — delegates all business logic for 8 REST endpoints
- [[riven/docs/system-design/domains/Entities/Type Definitions/EntityTypeService]] — lifecycle hooks on entity type publish and delete
- [[riven/docs/system-design/domains/Entities/Type Definitions/EntityTypeAttributeService]] — lifecycle hooks on attribute add and remove
- [[riven/docs/system-design/domains/Entities/Relationships/EntityTypeRelationshipService]] — lifecycle hooks on relationship add and remove
- [[riven/docs/system-design/domains/Entities/Type Definitions/EntityTypeController]] — `?include=semantics` feature fetches metadata in batch

---

## Key Logic

**Upsert (PUT semantics):**

1. Look up existing metadata by `(entityTypeId, targetType, targetId)`
2. If exists: update all fields (definition, classification, tags) — full replacement, not merge
3. If not exists: create new metadata record with provided fields
4. Save and return domain model via `toModel()`

**Bulk upsert (attribute metadata):**

1. Fetch all existing attribute metadata for entity type in single query
2. Associate by targetId for O(1) lookup
3. For each request entry: update existing or create new
4. Batch save all records

**Lifecycle hooks:**

| Hook | Called By | Action |
|------|----------|--------|
| `initializeForEntityType()` | EntityTypeService.publishEntityType() | Creates ENTITY_TYPE metadata + one ATTRIBUTE metadata per initial attribute |
| `initializeForTarget()` | EntityTypeAttributeService, EntityTypeRelationshipService | Creates single empty metadata record for new attribute/relationship |
| `deleteForTarget()` | EntityTypeAttributeService, EntityTypeRelationshipService | Hard-deletes metadata for removed attribute/relationship |
| `softDeleteForEntityType()` | EntityTypeService.deleteEntityType() | Soft-deletes ALL metadata for entity type (preserves for audit) |

All lifecycle hooks execute within the same `@Transactional` boundary as the triggering mutation.

---

## Public Methods

### `getForEntityType(workspaceId, entityTypeId): EntityTypeSemanticMetadata?`

Returns entity-type-level metadata (targetType = ENTITY_TYPE). Null if not yet populated.

### `getAttributeMetadata(workspaceId, entityTypeId): Map<UUID, EntityTypeSemanticMetadata>`

Returns all attribute metadata for entity type, keyed by attribute UUID (targetId).

### `getRelationshipMetadata(workspaceId, entityTypeId): Map<UUID, EntityTypeSemanticMetadata>`

Returns all relationship metadata for entity type, keyed by relationship UUID (targetId).

### `getAllMetadataForEntityType(workspaceId, entityTypeId): List<EntityTypeSemanticMetadata>`

Returns all metadata records for entity type (all target types).

### `getMetadataForEntityTypes(workspaceId, entityTypeIds): List<EntityTypeSemanticMetadata>`

Batch-reads metadata for multiple entity types. Used for `?include=semantics` on list endpoints.

### `upsertMetadata(workspaceId, entityTypeId, targetType, targetId, request): EntityTypeSemanticMetadata`

PUT semantics — creates or fully replaces metadata for a specific target. Returns updated model.

### `bulkUpsertAttributeMetadata(workspaceId, entityTypeId, requests): List<EntityTypeSemanticMetadata>`

Bulk upserts attribute metadata. Efficient batch operation avoiding N+1 queries.

### `initializeForEntityType(entityTypeId, workspaceId, attributeIds)`

Lifecycle hook: creates empty ENTITY_TYPE metadata + one ATTRIBUTE metadata per provided attribute ID.

### `initializeForTarget(entityTypeId, workspaceId, targetType, targetId)`

Lifecycle hook: creates single empty metadata record.

### `deleteForTarget(entityTypeId, targetType, targetId)`

Lifecycle hook: hard-deletes metadata for removed attribute/relationship.

### `softDeleteForEntityType(entityTypeId)`

Lifecycle hook: soft-deletes ALL metadata records for entity type.

---

## Gotchas

- **PUT semantics only:** Every upsert fully replaces all fields. Omitting `definition` clears it. There is no PATCH/merge behavior.
- **No activity logging:** Per locked decision, metadata mutations are not logged to the activity trail. This is intentional — metadata is not core entity data.
- **Restore not implemented:** `restoreForEntityType()` throws `NotImplementedError`. Restoring soft-deleted metadata requires a native query to bypass `@SQLRestriction("deleted = false")` on the entity class.
- **Non-partial UNIQUE constraint:** The `(entity_type_id, target_type, target_id)` unique constraint is NOT partial — soft-deleted rows occupy the tuple. New INSERT would fail; restore requires UPDATE. Current code is safe because new entity types get new UUIDs.
- **Workspace verification pattern:** Public read methods use `@PreAuthorize`. Lifecycle hooks do NOT verify workspace (they're called from already-authorized service methods within the same transaction).

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Semantics/EntityTypeSemanticMetadataRepository]] — data access layer
- [[riven/docs/system-design/domains/Knowledge/KnowledgeController]] — API layer
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Semantics/Entity Semantics]] — parent subdomain
