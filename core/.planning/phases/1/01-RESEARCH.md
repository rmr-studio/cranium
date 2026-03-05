# Phase 1: Database Foundation - Research

**Researched:** 2026-03-04
**Domain:** JPA entity design, SQL schema creation, JSON Schema authoring, classpath resource scaffolding
**Confidence:** HIGH

## Summary

Phase 1 establishes the catalog schema (6 new tables), JPA entity layer, Spring Data repositories, JSON Schema validation files, and manifest directory scaffolding. The codebase has a clear precedent for global (non-workspace-scoped) entities in `IntegrationDefinitionEntity` -- no `AuditableEntity` base class, no `SoftDeletable`, no RLS, manual `created_at`/`updated_at` timestamps. All 6 catalog entities follow this pattern.

The existing entity domain provides the structural blueprint for the catalog's child tables: `RelationshipDefinitionEntity` and `RelationshipTargetRuleEntity` show the parent-child FK pattern with `ON DELETE CASCADE`, `EntityTypeSemanticMetadataEntity` shows JSONB columns with `JsonBinaryType`, and the `Schema<T>` generic class shows how attribute schemas are structured (with `SchemaType`, `DataType`, `DataFormat` enums). The JSON Schema validator (`networknt json-schema-validator 1.0.83`) is already in the dependency tree and used by `SchemaService`.

**Primary recommendation:** Follow the `IntegrationDefinitionEntity` pattern exactly for all 6 catalog entities. Use the existing `db/schema/` directory conventions with a new `catalog.sql` file in `01_tables/`. Write 3 JSON Schema files (model, template, integration) validated against the manifest structure defined in ADR-004 and refined in CONTEXT.md.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Area 1: Catalog Table Schema**
- Catalog entries are permanent. No `deleted`, `deleted_at`, or `active` columns on `manifest_catalog`.
- Catalog is append/update-only. Reloads upsert over existing rows. No rows are ever removed or deactivated.
- Rename `active` to `stale` on `integration_definitions`. When an integration manifest file disappears from disk, the loader marks `integration_definitions.stale = true`. Staleness only applies to integrations.
- Add `last_loaded_at` to `manifest_catalog`. `TIMESTAMPTZ` updated on each successful upsert.
- `IntegrationDefinitionEntity` stays. It owns platform/connection metadata not part of entity schemas.
- Shared key, no FK constraint. `integration_definitions.slug` and `manifest_catalog.key` use the same value. Application-level link, not DB FK.
- `manifest_catalog.integration_definition_id` is removed. The shared key replaces the explicit FK.
- Catalog entities use manual `created_at` + `updated_at` fields (not `AuditableEntity`, no `createdBy`/`updatedBy`).
- Child tables reference `manifest_catalog.id` via UUID FK with `ON DELETE CASCADE`.
- Key-based references within the catalog (e.g., `source_entity_type_key`) use VARCHAR, not UUID FKs.

**Area 2: Manifest JSON Structure**
- ADR-004 defines the canonical manifest structure.
- Manifest entity type attributes use human-readable string keys in the `properties` map. UUIDs assigned at clone time.
- Manifest schema mirrors the existing `Schema<T>` structure but with string keys instead of UUID keys.
- Relationship `key` fields remain in the manifest JSON for unique identification and idempotent upsert keying.
- Semantics on entity types and attributes: `{ "definition", "classification", "tags" }`.
- Semantics on relationships: `{ "definition", "tags" }` -- no `classification`.
- `targetType` and `targetId` are structural fields resolved at load time, not in manifest JSON.
- `analyticalBriefs` and `exampleQueries` are deferred -- not in Phase 1 JSON Schemas.
- Field mappings table created in Phase 1 but manifest structure for mappings is Phase 2.

**Area 3: entity_types Column Additions**
- entity_types will NOT gain new columns in Phase 1. `integration_definition_id`, `readonly`, and `source_manifest_key` are deferred to clone service phase (v2).

**Area 4: Manifest File Location**
- Phase 1 scaffolding: `src/main/resources/manifests/` -- standard classpath location.
- Directory structure with `schemas/`, `models/`, `templates/`, `integrations/`, `README.md`.
- Models: flat files. Templates: directory-based. Integrations: directory-based.
- JSON Schema files always live on classpath.
- Test fixtures: `src/test/resources/manifests/` -- same structure.

### Claude's Discretion
(None explicitly designated -- all areas have locked decisions)

