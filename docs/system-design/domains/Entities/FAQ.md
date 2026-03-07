This document outlines all common answers to questions needed when forming general understandings behind the underlying structure of the entity ecosystem

### How are entity relationships defined

Relationships are defined at the **type level** via `relationship_definitions` and `relationship_target_rules` tables. A definition belongs to a source entity type and specifies a name, cardinality default, icon, and whether it allows polymorphic targets. Target rules specify which entity types can be targets of the relationship, with optional cardinality overrides and per-rule inverse names.

Definitions are created through `EntityTypeRelationshipService.createRelationshipDefinition()` and are orchestrated by `EntityTypeService.saveEntityTypeDefinition()`. At the instance level, actual links between entities are stored in the `entity_relationships` table and managed by `EntityRelationshipService`.

### What metadata is associated with an entity

Each entity instance has a JSONB `payload` containing attribute values validated against the entity type's schema. Entities also carry denormalized fields from their type: `type_key`, `identifier_key`, `icon_type`, `icon_colour`. Provenance metadata (source type, integration ID, external ID, sync timestamps) tracks where the entity data originated.

At the type level, semantic metadata (`entity_type_semantic_metadata` table) provides definitions, classifications, and tags for the entity type itself, its attributes, and its relationships. This uses a single-table discriminator pattern with `target_type` distinguishing ENTITY_TYPE, ATTRIBUTE, and RELATIONSHIP metadata.

### How do multi-entity relationships work

Relationships support multiple target types through **target rules**. A single relationship definition can have multiple `RelationshipTargetRuleEntity` records, each pointing to a different target entity type with its own cardinality override and inverse name. Alternatively, a definition can use `allowPolymorphic = true` with no target rules, allowing links to any entity type.

**Semantic group constraints** allow rules to target entity types by group membership (e.g., all OPERATIONAL types) rather than explicit IDs, though this matching is still being implemented.

Cardinality is enforced at write time using pessimistic write locks to serialize concurrent link creation for the same source + definition pair.

### How does relationship bidirectionality work

All relationships are **always bidirectional**. When entity A has a relationship to entity B, entity B automatically sees an inverse relationship back to A. There is no flag to control this — bidirectionality is guaranteed.

**Forward direction:** The source entity type owns the `relationship_definition`. Links from entities of this type are queried via `findEntityLinksBySourceId`.

**Inverse direction:** Target entity types see the relationship via `findInverseEntityLinksByTargetId`, which joins through target rules to determine which definitions apply. The `inverse_name` on each target rule provides the label shown from the target's perspective.

**Exclusions:** If an entity type is implicitly included in a relationship (via semantic group or polymorphic matching) but wants to opt out, it can create an **exclusion** in `relationship_definition_exclusions`. Exclusions are respected in both definition resolution and inverse link queries. For explicit target rules, the rule is simply deleted instead of creating an exclusion.

### How can an entity type opt out of a relationship it didn't define

Through the **target-side exclusion** mechanism. When a relationship definition uses polymorphic or semantic group targeting, any entity type can exclude itself by sending a `DeleteRelationshipDefinitionRequest` with the `sourceEntityTypeKey` field set. This triggers `EntityTypeRelationshipService.excludeEntityTypeFromDefinition()`, which:

1. Checks if there's an explicit target rule for this type → deletes the rule
2. Otherwise (semantic/polymorphic match) → creates an exclusion record in `relationship_definition_exclusions`
3. Soft-deletes any existing instance links between this type's entities and the definition
4. Uses the two-pass impact pattern: returns impact count first, executes on confirmation

Exclusions can be reversed with `removeExclusion()`, which hard-deletes the exclusion record and re-enables the relationship.

### What is the CONNECTED_ENTITIES system relationship

Every entity type gets a system-managed `CONNECTED_ENTITIES` relationship definition created automatically at publish time. This definition has `protected = true` (cannot be deleted), `allowPolymorphic = true` (can link to any type), and `cardinalityDefault = MANY_TO_MANY`. It has no target rules.

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
   - Hard-deletes all exclusion records
   - Cleans up semantic metadata
   - Logs activity

Protected definitions (like CONNECTED_ENTITIES) cannot be deleted and throw `IllegalStateException`.

### What tables make up the relationship system

| Table | Purpose | Delete Strategy |
|-------|---------|-----------------|
| `relationship_definitions` | Type-level relationship configuration (name, cardinality, icon, polymorphism) | Soft-delete |
| `relationship_target_rules` | Per-target-type rules (which types can be targets, cardinality overrides, inverse names) | Hard-delete |
| `relationship_definition_exclusions` | Target-side opt-out records (entity type excluded from a definition) | Hard-delete |
| `entity_relationships` | Instance-level links between entities | Soft-delete |
