---
Created: 2026-03-06
Domains:
  - "[[riven/docs/system-design/domains/Catalog/Catalog]]"
tags:
  - architecture/subdomain
  - domain/catalog
---
# Subdomain: Catalog Query

## Overview

Read-only query surface for the manifest catalog. Provides downstream services with access to loaded manifest data including templates, models, and bundles, filtering out stale entries automatically. No workspace scoping — all catalog data is globally accessible. Consumed via direct service injection and indirectly via [[riven/docs/system-design/domains/Catalog/Template Installation/TemplateController]].

## Components

| Component | Purpose | Type |
|-----------|---------|------|
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Catalog Query/ManifestCatalogService]] | Query service for manifest summaries, details, and entity types | Service |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-03-06 | Initial catalog query surface | Template Manifestation |
| 2026-03-09 | Added bundle query methods (getAvailableBundles, getBundleByKey) | Entity Semantics |