### Deferred Ideas (OUT OF SCOPE)
- Configurable external manifest path for self-hosters -- Phase 2 loader concern
- `deprecated` flag on catalog entries
- Workspace notification when previously-cloned manifest is updated
- Batch model installation API
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| DB-01 | Catalog tables (6 tables) created following existing `db/schema/` conventions with no RLS and no workspace scope | `IntegrationDefinitionEntity` pattern documented; `db/schema/01_tables/` conventions mapped; table structures from feature design + CONTEXT.md refinements documented |
| DB-02 | JPA entity classes exist for all catalog tables following `IntegrationDefinitionEntity` pattern (no `AuditableEntity`, manual timestamps) | Full `IntegrationDefinitionEntity` source analyzed; annotation patterns, timestamp handling, JSONB usage documented |
| DB-03 | Spring Data repository interfaces exist for all catalog entities | `IntegrationDefinitionRepository` pattern documented; derived query method conventions mapped |
| DB-04 | `manifest_catalog` table has composite unique constraint `UNIQUE(key, manifest_type)` | Constraint patterns from existing codebase documented; placement in `04_constraints/` noted |
| DB-05 | `entity_types` table gains `source_manifest_key` and `readonly` columns | **OVERRIDDEN by CONTEXT.md**: entity_types will NOT gain new columns in Phase 1. This requirement is DEFERRED per user decision. |
| VAL-01 | JSON Schema files exist for model, template, and integration manifest formats | Manifest structure from ADR-004 documented; existing `networknt` validator usage analyzed; classpath loading patterns identified |
| SCAF-01 | Directory structure created for `models/`, `templates/`, `integrations/` with README | Directory structure from CONTEXT.md documented; classpath resource patterns identified |
| SCAF-02 | README documents manifest format, `$ref` syntax, `extend` semantics, relationship shorthand vs full format | ADR-004 provides canonical documentation; format details extracted |
</phase_requirements>

## Standard Stack

### Core (Already in Project)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.5.3 | Application framework | Project foundation |
| Spring Data JPA | (via Boot) | Repository interfaces, entity management | Project standard ORM layer |
| Hibernate | (via Boot) | JPA implementation with `@SQLRestriction`, auditing | Project standard |
| Hypersistence Utils | (via Boot) | `JsonBinaryType` for JSONB columns | Project standard for all JSONB |
| networknt json-schema-validator | 1.0.83 | JSON Schema validation (Draft 2019-09) | Already used by `SchemaService` |
| Jackson | (via Boot) | JSON serialization/deserialization | Project standard |

### No New Dependencies Required

Phase 1 requires no new dependencies. All required libraries are already in `build.gradle.kts`:
- JPA entities: Spring Data JPA + Hibernate
- JSONB columns: Hypersistence `JsonBinaryType`
- JSON Schema files: authored manually, validated by existing `networknt` library
- Classpath resource loading: Spring `ClassPathResource` (core Spring)

## Architecture Patterns

### Recommended Package Structure

New catalog code follows the existing `riven.core.{layer}.{domain}` convention:

```
src/main/kotlin/riven/core/
  entity/catalog/
    ManifestCatalogEntity.kt
    CatalogEntityTypeEntity.kt
    CatalogRelationshipEntity.kt
    CatalogRelationshipTargetRuleEntity.kt
    CatalogFieldMappingEntity.kt
    CatalogSemanticMetadataEntity.kt
  repository/catalog/
    ManifestCatalogRepository.kt
    CatalogEntityTypeRepository.kt
    CatalogRelationshipRepository.kt
    CatalogRelationshipTargetRuleRepository.kt
    CatalogFieldMappingRepository.kt
    CatalogSemanticMetadataRepository.kt
  enums/catalog/
    ManifestType.kt
```

### Pattern 1: IntegrationDefinitionEntity (Global Catalog Entity)

**What:** JPA entity for global (non-workspace-scoped) catalog data with no AuditableEntity inheritance, no SoftDeletable, manual timestamps.
**When to use:** All 6 catalog entities in this phase.

```kotlin
// Source: IntegrationDefinitionEntity.kt (existing codebase)
@Entity
@Table(name = "table_name")
data class ExampleEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "key", nullable = false)
    val key: String,

    // ... domain fields ...

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: ZonedDateTime = ZonedDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: ZonedDateTime = ZonedDateTime.now()
)
```

**Key differences from workspace-scoped entities:**
- Does NOT extend `AuditableEntity` or `AuditableSoftDeletableEntity`
- No `@CreatedBy`/`@LastModifiedBy` (no user context -- system-loaded)
- No `@SQLRestriction("deleted = false")` (no soft delete per CONTEXT.md)
- No `workspace_id` column
- Uses `@UpdateTimestamp` on `updatedAt` (Hibernate auto-manages)
- `createdAt` defaults to `ZonedDateTime.now()` with `updatable = false`

### Pattern 2: JSONB Column with Hypersistence

**What:** Storing JSON payloads in PostgreSQL JSONB columns.
**When to use:** `catalog_entity_types.schema`, `catalog_entity_types.columns`, `catalog_field_mappings.mappings`, `catalog_semantic_metadata.tags`.

```kotlin
// Source: EntityTypeEntity.kt, IntegrationDefinitionEntity.kt (existing codebase)
@Type(JsonBinaryType::class)
@Column(name = "schema", columnDefinition = "jsonb", nullable = false)
var schema: Map<String, Any> = emptyMap()

// For typed JSONB:
@Type(JsonBinaryType::class)
@Column(name = "tags", columnDefinition = "jsonb", nullable = false)
var tags: List<String> = emptyList()
```

