---
tags:
  - architecture/subdomain
  - domain/integration
Created: 2025-07-17
Updated: 2026-03-18
Domains:
  - "[[riven/docs/system-design/domains/Integrations/Integrations]]"
---
# Subdomain: Enablement

## Overview

Manages the integration disable lifecycle for workspaces. When disabling, soft-deletes materialized entity types, disconnects Nango, and snapshots sync state for gap recovery on re-enable. Integration enablement (connection + installation creation) has moved to the [[riven/docs/system-design/domains/Integrations/Webhook Authentication/Webhook Authentication]] subdomain — the auth webhook handler now orchestrates connection creation, installation tracking, and template materialization after successful OAuth completion.

## Components

| Component | Purpose | Type |
| --------- | ------- | ---- |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Enablement/IntegrationEnablementService]] | Orchestrates the disable lifecycle — soft-deletes entity types, disconnects Nango, snapshots sync state, soft-deletes installation | Service |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Enablement/IntegrationController]] | REST endpoints for enable, disable, list available integrations, and workspace status | Controller |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Enablement/TemplateMaterializationService]] | Creates workspace-scoped entity types and relationships from catalog template definitions using deterministic UUID v3 | Service |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Enablement/WorkspaceIntegrationInstallationEntity]] | Tracks which integrations are enabled per workspace, with sync config and soft-delete support | Entity |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Enablement/WorkspaceIntegrationInstallationRepository]] | Queries active and soft-deleted installations by workspace and integration definition | Repository |

---

## Flows

| Flow | Type | Description |
|------|------|-------------|
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Enablement/Flow - Integration Disable]] | User-facing | Workspace admin disables an integration — soft-deletes entity types, disconnects Nango, snapshots sync state |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2025-07-17 | Initial implementation — enable/disable endpoints, template materialization, installation tracking | Integration Enablement |
| 2026-03-18 | Enable lifecycle moved to [[riven/docs/system-design/domains/Integrations/Webhook Authentication/Webhook Authentication]] subdomain. Service now handles disable only. `enableIntegration` removed. | Integration Sync Phase 2 |
