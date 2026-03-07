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

Manages both relationship definitions (type-level schema) and relationship instances (entity-level data). The subdomain uses a table-based architecture with `relationship_definitions`, `relationship_target_rules`, and `relationship_definition_exclusions` tables. All relationships are always bidirectional — inverse visibility is resolved at query time by matching target entity types against target rules or polymorphic/semantic group membership.

### Relationship Definitions

Type-level configuration stored in dedicated tables. Each definition belongs to a source entity type and defines relationship name, cardinality, polymorphism, and icon. Target rules specify which entity types can be targets, with optional cardinality overrides and per-rule inverse names.

### System Definitions

Certain relationship definitions are managed by the platform rather than by users. These are identified by a `system_type` column on `relationship_definitions`. Currently, the only system type is `CONNECTED_ENTITIES` — a polymorphic, many-to-many definition auto-created per entity type at publish time. System definitions have `protected = true` and no target rules.

### Target-Side Exclusions

Entity types can opt out of relationship definitions they would otherwise be included in (via semantic group or polymorphic matching). For explicit target rules, the target rule is deleted instead. For implicit matches (semantic group or polymorphic), an exclusion record is created in `relationship_definition_exclusions`. Exclusions are respected in both definition resolution (`getDefinitionsForEntityType`) and inverse link queries (`findInverseEntityLinksByTargetId`).

The exclusion flow is triggered through the `deleteEntityTypeDefinition` endpoint when a `DeleteRelationshipDefinitionRequest` includes a `sourceEntityTypeKey` field, signalling a target-side opt-out rather than a source-side deletion.

### Relationship Instances

Entity-level data stored in `entity_relationships` linking source entities to targets via `definition_id`. Write-time cardinality enforcement validates relationship limits at insert. Instance management is handled by [[EntityRelationshipService]] in the [[Entity Management]] subdomain.

## Components

| Component | Purpose | Type |
| --------- | ------- | ---- |
| [[EntityTypeRelationshipService]] | Relationship definition CRUD with target rule management, exclusions, and two-pass impact deletion | Service |
| [[RelationshipDefinitionRepository]] | JPA repository for relationship definition persistence | Repository |
| [[RelationshipTargetRuleRepository]] | JPA repository for target rule persistence | Repository |
| RelationshipDefinitionExclusionRepository | JPA repository for target-side exclusion persistence | Repository |

## Technical Debt

| Issue | Impact | Effort |
| ----- | ------ | ------ |
| Semantic type constraint matching stubbed | Target rules with `semanticTypeConstraint` don't match at runtime — only explicit type ID matching works. Addressed by [[Semantic Entity Groups]] which adds the `SemanticGroup` enum to entity types and enforces array-based constraint matching at link creation time. | Low |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-02-08 | Subdomain overview created | [[02-01-PLAN]] |
| 2026-02-21 | Replaced ORIGIN/REFERENCE sync with table-based architecture. Deleted DiffService and ImpactAnalysisService. Added RelationshipDefinitionRepository and RelationshipTargetRuleRepository. | Entity Relationships |
| 2026-03-01 | Added system-managed fallback definitions (CONNECTED_ENTITIES) with create/getOrCreate/getId methods on EntityTypeRelationshipService. New system_type column and unique constraint on relationship_definitions. | Entity Connections |
| 2026-03-06 | All relationships always bidirectional — removed `inverse_visible` flag. Added target-side exclusion mechanism with `relationship_definition_exclusions` table. Exclusions filter inverse definitions in resolution and native link queries. New `excludeEntityTypeFromDefinition` and `removeExclusion` methods on EntityTypeRelationshipService. | Target-Side Exclusions / Always Bidirectional |
