---
tags:
  - architecture/subdomain
  - domain/entity
Created: 2026-02-08
Domains:
  - "[[Entities]]"
---
# Subdomain: Relationships

## Overview

Manages both relationship definitions (type-level schema) and relationship instances (entity-level data). The subdomain uses a table-based architecture with `relationship_definitions` and `relationship_target_rules` tables. All relationships are always bidirectional — inverse visibility is resolved at query time by matching target entity types against explicit target rules or system-type definitions.

### Relationship Definitions

Type-level configuration stored in dedicated tables. Each definition belongs to a source entity type and defines relationship name, cardinality, and icon. Target rules specify which entity types can be targets via explicit `targetEntityTypeId` (required, non-nullable), with optional cardinality overrides and per-rule inverse names.

### System Definitions

Certain relationship definitions are managed by the platform rather than by users. These are identified by a `system_type` column on `relationship_definitions`. Currently, the only system type is `CONNECTED_ENTITIES` — a many-to-many definition auto-created per entity type at publish time. System definitions have `protected = true` and no target rules.

Polymorphism is restricted to system definitions only. The `RelationshipDefinition` model has a computed `isPolymorphic` property that returns `true` when `systemType != null`. User-created definitions always require explicit target rules with `targetEntityTypeId`.

### Target-Side Exclusions

Entity types can be excluded from relationship definitions by deleting their explicit target rule. When `excludeEntityTypeFromDefinition` is called, it finds and deletes the target rule for the entity type and soft-deletes any existing instance links between the definition and entities of that type. The two-pass impact pattern is used when existing links would be affected.

The exclusion flow is triggered through the `deleteEntityTypeDefinition` endpoint when a `DeleteRelationshipDefinitionRequest` includes a `sourceEntityTypeKey` field, signalling a target-side opt-out rather than a source-side deletion.

### Relationship Instances

Entity-level data stored in `entity_relationships` linking source entities to targets via `definition_id`. Write-time cardinality enforcement validates relationship limits at insert. Instance management is handled by [[EntityRelationshipService]] in the [[Entity Management]] subdomain.

## Components

| Component | Purpose | Type |
| --------- | ------- | ---- |
| [[EntityTypeRelationshipService]] | Relationship definition CRUD with target rule management, target-side exclusion via rule deletion, and two-pass impact deletion | Service |
| [[RelationshipDefinitionRepository]] | JPA repository for relationship definition persistence | Repository |
| [[RelationshipTargetRuleRepository]] | JPA repository for target rule persistence | Repository |

## Technical Debt

| Issue | Impact | Effort |
| ----- | ------ | ------ |
| No validation that `targetEntityTypeId` exists in the workspace before saving rules | Can create rules pointing to deleted or foreign entity types | Medium |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-02-08 | Subdomain overview created | [[02-01-PLAN]] |
| 2026-02-21 | Replaced ORIGIN/REFERENCE sync with table-based architecture. Deleted DiffService and ImpactAnalysisService. Added RelationshipDefinitionRepository and RelationshipTargetRuleRepository. | Entity Relationships |
| 2026-03-01 | Added system-managed fallback definitions (CONNECTED_ENTITIES) with create/getOrCreate/getId methods on EntityTypeRelationshipService. New system_type column and unique constraint on relationship_definitions. | Entity Connections |
| 2026-03-06 | All relationships always bidirectional — removed `inverse_visible` flag. Added target-side exclusion mechanism with `relationship_definition_exclusions` table. Exclusions filter inverse definitions in resolution and native link queries. New `excludeEntityTypeFromDefinition` and `removeExclusion` methods on EntityTypeRelationshipService. | Target-Side Exclusions / Always Bidirectional |
| 2026-03-09 | Relationship simplification — removed `allowPolymorphic` field (replaced by computed `isPolymorphic` property: `systemType != null`), removed `semanticTypeConstraint` from target rules, removed `RelationshipDefinitionExclusionEntity`/`RelationshipDefinitionExclusionRepository` and the `relationship_definition_exclusions` table. Exclusion now works by deleting the explicit target rule. `removeExclusion` method removed. `targetEntityTypeId` made non-nullable on target rules. Inverse link queries simplified (no exclusion subqueries). Repository queries `countByDefinitionIdAndTargetEntityTypeId` and `softDeleteByDefinitionIdAndTargetEntityTypeId` converted from native SQL to JPQL. | Relationship Simplification |
