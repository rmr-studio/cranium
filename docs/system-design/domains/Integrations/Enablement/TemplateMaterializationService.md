---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2025-07-17
Domains:
  - "[[Integrations]]"
---
# TemplateMaterializationService

Part of [[Enablement]]

## Purpose

Creates workspace-scoped entity types and relationships from global catalog definitions when an integration is connected. Bridges the catalog layer (string-keyed, global) and the entity layer (UUID-keyed, workspace-scoped) by converting catalog templates into materialized `EntityTypeEntity` and `RelationshipDefinitionEntity` instances.

---

## Responsibilities

- Convert catalog entity type schemas (string-keyed attribute maps) into workspace-scoped `EntityTypeSchema` objects with UUID-keyed attributes
- Generate deterministic UUID v3 attribute keys from integration slug, entity type key, and attribute key for idempotent reconnection
- Create new entity types from catalog templates with `sourceType = INTEGRATION`, `readonly = true`, `protected = true`
- Restore soft-deleted entity types on reconnection instead of creating duplicates
- Skip already-existing entity types on reconnection
- Materialize relationship definitions and target rules from catalog relationship entries
- Deduplicate relationships on reconnect (skip existing, restore soft-deleted)
- Initialize semantic metadata for newly created entity types
- Create fallback CONNECTED_ENTITIES relationship definitions for each materialized entity type
- Initialize ID sequences for attributes with `SchemaType.ID`
- Build column configurations from catalog column definitions or schema property ordering
- Resolve catalog identifier keys to deterministic UUIDs

---

## Dependencies

- `EntityTypeRepository` — Persistence and lookup of workspace-scoped entity types, including soft-deleted queries
- `RelationshipDefinitionRepository` — Persistence and deduplication of materialized relationship definitions
- `RelationshipTargetRuleRepository` — Persistence of relationship target rules
- `CatalogEntityTypeRepository` — Reads catalog entity type templates by manifest ID
- `CatalogRelationshipRepository` — Reads catalog relationship definitions by manifest ID
- `CatalogRelationshipTargetRuleRepository` — Reads catalog target rules by relationship IDs
- `ManifestCatalogRepository` — Resolves integration slug to manifest entry
- [[EntityTypeSemanticMetadataService]] — Initializes semantic metadata records for materialized entity types and their attributes
- [[EntityTypeRelationshipService]] — Creates fallback CONNECTED_ENTITIES relationship definitions
- [[EntityTypeSequenceService]] — Initializes ID sequences for `SchemaType.ID` attributes
- `ObjectMapper` — JSON processing (injected but used for potential schema transformations)
- `KLogger` — Structured logging

## Used By

- [[IntegrationEnablementService]] — Calls `materializeIntegrationTemplates()` during the integration enable lifecycle

---

## Key Logic

### Materialization overview

The service converts global catalog templates into workspace-scoped entity types and relationships in a single transactional operation. The flow is:

1. Look up the integration manifest by slug via `ManifestCatalogRepository`
2. Fetch all `CatalogEntityTypeEntity` and `CatalogRelationshipEntity` entries for that manifest
3. Query the workspace for existing and soft-deleted entity types matching the catalog keys
4. Materialize entity types (create new, restore soft-deleted, skip existing) and build a `keyToIdMap` (string key to workspace UUID)
5. Materialize relationships using the `keyToIdMap` to resolve string-keyed source/target references to workspace UUIDs
6. Return a `MaterializationResult` with counts and entity type summaries

### Schema conversion

Catalog schemas are stored as `Map<String, Any>` with string attribute keys and loosely-typed attribute definition maps. The conversion process:

1. Iterates each attribute key in the catalog schema
2. Generates a deterministic UUID v3 for the attribute key (see below)
3. Parses the attribute definition map to extract `SchemaType`, `DataType`, `DataFormat`, label, required/unique/protected flags
4. Builds a `Schema<UUID>` for each attribute
5. Wraps all attributes in an `EntityTypeSchema` (a `Schema<UUID>` with `key = SchemaType.OBJECT`)

Unknown or null schema types fall back to `SchemaType.TEXT` / `DataType.STRING`. Data format is nullable and returns null if unrecognized.

### Deterministic UUID generation

Attribute UUIDs are generated using `UUID.nameUUIDFromBytes()` (UUID v3, MD5-based) with the input string pattern:

```
{integrationSlug}:{entityTypeKey}:{attributeKey}
```

This guarantees that the same integration, entity type, and attribute always produce the same UUID regardless of when or how many times materialization runs. This is critical for:

- **Idempotent reconnection** — re-enabling an integration does not create duplicate attributes
- **Identifier key resolution** — the catalog's string-based `identifierKey` is resolved to the same UUID used in the schema
- **Column configuration** — catalog column definitions reference string keys that map to the same deterministic UUIDs

If no `identifierKey` is set on a catalog entity type, a random UUID is generated as fallback.

### Deduplication on reconnect

When materializing entity types, the service handles three cases per catalog entry:

