---
tags:
  - layer/repository
  - component/active
  - architecture/component
Created: 2026-02-19
Updated: 2026-02-19
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
---
# EntityTypeSemanticMetadataRepository

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Semantics/Entity Semantics]]

## Purpose

Data access layer for semantic metadata records, providing derived queries for common lookups and custom JPQL for hard-delete and cascade soft-delete operations.

---

## Responsibilities

- Standard JPA CRUD operations via `JpaRepository<EntityTypeSemanticMetadataEntity, UUID>`
- Derived queries filtered by entity type, target type, and target ID combinations
- Batch queries for multiple entity types (supports `?include=semantics` feature)
- Custom JPQL hard-delete for orphaned attribute/relationship metadata
- Custom JPQL cascade soft-delete for entity type deletion

---

## Dependencies

- `EntityTypeSemanticMetadataEntity` — JPA entity with `@SQLRestriction("deleted = false")`

## Used By

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Semantics/EntityTypeSemanticMetadataService]] — sole consumer of this repository

---

## Key Logic

**Derived queries (auto-filtered by `@SQLRestriction`):**

| Query | Purpose |
|-------|---------|
| `findByEntityTypeIdAndTargetTypeAndTargetId()` | Exact lookup for specific target's metadata |
| `findByEntityTypeIdIn()` | Batch metadata for multiple entity types |
| `findByEntityTypeIdAndTargetType()` | All metadata of one target type for an entity type |
| `findByEntityTypeId()` | All metadata for an entity type (all target types) |

**Custom JPQL mutations:**

| Method | SQL Operation | Purpose |
|--------|--------------|---------|
| `hardDeleteByTarget(entityTypeId, targetType, targetId)` | `DELETE FROM ... WHERE` | Permanently removes metadata for deleted attributes/relationships |
| `softDeleteByEntityTypeId(entityTypeId)` | `UPDATE ... SET deleted=true, deletedAt=now()` | Cascade soft-delete all metadata when entity type is deleted |

---

## Gotchas

- **@SQLRestriction filters all derived queries:** Soft-deleted records are invisible to all `findBy*` methods. This is why `restoreForEntityType()` in the service is unimplemented — restoring requires a native query to bypass the restriction.
- **Hard-delete vs soft-delete:** Attribute/relationship metadata uses hard-delete (prevents orphans). Entity-type-level cascade uses soft-delete (preserves audit trail).
- **Non-partial UNIQUE constraint:** `(entity_type_id, target_type, target_id)` is non-partial. Soft-deleted rows still occupy the unique tuple. INSERT-based restore would fail — must use UPDATE.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Semantics/EntityTypeSemanticMetadataService]] — sole consumer
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Semantics/Entity Semantics]] — parent subdomain
