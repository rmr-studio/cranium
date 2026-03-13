---
Created: 2026-03-06
Domains:
  - "[[Catalog]]"
tags:
  - architecture/subdomain
  - domain/catalog
---
# Subdomain: Catalog Query

## Overview

Read-only query surface for the manifest catalog. Provides downstream services with access to loaded manifest data including templates, models, and bundles, filtering out stale entries automatically. No workspace scoping — all catalog data is globally accessible. Consumed via direct service injection and indirectly via [[TemplateController]].

## Components

| Component | Purpose | Type |
|-----------|---------|------|
| [[ManifestCatalogService]] | Query service for manifest summaries, details, and entity types | Service |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-03-06 | Initial catalog query surface | Template Manifestation |
| 2026-03-09 | Added bundle query methods (getAvailableBundles, getBundleByKey) | Entity Semantics |
