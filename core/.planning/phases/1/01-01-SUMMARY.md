---
phase: 01-database-foundation
plan: 01
subsystem: catalog
tags: [database, jpa, schema, json-schema, scaffolding]
dependency-graph:
  requires: []
  provides: [catalog-tables, catalog-entities, catalog-repositories, manifest-type-enum, json-schemas, manifest-directories]
  affects: [integration-definitions]
tech-stack:
  added: []
  patterns: [global-catalog-entity, manual-timestamps, jsonb-hypersistence, varchar-enum-check]
key-files:
  created:
    - db/schema/01_tables/catalog.sql
    - db/schema/02_indexes/catalog_indexes.sql
    - src/main/kotlin/riven/core/enums/catalog/ManifestType.kt
    - src/main/kotlin/riven/core/entity/catalog/ManifestCatalogEntity.kt
    - src/main/kotlin/riven/core/entity/catalog/CatalogEntityTypeEntity.kt
    - src/main/kotlin/riven/core/entity/catalog/CatalogRelationshipEntity.kt
    - src/main/kotlin/riven/core/entity/catalog/CatalogRelationshipTargetRuleEntity.kt
    - src/main/kotlin/riven/core/entity/catalog/CatalogFieldMappingEntity.kt
    - src/main/kotlin/riven/core/entity/catalog/CatalogSemanticMetadataEntity.kt
    - src/main/kotlin/riven/core/repository/catalog/ManifestCatalogRepository.kt
    - src/main/kotlin/riven/core/repository/catalog/CatalogEntityTypeRepository.kt
    - src/main/kotlin/riven/core/repository/catalog/CatalogRelationshipRepository.kt
    - src/main/kotlin/riven/core/repository/catalog/CatalogRelationshipTargetRuleRepository.kt
    - src/main/kotlin/riven/core/repository/catalog/CatalogFieldMappingRepository.kt
    - src/main/kotlin/riven/core/repository/catalog/CatalogSemanticMetadataRepository.kt
    - src/main/resources/manifests/schemas/model.schema.json
    - src/main/resources/manifests/schemas/template.schema.json
    - src/main/resources/manifests/schemas/integration.schema.json
    - src/main/resources/manifests/README.md
    - src/main/resources/manifests/models/.gitkeep
    - src/main/resources/manifests/templates/.gitkeep
    - src/main/resources/manifests/integrations/.gitkeep
    - src/test/resources/manifests/schemas/.gitkeep
    - src/test/resources/manifests/models/.gitkeep
    - src/test/resources/manifests/templates/.gitkeep
    - src/test/resources/manifests/integrations/.gitkeep
  modified:
    - db/schema/01_tables/integrations.sql
    - db/schema/02_indexes/integration_indexes.sql
    - src/main/kotlin/riven/core/entity/integration/IntegrationDefinitionEntity.kt
    - src/main/kotlin/riven/core/repository/integration/IntegrationDefinitionRepository.kt
    - src/main/kotlin/riven/core/service/integration/IntegrationDefinitionService.kt
decisions:
  - "Catalog entities follow IntegrationDefinitionEntity pattern: no AuditableEntity, no SoftDeletable, manual timestamps, no workspace_id"
  - "CatalogRelationshipEntity.protected uses backtick-escaped Kotlin keyword matching existing RelationshipDefinitionEntity pattern"
  - "JSON Schema files use Draft 2019-09 matching existing SchemaService SpecVersion.VersionFlag.V201909"
  - "Relationship semantics have definition + tags but no classification, per CONTEXT.md"
metrics:
  duration: "8 minutes"
  completed: 2026-03-04
  tasks: 3
  files-created: 26
  files-modified: 5
---

# Phase 1 Plan 1: Database Foundation Summary

6 catalog SQL tables, 6 JPA entity data classes, 6 Spring Data repositories, ManifestType enum, integration_definitions active-to-stale rename, 3 JSON Schema validation files (Draft 2019-09), and manifest directory scaffolding with README

## Tasks Completed

### Task 1: SQL schema files + ManifestType enum + integration_definitions rename
**Commit:** 61d64a5fc

