---
Created: 2026-03-09
Domains:
  - "[[riven/docs/system-design/domains/Catalog/Catalog]]"
tags:
  - architecture/subdomain
  - domain/catalog
---
# Subdomain: Template Installation

## Overview

Workspace-scoped installation orchestration for catalog templates. Reads fully-resolved template definitions from the manifest catalog, creates workspace-scoped entity types, relationships, and semantic metadata in a single atomic transaction, and tracks installation state for idempotency. Template installation is triggered from `WorkspaceController` in the [[riven/docs/system-design/domains/Workspaces & Users/Workspaces & Users]] domain via `POST /api/v1/workspace/{workspaceId}/install-template`. Distinct from [[riven/docs/system-design/domains/Catalog/Manifest Pipeline/Manifest Pipeline]] (global, startup-time ingestion) and [[riven/docs/system-design/domains/Catalog/Catalog Query/Catalog Query]] (read-only catalog access).

## Components

| Component | Purpose | Type |
|-----------|---------|------|
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Template Installation/TemplateInstallationService]] | Orchestrates atomic template installation into workspaces | Service |
| `WorkspaceController` ([[riven/docs/system-design/domains/Workspaces & Users/Workspaces & Users]]) | REST API endpoint for template installation — install endpoint lives outside this subdomain | Controller (external) |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Template Installation/WorkspaceTemplateInstallationEntity]] | JPA entity tracking template installations per workspace | Entity |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Template Installation/WorkspaceTemplateInstallationRepository]] | Data access for workspace template installation records | Repository |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-03-09 | Initial template installation subdomain | Entity Semantics |
| 2026-03-26 | TemplateController removed — install endpoint moved to WorkspaceController. Bundle references removed. | Lifecycle Spine |
