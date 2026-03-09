This document outlines all common answers to questions needed when forming general understandings behind the underlying structure of the entity ecosystem

### How are entity relationships defined

Relationships are defined at the **type level** via `relationship_definitions` and `relationship_target_rules` tables. A definition belongs to a source entity type and specifies a name, cardinality default, icon, and system type. Target rules specify which entity types can be targets of the relationship, with optional cardinality overrides and per-rule inverse names. Every target rule requires an explicit `targetEntityTypeId`.

Definitions are created through `EntityTypeRelationshipService.createRelationshipDefinition()` and are orchestrated by `EntityTypeService.saveEntityTypeDefinition()`. At the instance level, actual links between entities are stored in the `entity_relationships` table and managed by `EntityRelationshipService`.

### What metadata is associated with an entity

Each entity instance has a JSONB `payload` containing attribute values validated against the entity type's schema. Entities also carry denormalized fields from their type: `type_key`, `identifier_key`, `icon_type`, `icon_colour`. Provenance metadata (source type, integration ID, external ID, sync timestamps) tracks where the entity data originated.

At the type level, semantic metadata (`entity_type_semantic_metadata` table) provides definitions, classifications, and tags for the entity type itself, its attributes, and its relationships. This uses a single-table discriminator pattern with `target_type` distinguishing ENTITY_TYPE, ATTRIBUTE, and RELATIONSHIP metadata.

### How do multi-entity relationships work

Relationships support multiple target types through **target rules**. A single relationship definition can have multiple `RelationshipTargetRuleEntity` records, each pointing to a different target entity type with its own cardinality override and inverse name. Every target rule requires an explicit `targetEntityTypeId` — there is no semantic group or wildcard targeting.

Polymorphic relationships (linking to any entity type without explicit rules) are reserved for system-managed definitions only. A definition is polymorphic when `systemType != null`, exposed via the computed `isPolymorphic` property on the `RelationshipDefinition` model.

Cardinality is enforced at write time using pessimistic write locks to serialize concurrent link creation for the same source + definition pair.

### How does relationship bidirectionality work

All relationships are **always bidirectional**. When entity A has a relationship to entity B, entity B automatically sees an inverse relationship back to A. There is no flag to control this — bidirectionality is guaranteed.

**Forward direction:** The source entity type owns the `relationship_definition`. Links from entities of this type are queried via `findEntityLinksBySourceId`.

**Inverse direction:** Target entity types see the relationship via `findInverseEntityLinksByTargetId`, which joins through explicit target rules to determine which definitions apply. The `inverse_name` on each target rule provides the label shown from the target's perspective.

### How can an entity type opt out of a relationship it didn't define

Through the `excludeEntityTypeFromDefinition()` method on `EntityTypeRelationshipService`. Since all user-defined relationships require explicit target rules, opting out means deleting the target rule for that entity type. The method:

1. Deletes the explicit target rule for this entity type from the definition
2. Soft-deletes any existing instance links between this type's entities and the definition
3. Uses the two-pass impact pattern: returns impact count first, executes on confirmation

### What is the CONNECTED_ENTITIES system relationship

Every entity type gets a system-managed `CONNECTED_ENTITIES` relationship definition created automatically at publish time. This definition has `protected = true` (cannot be deleted), `systemType = CONNECTED_ENTITIES` (which makes it polymorphic via the computed `isPolymorphic` property), and `cardinalityDefault = MANY_TO_MANY`. It has no target rules — polymorphic definitions are the only ones that can link to any entity type without explicit rules.

This provides a lightweight "connected" capability for every entity type without requiring users to define explicit relationships. It powers the generic entity connection UI. The definition is created by `EntityTypeRelationshipService.createFallbackDefinition()` and retrieved via `getOrCreateFallbackDefinition()` (which handles lazy creation for pre-existing types).

### How is semantic metadata attached to a particular entity/connection

Semantic metadata is stored in the `entity_type_semantic_metadata` table using a single-table discriminator pattern. The `target_type` column distinguishes between:

