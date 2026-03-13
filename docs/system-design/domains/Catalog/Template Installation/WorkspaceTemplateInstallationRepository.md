---
tags:
  - layer/repository
  - component/active
  - architecture/component
Created: 2026-03-09
Domains:
  - "[[Catalog]]"
---
# WorkspaceTemplateInstallationRepository

Part of [[Template Installation]]

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

- [[TemplateInstallationService]] — idempotency checks and installation recording

---

## Related

- [[WorkspaceTemplateInstallationEntity]] — managed entity
- [[Template Installation]] — parent subdomain
