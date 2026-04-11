---
tags:
  - component/deprecated
  - architecture/component
Created: 2026-02-09
Updated: 2026-02-21
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
---
# EntityTypeRelationshipDataLossWarning

> [!warning] Deprecated
> This model was deleted from the codebase on 2026-02-21 as part of the Entity Relationships overhaul. Data loss warnings are no longer a separate model — the simplified impact pattern uses `DeleteDefinitionImpact.impactedLinkCount` to communicate the scope of data loss.

## Replaced By

- `DeleteDefinitionImpact.impactedLinkCount` — Single count field in [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Relationships/EntityTypeRelationshipService]] indicating how many relationship links will be affected