1. **Soft-deleted** — An entity type with a matching key exists but is soft-deleted. The service restores it by clearing `deleted` and `deletedAt`, reusing the existing entity and its UUID.
2. **Already existing** — An active entity type with a matching key exists. The service skips creation but includes its ID in the `keyToIdMap` so relationships can still reference it.
3. **New** — No matching entity type exists. The service creates a new one from the catalog template.

Soft-deleted entries are checked via a dedicated `findSoftDeletedByWorkspaceIdAndKeyIn()` repository query that bypasses the `@SQLRestriction("deleted = false")` filter.

### Relationship materialization

After entity types are materialized, the service processes catalog relationships:

1. Fetches all `CatalogRelationshipTargetRuleEntity` entries for the batch of catalog relationships
2. Groups target rules by catalog relationship ID
3. For each catalog relationship:
   - Resolves the source entity type key to a workspace UUID via `keyToIdMap` (skips if unresolvable)
   - Checks for an existing active relationship with the same workspace, source, and name — skips if found
   - Checks for a soft-deleted relationship — restores if found
   - Otherwise creates a new `RelationshipDefinitionEntity` with `protected = true`
   - Materializes target rules by resolving each target entity type key to its workspace UUID

### Semantic metadata initialization

For each newly created entity type, the service calls:

1. `semanticMetadataService.initializeForEntityType()` — creates metadata records for the entity type and each attribute
2. `relationshipService.createFallbackDefinition()` — creates the system-managed CONNECTED_ENTITIES relationship
3. Iterates schema properties to find `SchemaType.ID` attributes and initializes their sequences

This brings materialized entity types to parity with user-published entity types.

### Fallback relationship handling

Every materialized entity type receives a fallback CONNECTED_ENTITIES relationship definition via `relationshipService.createFallbackDefinition()`. This is the same system-managed definition that user-published entity types receive, ensuring integration-sourced types behave identically in the relationship system.

### ID sequence generation

Schema attributes with `key = SchemaType.ID` require an auto-incrementing sequence. During initialization, the service iterates all schema properties and calls `sequenceService.initializeSequence(entityTypeId, attributeId)` for each ID-type attribute.

---

## Public Methods

### `materializeIntegrationTemplates(workspaceId: UUID, integrationSlug: String): MaterializationResult`

Materializes all entity types and relationships for an integration into a workspace. Transactional — all entity types are saved and flushed before relationships are created so that IDs are available for foreign key resolution.

**Returns:** `MaterializationResult` containing `entityTypesCreated`, `entityTypesRestored`, `relationshipsCreated`, `integrationSlug`, and a list of `EnabledEntityTypeSummary` for each created or restored entity type.

**Throws:** `NotFoundException` if the integration manifest does not exist for the given slug.

---

## Gotchas

- **UUID determinism is load-bearing.** The `{slug}:{entityType}:{attribute}` pattern used for `UUID.nameUUIDFromBytes()` must remain stable across versions. Changing the pattern would break reconnection idempotency and create duplicate attributes.
- **Readonly and protected flags.** All materialized entity types are created with `readonly = true` and `protected = true`. All materialized relationship definitions are created with `protected = true`. These flags prevent users from modifying integration-sourced types through the standard entity type editing API.
- **Catalog dependency.** Materialization requires catalog data to be seeded before it can run. If `CatalogEntityTypeRepository.findByManifestId()` returns an empty list, the service returns an empty `MaterializationResult` without error.
- **Flush before relationships.** Entity types are explicitly flushed after save (`entityTypeRepository.flush()`) so that their generated IDs are available for relationship foreign keys. Without this, JPA deferred writes could cause FK resolution failures.
- **Soft-delete bypass queries.** The `findSoftDeletedByWorkspaceIdAndKeyIn()` and `findSoftDeletedByWorkspaceIdAndSourceEntityTypeIdAndName()` repository methods use custom queries to bypass the `@SQLRestriction("deleted = false")` filter. These are required for the restore-on-reconnect flow.
- **Unresolvable target rules are silently skipped.** If a catalog relationship target rule references an entity type key that is not in the `keyToIdMap` (e.g., the target type belongs to a different integration), the rule is skipped with a warning log rather than failing the entire materialization.
- **Random UUID fallback for missing identifier key.** If a catalog entity type has no `identifierKey`, a random UUID is used. This means the identifier key will differ across reconnections for that type, which may affect entity identity resolution.

---

## Related

- [[IntegrationEnablementService]] — Orchestrates the enable/disable lifecycle, calls this service during enable
- [[EntityTypeService]] — Standard entity type management; materialized types appear alongside user-created types
- [[EntityTypeSemanticMetadataService]] — Semantic metadata initialization
- [[EntityTypeRelationshipService]] — Fallback relationship creation
- [[EntityTypeSequenceService]] — ID sequence initialization
- [[Enablement]] — Parent subdomain

---

## Changelog

### 2025-07-17

- Initial documentation for `TemplateMaterializationService`
- Covers entity type and relationship materialization, deterministic UUID generation, deduplication on reconnect, semantic metadata initialization, and column configuration building