Created `db/schema/01_tables/catalog.sql` with 6 tables following `integrations.sql` conventions:
- `manifest_catalog` with `UNIQUE(key, manifest_type)` composite constraint
- `catalog_entity_types` with JSONB `schema` and `columns` columns
- `catalog_relationships` with CHECK constraint on `cardinality_default`
- `catalog_relationship_target_rules` with nullable CHECK constraints on `semantic_type_constraint` and `cardinality_override`
- `catalog_field_mappings` with JSONB `mappings` column
- `catalog_semantic_metadata` with VARCHAR `target_id` (not UUID) and `UNIQUE(catalog_entity_type_id, target_type, target_id)`

Created `db/schema/02_indexes/catalog_indexes.sql` with 8 performance indexes using `CREATE INDEX IF NOT EXISTS`.

Renamed `active BOOLEAN NOT NULL DEFAULT true` to `stale BOOLEAN NOT NULL DEFAULT false` in `integrations.sql`. Updated partial index from `idx_integration_definitions_active WHERE active = true` to `idx_integration_definitions_stale WHERE stale = false`.

Created `ManifestType` enum with `MODEL`, `TEMPLATE`, `INTEGRATION` values.

### Task 2: JPA entities, repositories, and integration_definitions code rename
**Commit:** 2ff7b97b9

Created 6 JPA entity data classes in `entity/catalog/` following the `IntegrationDefinitionEntity` pattern exactly:
- No `AuditableEntity` or `SoftDeletable` inheritance
- Manual `createdAt` with `ZonedDateTime.now()` default and `updatable = false`
- `@UpdateTimestamp` on `updatedAt`
- `@Type(JsonBinaryType::class)` for JSONB columns
- `@Enumerated(EnumType.STRING)` for enum columns
- Plain UUID for FK columns (no `@ManyToOne`)
- `@Table` with `uniqueConstraints` where applicable

Created 6 repository interfaces in `repository/catalog/` with derived query methods for manifest lookup, child entity retrieval, and unique key lookups.

Renamed `IntegrationDefinitionEntity.active` to `stale` (Boolean, default false), `IntegrationDefinitionRepository.findByActiveTrue()` to `findByStaleIsFalse()`, and updated `IntegrationDefinitionService.getActiveIntegrations()` to call the new repository method.

### Task 3: JSON Schema files, manifest directory scaffolding, and README
**Commit:** f23434180

Created 3 JSON Schema files using Draft 2019-09:
- `model.schema.json`: validates single entity type manifests with `manifestVersion`, `key`, `name`, `displayName`, `attributes` required. No `entityTypes` array or `relationships`.
- `template.schema.json`: validates manifests with `entityTypes` array (supporting both `$ref` + `extend` and inline definitions) and optional `relationships` array with shorthand/full format support.
- `integration.schema.json`: validates manifests with inline `entityTypes`, optional `relationships`, and optional `fieldMappings`.

Created directory structure under `src/main/resources/manifests/` (models, templates, integrations with .gitkeep) and `src/test/resources/manifests/` (schemas, models, templates, integrations with .gitkeep).

Created `README.md` documenting manifest format, `$ref` syntax, `extend` semantics (shallow additive merge), relationship shorthand vs full format, attribute schema structure, and semantics.

## Deviations from Plan

None -- plan executed exactly as written.

## Verification Results

1. `./gradlew build -x test` -- PASSED (compilation clean)
2. `./gradlew test` -- PASSED (no regressions from integration rename)
3. `catalog.sql` contains exactly 6 CREATE TABLE statements
4. No catalog entity extends AuditableEntity or has workspace_id
5. `UNIQUE (key, manifest_type)` present in catalog.sql
6. No remaining `active`/`findByActiveTrue` references in integration package
7. All 3 JSON Schema files parse as valid JSON
8. All manifest directories exist in both main and test resources

## Self-Check: PASSED

- All 26 created files verified on disk
- All 3 task commits (61d64a5fc, 2ff7b97b9, f23434180) verified in git log