**Critical note for catalog schema column:** The existing `EntityTypeEntity` stores schema as `EntityTypeSchema` which is `Schema<UUID>`. Catalog entity types use string keys, so the JSONB column should store the raw schema structure as `Map<String, Any>` (or a catalog-specific type alias). Do NOT use `Schema<UUID>` -- that requires UUID keys. Use `Schema<String>` if typing is desired, but `Map<String, Any>` is simpler since the schema is just stored and later resolved at clone time.

### Pattern 3: Enum with VARCHAR Storage

**What:** Kotlin enum stored as VARCHAR in the database via `@Enumerated(EnumType.STRING)`.
**When to use:** `ManifestType` enum on `manifest_catalog.manifest_type`.

```kotlin
// Source: EntityRelationshipCardinality.kt pattern
enum class ManifestType {
    MODEL,
    TEMPLATE,
    INTEGRATION
}

// On the entity:
@Enumerated(EnumType.STRING)
@Column(name = "manifest_type", nullable = false)
val manifestType: ManifestType
```

### Pattern 4: Parent-Child FK with CASCADE

**What:** Child entities reference parent via UUID FK with `ON DELETE CASCADE`.
**When to use:** All child tables referencing `manifest_catalog.id`.

```kotlin
// Source: RelationshipTargetRuleEntity.kt pattern
@Column(name = "manifest_id", nullable = false, columnDefinition = "uuid")
val manifestId: UUID
```

**No JPA `@ManyToOne` annotation:** The codebase uses plain UUID columns for FK references, not JPA relationship annotations. This is the established pattern -- follow it. Joins are done at the service/repository level via separate queries.

### Pattern 5: Repository Interface

**What:** Spring Data JPA repository with derived query methods.
**When to use:** All 6 catalog repositories.

```kotlin
// Source: IntegrationDefinitionRepository.kt pattern
interface ManifestCatalogRepository : JpaRepository<ManifestCatalogEntity, UUID> {
    fun findByKey(key: String): ManifestCatalogEntity?
    fun findByManifestType(type: ManifestType): List<ManifestCatalogEntity>
    fun findByKeyAndManifestType(key: String, type: ManifestType): ManifestCatalogEntity?
}
```

### Anti-Patterns to Avoid

- **Do NOT extend AuditableEntity:** Catalog entities are system-loaded, not user-created. No `createdBy`/`updatedBy` fields.
- **Do NOT add soft-delete columns:** Per CONTEXT.md, catalog entries are permanent. No `deleted`/`deleted_at` columns on `manifest_catalog`.
- **Do NOT use `@ManyToOne` JPA relationships:** Codebase uses plain UUID FK columns, not JPA relationship mappings.
- **Do NOT add `workspace_id`:** Catalog tables are global.
- **Do NOT use Flyway/Liquibase:** Schema is managed via raw SQL files in `db/schema/`.
- **Do NOT use `Schema<UUID>` for catalog schemas:** Catalog attribute keys are strings, not UUIDs.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JSON Schema validation | Custom validator | `networknt json-schema-validator` (already in deps) | Complex specification; library handles Draft 2019-09 fully |
| UUID generation | Manual UUID creation | `@GeneratedValue(strategy = GenerationType.UUID)` | Hibernate handles it; consistent with all other entities |
| Timestamp management | Manual `ZonedDateTime.now()` in service code | `@UpdateTimestamp` on `updatedAt`, default value on `createdAt` | Hibernate auto-manages; matches `IntegrationDefinitionEntity` |
| JSONB serialization | Custom JSON serializers | `@Type(JsonBinaryType::class)` from Hypersistence | Handles PostgreSQL JSONB transparently; consistent with all other JSONB columns |

## Common Pitfalls

### Pitfall 1: Using AuditableEntity for Catalog Entities
**What goes wrong:** Adding `createdBy`/`updatedBy` columns that are always null because the loader is system, not user.
**Why it happens:** All other entities in the codebase extend `AuditableEntity`. Muscle memory.
**How to avoid:** Follow `IntegrationDefinitionEntity` exactly -- manual `createdAt`/`updatedAt` fields, no `AuditableEntity` inheritance.
**Warning signs:** Entity class extending `AuditableEntity` or `AuditableSoftDeletableEntity`.

### Pitfall 2: DB-05 Confusion -- entity_types Column Changes Deferred
**What goes wrong:** Adding `source_manifest_key` and `readonly` columns to `entity_types` in Phase 1.
**Why it happens:** REQUIREMENTS.md lists DB-05 as Phase 1, but CONTEXT.md explicitly defers these columns to the clone service phase.
**How to avoid:** CONTEXT.md is the authority. Do NOT modify `entity_types` in Phase 1. DB-05 is effectively deferred.
**Warning signs:** SQL altering `entity_types`, new columns in `EntityTypeEntity`.

