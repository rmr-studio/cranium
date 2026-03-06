---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-03-06
Domains:
  - "[[Catalog]]"
---
# ManifestScannerService

Part of [[Manifest Pipeline]]

## Purpose

Scans classpath directories for manifest JSON files, validates them against JSON Schema (V201909), and returns parsed manifest maps keyed by derived identifier.

---

## Responsibilities

- Scan classpath for model manifests (`models/*.json`)
- Scan classpath for template manifests (`templates/*/manifest.json`)
- Scan classpath for integration manifests (`integrations/*/manifest.json`)
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

- [[ManifestLoaderService]] — invokes all three scan methods during pipeline execution

---

## Key Logic

**Key derivation:**
- Models: filename without extension (e.g., `contact.json` -> `contact`)
- Templates/Integrations: parent directory name (e.g., `templates/crm/manifest.json` -> `crm`)

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

---

## Gotchas

- **networknt URI resolution:** The `$id` and `$schema` stripping is a workaround for networknt 1.0.83. If the library is upgraded, this workaround should be revisited.
- **Silent skip on failure:** Invalid manifests are logged at WARN and skipped — no exception propagated. The pipeline continues with remaining manifests. Check logs if a manifest is missing from the catalog.

---

## Related

- [[ManifestLoaderService]] — Primary consumer, orchestrates the pipeline
- [[ManifestResolverService]] — Processes the raw maps returned by this service
- [[Manifest Pipeline]] — Parent subdomain
