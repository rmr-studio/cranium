---
tags:
  - architecture/domain
  - domain/catalog
Created: 2026-03-06
---
# Domain: Catalog

---

## Overview

The Catalog domain provides a startup-time pipeline that scans classpath manifest files (models, templates, integrations, bundles), resolves references and relationships, and upserts them into a global catalog. It also provides workspace-scoped template and bundle installation, creating entity types, relationships, and semantic metadata from catalog definitions. The catalog is global-scoped — no workspace scoping, no soft-delete, no RLS. Lifecycle management uses stale flags instead of soft-delete: entries not seen during a load cycle are marked stale and excluded from query results automatically.

---

## Boundaries

### This Domain Owns

- Manifest discovery and classpath scanning (models, templates, integrations)
- JSON Schema validation of manifest files
- Reference resolution (`$ref`, `extends`, relationship normalization)
- Idempotent manifest persistence with SHA-256 content hashing
- Stale flag reconciliation for manifests and cross-domain integration definitions
- Catalog query surface for downstream consumers
- Actuator health indicator for catalog load state
- Template and bundle installation into workspaces (creating entity types, relationships, and semantic metadata from catalog definitions)
- Installation tracking via workspace_template_installations

### This Domain Does NOT Own

- Workspace-scoped data — the catalog is global, not tenant-specific
- Integration runtime behavior — owned by [[Integrations]] domain
- Entity type definitions created by users — owned by [[Entities]] domain
- Enum definitions (IconType, IconColour, SemanticGroup, etc.) — shared from [[Entities]] domain

---

## Sub-Domains

| Sub-Domain | Purpose |
|---|---|
| [[Manifest Pipeline]] | Startup-time pipeline: discovery, validation, resolution, persistence, reconciliation |
| [[Catalog Query]] | Read-only query surface for loaded manifest data |
| [[Template Installation]] | Workspace-scoped installation of catalog templates and bundles via REST API |

---

## Flows

| Flow | Type | Description |
|---|---|---|
| [[Flow - Manifest Loading Pipeline]] | Background | ApplicationReadyEvent -> scan -> resolve -> upsert -> reconcile -> stale sync |

---

## Data

### Owned Entities

| Entity | Purpose | Key Fields |
|---|---|---|
| ManifestCatalogEntity | Root catalog entry for a manifest | id, key, manifest_type, display_name, content_hash, stale |
| CatalogEntityTypeEntity | Entity type definition within a manifest | id, manifest_id, slug, schema (JSONB) |
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
| [[Entities]] | Shared enums: IconType, IconColour, SemanticGroup, EntityRelationshipCardinality, SemanticMetadataTargetType, SemanticAttributeClassification | Enum imports | [[Flow - Manifest Loading Pipeline]] |
| [[Entities]] | Entity type creation, relationship definitions, semantic metadata initialization | [[TemplateInstallationService]] | Template/bundle installation |

### Consumed By

| Consumer | What They Consume | Via Component | Related Flow |
|----------|------------------|---------------|--------------|
| [[Integrations]] | Stale flag propagation from catalog to integration_definitions | [[IntegrationDefinitionStaleSyncService]] | [[Flow - Manifest Loading Pipeline]] |
| Downstream services | Manifest summaries, entity type definitions, relationship schemas | [[ManifestCatalogService]] | Direct service injection |
| Workspace users | Template and bundle installation via REST API | [[TemplateController]] | Template/bundle installation |

---

## Key Decisions

| Decision | Summary |
|---|---|
| REST controller for templates only | Catalog was previously consumed via service injection only. TemplateController is the single REST surface, limited to template/bundle listing and installation |
| Global scope | No workspace scoping, no RLS — catalog data is system-managed and shared across all workspaces |
| Stale flags over soft-delete | System-managed entities use stale flags for lifecycle instead of SoftDeletable, since they are not user-created |
| SHA-256 content hashing | Manifests are only re-persisted when content changes, enabling idempotent startup loads |
| Delete-reinsert for updates | Child entities (entity types, relationships, etc.) are deleted and reinserted on manifest update rather than diffed, simplifying reconciliation |
| Background thread loading | Manifest loading runs on a background thread after ApplicationReadyEvent to avoid blocking application startup |
| Bundle manifest type | BUNDLE added to ManifestType enum — bundles are curated collections of templates, resolved at installation time rather than at manifest load time |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-03-06 | Initial catalog domain implementation | Template Manifestation |
| 2026-03-09 | Added Template Installation subdomain with REST API, bundle manifest support across pipeline | Entity Semantics |