### Pitfall 3: active Column on manifest_catalog
**What goes wrong:** Adding an `active` boolean column on `manifest_catalog` per the feature design doc.
**Why it happens:** Feature design doc shows `active BOOLEAN DEFAULT TRUE` on the table.
**How to avoid:** CONTEXT.md explicitly says "No `deleted`, `deleted_at`, or `active` columns on `manifest_catalog`." Catalog entries are permanent. Add `last_loaded_at` instead.
**Warning signs:** `active` or `deleted` column in catalog SQL or entity.

### Pitfall 4: integration_definition_id FK on manifest_catalog
**What goes wrong:** Adding an FK from `manifest_catalog` to `integration_definitions`.
**Why it happens:** Feature design doc shows `integration_definition_id UUID FK` on the table.
**How to avoid:** CONTEXT.md explicitly says "manifest_catalog.integration_definition_id is removed." The shared key (`manifest_catalog.key = integration_definitions.slug`) replaces the FK.
**Warning signs:** UUID FK column referencing `integration_definitions` in catalog SQL or entity.

### Pitfall 5: SQL File Naming and Placement
**What goes wrong:** Creating SQL in wrong directory or with wrong naming convention.
**Why it happens:** Not checking existing file naming patterns.
**How to avoid:** New table SQL goes in `db/schema/01_tables/catalog.sql`. New indexes in `02_indexes/catalog_indexes.sql`. New constraints in `04_constraints/catalog_constraints.sql`. Follow the multi-table-per-file pattern used by `entities.sql`.
**Warning signs:** SQL files in root `db/schema/` or with inconsistent naming.

### Pitfall 6: UNIQUE(key, type) vs UNIQUE(key, manifest_type) Column Naming
**What goes wrong:** Using `type` as a column name in PostgreSQL, which is a reserved word in some contexts.
**Why it happens:** Feature design doc uses `type` as the column name.
**How to avoid:** Use `manifest_type` as the column name. CONTEXT.md success criteria says "UNIQUE(key, manifest_type)." The column stores the `ManifestType` enum.
**Warning signs:** Column named just `type` without prefix.

### Pitfall 7: stale Column Rename on IntegrationDefinitionEntity
**What goes wrong:** Forgetting to rename `active` to `stale` on `integration_definitions`.
**Why it happens:** The rename is a CONTEXT.md decision that modifies an existing entity.
**How to avoid:** Rename `active` to `stale` in both the SQL and `IntegrationDefinitionEntity`. Note the semantic inversion: `active = true` becomes `stale = false` (default state). When an integration manifest disappears, `stale = true`.
**Warning signs:** `IntegrationDefinitionEntity` still has `active` field after Phase 1.

## Code Examples

### Example 1: ManifestCatalogEntity (Primary Catalog Entity)

```kotlin
// Following IntegrationDefinitionEntity pattern exactly
@Entity
@Table(
    name = "manifest_catalog",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["key", "manifest_type"])
    ]
)
data class ManifestCatalogEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "key", nullable = false)
    val key: String,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "description")
    val description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "manifest_type", nullable = false)
    val manifestType: ManifestType,

    @Column(name = "manifest_version")
    val manifestVersion: String? = null,

    @Column(name = "last_loaded_at")
    var lastLoadedAt: ZonedDateTime? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: ZonedDateTime = ZonedDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: ZonedDateTime = ZonedDateTime.now()
)
```

### Example 2: CatalogEntityTypeEntity (Child Table)

```kotlin
@Entity
@Table(
    name = "catalog_entity_types",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["manifest_id", "key"])
    ]
)
data class CatalogEntityTypeEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "manifest_id", nullable = false, columnDefinition = "uuid")
    val manifestId: UUID,

    @Column(name = "key", nullable = false)
    val key: String,

    @Column(name = "display_name_singular", nullable = false)
    val displayNameSingular: String,

    @Column(name = "display_name_plural", nullable = false)
    val displayNamePlural: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "icon_type", nullable = false)
    val iconType: IconType = IconType.CIRCLE_DASHED,

    @Enumerated(EnumType.STRING)
    @Column(name = "icon_colour", nullable = false)
    val iconColour: IconColour = IconColour.NEUTRAL,

    @Enumerated(EnumType.STRING)
    @Column(name = "semantic_group", nullable = false)
    val semanticGroup: SemanticGroup = SemanticGroup.UNCATEGORIZED,

    @Column(name = "identifier_key")
    val identifierKey: String? = null,

    @Column(name = "readonly", nullable = false)
    val readonly: Boolean = false,

    @Type(JsonBinaryType::class)
    @Column(name = "schema", columnDefinition = "jsonb", nullable = false)
    var schema: Map<String, Any> = emptyMap(),

    @Type(JsonBinaryType::class)
    @Column(name = "columns", columnDefinition = "jsonb", nullable = true)
    var columns: List<Map<String, Any>>? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: ZonedDateTime = ZonedDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: ZonedDateTime = ZonedDateTime.now()
)
```

### Example 3: manifest_catalog SQL

