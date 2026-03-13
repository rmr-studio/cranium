---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-03-09
Updated: 2026-03-12
Domains:
  - "[[Catalog]]"
---
# TemplateInstallationService

Part of [[Template Installation]]

## Purpose

Orchestrates template and bundle installation into workspaces — reads fully-resolved templates from the manifest catalog, creates workspace-scoped entity types with UUID-keyed schemas, establishes relationships, applies semantic metadata, and records installation state, all within a single atomic transaction.

---

## Responsibilities

- Install individual templates into workspaces, creating entity types, relationships, and semantic metadata atomically
- Install bundles by resolving all referenced templates, deduplicating shared entity types, and creating everything in one transaction
- Translate string-keyed manifest schemas into UUID-keyed internal schemas with proper DataType/DataFormat mapping
- Resolve cross-template entity type references for bundle installations (reusing existing workspace entity types)
- Track installation state via `workspace_template_installations` for idempotency (skip already-installed templates)
- Apply entity-type-level and attribute-level semantic metadata from catalog definitions
- Log activity for all template and bundle installations

---

## Dependencies

- [[ManifestCatalogService]] — reads fully-hydrated template/bundle definitions from the catalog
- `EntityTypeRepository` — persists workspace-scoped entity type entities (cross-domain: [[Entities]])
- [[WorkspaceTemplateInstallationRepository]] — tracks template installation state per workspace
- `EntityTypeRelationshipService` — creates relationship definitions and fallback definitions (cross-domain: [[Entities]])
- `EntityTypeSemanticMetadataService` — initializes and upserts semantic metadata (cross-domain: [[Entities]])
- `AuthTokenService` — retrieves authenticated user ID for activity logging
- `ActivityService` — logs template/bundle installation activity
- `KLogger` — structured logging

## Used By

- [[TemplateController]] — delegates template and bundle installation requests
- [[OnboardingService]] — installs templates and bundles during onboarding via `installTemplateInternal` and `installBundleInternal`

---

## Key Logic

**Template installation flow (`installTemplate`):**

1. Check `workspace_template_installations` for existing installation — return early with zero-count response if already installed
2. Fetch fully-hydrated manifest from catalog via `ManifestCatalogService.getManifestByKey()`
3. Partition entity types into "create" vs "reuse" sets by checking workspace for existing entity types with matching keys
4. Create new entity types: translate string-keyed schemas to UUID-keyed, generate attribute UUIDs, initialize semantic metadata scaffolding, create fallback relationship definitions
5. Create relationships: resolve string entity type keys to workspace UUIDs, map target rules, apply relationship-level semantic metadata from catalog
6. Apply entity-type and attribute-level semantic metadata from catalog definitions
7. Record installation in `workspace_template_installations` with attribute key mappings JSONB
8. Log activity

**Bundle installation flow (`installBundle`):**

1. Fetch bundle detail from catalog, getting the list of template keys
2. Partition templates into install vs skip sets by checking existing installations
3. For skipped templates, resolve their entity type IDs from the workspace (needed for cross-template relationships)
4. Load all manifests for templates-to-install, merge and deduplicate entity types across templates
5. Partition merged entity types into create vs reuse (checking workspace for existing matches)
6. Create entity types, then create all relationships across all manifests using the merged ID map
7. Apply semantic metadata, record per-template installations, log bundle activity

**Entity type schema translation:**

Manifest schemas use string attribute keys (e.g., `"name"`, `"email"`). The installation translates these to UUID-keyed schemas:
- Generate a UUID for each attribute
- Map manifest `type` strings to `DataType` enum values
- Map manifest `format` strings (JSON Schema conventions like `"date-time"`, `"phone-number"`) to `DataFormat` enum values via a companion object mapping table
- Resolve `identifierKey` from string to UUID
- Build `EntityTypeAttributeColumn` entries for each attribute

**Entity type deduplication (bundles):**

