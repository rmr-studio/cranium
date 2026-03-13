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

Workspace-scoped installation orchestration for catalog templates and bundles. Reads fully-resolved template definitions from the manifest catalog, creates workspace-scoped entity types, relationships, and semantic metadata in a single atomic transaction, and tracks installation state for idempotency. This subdomain owns the Catalog domain's only REST API surface — four endpoints for listing and installing templates and bundles. Distinct from [[Manifest Pipeline]] (global, startup-time ingestion) and [[Catalog Query]] (read-only catalog access).

## Components

| Component | Purpose | Type |
|-----------|---------|------|
| [[TemplateInstallationService]] | Orchestrates atomic template/bundle installation into workspaces | Service |
| [[TemplateController]] | REST API for listing and installing templates and bundles | Controller |
| [[WorkspaceTemplateInstallationEntity]] | JPA entity tracking template installations per workspace | Entity |
| [[WorkspaceTemplateInstallationRepository]] | Data access for workspace template installation records | Repository |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-03-09 | Initial template installation subdomain | Entity Semantics |
