---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-03-06
Domains:
  - "[[riven/docs/system-design/domains/Catalog/Catalog]]"
---
# ManifestCatalogService

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Catalog Query/Catalog Query]]

## Purpose

Read-only query service for the manifest catalog. Provides downstream services with manifest summaries, detailed manifest views with fully hydrated entity types, relationships, and field mappings. All queries exclude stale entries automatically.

---

## Responsibilities

- List available templates (non-stale, TEMPLATE type)
- List available models (non-stale, MODEL type)
- Retrieve a fully hydrated manifest by key and type, including entity types, relationships, target rules, semantic metadata, and field mappings
- Retrieve entity types for a specific manifest
- List available bundles (non-stale, BUNDLE type) with template key lists
- Retrieve a specific bundle by key with template key list
- Throw `NotFoundException` for missing or stale manifests

---

## Dependencies

- `ManifestCatalogRepository` — manifest catalog queries
- `CatalogEntityTypeRepository` — entity type queries
- `CatalogRelationshipRepository` — relationship queries
- `CatalogRelationshipTargetRuleRepository` — target rule queries
- `CatalogSemanticMetadataRepository` — semantic metadata queries
- `CatalogFieldMappingRepository` — field mapping queries
- `KLogger` — logging

## Used By

- Downstream services — consumed via direct service injection (no REST controllers)
- [[riven/docs/system-design/domains/Catalog/Template Installation/TemplateInstallationService]] — queries template manifests and bundles for workspace installation

---

## Key Logic

**Batch loading pattern:**
`getManifestByKey` fetches all child entities (entity types, relationships, target rules, semantic metadata, field mappings) in bulk queries, groups them by parent ID, then assembles the full model. This avoids N+1 queries when hydrating a manifest with many entity types and relationships.

**Stale filtering:**
All queries exclude entries where `stale = true`. A manifest that exists in the database but is marked stale will cause `NotFoundException` to be thrown.

---

## Public Methods

### `getAvailableTemplates(): List<ManifestSummary>`

Returns summaries of all non-stale TEMPLATE-type manifests.

### `getAvailableModels(): List<ManifestSummary>`

Returns summaries of all non-stale MODEL-type manifests.

### `getAvailableBundles(): List<BundleDetail>`

Returns all non-stale BUNDLE-type manifests with their template key lists.

### `getBundleByKey(key: String): BundleDetail`

Returns a specific bundle by key. Throws `NotFoundException` if the bundle does not exist or is stale.

### `getManifestByKey(key: String, manifestType: ManifestType): ManifestDetail`

Returns a fully hydrated manifest detail including entity types with semantic metadata, relationships with target rules, and field mappings. Throws `NotFoundException` if the manifest does not exist or is stale.

### `getEntityTypesForManifest(manifestId: UUID): List<CatalogEntityType>`

Returns all entity types belonging to the specified manifest.

---

## Gotchas

- **No workspace scoping:** This is a global catalog — no `workspaceId` parameter on any method. No `@PreAuthorize` annotations.
- **No write operations:** This service is strictly read-only. All writes go through [[riven/docs/system-design/domains/Catalog/Manifest Pipeline/ManifestUpsertService]] and [[riven/docs/system-design/domains/Catalog/Manifest Pipeline/ManifestReconciliationService]].

---

## Related

- [[riven/docs/system-design/domains/Catalog/Manifest Pipeline/ManifestUpsertService]] — Writes the data this service reads
- [[riven/docs/system-design/domains/Catalog/Manifest Pipeline/ManifestReconciliationService]] — Sets stale flags that affect query results
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Catalog Query/Catalog Query]] — Parent subdomain
