---
tags:
  - architecture/subdomain
  - domain/entity
Created: 2026-02-08
Domains:
  - "[[Entities]]"
---
# Subdomain: Type Definitions

## Overview

Manages entity type schemas — creating types, defining attributes (with property types, display config), and publishing type definitions. The schema defines what data each entity instance can hold.

## Components

| Component | Purpose | Type |
| --------- | ------- | ---- |
| [[EntityTypeService]] | Type CRUD, attribute management, definition publishing | Service |
| [[EntityTypeAttributeService]] | Attribute schema utilities (column extraction, validation) | Service |
| [[EntityTypeController]] | REST API for entity type operations | Controller |
| [[EntityTypeRepository]] | JPA repository for entity type persistence | Repository |

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
