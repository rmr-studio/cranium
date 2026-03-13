---
Created: 2026-03-06
Domains:
  - "[[Catalog]]"
tags:
  - architecture/subdomain
  - domain/catalog
---
# Subdomain: Manifest Pipeline

## Overview

Startup-time pipeline that discovers, validates, resolves, and persists manifest definitions from the classpath into the catalog database. Triggered by `ApplicationReadyEvent`, the pipeline runs on a background thread and processes four manifest types: models, templates, integrations, and bundles. Each manifest goes through scanning (with JSON Schema validation), reference resolution (`$ref` expansion, `extends` merging, relationship normalization), and idempotent persistence (SHA-256 content hashing with delete-reinsert reconciliation). After all manifests are loaded, stale entries are reconciled and cross-domain stale flags are propagated. Bundle manifests are lightweight — they store only a list of template keys and are resolved separately from other manifest types.

## Components

| Component | Purpose | Type |
|-----------|---------|------|
| [[ManifestLoaderService]] | Orchestrates the full scan, resolve, upsert pipeline on ApplicationReadyEvent | Service |
| [[ManifestScannerService]] | Scans classpath directories for manifest JSON files with schema validation | Service |
| [[ManifestResolverService]] | Resolves $ref references, extends merges, relationship normalization | Service |
| [[ManifestUpsertService]] | Idempotent persistence with SHA-256 content hashing and delete-reinsert reconciliation | Service |
| [[ManifestReconciliationService]] | Post-load stale flag reconciliation for manifests not seen in current cycle | Service |
| [[IntegrationDefinitionStaleSyncService]] | Cross-domain stale flag propagation from catalog to integration_definitions | Service |
| [[ManifestCatalogHealthIndicator]] | Actuator health indicator reporting catalog load state | Component |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-03-06 | Initial manifest pipeline implementation | Template Manifestation |
| 2026-03-09 | Added bundle manifest scanning, resolution, and upsert across pipeline services | Entity Semantics |