```sql
-- db/schema/01_tables/catalog.sql

-- =====================================================
-- MANIFEST CATALOG TABLE
-- =====================================================
-- Global catalog of loaded manifests (models, templates, integrations)
-- No workspace_id - global catalog like integration_definitions
-- No RLS - catalog is globally readable
-- No soft-delete - catalog entries are permanent

CREATE TABLE IF NOT EXISTS manifest_catalog (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    key VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    manifest_type VARCHAR(50) NOT NULL CHECK (manifest_type IN ('MODEL', 'TEMPLATE', 'INTEGRATION')),
    manifest_version VARCHAR(50),
    last_loaded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (key, manifest_type)
);
```

### Example 4: ManifestType Enum

```kotlin
// enums/catalog/ManifestType.kt
package riven.core.enums.catalog

enum class ManifestType {
    MODEL,
    TEMPLATE,
    INTEGRATION
}
```

### Example 5: JSON Schema Skeleton (model.schema.json)

```json
{
  "$schema": "https://json-schema.org/draft/2019-09/schema",
  "$id": "model.schema.json",
  "title": "Model Manifest",
  "description": "Schema for shared entity type model manifests",
  "type": "object",
  "required": ["manifestVersion", "key", "name", "displayName", "attributes"],
  "properties": {
    "manifestVersion": {
      "type": "string",
      "const": "1.0"
    },
    "key": {
      "type": "string",
      "pattern": "^[a-z][a-z0-9-]*$"
    },
    "name": {
      "type": "string"
    },
    "displayName": {
      "type": "object",
      "required": ["singular", "plural"],
      "properties": {
        "singular": { "type": "string" },
        "plural": { "type": "string" }
      }
    },
    "icon": {
      "type": "object",
      "properties": {
        "type": { "type": "string" },
        "colour": { "type": "string" }
      }
    },
    "semanticGroup": {
      "type": "string",
      "enum": ["CUSTOMER", "PRODUCT", "TRANSACTION", "COMMUNICATION", "SUPPORT", "FINANCIAL", "OPERATIONAL", "CUSTOM", "UNCATEGORIZED"]
    },
    "identifierKey": {
      "type": "string"
    },
    "attributes": {
      "type": "object",
      "additionalProperties": {
        "$ref": "#/$defs/attribute"
      }
    },
    "semantics": {
      "$ref": "#/$defs/entityTypeSemantics"
    }
  },
  "$defs": {
    "attribute": {
      "type": "object",
      "required": ["key", "type"],
      "properties": {
        "key": {
          "type": "string",
          "enum": ["TEXT", "OBJECT", "NUMBER", "CHECKBOX", "DATE", "DATETIME", "RATING", "PHONE", "EMAIL", "URL", "CURRENCY", "PERCENTAGE", "SELECT", "MULTI_SELECT", "FILE_ATTACHMENT", "LOCATION"]
        },
        "label": { "type": "string" },
        "type": {
          "type": "string",
          "enum": ["string", "number", "boolean", "object", "array", "null"]
        },
        "format": {
          "type": "string",
          "enum": ["date", "date-time", "email", "phone-number", "currency", "uri", "percentage"]
        },
        "required": { "type": "boolean" },
        "unique": { "type": "boolean" },
        "protected": { "type": "boolean" },
        "icon": {
          "type": "object",
          "properties": {
            "type": { "type": "string" },
            "colour": { "type": "string" }
          }
        },
        "options": {
          "type": "object",
          "properties": {
            "default": {},
            "regex": { "type": "string" },
            "enum": { "type": "array", "items": { "type": "string" } },
            "enumSorting": { "type": "string" },
            "minLength": { "type": "integer" },
            "maxLength": { "type": "integer" },
            "minimum": { "type": "number" },
            "maximum": { "type": "number" }
          }
        },
        "semantics": {
          "$ref": "#/$defs/attributeSemantics"
        }
      }
    },
    "entityTypeSemantics": {
      "type": "object",
      "properties": {
        "definition": { "type": "string" },
        "classification": {
          "type": "string",
          "enum": ["IDENTIFIER", "CATEGORICAL", "QUANTITATIVE", "TEMPORAL", "FREETEXT", "RELATIONAL_REFERENCE"]
        },
        "tags": {
          "type": "array",
          "items": { "type": "string" }
        }
      }
    },
    "attributeSemantics": {
      "type": "object",
      "properties": {
        "definition": { "type": "string" },
        "classification": {
          "type": "string",
          "enum": ["IDENTIFIER", "CATEGORICAL", "QUANTITATIVE", "TEMPORAL", "FREETEXT", "RELATIONAL_REFERENCE"]
        },
        "tags": {
          "type": "array",
          "items": { "type": "string" }
        }
      }
    }
  }
}
```

## Existing Codebase Reference

### IntegrationDefinitionEntity (The Pattern to Follow)

**File:** `src/main/kotlin/riven/core/entity/integration/IntegrationDefinitionEntity.kt`

