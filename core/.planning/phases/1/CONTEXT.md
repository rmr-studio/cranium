# Phase 1 Context: Database Foundation

Phase goal: The catalog schema, JPA layer, and authoring infrastructure exist and are ready for the loader pipeline to build on.

## Area 1: Catalog Table Schema

### Catalog persistence model
- **Catalog entries are permanent.** Once a manifest is loaded into the catalog, the row persists forever. No `deleted`, `deleted_at`, or `active` columns on `manifest_catalog`.
- **Catalog is append/update-only.** Reloads upsert over existing rows. No rows are ever removed or deactivated.
- **Rename `active` to `stale` on `integration_definitions`.** When an integration manifest file disappears from disk, the loader marks `integration_definitions.stale = true`. Staleness only applies to integrations — model and template catalog entries are simply left untouched if their manifest disappears.
- **Add `last_loaded_at` to `manifest_catalog`.** `TIMESTAMPTZ` updated on each successful upsert. Provides passive visibility into which manifests were present in the most recent startup without needing a status column.

### IntegrationDefinitionEntity relationship
- **`IntegrationDefinitionEntity` stays.** It owns platform/connection metadata (nangoProviderKey, syncConfig, authConfig, capabilities, category) that is not part of entity schemas.
- **Shared key, no FK constraint.** `integration_definitions.slug` and `manifest_catalog.key` use the same value (e.g., `"hubspot"`). The link is application-level, not enforced by a DB foreign key. This avoids chicken-and-egg ordering between manifest loading and integration definition seeding.
- **`manifest_catalog.integration_definition_id` is removed.** The shared key replaces the explicit FK. Join: `WHERE manifest_catalog.key = integration_definitions.slug AND manifest_catalog.type = 'INTEGRATION'`.

### Timestamps
- Catalog entities use manual `created_at` + `updated_at` fields (not `AuditableEntity`, no `createdBy`/`updatedBy`). Follows `IntegrationDefinitionEntity` precedent.

### Child table references
- Child tables (`catalog_entity_types`, `catalog_relationships`, etc.) reference `manifest_catalog.id` via UUID FK with `ON DELETE CASCADE`.
- Key-based references within the catalog (e.g., `source_entity_type_key` on relationships) use VARCHAR — not UUID FKs. Loader validates key existence at load time. UUID resolution happens at clone time.

## Area 2: Manifest JSON Structure

### Source of truth
- ADR-004 defines the canonical manifest structure. All JSON Schema files must validate against this structure.

### Attribute keys
- Manifest entity type attributes use human-readable string keys (`"email"`, `"mrr"`, `"company_name"`) in the `properties` map for JSON readability. UUIDs are assigned at clone time when entity types are created in a workspace.
- The manifest schema mirrors the existing `Schema<T>` object structure (same fields: `key`/SchemaType, `type`/DataType, `format`/DataFormat, `label`, `required`, `protected`, `unique`, `options`, `properties`, `items`, `icon`) — but with string keys instead of UUID keys.

### Relationship keys
- Relationship `key` fields remain in the manifest JSON for unique identification within the file and for idempotent upsert keying (`UNIQUE(manifest_id, key)` on `catalog_relationships`). They have no runtime meaning — UUIDs are assigned at clone time.

### Semantics structure
- Semantics on entity types and attributes: `{ "definition": "...", "classification": "...", "tags": [...] }` — mirrors `EntityTypeSemanticMetadata` fields (definition, classification as `SemanticAttributeClassification` enum, tags as string array).
- Semantics on relationships: `{ "definition": "...", "tags": [...] }` — no `classification` (only meaningful for attributes).
- `targetType` and `targetId` are structural fields resolved at load time, not specified in the manifest JSON.

### Out of scope for manifest JSON
- `analyticalBriefs` and `exampleQueries` arrays shown in ADR-004 example — deferred, not included in Phase 1 JSON Schemas.
- Field mappings (`catalog_field_mappings`) — table is created in Phase 1 but manifest structure for mappings is a Phase 2 concern.

## Area 3: entity_types Column Additions

### Deferred to clone service phase (v2)
- `entity_types` will NOT gain new columns in Phase 1. The following additions are deferred to the clone service milestone:
  - `integration_definition_id UUID REFERENCES integration_definitions(id) ON DELETE RESTRICT` — nullable FK, only populated for entity types cloned from integration manifests.
  - `readonly BOOLEAN DEFAULT FALSE` — set to `true` for integration-derived entity types.
- **Rationale:** The clone service (v2) is what actually creates workspace entity types from catalog entries. Adding columns before any code writes to them is premature.
- Templates produce normal entity types with no back-reference and `readonly = false` — fully editable copies. Only integration-derived entity types link back to their integration definition.
- No `source_manifest_key` column — the `integration_definition_id` FK replaces it for integrations, and templates don't need a back-reference.

## Area 4: Manifest File Location

### Classpath-based with future configurable override
- **Phase 1 scaffolding:** `src/main/resources/manifests/` — standard Spring Boot classpath location. Bundled into JAR on build. CI validates automatically.
- **Phase 2 loader:** Will add a configurable property (`riven.manifests.path`) for self-hosters to mount an external directory. External path checked first, classpath fallback. Phase 1 only scaffolds the classpath location.

### Directory structure
```
src/main/resources/manifests/
├── schemas/                         # JSON Schema validation files
│   ├── model.schema.json
│   ├── template.schema.json
│   └── integration.schema.json
├── models/                          # Shared entity type definitions (flat files)
│   ├── customer.json
│   └── ...
├── templates/                       # Workspace bootstrapping bundles (directory per template)
│   └── saas-startup/
│       └── manifest.json
├── integrations/                    # Per-integration definitions (directory per integration)
│   └── hubspot/
│       └── manifest.json
└── README.md                        # Authoring guidelines
```

- **Models:** Flat files (`models/customer.json`). Key derived from filename without extension.
- **Templates:** Directory-based (`templates/saas-startup/manifest.json`). Key derived from directory name.
- **Integrations:** Directory-based (`integrations/hubspot/manifest.json`). Key derived from directory name.
- **Schemas:** Nested inside `manifests/schemas/` — part of the same concern as manifests.
- **JSON Schema files** always live on the classpath regardless of manifest source.
- **Test fixtures:** `src/test/resources/manifests/` — same structure, always classpath-loaded.
- **README.md:** Documents manifest format, `$ref` syntax, `extend` semantics, relationship shorthand vs full format, and attribute schema structure.

## Deferred Ideas

- Configurable external manifest path for self-hosters — Phase 2 loader concern
- `deprecated` flag on catalog entries for manifests being phased out — noted in feature design open questions
- Workspace notification when a previously-cloned manifest is updated — noted in feature design open questions
- Batch model installation API — noted in feature design open questions

---
*Created: 2026-03-03 during Phase 1 discussion*
