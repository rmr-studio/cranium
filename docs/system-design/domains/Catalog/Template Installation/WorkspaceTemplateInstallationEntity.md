---
tags:
  - layer/entity
  - component/active
  - architecture/component
Created: 2026-03-09
Domains:
  - "[[riven/docs/system-design/domains/Catalog/Catalog]]"
---
# WorkspaceTemplateInstallationEntity

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Template Installation/Template Installation]]

## Purpose

JPA entity tracking which templates have been installed into which workspaces. Enables idempotency (unique constraint prevents duplicate installations), template origin tracking (attribute mappings JSONB), and future uninstall support.

---

## Table

`workspace_template_installations`

## Columns

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| id | UUID | No | PK, auto-generated |
| workspace_id | UUID | No | Part of unique constraint |
| manifest_key | String | No | Part of unique constraint |
| installed_by | UUID | No | User who triggered installation |
| installed_at | ZonedDateTime | No | Defaults to now, not updatable |
| attribute_mappings | JSONB | No | Maps entity type keys to attribute key→UUID mappings |

## Constraints

- **Unique:** `(workspace_id, manifest_key)` — prevents duplicate template installations per workspace

---

## Key Details

- **Does NOT extend AuditableEntity** — system-managed installation record, not user-created data
- **Does NOT implement SoftDeletable** — follows the Catalog domain pattern of system-managed entities
- **`toModel()` method** maps to `WorkspaceTemplateInstallationModel`

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Template Installation/TemplateInstallationService]] — creates installation records
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Template Installation/WorkspaceTemplateInstallationRepository]] — data access layer
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Template Installation/Template Installation]] — parent subdomain
