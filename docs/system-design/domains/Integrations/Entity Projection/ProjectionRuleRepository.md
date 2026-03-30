---
tags:
  - layer/repository
  - component/active
  - architecture/component
Created: 2026-03-29
Domains:
  - "[[Integrations]]"
---

# ProjectionRuleRepository

Part of [[Entity Projection]]

## Purpose

JPA repository for projection rule queries — lookups by source entity type with optional workspace scoping.

## Dependencies

| Dependency | Purpose |
|------------|---------|
| [[ProjectionRuleEntity]] | JPA entity |

## Used By

| Consumer | Context |
|----------|---------|
| [[EntityProjectionService]] | Loads rules by source type and workspace during projection |
| [[TemplateMaterializationService]] | Checks existence before creating new rules during enablement |

## Public Methods

### `findBySourceEntityTypeId(sourceEntityTypeId: UUID): List<ProjectionRuleEntity>`

All rules for a source entity type (no workspace filter). JPQL: `SELECT r FROM ProjectionRuleEntity r WHERE r.sourceEntityTypeId = :sourceEntityTypeId`.

### `findBySourceEntityTypeIdAndWorkspace(sourceEntityTypeId: UUID, workspaceId: UUID): List<ProjectionRuleEntity>`

Rules matching the source type that are either system-level (`workspaceId IS NULL`) or workspace-specific (`workspaceId = :workspaceId`). Used by the projection pipeline at runtime.

### `existsByWorkspaceAndSourceAndTarget(workspaceId: UUID, sourceEntityTypeId: UUID, targetEntityTypeId: UUID): Boolean`

Idempotency check during rule installation. Returns `COUNT(r) > 0` for matching workspace + source + target triple.

## Gotchas

- **System + workspace rules.** `findBySourceEntityTypeIdAndWorkspace` returns both `workspaceId IS NULL` (system) and `workspaceId = :workspaceId` (workspace-specific) rules. This is intentional — system rules apply to all workspaces.

## Related

- [[ProjectionRuleEntity]]
- [[EntityProjectionService]]
- [[TemplateMaterializationService]]
- [[Entity Projection]]
