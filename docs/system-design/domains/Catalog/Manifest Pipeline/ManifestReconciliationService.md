---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-03-06
Domains:
  - "[[riven/docs/system-design/domains/Catalog/Catalog]]"
---
# ManifestReconciliationService

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/Manifest Pipeline]]

## Purpose

Post-load reconciliation service that marks catalog entries as stale if they were not seen during the current load cycle, and un-stales entries that were seen.

---

## Responsibilities

- Mark catalog entries stale when not present in the current load cycle's seen set
- Un-stale catalog entries that reappear after being previously marked stale
- Log counts of stale and un-staled entries for observability

---

## Dependencies

- `ManifestCatalogRepository` — catalog entry persistence
- `KLogger` — logging

## Used By

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestLoaderService]] — called after all manifests have been upserted

---

## Key Logic

**Reconciliation strategy:**
Compares the set of `(key, manifestType)` pairs seen during the current load cycle against all entries in `manifest_catalog`. Entries not in the seen set are marked `stale = true`. Entries in the seen set that were previously stale are marked `stale = false`.

This replaced an earlier "mark all stale up front" approach. The previous design would leave the entire catalog dark if the application crashed mid-load. The current approach only marks entries stale after all manifests have been successfully processed.

---

## Public Methods

### `reconcileStaleEntries(seenManifests: Set<Pair<String, ManifestType>>)`

Transactional. Reconciles stale flags for all catalog entries based on the provided seen set.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestLoaderService]] — Provides the seen set and invokes reconciliation
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/IntegrationDefinitionStaleSyncService]] — Propagates stale flags to integration_definitions after reconciliation
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/Manifest Pipeline]] — Parent subdomain
