---
tags:
  - component/active
  - layer/repository
  - architecture/component
Created: 2026-02-21
Domains:
  - "[[Entities]]"
---
# RelationshipDefinitionRepository

Part of [[Relationships]]

## Purpose

JPA repository for `RelationshipDefinitionEntity` persistence. Provides workspace-scoped queries for relationship definition lookup by source entity type.

---

## Query Methods

| Method | Purpose | Query Type |
| ------ | ------- | ---------- |
| `findByWorkspaceIdAndSourceEntityTypeId(workspaceId, sourceEntityTypeId)` | All definitions where entity type is source | Spring Data derived |
| `findByIdAndWorkspaceId(id, workspaceId)` | Single definition by ID within workspace | Spring Data derived |
| `findByWorkspaceIdAndSourceEntityTypeIdIn(workspaceId, entityTypeIds)` | Batch load definitions for multiple source entity types | JPQL @Query |
| `findBySourceEntityTypeIdAndSystemType(sourceEntityTypeId, systemType)` | Lookup fallback definition by entity type and system type | Spring Data derived |
| `findDefinitionsWithRulesForEntityTypes(workspaceId, entityTypeIds)` | Batch load definitions with LEFT JOIN on target rules for multiple entity types (forward by source, inverse by target rule) | JPQL @Query |
| `findByWorkspaceIdAndSourceEntityTypeIdAndName(workspaceId, sourceEntityTypeId, name)` | Lookup definition by name within a source entity type | Spring Data derived |
| `findSoftDeletedByWorkspaceIdAndSourceEntityTypeIdAndName(workspaceId, sourceEntityTypeId, name)` | Lookup soft-deleted definition by name (bypasses @SQLRestriction) | Native @Query |

---

## Used By

| Component | Purpose |
| --------- | ------- |
| [[EntityTypeRelationshipService]] | Primary CRUD operations |
| [[EntityTypeService]] | Check definition existence during save, load definitions during delete |
| [[EntityQueryService]] | Load definitions for filter validation and direction resolution |
| [[EntityContextService]] | Load definitions for context building |
| [[EntityRelationshipService]] | Validate connection belongs to CONNECTED_ENTITIES definition |

---

## Related

- [[RelationshipTargetRuleRepository]] — Companion repository for target rules
- `RelationshipDefinitionEntity` — JPA entity
- [[Relationships]] — Parent subdomain

---

## Changelog

| Date | Change | Reason |
| ---- | ------ | ------ |
| 2026-02-21 | Initial documentation | Entity Relationships overhaul |
| 2026-03-01 | Added findBySourceEntityTypeIdAndSystemType query; EntityRelationshipService as consumer | Entity Connections |
| 2026-03-06 | Added findDefinitionsWithRulesForEntityTypes batch query | Inverse Definition Resolution |
| 2026-03-09 | Removed exclusion filtering from findDefinitionsWithRulesForEntityTypes (exclusion mechanism removed). Added findByWorkspaceIdAndSourceEntityTypeIdAndName and findSoftDeletedByWorkspaceIdAndSourceEntityTypeIdAndName queries | Relationship Simplification |
