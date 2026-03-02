---
tags:
  - architecture/subdomain
  - domain/entity
Created: 2026-02-08
Domains:
  - "[[Entities]]"
---
# Subdomain: Entity Management

## Overview

Handles the lifecycle of entity instances — creating, updating, reading, and soft-deleting entities against their type schemas. Coordinates validation, relationship hydration, and activity logging during save operations.

## Components

| Component | Purpose | Type |
| --------- | ------- | ---- |
| [[EntityService]] | Entity instance CRUD with validation and relationship hydration | Service |
| [[EntityRelationshipService]] | Instance-level relationship data management | Service |
| [[EntityController]] | REST API for entity operations | Controller |
| [[EntityRepository]] | JPA repository for entity persistence | Repository |

## Technical Debt

| Issue | Impact | Effort |
| ----- | ------ | ------ |
| None yet | - | - |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-02-08 | Subdomain overview created | [[02-01-PLAN]] |
| 2026-02-21 | EntityRelationshipService rewritten — removed bidirectional sync, added write-time cardinality enforcement and target type validation | Entity Relationships |
| 2026-03-01 | EntityRelationshipService updated — semantic group matching implemented (was stubbed), target-side cardinality batch-optimized, new EntityTypeRepository dependency | Semantic Entity Groups |
| 2026-03-01 | EntityRelationshipService expanded with unified relationship CRUD (addRelationship, getRelationships, updateRelationship, removeRelationship); EntityController upgraded with 4 relationship endpoints under `/relationships` | Unified Relationship CRUD |
