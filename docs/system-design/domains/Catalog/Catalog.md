---
tags:
  - architecture/domain
  - domain/catalog
Created: 2026-03-06
Updated: 2026-03-27
---
# Domain: Catalog

---

## Overview

The Catalog domain provides a startup-time pipeline that scans classpath manifest files (models, templates, integrations) and loads Kotlin-defined core model objects, resolves references and relationships, and upserts them into a global catalog. It also provides workspace-scoped template installation, creating entity types, relationships, and semantic metadata from catalog definitions. The catalog is global-scoped — no workspace scoping, no soft-delete, no RLS. Lifecycle management uses stale flags instead of soft-delete: entries not seen during a load cycle are marked stale and excluded from query results automatically.

---

## Boundaries

### This Domain Owns

- Manifest discovery via classpath scanning (models, templates, integrations) and Kotlin core model definitions
- Core model registry: compile-time Kotlin definitions of business-type entity templates with boot-time catalog population
- JSON Schema validation of manifest files
- Reference resolution (`$ref`, `extends`, relationship normalization)
- Idempotent manifest persistence with SHA-256 content hashing
- Stale flag reconciliation for manifests and cross-domain integration definitions
- Catalog query surface for downstream consumers
- Actuator health indicator for catalog load state
- Template installation into workspaces (creating entity types, relationships, and semantic metadata from catalog definitions)
- Installation tracking via workspace_template_installations

### This Domain Does NOT Own

- Workspace-scoped data — the catalog is global, not tenant-specific
- Integration runtime behavior — owned by [[riven/docs/system-design/domains/Integrations/Integrations]] domain
- Entity type definitions created by users — owned by [[riven/docs/system-design/domains/Entities/Entities]] domain
- Enum definitions (IconType, IconColour, SemanticGroup, etc.) — shared from [[riven/docs/system-design/domains/Entities/Entities]] domain

---

## Sub-Domains

| Sub-Domain | Purpose |
|---|---|
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/Manifest Pipeline]] | Startup-time pipeline: discovery, validation, resolution, persistence, reconciliation |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Catalog Query/Catalog Query]] | Read-only query surface for loaded manifest data |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Template Installation/Template Installation]] | Workspace-scoped installation of catalog templates and bundles via REST API |
| [[Core Model Definitions]] | Compile-time Kotlin entity type definitions with boot-time catalog population |

---

## Flows

| Flow | Type | Description |
|---|---|---|
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/Flow - Manifest Loading Pipeline]] | Background | ApplicationReadyEvent -> scan -> resolve -> upsert -> reconcile -> stale sync |

---

## Data

### Owned Entities

| Entity | Purpose | Key Fields |
|---|---|---|
| ManifestCatalogEntity | Root catalog entry for a manifest | id, key, manifest_type, display_name, content_hash, stale |
| CatalogEntityTypeEntity | Entity type definition within a manifest | id, manifest_id, slug, schema (JSONB), lifecycle_domain |
| CatalogRelationshipEntity | Relationship definition between catalog entity types | id, entity_type_id, relationship_key, cardinality |
| CatalogRelationshipTargetRuleEntity | Target constraint rules for relationships | id, relationship_id, target_entity_type_slug |
| CatalogFieldMappingEntity | Field mapping definitions within a manifest | id, manifest_id, source_field, target_field |
| CatalogSemanticMetadataEntity | Semantic metadata annotations for entity types | id, entity_type_id, classification, target_type |
| WorkspaceTemplateInstallationEntity | Tracks template installations per workspace | id, workspace_id, manifest_key, installed_by, installed_at, attribute_mappings |

### Database Tables

| Table | Entity | Notes |
|---|---|---|
| manifest_catalog | ManifestCatalogEntity | Root table, cascading deletes to all children |
| catalog_entity_types | CatalogEntityTypeEntity | Schema stored as JSONB |
| catalog_relationships | CatalogRelationshipEntity | References catalog_entity_types |
| catalog_relationship_target_rules | CatalogRelationshipTargetRuleEntity | References catalog_relationships |
| catalog_field_mappings | CatalogFieldMappingEntity | References manifest_catalog |
| catalog_semantic_metadata | CatalogSemanticMetadataEntity | References catalog_entity_types |
| workspace_template_installations | WorkspaceTemplateInstallationEntity | Unique constraint on (workspace_id, manifest_key) |

