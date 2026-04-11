---
tags:
  - layer/component
  - component/active
  - architecture/component
Created: 2026-03-06
Domains:
  - "[[riven/docs/system-design/domains/Catalog/Catalog]]"
---
# ManifestCatalogHealthIndicator

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/Manifest Pipeline]]

## Purpose

Spring Boot Actuator health indicator that reports the current state of the manifest catalog loading pipeline, enabling monitoring and readiness probes to detect load failures.

---

## Responsibilities

- Track catalog load state transitions: PENDING, LOADING, LOADED, FAILED
- Report health status via `/actuator/health` endpoint
- Expose last error message and last loaded timestamp for diagnostics

---

## Dependencies

None (updated by [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestLoaderService]] via direct method calls)

## Used By

- Spring Boot Actuator — auto-registered as a health contributor
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestLoaderService]] — sets load state during pipeline execution

---

## Key Logic

**Load states:**

| State | Health Status | Meaning |
|---|---|---|
| PENDING | UP | Application started, catalog load not yet begun |
| LOADING | UP | Pipeline is actively loading manifests |
| LOADED | UP | Pipeline completed successfully |
| FAILED | DOWN | Pipeline encountered a fatal error (detail included) |

**Thread safety:**
Uses `@Volatile` fields for `loadState`, `lastError`, and `lastLoadedAt` since the health indicator is written from the manifest loading background thread and read from Actuator HTTP request threads.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestLoaderService]] — Updates health state during pipeline execution
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/Manifest Pipeline]] — Parent subdomain
