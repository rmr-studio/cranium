---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-03-06
Domains:
  - "[[riven/docs/system-design/domains/Catalog/Catalog]]"
---
# ManifestScannerService

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/Manifest Pipeline]]

## Purpose

Scans classpath directories for manifest JSON files, validates them against JSON Schema (V201909), and returns parsed manifest maps keyed by derived identifier.

---

## Responsibilities

- Scan classpath for model manifests (`models/*.json`)
- Scan classpath for template manifests (`templates/*/manifest.json`)
- Scan classpath for integration manifests (`integrations/*/manifest.json`)
- Scan classpath for bundle manifests (`bundles/*/manifest.json`)
- Validate each manifest against its JSON Schema before returning
- Skip invalid or unparseable manifests with WARN-level logging
- Derive manifest keys from filename (models) or directory name (templates, integrations)

---

## Dependencies

- `ResourcePatternResolver` — classpath resource scanning
- `ObjectMapper` — JSON parsing
- `KLogger` — logging
- `@Value` basePath — configurable classpath root for manifest directories

## Used By

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestLoaderService]] — invokes all three scan methods during pipeline execution

---

## Key Logic

**Key derivation:**
- Models: filename without extension (e.g., `contact.json` -> `contact`)
- Templates/Integrations: parent directory name (e.g., `templates/crm/manifest.json` -> `crm`)
- Bundles: parent directory name (e.g., `bundles/saas-starter/manifest.json` -> `saas-starter`)

**Schema validation:**
Each manifest is validated against a JSON Schema using networknt's `JsonSchemaFactory` (V201909). Manifests that fail validation are skipped entirely — the pipeline continues with remaining manifests.

**networknt workaround:**
Strips `$id` and `$schema` properties from schema files before validation to work around networknt 1.0.83 URI resolution issues that cause false validation failures.

---

## Public Methods

### `scanModels(): Map<String, Map<String, Any>>`

Scans `models/*.json` from the classpath. Returns a map of model key to parsed manifest content.

### `scanTemplates(): Map<String, Map<String, Any>>`

Scans `templates/*/manifest.json` from the classpath. Returns a map of template key to parsed manifest content.

### `scanIntegrations(): Map<String, Map<String, Any>>`

Scans `integrations/*/manifest.json` from the classpath. Returns a map of integration key to parsed manifest content.

### `scanBundles(): List<ScannedManifest>`

Scans `bundles/*/manifest.json` from the classpath. Validated against `bundle.schema.json`. Returns a list of scanned bundle manifests.

---

## Gotchas

- **networknt URI resolution:** The `$id` and `$schema` stripping is a workaround for networknt 1.0.83. If the library is upgraded, this workaround should be revisited.
- **Silent skip on failure:** Invalid manifests are logged at WARN and skipped — no exception propagated. The pipeline continues with remaining manifests. Check logs if a manifest is missing from the catalog.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestLoaderService]] — Primary consumer, orchestrates the pipeline
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/ManifestResolverService]] — Processes the raw maps returned by this service
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/Manifest Pipeline]] — Parent subdomain
