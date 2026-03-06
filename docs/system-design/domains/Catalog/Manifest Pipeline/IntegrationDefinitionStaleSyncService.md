---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-03-06
Domains:
  - "[[Catalog]]"
---
# IntegrationDefinitionStaleSyncService

Part of [[Manifest Pipeline]]

## Purpose

Cross-domain service that propagates stale flags from `manifest_catalog` (INTEGRATION type entries) to the `integration_definitions` table, keeping the [[Integrations]] domain in sync with catalog lifecycle state.

---

## Responsibilities

- Read stale flags from catalog entries of type INTEGRATION
- Match catalog entries to integration definitions by slug/key
- Propagate stale flag values to the corresponding `integration_definitions` rows

---

## Dependencies

- `ManifestCatalogRepository` — reads catalog stale state
- `IntegrationDefinitionRepository` — writes stale flags to integration_definitions
- `KLogger` — logging

## Used By

- [[ManifestLoaderService]] — called after reconciliation to propagate stale flags cross-domain

---

## Key Logic

**Why this is a separate service:**
Extracted from [[ManifestLoaderService]] to ensure `@Transactional` works correctly via Spring AOP proxy. Self-invocation within ManifestLoaderService would bypass the proxy and run without transaction boundaries.

**Matching logic:**
Catalog entries are matched to integration definitions by their key/slug. The stale flag from the catalog entry is written directly to the integration definition row.

---

## Public Methods

### `syncStaleFlags()`

Transactional. Reads all INTEGRATION-type catalog entries, matches them to integration definitions by slug, and sets the `stale` flag on each integration definition to match its catalog entry.

---

## Related

- [[ManifestReconciliationService]] — Runs before this service to set catalog stale flags
- [[ManifestLoaderService]] — Orchestrates the pipeline and invokes this service
- [[Manifest Pipeline]] — Parent subdomain