---

## Domain Interactions

### Depends On

| Domain | What We Consume | Via Component | Related Flow |
|--------|----------------|---------------|--------------|
| [[riven/docs/system-design/domains/Entities/Entities]] | Shared enums: IconType, IconColour, SemanticGroup, EntityRelationshipCardinality, SemanticMetadataTargetType, SemanticAttributeClassification | Enum imports | [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/Flow - Manifest Loading Pipeline]] |
| [[riven/docs/system-design/domains/Entities/Entities]] | Entity type creation, relationship definitions, semantic metadata initialization | [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Template Installation/TemplateInstallationService]] | Template/bundle installation |

### Consumed By

| Consumer | What They Consume | Via Component | Related Flow |
|----------|------------------|---------------|--------------|
| [[riven/docs/system-design/domains/Integrations/Integrations]] | Stale flag propagation from catalog to integration_definitions | [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/IntegrationDefinitionStaleSyncService]] | [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Manifest Pipeline/Flow - Manifest Loading Pipeline]] |
| Downstream services | Manifest summaries, entity type definitions, relationship schemas | [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Catalog Query/ManifestCatalogService]] | Direct service injection |
| Workspace users | Template installation via REST API | WorkspaceController | Template installation |

---

## Key Decisions

| Decision | Summary |
|---|---|
| REST controller removed | Catalog was previously consumed via service injection only. TemplateController was the single REST surface but has been removed — install endpoint moved to WorkspaceController |
| Global scope | No workspace scoping, no RLS — catalog data is system-managed and shared across all workspaces |
| Stale flags over soft-delete | System-managed entities use stale flags for lifecycle instead of SoftDeletable, since they are not user-created |
| SHA-256 content hashing | Manifests are only re-persisted when content changes, enabling idempotent startup loads |
| Delete-reinsert for updates | Child entities (entity types, relationships, etc.) are deleted and reinserted on manifest update rather than diffed, simplifying reconciliation |
| Background thread loading | Manifest loading runs on a background thread after ApplicationReadyEvent to avoid blocking application startup |
| Core models over JSON manifests | Business-type entity templates defined as Kotlin objects (CoreModelDefinition) with compile-time type safety, replacing JSON manifest files. CoreModelCatalogService converts them to ResolvedManifest at boot time, converging with the JSON pipeline at ManifestUpsertService |
| Template installation moved to WorkspaceController | TemplateController removed — install endpoint consolidated into WorkspaceController. Catalog domain no longer has its own REST surface |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-03-06 | Initial catalog domain implementation | Template Manifestation |
| 2026-03-09 | Added Template Installation subdomain with REST API, bundle manifest support across pipeline | Entity Semantics |
| 2026-03-26 | Lifecycle spine: core model definitions (Kotlin objects), CoreModelCatalogService for boot-time catalog population, BUNDLE manifest type removed, TemplateController removed (install moved to WorkspaceController), lifecycle_domain column added to catalog_entity_types | Lifecycle Spine |
| 2026-03-27 | Field mapping consumed by ingestion pipeline via FieldMappingService | Entity Ingestion Pipeline |

---

## Field Mapping for Ingestion

`CatalogFieldMappingEntity` is consumed by the new `FieldMappingService` during data ingestion. Field mappings transform integration source fields into the core entity type schema, bridging the gap between external data models and workspace-scoped entity types.

The generic mapping engine (documented under [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Catalog/Catalog Query/Catalog Query]]) handles type coercion, value mapping, and JSONPath extraction. The `FieldMappingService` applies these mappings at runtime as Step 3 (Map) of the ingestion pipeline.

### References

- [[2. Areas/2.1 Startup & Content/Riven/7. Todo/Entity Ingestion Pipeline]]
