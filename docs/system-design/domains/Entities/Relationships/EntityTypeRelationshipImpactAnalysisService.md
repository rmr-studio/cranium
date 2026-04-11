---
tags:
  - component/deprecated
  - layer/service
  - architecture/component
Created: 2026-02-08
Updated: 2026-02-21
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
---
# EntityTypeRelationshipImpactAnalysisService

> [!warning] Deprecated
> This component was deleted from the codebase on 2026-02-21 as part of the Entity Relationships overhaul. Impact analysis is now a simple two-pass pattern in [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Relationships/EntityTypeRelationshipService]].`deleteRelationshipDefinition()`: count existing links, return `DeleteDefinitionImpact` if confirmation needed, otherwise execute deletion.

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Relationships/Relationships]]

## Replaced By

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Relationships/EntityTypeRelationshipService]] — `deleteRelationshipDefinition()` uses two-pass impact pattern with `DeleteDefinitionImpact` data class
