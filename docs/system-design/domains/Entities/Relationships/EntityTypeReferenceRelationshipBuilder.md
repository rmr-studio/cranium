---
tags:
  - component/deprecated
  - architecture/component
Created: 2026-02-09
Updated: 2026-02-21
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
---
# EntityTypeReferenceRelationshipBuilder

> [!warning] Deprecated
> This component was deleted from the codebase on 2026-02-21 as part of the Entity Relationships overhaul. REFERENCE relationships no longer exist — the ORIGIN/REFERENCE pattern has been replaced with table-based definitions where inverse visibility is a per-rule flag (`inverseVisible` on `RelationshipTargetRuleEntity`).

## Replaced By

- No direct replacement. Inverse visibility is now configured via `RelationshipTargetRuleEntity.inverseVisible` flag.
