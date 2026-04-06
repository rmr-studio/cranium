---
Created: 2026-03-09
Domains:
  - "[[Catalog]]"
tags:
  - architecture/subdomain
  - domain/catalog
---
# Subdomain: Template Installation

## Overview

Workspace-scoped installation orchestration for catalog templates. Reads fully-resolved template definitions from the manifest catalog, creates workspace-scoped entity types, relationships, and semantic metadata in a single atomic transaction, and tracks installation state for idempotency. Template installation is triggered from `WorkspaceController` in the [[Workspaces & Users]] domain via `POST /api/v1/workspace/{workspaceId}/install-template`. Distinct from [[Manifest Pipeline]] (global, startup-time ingestion) and [[Catalog Query]] (read-only catalog access).

## Components

| Component | Purpose | Type |
|-----------|---------|------|
| [[TemplateInstallationService]] | Orchestrates atomic template installation into workspaces | Service |
| `WorkspaceController` ([[Workspaces & Users]]) | REST API endpoint for template installation — install endpoint lives outside this subdomain | Controller (external) |
| [[WorkspaceTemplateInstallationEntity]] | JPA entity tracking template installations per workspace | Entity |
| [[WorkspaceTemplateInstallationRepository]] | Data access for workspace template installation records | Repository |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-03-09 | Initial template installation subdomain | Entity Semantics |
| 2026-03-26 | TemplateController removed — install endpoint moved to WorkspaceController. Bundle references removed. | Lifecycle Spine |
