---
tags:
  - flow/background
  - architecture/flow
  - domain/catalog
Domains:
  - "[[Catalog]]"
Created: 2026-03-06
---
# Flow: Manifest Loading Pipeline

## Overview

Background startup flow that populates the manifest catalog from classpath JSON files. Runs once on application startup via `ApplicationReadyEvent`, loading models, templates, and integrations into the `manifest_catalog` table and its child tables.

---

## Trigger

`ApplicationReadyEvent` fired by Spring Boot after context initialization.

## Entry Point

[[ManifestLoaderService]]

---

## Steps

1. **ManifestLoaderService** spawns a background thread and sets health indicator to LOADING
2. **ManifestScannerService** scans the classpath for models, templates, and integrations, validating each against JSON Schema
3. For each scanned manifest, **ManifestResolverService** resolves `$ref` references, applies `extends` merges, and normalizes relationships
4. For each resolved manifest, **ManifestUpsertService** persists the manifest in a single transaction (SHA-256 hash check, delete-reinsert if changed)
5. **ManifestReconciliationService** marks unseen entries as stale and un-stales entries that reappeared
6. **IntegrationDefinitionStaleSyncService** propagates stale flags from catalog to `integration_definitions`
7. **ManifestLoaderService** sets health indicator to LOADED (or FAILED on error)

```mermaid
sequenceDiagram
    participant Loader as ManifestLoaderService
    participant Scanner as ManifestScannerService
    participant Resolver as ManifestResolverService
    participant Upsert as ManifestUpsertService
    participant Reconciliation as ManifestReconciliationService
    participant StaleSync as IntegrationDefinitionStaleSyncService
    participant Health as ManifestCatalogHealthIndicator

    Loader->>Health: setState(LOADING)
    Loader->>Scanner: scanModels()
    Scanner-->>Loader: Map<key, manifest>
    Loader->>Scanner: scanTemplates()
    Scanner-->>Loader: Map<key, manifest>
    Loader->>Scanner: scanIntegrations()
    Scanner-->>Loader: Map<key, manifest>

    loop Each manifest
        Loader->>Resolver: resolve(manifest)
        Resolver-->>Loader: resolved manifest
        Loader->>Upsert: upsert(key, type, resolved)
        Upsert-->>Loader: seen (key, type)
    end

    Loader->>Reconciliation: reconcileStaleEntries(seenSet)
    Loader->>StaleSync: syncStaleFlags()
    Loader->>Health: setState(LOADED)
```

---

## Failure Modes

| What Fails | Impact | Recovery |
|---|---|---|
| Individual manifest parse/resolve fails | Manifest skipped, others continue loading | Fix manifest JSON, restart application |
| Entire pipeline fails | Health indicator reports FAILED | Check logs, fix root cause, restart |
| Missing `$ref` in template | Template resolution fails, template skipped | Ensure referenced model exists on classpath |
| Database unavailable during upsert | Pipeline fails, health reports FAILED | Restore database connectivity, restart |

---

## Components Involved

- [[ManifestLoaderService]]
- [[ManifestScannerService]]
- [[ManifestResolverService]]
- [[ManifestUpsertService]]
- [[ManifestReconciliationService]]
- [[IntegrationDefinitionStaleSyncService]]
- [[ManifestCatalogHealthIndicator]]
