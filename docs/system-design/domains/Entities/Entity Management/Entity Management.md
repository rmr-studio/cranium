---
tags:
  - architecture/subdomain
  - domain/entity
Created: 2026-02-08
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
---
# Subdomain: Entity Management

## Overview

Handles the lifecycle of entity instances — creating, updating, reading, and soft-deleting entities against their type schemas. Coordinates validation, relationship hydration, and activity logging during save operations.

## Components

| Component | Purpose | Type |
| --------- | ------- | ---- |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/EntityService]] | Entity instance CRUD with validation and relationship hydration | Service |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/EntityRelationshipService]] | Instance-level relationship data management | Service |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/EntityController]] | REST API for entity operations | Controller |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/EntityRepository]] | JPA repository for entity persistence | Repository |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/EntityAttributeService]] | Normalized entity attribute persistence and batch-loading | Service |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/EntityAttributeRepository]] | JPA repository for entity_attributes table | Repository |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/EntityAttributeEntity]] | JPA entity for normalized attribute values | Entity |

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
| 2026-03-09 | Entity attributes normalized — new EntityAttributeService, EntityAttributeRepository, EntityAttributeEntity for per-attribute storage replacing JSONB payload column | Entity Attributes Normalization |
| 2026-03-14 | [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/EntityService]] publishes `EntityEvent` via `ApplicationEventPublisher` on create, update, and delete — consumed by [[riven/docs/system-design/domains/Workspaces & Users/Real-time Events/WebSocketEventListener]] for real-time WebSocket broadcasting | WebSocket Notifications |
| 2026-03-17 | [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/EntityRepository]] gains `findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn` — integration dedup query backed by partial unique index on `(workspace_id, source_integration_id, source_external_id)` | Integration Sync Persistence Foundation |