Key characteristics:
- `@Entity @Table(name = "integration_definitions")` -- standard JPA
- `data class` with `val id: UUID? = null` and `@GeneratedValue(strategy = GenerationType.UUID)`
- No superclass -- does NOT extend `AuditableEntity`
- Manual timestamps: `val createdAt: ZonedDateTime = ZonedDateTime.now()` and `@UpdateTimestamp var updatedAt`
- `@Enumerated(EnumType.STRING)` for enum columns
- `@Type(JsonBinaryType::class)` + `columnDefinition = "jsonb"` for JSONB columns
- No `toModel()` method currently -- catalog entities may not need domain models in Phase 1
- No `workspace_id` column
- `active` boolean column (to be renamed to `stale` per CONTEXT.md)

### IntegrationDefinitionRepository (Repository Pattern)

**File:** `src/main/kotlin/riven/core/repository/integration/IntegrationDefinitionRepository.kt`

Key characteristics:
- Extends `JpaRepository<IntegrationDefinitionEntity, UUID>`
- Uses Spring Data derived query methods: `findBySlug()`, `findByActiveTrue()`, `findByCategory()`
- Returns nullable entity directly (not `Optional`)
- No `@Query` annotations for simple lookups

### Schema<T> (Attribute Schema Structure)

**File:** `src/main/kotlin/riven/core/models/common/validation/Schema.kt`

The `Schema<T>` class defines entity type attribute structure:
- Generic over `T` (UUID for workspace entity types, String for catalog)
- Fields: `label`, `key` (SchemaType enum), `icon`, `type` (DataType enum), `format` (DataFormat enum), `required`, `properties` (Map<T, Schema<T>>), `items`, `unique`, `protected`, `options`
- `SchemaOptions`: `default`, `regex`, `enum`, `enumSorting`, `minLength`, `maxLength`, `minimum`, `maximum`, `minDate`, `maxDate`
- `typealias EntityTypeSchema = Schema<UUID>` (in `models/entity/EntityType.kt`)

For catalog: attribute schemas are stored as raw JSONB since keys are strings. The schema shape mirrors `Schema<String>` but stored as `Map<String, Any>`.

### Relevant Enums

| Enum | Package | Values | Used For |
|------|---------|--------|----------|
| `SchemaType` | `enums.common.validation` | TEXT, OBJECT, NUMBER, CHECKBOX, DATE, DATETIME, RATING, PHONE, EMAIL, URL, CURRENCY, PERCENTAGE, SELECT, MULTI_SELECT, FILE_ATTACHMENT, LOCATION | Attribute `key` field |
| `DataType` | `enums.core` | STRING, NUMBER, BOOLEAN, OBJECT, ARRAY, NULL | Attribute `type` field |
| `DataFormat` | `enums.core` | DATE, DATETIME, EMAIL, PHONE, CURRENCY, URL, PERCENTAGE | Attribute `format` field |
| `EntityRelationshipCardinality` | `enums.entity` | ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY | Relationship cardinality |
| `SemanticAttributeClassification` | `enums.entity.semantics` | IDENTIFIER, CATEGORICAL, QUANTITATIVE, TEMPORAL, FREETEXT, RELATIONAL_REFERENCE | Semantic classification |
| `SemanticMetadataTargetType` | `enums.entity.semantics` | ENTITY_TYPE, ATTRIBUTE, RELATIONSHIP | Semantic target discriminator |
| `SemanticGroup` | `enums.entity.semantics` | CUSTOMER, PRODUCT, TRANSACTION, COMMUNICATION, SUPPORT, FINANCIAL, OPERATIONAL, CUSTOM, UNCATEGORIZED | Entity type grouping |
| `IconType` | `enums.common.icon` | ~1670 Lucide icon names | Icon display |
| `IconColour` | `enums.common.icon` | NEUTRAL, PURPLE, BLUE, TEAL, GREEN, YELLOW, ORANGE, RED, PINK, GREY | Icon color |

### Database Schema Conventions

**File:** `db/schema/README.md`

- Tables in `01_tables/` -- one file can contain multiple related tables (see `entities.sql` with 7 tables)
- Indexes in `02_indexes/` -- named `{domain}_indexes.sql`
- Constraints in `04_constraints/` -- named `{domain}_constraints.sql`
- No RLS needed (no `05_rls/` file) -- catalog is global
- Execution order matters -- catalog tables have no FK dependencies on workspace tables
- All UUIDs use `uuid_generate_v4()` default (requires `00_extensions/extensions.sql`)
- TIMESTAMPTZ for all timestamps, default `now()`
- VARCHAR for enum columns with CHECK constraints (not PostgreSQL enums)
- `CREATE TABLE IF NOT EXISTS` pattern

### SQL Table Pattern (from entities.sql)

```sql
CREATE TABLE IF NOT EXISTS public.catalog_table_name
(
    "id"           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    "column_name"  TYPE NOT NULL,
    "fk_column"    UUID NOT NULL REFERENCES parent_table(id) ON DELETE CASCADE,
    "enum_column"  TEXT NOT NULL CHECK (enum_column IN ('VAL1', 'VAL2')),
    "jsonb_col"    JSONB NOT NULL DEFAULT '{}'::jsonb,
    "nullable_col" TEXT,
    "created_at"   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    "updated_at"   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
```

