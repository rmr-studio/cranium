---
tags:
  - component/deprecated
  - architecture/component
Created: 2026-02-09
Updated: 2026-02-21
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
---
# EntityTypeRelationshipImpactAnalysis

> [!warning] Deprecated
> This model was deleted from the codebase on 2026-02-21 as part of the Entity Relationships overhaul. The complex impact analysis model has been replaced by a simple `DeleteDefinitionImpact` data class in [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Relationships/EntityTypeRelationshipService]].

## Replaced By

- `DeleteDefinitionImpact(definitionId, definitionName, impactedLinkCount)` — Simple data class defined in `EntityTypeRelationshipService.kt`
