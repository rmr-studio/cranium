---
tags:
  - architecture/subdomain
  - domain/integration
Created: 2025-07-17
Domains:
  - "[[Integrations]]"
---
# Subdomain: Enablement

## Overview

Orchestrates the integration enable/disable lifecycle for workspaces. When enabling, validates the integration definition, creates a Nango connection, materializes catalog templates into workspace-scoped entity types and relationships using deterministic UUID mapping, and tracks the installation. When disabling, soft-deletes materialized entity types, disconnects Nango, and snapshots sync state for gap recovery on re-enable.

## Components

| Component | Purpose | Type |
| --------- | ------- | ---- |
| [[IntegrationEnablementService]] | Orchestrates enable/disable lifecycle — idempotency checks, connection delegation, materialization, installation tracking | Service |
| [[IntegrationController]] | REST endpoints for enable, disable, list available integrations, and workspace status | Controller |
| [[TemplateMaterializationService]] | Creates workspace-scoped entity types and relationships from catalog template definitions using deterministic UUID v3 | Service |
| [[WorkspaceIntegrationInstallationEntity]] | Tracks which integrations are enabled per workspace, with sync config and soft-delete support | Entity |
| [[WorkspaceIntegrationInstallationRepository]] | Queries active and soft-deleted installations by workspace and integration definition | Repository |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2025-07-17 | Initial implementation — enable/disable endpoints, template materialization, installation tracking | Integration Enablement |