Note: `entities.sql` uses `TIMESTAMP WITH TIME ZONE` while `integrations.sql` uses `TIMESTAMPTZ` -- they are equivalent. Prefer `TIMESTAMPTZ` for consistency with `integrations.sql` (the catalog precedent).

### JSON Schema Validation (SchemaService)

**File:** `src/main/kotlin/riven/core/service/schema/SchemaService.kt`

- Uses `com.networknt.schema.JsonSchemaFactory` with `SpecVersion.VersionFlag.V201909`
- Creates schema from `JsonNode`: `schemaFactory.getSchema(schemaNode)`
- Validates: `jsonSchema.validate(payloadNode)` returns set of validation messages
- Used with `ObjectMapper` for JSON serialization

For manifest validation (Phase 2, but JSON Schema files authored in Phase 1), the same factory will be used to load schema from classpath resources.

### EntityTypeSemanticMetadataEntity (Semantic Metadata Pattern)

**File:** `src/main/kotlin/riven/core/entity/entity/EntityTypeSemanticMetadataEntity.kt`

- Uses discriminator pattern: `targetType` (ENTITY_TYPE, ATTRIBUTE, RELATIONSHIP) + `targetId`
- `definition` (TEXT nullable), `classification` (enum nullable), `tags` (JSONB list)
- UNIQUE constraint on `(entity_type_id, target_type, target_id)`

Catalog semantic metadata mirrors this but references `catalog_entity_type_id` instead of `entity_type_id`, and uses VARCHAR target keys instead of UUID target IDs (since catalog attributes have string keys).

### RelationshipDefinitionEntity + RelationshipTargetRuleEntity (Relationship Pattern)

**Files:**
- `src/main/kotlin/riven/core/entity/entity/RelationshipDefinitionEntity.kt`
- `src/main/kotlin/riven/core/entity/entity/RelationshipTargetRuleEntity.kt`

Catalog relationships mirror this structure:
- Definition level: `name`, `iconType`, `iconColour`, `allowPolymorphic`, `cardinalityDefault`, `protected`
- Target rule level: `targetEntityTypeKey` (VARCHAR in catalog, UUID in workspace), `semanticTypeConstraint`, `cardinalityOverride`, `inverseVisible`, `inverseName`
- CASCADE delete from definition to target rules

## State of the Art

| Old Approach (Feature Design) | Current Approach (CONTEXT.md) | Impact |
|------|------|------|
| `active` boolean on `manifest_catalog` | No `active`/`deleted` columns; entries are permanent; `last_loaded_at` tracks load recency | Simplifies entity -- no deactivation logic needed |
| `integration_definition_id` FK on `manifest_catalog` | Shared key join via `manifest_catalog.key = integration_definitions.slug` | No FK constraint; application-level join; avoids load-ordering issues |
| `active` boolean on `integration_definitions` | Rename to `stale` (semantic inversion) | Existing `IntegrationDefinitionEntity` field + SQL column must be renamed |
| `entity_types` gains `source_manifest_key` + `readonly` in Phase 1 | Deferred to clone service phase (v2) | Phase 1 does NOT touch `entity_types` table or entity |
| `type` column on `manifest_catalog` | `manifest_type` column (avoids reserved word confusion) | Column name is `manifest_type` in SQL and entity |

## DB-05 Handling

**CRITICAL:** REQUIREMENTS.md lists DB-05 (entity_types column additions) as Phase 1, but CONTEXT.md Area 3 explicitly defers it:

> entity_types will NOT gain new columns in Phase 1. The following additions are deferred to the clone service milestone:
> - `integration_definition_id UUID`
> - `readonly BOOLEAN DEFAULT FALSE`
> - No `source_manifest_key` column

**Resolution:** CONTEXT.md takes precedence. DB-05 is effectively deferred. The planner should NOT create tasks for entity_types column modifications. The feature design document's `source_manifest_key` column is also dropped (replaced by `integration_definition_id` FK for integrations; templates don't need a back-reference).

## integration_definitions Rename: active -> stale

CONTEXT.md requires renaming the `active` column to `stale` on `integration_definitions`. This is a Phase 1 task because it aligns with the catalog's relationship model (shared key, staleness tracking).

**Changes required:**
1. SQL: `ALTER TABLE integration_definitions RENAME COLUMN active TO stale; ALTER TABLE integration_definitions ALTER COLUMN stale SET DEFAULT false;`
2. Entity: Rename field from `active: Boolean = true` to `stale: Boolean = false`
3. Repository: Update derived query methods (e.g., `findByActiveTrue()` -> `findByStaleIsFalse()`)
4. Index: Update `idx_integration_definitions_active` (currently `WHERE active = true`) to `idx_integration_definitions_stale` (WHERE `stale = false`)
5. Any service code referencing `active` field

