---
tags:
  - architecture/subdomain
  - domain/entity
Created: 2026-02-08
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
---
# Subdomain: Type Definitions

## Overview

Manages entity type schemas — creating types, defining attributes (with property types, display config), and publishing type definitions. The schema defines what data each entity instance can hold.

## Components

| Component | Purpose | Type |
| --------- | ------- | ---- |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Type Definitions/EntityTypeService]] | Type CRUD, attribute management, definition publishing | Service |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Type Definitions/EntityTypeAttributeService]] | Attribute schema utilities (column extraction, validation) | Service |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Type Definitions/EntityTypeController]] | REST API for entity type operations | Controller |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Type Definitions/EntityTypeRepository]] | JPA repository for entity type persistence | Repository |

## Technical Debt

| Issue | Impact | Effort |
| ----- | ------ | ------ |
| None yet | - | - |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-02-08 | Subdomain overview created | [[02-01-PLAN]] |
| 2026-02-21 | EntityTypeService updated — removed DiffService and ImpactAnalysisService dependencies, added direct repository access for relationship definition management | Entity Relationships |
| 2026-03-01 | EntityTypeService and EntityTypeController updated — `description` replaced with `semanticGroup`, `?include=semantics` removed (always loaded), EntityTypeRepository documented with semantic group projection query | Semantic Entity Groups |
| 2025-07-17 | Integration enablement: readonly guards on schema mutations, batch soft-delete/restore by integration ID, integration-scoped repository queries | Integration Enablement |
| 2026-03-26 | lifecycle_domain (LifecycleDomain enum) added to EntityTypeEntity — lifecycle stage classification for entity types | Lifecycle Spine |
