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
# QueryFilterValidator

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/Querying]]

## Purpose

Eager pre-validation pass over QueryFilter trees to collect all relationship validation errors before SQL generation.

---

## Responsibilities

- Walk entire filter tree recursively
- Validate relationship IDs exist in entity type definitions
- Enforce relationship traversal depth limits
- Recurse into TargetTypeMatches branches
- Collect all errors in single pass (aggregate errors vs. fail-fast)
- Return error list for caller to throw if non-empty

---

## Dependencies

- Entity models: `QueryFilter`, `RelationshipDefinition`, `RelationshipFilter`
- Exception types: `InvalidRelationshipReferenceException`, `RelationshipDepthExceededException`

## Used By

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/EntityQueryService]] — Pre-validate filters before assembly

---

## Key Logic

**Validation strategy:**

Aggregate all errors in single tree walk rather than surfacing one at a time. Gives callers complete diagnostic context upfront.

**Validations performed:**

- **Relationship existence:** Checks relationship ID exists in definitions map (depth 0 only)
- **Depth enforcement:** Tracks relationship nesting depth, throws if exceeds maxDepth
- **Branch recursion:** Validates nested filters in TargetTypeMatches branches
- **IsRelatedTo pass-through:** `QueryFilter.IsRelatedTo` requires no validation — it has no relationship definition ID to check and no depth implications

**Depth tracking:**

- Increments only on `QueryFilter.Relationship` nodes
- `And`/`Or` nodes do NOT increment depth (those are logical combinations, not traversals)
- `relationshipDepth` parameter propagates through recursive calls

**Phase 5 limitation:**

Only validates root-level relationship IDs (depth 0). Nested relationships reference target entity type definitions, which aren't loaded. Cross-type validation deferred to future phase.

---

## Public Methods

### `validate(filter, relationshipDefinitions, maxDepth): List<QueryFilterException>`

Walks filter tree and returns list of validation errors. Empty list means valid.

`relationshipDefinitions` maps relationship definition IDs to `RelationshipDefinition` objects (the domain model backed by `relationship_definitions` table, replacing the old `EntityRelationshipDefinition` JSONB model).

Callers should wrap non-empty results in `QueryValidationException` if throwing.

---

## Gotchas

- **Returns errors, doesn't throw:** Caller decides whether to throw `QueryValidationException(errors)`
- **Aggregate vs. fail-fast:** Collects all errors before returning (better UX for developers)
- **Depth 0 only:** Only validates root-level relationship IDs (nested relationships skip ID lookup)
- **Not responsible for attribute validation:** EntityQueryService handles attribute ID checks separately

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/EntityQueryService]] — Uses validator in pipeline
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/AttributeFilterVisitor]] — Enforces depth during SQL generation (redundant safety)
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/Querying]] — Parent subdomain

---

## Changelog

| Date | Change | Reason |
| ---- | ------ | ------ |
| 2026-02-21 | Updated to use RelationshipDefinition model (replaces EntityRelationshipDefinition from JSONB schema) | Entity Relationships |
| 2026-03-01 | Added IsRelatedTo no-op validation branch — no relationship definition ID to validate | Entity Connections |
