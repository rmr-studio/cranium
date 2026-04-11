---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-03-06
Domains:
  - "[[riven/docs/system-design/domains/Catalog/Catalog]]"
---
# ManifestLoaderService

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/Manifest Pipeline]]

## Purpose

Top-level orchestrator for the manifest loading pipeline — scans, resolves, and upserts all manifest types on application startup, then reconciles stale entries and syncs cross-domain flags.

---

## Responsibilities

- Orchestrate the full Scan → Resolve → Upsert pipeline across models, templates, integrations, and bundles
- Fire on `ApplicationReadyEvent` in a dedicated background thread to avoid blocking startup
- Build an in-memory model index during model loading for template `$ref` resolution
- Isolate individual manifest failures (log and skip) to prevent one bad manifest from aborting the pipeline
- Track pipeline health state transitions via [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestCatalogHealthIndicator]]
- Trigger post-load stale reconciliation via [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestReconciliationService]]
- Trigger cross-domain stale flag sync via [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/IntegrationDefinitionStaleSyncService]]

---

## Dependencies

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestScannerService]] — classpath scanning for model, template, and integration manifests
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestResolverService]] — manifest resolution including `$ref` expansion and schema validation
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestUpsertService]] — individual manifest persistence (insert or update)
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestReconciliationService]] — marks catalog entries not seen in the current scan as stale
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/IntegrationDefinitionStaleSyncService]] — propagates stale flags from catalog to integration definitions
- [[ManifestCatalogRepository]] — post-load stale count query
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestCatalogHealthIndicator]] — health state reporting for readiness probes

## Used By

- Spring application lifecycle — triggered automatically via `@EventListener(ApplicationReadyEvent::class)`

---

## Key Logic

**4-phase pipeline (models → templates → integrations → bundles):**

1. **Models first:** Scan and load model manifests. Non-stale models are indexed by key into an in-memory `modelIndex` map (key → raw JSON). This ordering is intentional — templates depend on models for `$ref` resolution.
2. **Templates second:** Scan and load template manifests, passing the `modelIndex` to the resolver so `$ref` references can be expanded against loaded models.
3. **Integrations third:** Scan and load integration manifests with no model index (they don't reference models).
4. **Bundles last:** Scan and load bundle manifests. Bundles are resolved via `resolverService.resolveBundle()` and persisted via `upsertService.upsertBundle()` — separate methods from the manifest flow since bundles have no entity types or child rows.

Each phase iterates its scanned manifests individually: resolve, upsert, and track in a `seenManifests` set. Failures are caught per-manifest, logged at WARN, and the manifest is skipped.

**Post-load reconciliation:**

After all four phases, if any manifests were scanned (including bundles), `reconciliationService.reconcileStaleEntries(seenManifests)` marks any catalog entries NOT in the seen set as stale. If zero manifests were found (likely a classpath misconfiguration), reconciliation is skipped entirely to avoid marking everything stale.

**Cross-domain stale sync:**

`integrationDefinitionStaleSyncService.syncStaleFlags()` propagates stale state from the manifest catalog into the integration definitions table.

**Health state transitions:**

| State | When |
|-------|------|
| PENDING | Default on startup (before event fires) |
| LOADING | Set immediately when the background thread starts |
| LOADED | Set after `loadAllManifests()` completes successfully |
| FAILED | Set if `loadAllManifests()` throws; error message captured |

---

## Public Methods

### `onApplicationReady(event: ApplicationReadyEvent)`

Event listener that spawns the manifest-loader background thread. Updates health indicator through LOADING → LOADED/FAILED states.

### `loadAllManifests()`

Executes the full pipeline: scan all manifest types, resolve and upsert each, reconcile stale entries, sync integration definition flags. Logs a summary line with counts of loaded, stale, and skipped manifests.

---

## Events Consumed

- `ApplicationReadyEvent` — triggers the full manifest loading pipeline after the application context is fully initialized

---

## Gotchas

- **No `@Transactional`:** The main method is deliberately non-transactional. Each manifest is upserted individually by [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestUpsertService]], so a failure mid-pipeline does not roll back previously loaded manifests.
- **Background thread, not async:** Uses a raw `Thread("manifest-loader")` rather than `@Async` or a thread pool. This means there is no timeout or retry mechanism — if the thread hangs, the health indicator stays in LOADING indefinitely.
- **Model ordering is load-bearing:** Models MUST load before templates. The `modelIndex` built during model loading is passed to template resolution. Bundles and integrations have no ordering dependency on other phases.
- **Empty classpath guard:** If zero manifests are scanned across all three types, stale reconciliation is skipped. This prevents a classpath misconfiguration from marking the entire catalog stale.
- **8 constructor dependencies:** High fan-in reflects the orchestrator role. This service delegates all real work and holds no domain logic itself.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/Flow - Manifest Loading Pipeline]] — end-to-end flow documentation
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestScannerService]] — phase 1: classpath scanning
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestResolverService]] — phase 2: resolution and validation
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestUpsertService]] — phase 3: persistence
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestReconciliationService]] — post-load stale cleanup
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/Manifest Pipeline]] — parent subdomain
