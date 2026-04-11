---
tags:
  - component/deprecated
  - layer/utility
  - architecture/component
Created: 2026-02-08
Updated: 2026-02-21
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
---
# EntityTypeRelationshipDiffService

> [!warning] Deprecated
> This component was deleted from the codebase on 2026-02-21 as part of the Entity Relationships overhaul. Diff calculation logic was consolidated into [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Relationships/EntityTypeRelationshipService]] target rule diffing. The old ORIGIN/REFERENCE architecture that required complex diff objects has been replaced with direct CRUD on `relationship_definitions` and `relationship_target_rules` tables.

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Relationships/Relationships]]

## Replaced By

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Relationships/EntityTypeRelationshipService]] — `diffTargetRules()` private method handles add/remove/update of target rules
