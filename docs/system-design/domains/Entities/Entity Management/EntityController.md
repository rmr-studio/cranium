---
tags:
  - component/active
  - layer/controller
  - architecture/component
Created: 2026-02-09
Updated: 2026-03-01
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
---
# EntityController

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/Entity Management]]

## Purpose

REST controller for entity instance operations and entity relationships. Delegates all business logic to [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/EntityService]] and [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/EntityRelationshipService]].

---

## Responsibilities

- Expose entity CRUD endpoints under `/api/v1/entity/`
- Expose relationship CRUD endpoints for entity linking (both typed and fallback)
- Delegate to services and return `ResponseEntity`
- No business logic — thin controller layer

---

## Dependencies

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/EntityService]] — Entity instance CRUD operations
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/EntityRelationshipService]] — Relationship CRUD operations

## Used By

- REST API clients — Frontend and external integrations

---

## Endpoints

### Entity Operations

| Method | Path | Purpose | Delegates To |
|--------|------|---------|-------------|
| GET | `/workspace/{workspaceId}` | List entities by type IDs (query param `ids`) | `EntityService.getEntitiesByTypeIds` |
| GET | `/workspace/{workspaceId}/type/{id}` | List entities by single type ID | `EntityService.getEntitiesByTypeId` |
| POST | `/workspace/{workspaceId}/type/{entityTypeId}` | Save entity (create or update) | `EntityService.saveEntity` |
| DELETE | `/workspace/{workspaceId}` | Delete entities (list of IDs in body) | `EntityService.deleteEntities` |

### Relationship Operations

| Method | Path | Purpose | Delegates To |
|--------|------|---------|-------------|
| POST | `/workspace/{workspaceId}/entities/{entityId}/relationships` | Add relationship (typed or fallback) | `EntityRelationshipService.addRelationship` |
| GET | `/workspace/{workspaceId}/entities/{entityId}/relationships` | List relationships for entity | `EntityRelationshipService.getRelationships` |
| PUT | `/workspace/{workspaceId}/relationships/{relationshipId}` | Update relationship | `EntityRelationshipService.updateRelationship` |
| DELETE | `/workspace/{workspaceId}/relationships/{relationshipId}` | Remove relationship | `EntityRelationshipService.removeRelationship` |

---

## Gotchas

> [!warning] Relationship routes use different path patterns
> Entity-scoped relationship operations (add, list) use `/entities/{entityId}/relationships`. Relationship-level operations (update, remove) use `/relationships/{relationshipId}` directly under workspace.

> [!warning] Delete entity handles errors inline
> `deleteEntity` checks `response.error` and returns status codes manually (404 or 409) instead of using the exception hierarchy. This is a known inconsistency — should throw domain exceptions and let `@ControllerAdvice` handle it. See Known Inconsistencies in CLAUDE.md.

> [!warning] Save endpoint is create-or-update
> The POST `/workspace/{workspaceId}/type/{entityTypeId}` endpoint handles both creation and updates via `SaveEntityRequest`. It does not follow the typical POST-for-create / PUT-for-update REST convention.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/EntityService]] — Entity instance operations
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/EntityRelationshipService]] — Relationship operations
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/Entity Management]] — Parent subdomain

---

## Changelog

| Date | Change | Reason |
|------|--------|--------|
| 2026-02-09 | Stub created | Phase 5 cross-domain integration |
| 2026-03-01 | Full documentation with entity and relationship endpoints | Unified Relationship CRUD |