When multiple templates in a bundle reference the same entity type key:
- First occurrence wins for the base definition
- Attributes from later templates are merged additively (new keys added, existing keys preserved)
- Conflicting attribute definitions (same key, different schema) keep the first definition with a WARN log
- Semantic metadata is merged with deduplication by `(targetType, targetId)` pair

**Relationship semantic metadata resolution:**

Relationship semantics are stored on the source entity type's `semanticMetadata` list in the catalog, with `targetType = RELATIONSHIP` and `targetId = relationshipKey`. The service looks these up during relationship creation and passes them through as `SaveSemanticMetadataRequest`.

---

## Public Methods

### `installTemplate(workspaceId: UUID, templateKey: String): TemplateInstallationResponse`

Installs a single template into a workspace atomically. Returns early with zero counts if already installed. Creates entity types, relationships, and semantic metadata. Annotated with `@Transactional` and `@PreAuthorize`. Delegates to `installTemplateInternal`.

### `installTemplateInternal(workspaceId: UUID, templateKey: String): TemplateInstallationResponse`

Internal method without `@PreAuthorize` — used by [[OnboardingService]] when the workspace role is not yet in the JWT. Contains the full template installation logic (identical to `installTemplate` but bypasses workspace access check). Annotated with `@Transactional`.

### `installBundle(workspaceId: UUID, bundleKey: String): BundleInstallationResponse`

Installs all templates in a bundle, skipping already-installed ones. Deduplicates shared entity types across templates and resolves cross-template relationships. Returns summary of installed/skipped templates. Annotated with `@Transactional` and `@PreAuthorize`. Delegates to `installBundleInternal`.

### `installBundleInternal(workspaceId: UUID, bundleKey: String): BundleInstallationResponse`

Internal method without `@PreAuthorize` — used by [[OnboardingService]] when the workspace role is not yet in the JWT. Contains the full bundle installation logic (identical to `installBundle` but bypasses workspace access check). Annotated with `@Transactional`.

---

## Data Access

**Entities read:**
- `ManifestCatalogEntity` — via ManifestCatalogService (catalog template/bundle definitions)
- `EntityTypeEntity` — workspace entity type lookup for deduplication
- `WorkspaceTemplateInstallationEntity` — installation state checks

**Entities written:**
- `EntityTypeEntity` — new workspace-scoped entity types (cross-domain: Entities)
- `WorkspaceTemplateInstallationEntity` — installation tracking records

**Cross-domain writes (via service injection):**
- Entity type relationships — via `EntityTypeRelationshipService`
- Semantic metadata — via `EntityTypeSemanticMetadataService`

---

## Gotchas

- **Cross-domain write dependency:** This service writes to Entities-domain tables (entity_types, entity_type_relationships, entity_type_semantic_metadata) via service injection. This is the Catalog domain's only write path into Entities.
- **Idempotency is per-template, not per-entity-type:** If a template is already installed (tracked in `workspace_template_installations`), the entire installation is skipped. But entity types that happen to already exist (same key, different template) are reused rather than erroring.
- **Bundle atomicity:** The entire bundle installation is a single `@Transactional` — if any template in the bundle fails, all entity types and relationships created by the bundle are rolled back, including those from previously-successful templates within the same bundle.
- **Attribute UUID generation is non-deterministic:** Each installation generates fresh UUIDs for attributes. The `attributeMappings` JSONB on the installation record captures the string→UUID mapping for future reference. Re-installing the same template in a different workspace produces different attribute UUIDs.
- **DataIntegrityViolationException handling:** The `recordTemplateInstallation` method catches `DataIntegrityViolationException` and rethrows as `ConflictException` — this handles the race condition where two concurrent installations of the same template could pass the initial check.
- **First-wins on schema merge conflicts:** When bundle templates define the same entity type with different attribute schemas, the first template's definition wins. This is logged at WARN but does not fail the installation.

---

## Related

- [[ManifestCatalogService]] — provides the fully-hydrated template/bundle definitions
- [[TemplateController]] — REST endpoint delegation
- [[WorkspaceTemplateInstallationEntity]] — installation tracking entity
- [[Template Installation]] — parent subdomain
