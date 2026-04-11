---
tags:
  - layer/entity
  - component/active
  - architecture/component
Created: 2026-03-29
Domains:
  - "[[riven/docs/system-design/domains/Integrations/Integrations]]"
---

# ProjectionRuleEntity

Part of [[Entity Projection]]

## Purpose

JPA entity mapping source integration entity types to target core lifecycle entity types. System-managed — does NOT extend `AuditableEntity` or implement `SoftDeletable`. Installed automatically from core model `projectionAccepts` rules during template materialization.

## Fields

| Column | Type | Purpose |
|--------|------|---------|
| `id` | UUID | Primary key, auto-generated |
| `workspace_id` | UUID? | NULL for system rules; set for workspace-specific rules |
| `source_entity_type_id` | UUID | FK to the integration entity type |
| `target_entity_type_id` | UUID | FK to the core lifecycle entity type |
| `relationship_def_id` | UUID? | FK to the relationship definition linking source to target |
| `auto_create` | Boolean | Whether to auto-create core entities for unmatched integration entities (default true) |
| `created_at` | ZonedDateTime | Immutable creation timestamp |

**Table name:** `entity_type_projection_rules`

**Unique constraint:** `uq_projection_rule_source_target` on `(workspace_id, source_entity_type_id, target_entity_type_id)`

## Key Design Decisions

- **System-managed:** No audit columns, no soft-delete. Installed during materialization and CASCADE-deleted when entity types are removed.
- **Nullable workspace_id:** NULL indicates a system-level rule (from core model manifests). Future support for workspace-specific custom rules.
- **Relationship reference:** The `relationship_def_id` links to the "source-data" relationship created alongside the rule during materialization.

## Dependencies

None (JPA entity).

## Used By

| Consumer | Context |
|----------|---------|
| [[ProjectionRuleRepository]] | Persistence layer |
| [[EntityProjectionService]] | Loads rules to drive projection routing |

## Methods

### `toModel(): ProjectionRule`

Converts to domain model. Uses `requireNotNull(id) { "ProjectionRuleEntity.id must not be null when mapping to model" }`.

## Related

- [[ProjectionRuleRepository]]
- [[EntityProjectionService]]
- [[riven/docs/system-design/domains/Integrations/Enablement/TemplateMaterializationService]]
- [[Entity Projection]]