**Semantic inversion note:** `active = true` (currently default) becomes `stale = false` (new default). The meaning inverts: `stale = true` means the integration manifest is no longer present on disk.

## Catalog Table Summary (6 Tables)

| Table | Parent | Unique Constraint | Key Fields |
|-------|--------|-------------------|------------|
| `manifest_catalog` | -- | `(key, manifest_type)` | key, name, description, manifest_type, manifest_version, last_loaded_at |
| `catalog_entity_types` | `manifest_catalog` | `(manifest_id, key)` | key, display_name_singular/plural, icon_type/colour, semantic_group, identifier_key, readonly, schema (JSONB), columns (JSONB) |
| `catalog_relationships` | `manifest_catalog` | `(manifest_id, key)` | key, source_entity_type_key, name, icon_type/colour, allow_polymorphic, cardinality_default, protected |
| `catalog_relationship_target_rules` | `catalog_relationships` | -- | target_entity_type_key, semantic_type_constraint, cardinality_override, inverse_visible, inverse_name |
| `catalog_field_mappings` | `manifest_catalog` | `(manifest_id, entity_type_key)` | entity_type_key, mappings (JSONB) |
| `catalog_semantic_metadata` | `manifest_catalog` via `catalog_entity_types` | `(catalog_entity_type_id, target_type, target_id)` | catalog_entity_type_id, target_type, target_id, definition, classification, tags (JSONB) |

All tables: UUID PK, `created_at`/`updated_at` TIMESTAMPTZ, no workspace_id, no RLS, no soft-delete.

## Open Questions

1. **catalog_semantic_metadata.target_id type**
   - What we know: Workspace semantic metadata uses UUID `target_id` because workspace attributes have UUID keys. Catalog attributes have string keys.
   - What's unclear: Should `target_id` be VARCHAR (matching string attribute keys) or UUID (matching workspace pattern)? For ENTITY_TYPE target_type, `target_id` equals `catalog_entity_type_id` (UUID). For ATTRIBUTE target_type, the target is an attribute string key. For RELATIONSHIP target_type, the target is a catalog_relationship UUID.
   - Recommendation: Use VARCHAR for `target_id` in catalog semantic metadata. This accommodates string attribute keys natively. UUIDs can be cast to VARCHAR. The UNIQUE constraint still works.

2. **toModel() methods for catalog entities**
   - What we know: All workspace entities have `toModel()` methods. `IntegrationDefinitionEntity` does NOT have one (no domain model exists for it).
   - What's unclear: Should Phase 1 catalog entities include `toModel()` methods or defer until Phase 3 (query service)?
   - Recommendation: Defer `toModel()` to Phase 3 when `ManifestCatalogService` needs domain models for API responses. Phase 1 only needs the JPA entities and repositories to exist.

3. **catalog_entity_types schema column type**
   - What we know: Workspace `entity_types.schema` stores `Schema<UUID>` (typed). Catalog schemas use string keys.
   - What's unclear: Should the JSONB column be typed as `Map<String, Any>` (raw JSON) or a catalog-specific type like `Schema<String>`?
   - Recommendation: Use `Map<String, Any>` for now. The schema structure will be deserialized into a typed object by the loader (Phase 2). Raw storage avoids coupling the entity to the Schema class for Phase 1.

## Sources

### Primary (HIGH confidence)
- `IntegrationDefinitionEntity.kt` -- direct codebase reference for global entity pattern
- `IntegrationDefinitionRepository.kt` -- direct codebase reference for repository pattern
- `EntityTypeEntity.kt` -- direct codebase reference for entity structure and schema storage
- `RelationshipDefinitionEntity.kt` + `RelationshipTargetRuleEntity.kt` -- relationship pattern
- `EntityTypeSemanticMetadataEntity.kt` -- semantic metadata pattern
- `Schema.kt` -- attribute schema structure
- `SchemaService.kt` -- JSON Schema validation with networknt
- `db/schema/01_tables/integrations.sql` -- SQL table pattern for global tables
- `db/schema/01_tables/entities.sql` -- SQL table pattern for entity tables
- `db/schema/README.md` -- schema directory conventions
- CONTEXT.md -- user-locked decisions overriding feature design doc
- ADR-004 -- canonical manifest structure definition
- Feature design doc -- full data model specification

### Secondary (MEDIUM confidence)
- `build.gradle.kts` -- confirmed networknt json-schema-validator 1.0.83 dependency

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- all libraries already in the project, patterns directly observed
- Architecture: HIGH -- patterns extracted from existing codebase, not hypothesized
- Pitfalls: HIGH -- derived from concrete CONTEXT.md vs REQUIREMENTS.md conflicts and observed codebase patterns
- JSON Schema structure: MEDIUM -- skeleton based on ADR-004 manifest structure, may need refinement during implementation

**Research date:** 2026-03-04
**Valid until:** 2026-04-04 (stable -- internal codebase patterns change slowly)
