---
tags:
  - component/deprecated
  - architecture/component
Created: 2026-02-09
Updated: 2026-02-21
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
---
# EntityRelationshipDefinition

> [!warning] Deprecated
> This model was deleted from the codebase on 2026-02-21 as part of the Entity Relationships overhaul. Replaced by `RelationshipDefinition` and `RelationshipTargetRule` domain models backed by dedicated database tables.

## Replaced By

- `RelationshipDefinition` — Domain model for relationship definitions (documented in [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Relationships/EntityTypeRelationshipService]])
- `RelationshipTargetRule` — Domain model for per-target-type configuration (documented in [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Relationships/EntityTypeRelationshipService]])
