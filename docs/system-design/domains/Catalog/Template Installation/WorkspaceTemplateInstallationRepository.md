---
tags:
  - layer/repository
  - component/active
  - architecture/component
Created: 2026-03-09
Domains:
  - "[[riven/docs/system-design/domains/Catalog/Catalog]]"
---
# WorkspaceTemplateInstallationRepository

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Template Installation/Template Installation]]

## Purpose

Spring Data JPA repository for `WorkspaceTemplateInstallationEntity`. Provides workspace-scoped queries for checking template installation state.

---

## Query Methods

| Method | Return Type | Purpose |
|--------|-------------|---------|
| `findByWorkspaceIdAndManifestKey(workspaceId, manifestKey)` | `WorkspaceTemplateInstallationEntity?` | Check if a specific template is already installed |
| `findByWorkspaceId(workspaceId)` | `List<WorkspaceTemplateInstallationEntity>` | List all installations in a workspace |
| `findByWorkspaceIdAndManifestKeyIn(workspaceId, manifestKeys)` | `List<WorkspaceTemplateInstallationEntity>` | Batch check for multiple templates (used by bundle installation) |

---

## Used By

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Template Installation/TemplateInstallationService]] — idempotency checks and installation recording

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Template Installation/WorkspaceTemplateInstallationEntity]] — managed entity
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Template Installation/Template Installation]] — parent subdomain
