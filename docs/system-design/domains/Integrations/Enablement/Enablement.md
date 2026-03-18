---
tags:
  - architecture/subdomain
  - domain/integration
Created: 2025-07-17
Updated: 2026-03-18
Domains:
  - "[[Integrations]]"
---
# Subdomain: Enablement

## Overview

Manages the integration disable lifecycle for workspaces. When disabling, soft-deletes materialized entity types, disconnects Nango, and snapshots sync state for gap recovery on re-enable. Integration enablement (connection + installation creation) has moved to the [[Webhook Authentication]] subdomain — the auth webhook handler now orchestrates connection creation, installation tracking, and template materialization after successful OAuth completion.

## Components

| Component | Purpose | Type |
| --------- | ------- | ---- |
| [[IntegrationEnablementService]] | Orchestrates the disable lifecycle — soft-deletes entity types, disconnects Nango, snapshots sync state, soft-deletes installation | Service |
| [[IntegrationController]] | REST endpoints for enable, disable, list available integrations, and workspace status | Controller |
| [[TemplateMaterializationService]] | Creates workspace-scoped entity types and relationships from catalog template definitions using deterministic UUID v3 | Service |
| [[WorkspaceIntegrationInstallationEntity]] | Tracks which integrations are enabled per workspace, with sync config and soft-delete support | Entity |
| [[WorkspaceIntegrationInstallationRepository]] | Queries active and soft-deleted installations by workspace and integration definition | Repository |

---

## Flows

| Flow | Type | Description |
|------|------|-------------|
| [[Flow - Integration Disable]] | User-facing | Workspace admin disables an integration — soft-deletes entity types, disconnects Nango, snapshots sync state |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2025-07-17 | Initial implementation — enable/disable endpoints, template materialization, installation tracking | Integration Enablement |
| 2026-03-18 | Enable lifecycle moved to [[Webhook Authentication]] subdomain. Service now handles disable only. `enableIntegration` removed. | Integration Sync Phase 2 |
