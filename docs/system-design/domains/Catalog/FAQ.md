This document answers common questions about the Catalog domain — manifest loading, template installation, and bundle support.

### How does template installation work

Template installation reads a fully-resolved template from the manifest catalog and creates workspace-scoped entity types, relationships, and semantic metadata in a single atomic transaction. The flow is:

1. Check if the template is already installed in the workspace (via `workspace_template_installations`)
2. Fetch the hydrated manifest from `ManifestCatalogService.getManifestByKey()`
3. Partition entity types into "create" vs "reuse" — if an entity type with the same key already exists in the workspace, it is reused rather than duplicated
4. Create new entity types by translating string-keyed manifest schemas into UUID-keyed internal schemas
5. Create relationships by resolving string entity type keys to workspace UUIDs
6. Apply semantic metadata from catalog definitions onto the new entity types and attributes
7. Record the installation in `workspace_template_installations` for idempotency

This is handled by `TemplateInstallationService.installTemplate()` and exposed via `POST /api/v1/templates/{workspaceId}/install`.

### What is a bundle and how does it differ from a template

A **template** defines a set of entity types, relationships, and semantic metadata that can be installed into a workspace. A **bundle** is a curated collection of templates — it references multiple template keys and installs them all together.

Key differences:

- Templates are resolved at manifest load time (entity types, relationships, field mappings are all resolved and stored in the catalog)
- Bundles are resolved at installation time — the bundle manifest only stores a list of template keys. Entity type resolution happens when the bundle is installed into a workspace
- Bundle installation deduplicates shared entity types across templates (if two templates in a bundle define the same entity type key, they are merged)
- Bundle installation is atomic — if any template fails, the entire bundle rolls back

Bundles use `ManifestType.BUNDLE` in the catalog and are managed by `TemplateInstallationService.installBundle()`.

### What happens if a template is installed twice in the same workspace

Nothing destructive. The `workspace_template_installations` table has a unique constraint on `(workspace_id, manifest_key)`. When `installTemplate()` detects an existing installation record, it returns early with a zero-count response (no entity types created, no relationships created). The endpoint returns 200, not an error.

For bundles, each constituent template is checked individually — already-installed templates are skipped, but their entity type IDs are resolved so that cross-template relationships can still reference them.

### How are entity types deduplicated during bundle installation

When multiple templates in a bundle define entity types with the same key, the service merges them:

- The first template's definition is used as the base
- Attributes from later templates are added if the key doesn't already exist (additive merge)
- Conflicting attribute definitions (same key, different schema) keep the first definition and log a WARN
- Semantic metadata is merged with deduplication by `(targetType, targetId)` pair

Additionally, if an entity type key already exists in the workspace (from a previous template or manual creation), it is reused rather than recreated. The existing entity type ID is included in the merged ID map so relationships can reference it.

### What is the relationship between catalog entity types and workspace entity types

Catalog entity types (`catalog_entity_types` table) and workspace entity types (`entity_types` table) are structurally similar but serve different purposes:

- **Catalog entity types** are global, system-managed definitions loaded from manifest JSON files at startup. They use string keys for attributes and store raw manifest schemas. They belong to the Catalog domain.
- **Workspace entity types** are workspace-scoped, user-facing definitions. They use UUID keys for attributes and store fully typed `Schema<UUID>` objects. They belong to the Entities domain.

Template installation translates from one to the other: string attribute keys become UUIDs, manifest type/format strings become `DataType`/`DataFormat` enums, and identifier keys are resolved. The `attribute_mappings` JSONB on `workspace_template_installations` captures the string-to-UUID mapping for traceability.

### How does the manifest pipeline handle bundles

Bundles are the 4th phase of the manifest loading pipeline (after models, templates, integrations):

1. **Scanning:** `ManifestScannerService.scanBundles()` scans `bundles/*/manifest.json` from the classpath and validates against `bundle.schema.json`
2. **Resolution:** `ManifestResolverService.resolveBundle()` extracts key, name, description, manifestVersion, and templateKeys — no entity type resolution
3. **Persistence:** `ManifestUpsertService.upsertBundle()` persists the catalog entry with `templateKeys` JSONB. Bundles have no child rows (no entity types, relationships, or field mappings in the catalog)
4. **Reconciliation:** Bundles are included in the seen-set for stale reconciliation, same as other manifest types

Bundle loading has no ordering dependency on other phases since bundles don't reference models or resolve entity types at load time.

### Why does the Catalog domain have a REST controller

The Catalog domain was originally consumed only via direct service injection — no HTTP API surface. `TemplateController` was added as the domain's first and only REST controller because template installation is a user-initiated action (not a background pipeline step). The controller exposes four endpoints:

- `GET /api/v1/templates` — list available templates
- `POST /api/v1/templates/{workspaceId}/install` — install a template
- `GET /api/v1/templates/bundles` — list available bundles
- `POST /api/v1/templates/{workspaceId}/install-bundle` — install a bundle

This is intentionally limited — the rest of the Catalog domain (manifest loading, reconciliation, health) remains internal.

### What cross-domain dependencies does template installation introduce

`TemplateInstallationService` writes to Entities-domain tables via service injection:

- `EntityTypeRepository` — creates workspace-scoped entity type rows
- `EntityTypeRelationshipService` — creates relationship definitions and fallback CONNECTED_ENTITIES definitions
- `EntityTypeSemanticMetadataService` — initializes semantic metadata scaffolding and upserts entity-type/attribute-level metadata

This is the Catalog domain's only write path into the Entities domain. The dependency is unidirectional (Catalog → Entities) and all writes happen within a single `@Transactional` boundary.

### How does the content hash work for bundles vs manifests

Both use SHA-256 content hashing for idempotency, but hash different fields:

- **Manifests** hash: name, description, manifestVersion, entityTypes, relationships, fieldMappings
- **Bundles** hash: name, description, manifestVersion, templateKeys

When the hash matches on reload, only the `lastLoadedAt` timestamp is touched — no child rows are deleted or recreated. For bundles this is simpler since bundles have no child rows at all.