- `ENTITY_TYPE` — metadata about the entity type itself (target_id = entity type ID)
- `ATTRIBUTE` — metadata about a specific attribute (target_id = attribute UUID key)
- `RELATIONSHIP` — metadata about a relationship definition (target_id = definition ID)

Each record can have a `definition` (text description), `classification` (enum: IDENTIFIER, CATEGORICAL, QUANTITATIVE, etc.), and `tags` (JSONB array). Metadata is initialized when definitions are created and cleaned up when they are deleted, managed by `EntityTypeSemanticMetadataService`.

### How are [[Integrations]] connected into the entity ecosystem

Entities track their provenance through fields on the `entities` table: `source_type` (USER_CREATED, INTEGRATION, etc.), `source_integration_id` (FK to `integration_definitions`), `source_external_id`, `source_url`, `first_synced_at`, `last_synced_at`, and `sync_version`. Per-attribute provenance is tracked in `entity_attribute_provenance` with override tracking.

Entity relationships also carry a `link_source` field indicating whether the link was user-created or integration-sourced.

### What happens when a relationship definition is deleted

Deletion uses the **two-pass impact pattern**:

1. **First call** (`impactConfirmed = false`): counts existing instance links. If any exist, returns a `DeleteDefinitionImpact` with the count so the UI can show a confirmation dialog.
2. **Second call** (`impactConfirmed = true`): executes the full deletion cascade:
   - Soft-deletes all entity relationship links for the definition
   - Soft-deletes the definition itself
   - Hard-deletes all target rules (configuration data)
   - Cleans up semantic metadata
   - Logs activity

Protected definitions (like CONNECTED_ENTITIES) cannot be deleted and throw `IllegalStateException`.

### What tables make up the relationship system

| Table | Purpose | Delete Strategy |
|-------|---------|-----------------|
| `relationship_definitions` | Type-level relationship configuration (name, cardinality, icon, system type) | Soft-delete |
| `relationship_target_rules` | Per-target-type rules (which types can be targets, cardinality overrides, inverse names) | Hard-delete |
| `entity_relationships` | Instance-level links between entities | Soft-delete |

### How are entity types created from catalog templates

When a user installs a template (or bundle) via `TemplateInstallationService`, entity types are created by translating catalog definitions into workspace-scoped entity types:

1. Catalog entity types use string attribute keys (e.g., `"name"`, `"email"`) — these are converted to UUID keys via `UUID.randomUUID()` for each attribute
2. Manifest `type` strings (e.g., `"string"`, `"number"`) are mapped to `DataType` enum values
3. Manifest `format` strings (JSON Schema conventions like `"date-time"`, `"phone-number"`) are mapped to `DataFormat` enum values via a static mapping table
4. The `identifierKey` is resolved from string to UUID
5. Semantic metadata scaffolding is initialized via `EntityTypeSemanticMetadataService.initializeForEntityType()`
6. A fallback `CONNECTED_ENTITIES` relationship definition is created via `EntityTypeRelationshipService.createFallbackDefinition()`

The created entity types are standard workspace entity types — they can be queried, have entities created against them, and participate in relationships exactly like manually-created types. The `workspace_template_installations` table in the Catalog domain tracks which templates produced which entity types.

### Can template-created entity types be modified or deleted

Yes. Once created, template-installed entity types are indistinguishable from manually-created ones at the Entities domain level. They are not marked as `protected` and can be modified or deleted through the normal entity type APIs.

The `workspace_template_installations` record persists even if the entity types are later modified or deleted — it tracks that the template was installed, not that its entity types are unchanged. Re-installing the same template will return early (zero-count response) based on the installation record, regardless of what happened to the entity types afterward.

### How do template relationships interact with existing entity types

During template installation, if an entity type key already exists in the workspace, it is **reused** rather than duplicated. The existing entity type's UUID is included in the ID map used for relationship creation. This means template relationships can reference pre-existing entity types seamlessly.

For bundles, this extends further: skipped templates (already installed) have their entity type IDs resolved from the workspace so that cross-template relationships in newly-installed templates can reference entity types from previously-installed templates.
