---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-03-26
Domains:
  - "[[riven/docs/system-design/domains/Catalog/Catalog]]"
---
# CoreModelCatalogService

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/Manifest Pipeline]]

## Purpose

Spring `@Service` that populates the manifest catalog with core lifecycle model definitions at boot time. Listens for `ApplicationReadyEvent`, iterates over all resolved manifests from [[CoreModelRegistry]], and calls [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestUpsertService]] for each. This is the second catalog population path -- the first being JSON classpath scanning via [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestLoaderService]]. Both converge at [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestUpsertService]] for idempotent persistence.

---

## Responsibilities

- Listen for `ApplicationReadyEvent` to trigger catalog population
- Retrieve all resolved manifests from [[CoreModelRegistry]]
- Call `ManifestUpsertService.upsertManifest()` for each resolved manifest
- Handle individual model set failures gracefully -- log and skip, do not crash the application
- Update [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestCatalogHealthIndicator]] on completion

---

## Dependencies

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestUpsertService]] -- idempotent catalog persistence
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestCatalogHealthIndicator]] -- status tracking for actuator health endpoint
- `KLogger` -- structured logging

## Used By

Spring lifecycle only (`ApplicationReadyEvent` listener). Not called directly by other services.

---

## Key Logic

**Boot-time catalog population:**

1. `ApplicationReadyEvent` fires after the application context is fully initialized
2. Retrieve all resolved manifests via `CoreModelRegistry.allResolvedManifests()` -- this triggers lazy validation of all registered models
3. Iterate over each resolved manifest and call `ManifestUpsertService.upsertManifest()`
4. Individual failures are caught, logged, and skipped -- partial success is acceptable
5. Update health indicator on completion

---

## Public Methods

### `onApplicationReady(event: ApplicationReadyEvent)`

Event handler triggered by Spring's `ApplicationReadyEvent`. Retrieves all core model resolved manifests and persists each to the catalog via [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestUpsertService]].

---

## Gotchas

- **Synchronous execution on ApplicationReadyEvent.** Runs on the main thread during startup. Ordering relative to [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestLoaderService]] depends on Spring event listener ordering -- both listen for the same event.
- **Partial failure is acceptable.** Individual model set failures are logged and skipped. A single broken model definition does not prevent other model sets from being loaded into the catalog.
- **Content hash idempotency from ManifestUpsertService.** Repeated application restarts with unchanged model definitions are no-ops -- the SHA-256 content hash check in [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestUpsertService]] short-circuits child reconciliation.
- **Core models always produce `manifestType = TEMPLATE`.** All core model definitions are persisted as template-type catalog entries, consistent with how JSON-based templates are stored.

---

## Related

- [[CoreModelRegistry]] -- source of all core model resolved manifests
- [[CoreModelDefinition]] -- base class for individual model definitions
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestUpsertService]] -- downstream idempotent persistence
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestLoaderService]] -- the other catalog population path (JSON classpath scanning)
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestCatalogHealthIndicator]] -- health status updated on completion
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/Manifest Pipeline]] -- parent subdomain
