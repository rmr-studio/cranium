---
tags:
  - component/active
  - layer/repository
  - architecture/component
Created: 2026-02-21
Domains:
  - "[[Entities]]"
---
# RelationshipTargetRuleRepository

Part of [[Relationships]]

## Purpose

JPA repository for `RelationshipTargetRuleEntity` persistence. Provides queries for loading target rules by definition ID and finding inverse-visible rules by target entity type.

---

## Query Methods

| Method | Purpose | Query Type |
| ------ | ------- | ---------- |
| `findByRelationshipDefinitionId(definitionId)` | All rules for a single definition | Spring Data derived |
| `findByRelationshipDefinitionIdIn(definitionIds)` | Batch load rules for multiple definitions | Spring Data derived |
| `deleteByRelationshipDefinitionId(definitionId)` | Hard-delete all rules for a definition (used during definition deletion) | Spring Data derived |
| `findByTargetEntityTypeId(entityTypeId)` | Rules where entity type is a target | Spring Data derived |
| `findByTargetEntityTypeIdIn(entityTypeIds)` | Batch version of target type lookup | Spring Data derived |
| `deleteByTargetEntityTypeId(entityTypeId)` | Hard-delete all rules targeting a specific entity type (used during entity type deletion) | Spring Data derived |

---

## Used By

| Component | Purpose |
| --------- | ------- |
| [[EntityTypeRelationshipService]] | Primary CRUD operations, target rule diff |
| [[EntityQueryService]] | Load inverse-visible rules for direction resolution |
| [[EntityContextService]] | Load inverse-visible rules for context building |

---

## Related

- [[RelationshipDefinitionRepository]] — Companion repository for definitions
- `RelationshipTargetRuleEntity` — JPA entity
- [[Relationships]] — Parent subdomain

---

## Changelog

| Date | Change | Reason |
| ---- | ------ | ------ |
| 2026-02-21 | Initial documentation | Entity Relationships overhaul |
| 2026-03-06 | Replaced inverseVisible queries with standard target type lookups (always bidirectional). Added deleteByTargetEntityTypeId. | Always Bidirectional / Target-Side Exclusions |
