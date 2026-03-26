---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-03-06
Domains:
  - "[[Catalog]]"
---
# ManifestUpsertService

Part of [[Manifest Pipeline]]

## Purpose

Idempotent persistence layer for resolved manifests. Upserts the catalog entry keyed on `(key, manifestType)`, then reconciles child rows using delete-then-reinsert within a single transaction boundary, skipping child reconciliation entirely when content is unchanged.

---

## Responsibilities

- Create or update `manifest_catalog` entries keyed on `(key, manifestType)`
- Compute SHA-256 content hashes for idempotency — skip child reconciliation when content is unchanged
- Delete and reinsert all child rows (entity types, relationships, target rules, field mappings, semantic metadata) within a single `@Transactional` boundary
- Handle stale manifests by updating only the catalog row without creating child rows
- Map resolved domain models to 5 JPA entity types for persistence
- Persist bundle manifests (catalog entry only, no child rows — bundles store templateKeys JSONB)

---

## Dependencies

- [[ManifestCatalogRepository]] — data access for `manifest_catalog` entries
- `CatalogEntityTypeRepository` — data access for `catalog_entity_types`
- `CatalogRelationshipRepository` — data access for `catalog_relationships`
- `CatalogRelationshipTargetRuleRepository` — data access for `catalog_relationship_target_rules`
- `CatalogFieldMappingRepository` — data access for `catalog_field_mappings`
- `CatalogSemanticMetadataRepository` — data access for `catalog_semantic_metadata`
- `ObjectMapper` — JSON serialization for content hash computation
- `KLogger` — structured logging

## Used By

- [[ManifestLoaderService]] — calls `upsertManifest()` for each resolved manifest during the loading pipeline
- [[ManifestLoaderService]] — calls `upsertBundle()` for each resolved bundle during the loading pipeline
- [[CoreModelCatalogService]] — calls `upsertManifest()` for each Kotlin-defined core model set during boot-time catalog population

---

## Key Logic

**Content hash idempotency:**

1. Serialize manifest content (name, description, version, entity types, relationships, field mappings) to JSON via `ObjectMapper`
2. Compute SHA-256 digest of the serialized content
3. Compare against stored `contentHash` on the existing catalog entry
4. If hashes match: touch `lastLoadedAt` timestamp and return early — no child reconciliation
5. If hashes differ or entry is new: proceed with full upsert

**Upsert flow:**

1. Look up existing catalog entry by `(key, manifestType)`
2. Create or update the `manifest_catalog` row via `persistCatalogEntry()`
3. If manifest is stale: return after catalog row update (no child rows created)
4. Delete all existing child rows in cascading order
5. Reinsert child rows from the resolved manifest

**Cascading delete order (FK-safe):**

1. `catalog_relationship_target_rules` (depends on relationships)
2. `catalog_relationships` (depends on manifest)
3. `catalog_semantic_metadata` (depends on entity types)
4. `catalog_entity_types` (depends on manifest)
5. `catalog_field_mappings` (depends on manifest)
6. Explicit `flush()` after all deletes before inserts begin

**Bundle upsert flow (`upsertBundle`):**

1. Compute SHA-256 content hash from bundle fields (name, description, manifestVersion, templateKeys)
2. Look up existing catalog entry by `(key, BUNDLE)`
3. If hashes match and stale flag unchanged: touch `lastLoadedAt` and return early
4. Create or update the `manifest_catalog` row with `templateKeys` JSONB
5. No child row handling — bundles have no entity types, relationships, or field mappings

**Entity type insertion with semantic metadata:**

1. Map each `ResolvedEntityType` to `CatalogEntityTypeEntity` (icon, semantic group, schema, columns)
2. Batch save all entity types
3. For each saved entity type that has semantics: create a `CatalogSemanticMetadataEntity` with `targetType = ENTITY_TYPE`
4. Batch save semantic metadata

---

## Public Methods

### `upsertManifest(resolved: ResolvedManifest)`

Single entry point. Persists a resolved manifest idempotently within a `@Transactional` boundary. Creates or updates the catalog entry, then reconciles all child rows via delete-then-reinsert. Short-circuits on content hash match or stale manifest.

### `upsertBundle(resolved: ResolvedBundle)`

Persists a resolved bundle to the catalog. Bundles have no child rows — only the catalog entry with `templateKeys` JSONB. Uses the same content hash idempotency pattern as `upsertManifest()`.

---

## Data Access

**Entities written:**

- `ManifestCatalogEntity` — parent catalog entry
- `CatalogEntityTypeEntity` — entity type definitions with schema and columns
- `CatalogRelationshipEntity` — relationship definitions with source entity type key
- `CatalogRelationshipTargetRuleEntity` — target rules with cardinality overrides and semantic constraints
- `CatalogFieldMappingEntity` — field mappings per entity type
- `CatalogSemanticMetadataEntity` — semantic metadata for entity types that declare semantics
- `ManifestCatalogEntity` — bundle catalog entries (with `templateKeys` JSONB, `manifestType = BUNDLE`)

**Tables written to:** `manifest_catalog`, `catalog_entity_types`, `catalog_relationships`, `catalog_relationship_target_rules`, `catalog_field_mappings`, `catalog_semantic_metadata`

---

## Gotchas

- **Flush before insert is mandatory:** Hibernate's derived delete methods (`deleteByManifestId`, `deleteByCatalogRelationshipIdIn`) use `em.remove()` which defers SQL execution until flush time. Without an explicit `flush()` after deletes, subsequent inserts can hit unique constraint violations because the DELETE statements haven't been sent to the database yet.
- **Hash stabilizes child UUIDs across reloads:** When content hasn't changed, the early return preserves existing child row UUIDs. Without the hash check, every reload would delete and recreate children with new UUIDs, breaking any external references.
- **Stale manifests skip children entirely:** A stale manifest only updates the catalog row (`stale = true`, touched `lastLoadedAt`). No child rows are created or deleted. This means a previously non-stale manifest that becomes stale retains its last-known child rows until it becomes non-stale again.
- **Content hash is null for stale manifests:** `computeContentHash()` is only called when `resolved.stale == false`. Stale catalog entries store `contentHash = null`, so the next non-stale load will always trigger a full reconciliation.
- **Bundles have no children:** Unlike template/model/integration manifests, bundles only persist a catalog row with a `templateKeys` JSONB array. There is no cascading delete or child reconciliation for bundles.

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-03-26 | Added as shared persistence target for both JSON manifest pipeline and Kotlin core model pipeline | Lifecycle Spine |

---

## Related

- [[ManifestLoaderService]] — orchestrates the manifest loading pipeline
- [[ManifestCatalogService]] — read-side queries for catalog data
- [[Flow - Manifest Loading Pipeline]] — end-to-end pipeline documentation
- [[Manifest Pipeline]] — parent subdomain
