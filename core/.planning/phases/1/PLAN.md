---
phase: 01-database-foundation
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  # SQL schema files
  - db/schema/01_tables/catalog.sql
  - db/schema/02_indexes/catalog_indexes.sql
  # Enum
  - src/main/kotlin/riven/core/enums/catalog/ManifestType.kt
  # JPA entities
  - src/main/kotlin/riven/core/entity/catalog/ManifestCatalogEntity.kt
  - src/main/kotlin/riven/core/entity/catalog/CatalogEntityTypeEntity.kt
  - src/main/kotlin/riven/core/entity/catalog/CatalogRelationshipEntity.kt
  - src/main/kotlin/riven/core/entity/catalog/CatalogRelationshipTargetRuleEntity.kt
  - src/main/kotlin/riven/core/entity/catalog/CatalogFieldMappingEntity.kt
  - src/main/kotlin/riven/core/entity/catalog/CatalogSemanticMetadataEntity.kt
  # Repositories
  - src/main/kotlin/riven/core/repository/catalog/ManifestCatalogRepository.kt
  - src/main/kotlin/riven/core/repository/catalog/CatalogEntityTypeRepository.kt
  - src/main/kotlin/riven/core/repository/catalog/CatalogRelationshipRepository.kt
  - src/main/kotlin/riven/core/repository/catalog/CatalogRelationshipTargetRuleRepository.kt
  - src/main/kotlin/riven/core/repository/catalog/CatalogFieldMappingRepository.kt
  - src/main/kotlin/riven/core/repository/catalog/CatalogSemanticMetadataRepository.kt
  # integration_definitions rename
  - db/schema/01_tables/integrations.sql
  - db/schema/02_indexes/integration_indexes.sql
  - src/main/kotlin/riven/core/entity/integration/IntegrationDefinitionEntity.kt
  - src/main/kotlin/riven/core/repository/integration/IntegrationDefinitionRepository.kt
  - src/main/kotlin/riven/core/service/integration/IntegrationDefinitionService.kt
  # Manifest scaffolding
  - src/main/resources/manifests/schemas/model.schema.json
  - src/main/resources/manifests/schemas/template.schema.json
  - src/main/resources/manifests/schemas/integration.schema.json
  - src/main/resources/manifests/models/.gitkeep
  - src/main/resources/manifests/templates/.gitkeep
  - src/main/resources/manifests/integrations/.gitkeep
  - src/main/resources/manifests/README.md
  - src/test/resources/manifests/schemas/.gitkeep
  - src/test/resources/manifests/models/.gitkeep
  - src/test/resources/manifests/templates/.gitkeep
  - src/test/resources/manifests/integrations/.gitkeep
autonomous: true
requirements: [DB-01, DB-02, DB-03, DB-04, VAL-01, SCAF-01, SCAF-02]

must_haves:
  truths:
    - "6 new catalog SQL tables exist with correct columns, types, FKs, and unique constraints"
    - "All 6 catalog JPA entities compile and follow the IntegrationDefinitionEntity pattern (no AuditableEntity, manual timestamps, no workspace_id, no soft-delete)"
    - "All 6 catalog Spring Data repositories compile with useful derived query methods"
    - "manifest_catalog enforces UNIQUE(key, manifest_type) — same key with different manifest_type succeeds, same key with same manifest_type fails"
    - "integration_definitions.active is renamed to stale with inverted semantics (default false)"
    - "3 JSON Schema files exist on classpath and are syntactically valid JSON"
    - "Manifest directory structure exists under src/main/resources/manifests/ with README"
  artifacts:
    - path: "db/schema/01_tables/catalog.sql"
      provides: "6 catalog table CREATE statements"
      contains: "CREATE TABLE IF NOT EXISTS manifest_catalog"
    - path: "db/schema/02_indexes/catalog_indexes.sql"
      provides: "Performance indexes for catalog tables"
      contains: "CREATE INDEX"
    - path: "src/main/kotlin/riven/core/enums/catalog/ManifestType.kt"
      provides: "ManifestType enum (MODEL, TEMPLATE, INTEGRATION)"
      contains: "enum class ManifestType"
    - path: "src/main/kotlin/riven/core/entity/catalog/ManifestCatalogEntity.kt"
      provides: "Primary catalog entity"
      contains: "data class ManifestCatalogEntity"
    - path: "src/main/kotlin/riven/core/entity/catalog/CatalogEntityTypeEntity.kt"
      provides: "Catalog entity type child entity"
      contains: "data class CatalogEntityTypeEntity"
    - path: "src/main/kotlin/riven/core/entity/catalog/CatalogRelationshipEntity.kt"
      provides: "Catalog relationship child entity"
      contains: "data class CatalogRelationshipEntity"
    - path: "src/main/kotlin/riven/core/entity/catalog/CatalogRelationshipTargetRuleEntity.kt"
      provides: "Catalog relationship target rule grandchild entity"
      contains: "data class CatalogRelationshipTargetRuleEntity"
    - path: "src/main/kotlin/riven/core/entity/catalog/CatalogFieldMappingEntity.kt"
      provides: "Catalog field mapping child entity"
      contains: "data class CatalogFieldMappingEntity"
    - path: "src/main/kotlin/riven/core/entity/catalog/CatalogSemanticMetadataEntity.kt"
      provides: "Catalog semantic metadata entity"
      contains: "data class CatalogSemanticMetadataEntity"
    - path: "src/main/kotlin/riven/core/repository/catalog/ManifestCatalogRepository.kt"
      provides: "Repository for manifest_catalog queries"
      exports: ["ManifestCatalogRepository"]
    - path: "src/main/resources/manifests/schemas/model.schema.json"
      provides: "JSON Schema for model manifests"
      contains: "$schema"
    - path: "src/main/resources/manifests/README.md"
      provides: "Authoring documentation"
      contains: "Manifest Authoring Guide"
  key_links:
    - from: "src/main/kotlin/riven/core/entity/catalog/ManifestCatalogEntity.kt"
      to: "src/main/kotlin/riven/core/enums/catalog/ManifestType.kt"
      via: "@Enumerated(EnumType.STRING) val manifestType: ManifestType"
      pattern: "ManifestType"
    - from: "src/main/kotlin/riven/core/entity/catalog/CatalogEntityTypeEntity.kt"
      to: "src/main/kotlin/riven/core/entity/catalog/ManifestCatalogEntity.kt"
      via: "val manifestId: UUID (FK to manifest_catalog.id)"
      pattern: "manifestId.*UUID"
    - from: "src/main/kotlin/riven/core/entity/catalog/CatalogRelationshipEntity.kt"
      to: "src/main/kotlin/riven/core/entity/catalog/ManifestCatalogEntity.kt"
      via: "val manifestId: UUID (FK to manifest_catalog.id)"
      pattern: "manifestId.*UUID"
    - from: "db/schema/01_tables/catalog.sql"
      to: "db/schema/01_tables/integrations.sql"
      via: "No FK — shared key join (manifest_catalog.key = integration_definitions.slug)"
      pattern: "No direct SQL reference"
---

<objective>
Create the complete database foundation for the Declarative Manifest Catalog: 6 SQL catalog tables, 6 JPA entity classes, 6 Spring Data repository interfaces, the ManifestType enum, rename integration_definitions.active to stale, author 3 JSON Schema validation files, and scaffold the manifest directory structure with an authoring README.

Purpose: Establish the persistence layer and authoring infrastructure that the Phase 2 loader pipeline will consume. Every table, entity, repository, schema file, and directory must exist and compile before loader code can be written.

Output: SQL schema files, Kotlin entity/repository/enum classes, JSON Schema files, manifest directory structure with README. The application must compile cleanly with `./gradlew build`.
</objective>

<execution_context>
This is a single-plan phase. All tasks are in wave 1 because a single executor handles them sequentially within one plan. The plan is designed to be completed in one session within ~50% context budget.
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/1/CONTEXT.md
@.planning/phases/1/01-RESEARCH.md

@src/main/kotlin/riven/core/entity/integration/IntegrationDefinitionEntity.kt
@src/main/kotlin/riven/core/repository/integration/IntegrationDefinitionRepository.kt
@src/main/kotlin/riven/core/service/integration/IntegrationDefinitionService.kt
@db/schema/01_tables/integrations.sql
@db/schema/02_indexes/integration_indexes.sql
@src/main/kotlin/riven/core/entity/entity/RelationshipDefinitionEntity.kt
@src/main/kotlin/riven/core/entity/entity/RelationshipTargetRuleEntity.kt
@src/main/kotlin/riven/core/entity/entity/EntityTypeSemanticMetadataEntity.kt
@db/schema/01_tables/entities.sql

<interfaces>
<!-- Key types and contracts the executor needs. Extracted from codebase. -->

From src/main/kotlin/riven/core/entity/integration/IntegrationDefinitionEntity.kt:
```kotlin
// THE PATTERN TO FOLLOW for all catalog entities:
// - No AuditableEntity superclass
// - No SoftDeletable
// - No workspace_id
// - Manual createdAt with default, @UpdateTimestamp on updatedAt
// - @Type(JsonBinaryType::class) for JSONB columns
// - @Enumerated(EnumType.STRING) for enum columns
// - data class with val id: UUID? = null
@Entity
@Table(name = "integration_definitions")
data class IntegrationDefinitionEntity(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    val id: UUID? = null,
    // ... fields ...
    @Column(name = "active", nullable = false)
    var active: Boolean = true,  // RENAME TO: stale: Boolean = false
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: ZonedDateTime = ZonedDateTime.now(),
    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: ZonedDateTime = ZonedDateTime.now()
)
```

From src/main/kotlin/riven/core/repository/integration/IntegrationDefinitionRepository.kt:
```kotlin
interface IntegrationDefinitionRepository : JpaRepository<IntegrationDefinitionEntity, UUID> {
    fun findBySlug(slug: String): IntegrationDefinitionEntity?
    fun findByActiveTrue(): List<IntegrationDefinitionEntity>  // RENAME TO: findByStaleIsFalse()
    fun findByCategory(category: IntegrationCategory): List<IntegrationDefinitionEntity>
}
```

From src/main/kotlin/riven/core/service/integration/IntegrationDefinitionService.kt:
```kotlin
fun getActiveIntegrations(): List<IntegrationDefinitionEntity> {
    return repository.findByActiveTrue()  // UPDATE TO: findByStaleIsFalse()
}
```

Existing enums (reused by catalog entities — DO NOT recreate):
- riven.core.enums.common.icon.IconType — Lucide icon names
- riven.core.enums.common.icon.IconColour — NEUTRAL, PURPLE, BLUE, etc.
- riven.core.enums.entity.semantics.SemanticGroup — CUSTOMER, PRODUCT, etc.
- riven.core.enums.entity.semantics.SemanticAttributeClassification — IDENTIFIER, CATEGORICAL, etc.
- riven.core.enums.entity.semantics.SemanticMetadataTargetType — ENTITY_TYPE, ATTRIBUTE, RELATIONSHIP
- riven.core.enums.entity.EntityRelationshipCardinality — ONE_TO_ONE, ONE_TO_MANY, etc.
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: SQL schema files + ManifestType enum + integration_definitions rename</name>
  <files>
    db/schema/01_tables/catalog.sql
    db/schema/02_indexes/catalog_indexes.sql
    db/schema/01_tables/integrations.sql
    db/schema/02_indexes/integration_indexes.sql
    src/main/kotlin/riven/core/enums/catalog/ManifestType.kt
  </files>
  <action>
    **1a. Create `db/schema/01_tables/catalog.sql`** with all 6 catalog tables.

    Follow the `integrations.sql` conventions exactly: `CREATE TABLE IF NOT EXISTS`, `TIMESTAMPTZ`, `uuid_generate_v4()`, `CHECK` constraints for enum columns. Do NOT use `public.` schema prefix (integrations.sql does not use it). Do NOT add `deleted`/`deleted_at`/`active` columns. Do NOT add `workspace_id`. Do NOT add `integration_definition_id` FK.

    Tables in order (parent before child for FK resolution):

    **manifest_catalog:**
    - `id UUID PRIMARY KEY DEFAULT uuid_generate_v4()`
    - `key VARCHAR(255) NOT NULL`
    - `name VARCHAR(255) NOT NULL`
    - `description TEXT`
    - `manifest_type VARCHAR(50) NOT NULL CHECK (manifest_type IN ('MODEL', 'TEMPLATE', 'INTEGRATION'))`
    - `manifest_version VARCHAR(50)`
    - `last_loaded_at TIMESTAMPTZ`
    - `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
    - `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`
    - `UNIQUE (key, manifest_type)`

    **catalog_entity_types:**
    - `id UUID PRIMARY KEY DEFAULT uuid_generate_v4()`
    - `manifest_id UUID NOT NULL REFERENCES manifest_catalog(id) ON DELETE CASCADE`
    - `key VARCHAR(255) NOT NULL`
    - `display_name_singular VARCHAR(255) NOT NULL`
    - `display_name_plural VARCHAR(255) NOT NULL`
    - `icon_type TEXT NOT NULL DEFAULT 'CIRCLE_DASHED'`
    - `icon_colour TEXT NOT NULL DEFAULT 'NEUTRAL'`
    - `semantic_group TEXT NOT NULL DEFAULT 'UNCATEGORIZED'`
    - `identifier_key VARCHAR(255)`
    - `readonly BOOLEAN NOT NULL DEFAULT FALSE`
    - `schema JSONB NOT NULL DEFAULT '{}'::jsonb`
    - `columns JSONB`
    - `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
    - `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`
    - `UNIQUE (manifest_id, key)`

    **catalog_relationships:**
    - `id UUID PRIMARY KEY DEFAULT uuid_generate_v4()`
    - `manifest_id UUID NOT NULL REFERENCES manifest_catalog(id) ON DELETE CASCADE`
    - `key VARCHAR(255) NOT NULL`
    - `source_entity_type_key VARCHAR(255) NOT NULL`
    - `name TEXT NOT NULL`
    - `icon_type TEXT NOT NULL DEFAULT 'LINK'`
    - `icon_colour TEXT NOT NULL DEFAULT 'NEUTRAL'`
    - `allow_polymorphic BOOLEAN NOT NULL DEFAULT FALSE`
    - `cardinality_default TEXT NOT NULL CHECK (cardinality_default IN ('ONE_TO_ONE','ONE_TO_MANY','MANY_TO_ONE','MANY_TO_MANY'))`
    - `protected BOOLEAN NOT NULL DEFAULT FALSE`
    - `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
    - `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`
    - `UNIQUE (manifest_id, key)`

    **catalog_relationship_target_rules:**
    - `id UUID PRIMARY KEY DEFAULT uuid_generate_v4()`
    - `catalog_relationship_id UUID NOT NULL REFERENCES catalog_relationships(id) ON DELETE CASCADE`
    - `target_entity_type_key VARCHAR(255) NOT NULL`
    - `semantic_type_constraint TEXT` (nullable, CHECK for SemanticGroup values)
    - `cardinality_override TEXT` (nullable, CHECK for cardinality values)
    - `inverse_visible BOOLEAN NOT NULL DEFAULT FALSE`
    - `inverse_name TEXT`
    - `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
    - `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`

    **catalog_field_mappings:**
    - `id UUID PRIMARY KEY DEFAULT uuid_generate_v4()`
    - `manifest_id UUID NOT NULL REFERENCES manifest_catalog(id) ON DELETE CASCADE`
    - `entity_type_key VARCHAR(255) NOT NULL`
    - `mappings JSONB NOT NULL DEFAULT '{}'::jsonb`
    - `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
    - `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`
    - `UNIQUE (manifest_id, entity_type_key)`

    **catalog_semantic_metadata:**
    - `id UUID PRIMARY KEY DEFAULT uuid_generate_v4()`
    - `catalog_entity_type_id UUID NOT NULL REFERENCES catalog_entity_types(id) ON DELETE CASCADE`
    - `target_type TEXT NOT NULL CHECK (target_type IN ('ENTITY_TYPE', 'ATTRIBUTE', 'RELATIONSHIP'))`
    - `target_id VARCHAR(255) NOT NULL` (VARCHAR, not UUID — accommodates string attribute keys per research recommendation)
    - `definition TEXT`
    - `classification TEXT` (nullable, CHECK for SemanticAttributeClassification values)
    - `tags JSONB NOT NULL DEFAULT '[]'::jsonb`
    - `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
    - `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`
    - `UNIQUE (catalog_entity_type_id, target_type, target_id)`

    **1b. Create `db/schema/02_indexes/catalog_indexes.sql`** with performance indexes:
    - `idx_catalog_entity_types_manifest` on `catalog_entity_types(manifest_id)`
    - `idx_catalog_relationships_manifest` on `catalog_relationships(manifest_id)`
    - `idx_catalog_relationships_source_key` on `catalog_relationships(source_entity_type_key)`
    - `idx_catalog_rel_target_rules_relationship` on `catalog_relationship_target_rules(catalog_relationship_id)`
    - `idx_catalog_field_mappings_manifest` on `catalog_field_mappings(manifest_id)`
    - `idx_catalog_semantic_metadata_entity_type` on `catalog_semantic_metadata(catalog_entity_type_id)`
    - `idx_catalog_semantic_metadata_target` on `catalog_semantic_metadata(target_type, target_id)`
    - `idx_manifest_catalog_type` on `manifest_catalog(manifest_type)`

    Use `CREATE INDEX IF NOT EXISTS` pattern. No partial indexes needed (no soft-delete).

    **1c. Rename `active` to `stale` on `integration_definitions`:**

    In `db/schema/01_tables/integrations.sql`: Change `active BOOLEAN NOT NULL DEFAULT true` to `stale BOOLEAN NOT NULL DEFAULT false`. This is the CREATE TABLE definition, not an ALTER — the schema files are idempotent table definitions, not migrations.

    In `db/schema/02_indexes/integration_indexes.sql`: Change `idx_integration_definitions_active` to `idx_integration_definitions_stale`. Change filter from `WHERE active = true` to `WHERE stale = false`. Update the column reference from `active` to `stale`.

    **1d. Create `src/main/kotlin/riven/core/enums/catalog/ManifestType.kt`:**
    ```kotlin
    package riven.core.enums.catalog

    enum class ManifestType {
        MODEL,
        TEMPLATE,
        INTEGRATION
    }
    ```
  </action>
  <verify>
    <automated>
      cd /home/jared/dev/worktrees/template-manifestation/core && test -f db/schema/01_tables/catalog.sql && test -f db/schema/02_indexes/catalog_indexes.sql && test -f src/main/kotlin/riven/core/enums/catalog/ManifestType.kt && grep -q "UNIQUE (key, manifest_type)" db/schema/01_tables/catalog.sql && grep -q "stale BOOLEAN NOT NULL DEFAULT false" db/schema/01_tables/integrations.sql && grep -q "stale" db/schema/02_indexes/integration_indexes.sql && echo "PASS" || echo "FAIL"
    </automated>
  </verify>
  <done>
    - catalog.sql contains all 6 CREATE TABLE statements with correct columns, types, FKs, CHECK constraints, and UNIQUE constraints
    - catalog_indexes.sql contains performance indexes for all catalog tables
    - integrations.sql has `stale BOOLEAN NOT NULL DEFAULT false` (not `active`)
    - integration_indexes.sql references `stale` column
    - ManifestType.kt enum exists with MODEL, TEMPLATE, INTEGRATION values
    - No `active`/`deleted`/`deleted_at` columns on any catalog table
    - No `integration_definition_id` FK on manifest_catalog
    - No `workspace_id` on any catalog table
    - catalog_semantic_metadata.target_id is VARCHAR(255), not UUID
  </done>
</task>

<task type="auto">
  <name>Task 2: JPA entities, repositories, and integration_definitions code rename</name>
  <files>
    src/main/kotlin/riven/core/entity/catalog/ManifestCatalogEntity.kt
    src/main/kotlin/riven/core/entity/catalog/CatalogEntityTypeEntity.kt
    src/main/kotlin/riven/core/entity/catalog/CatalogRelationshipEntity.kt
    src/main/kotlin/riven/core/entity/catalog/CatalogRelationshipTargetRuleEntity.kt
    src/main/kotlin/riven/core/entity/catalog/CatalogFieldMappingEntity.kt
    src/main/kotlin/riven/core/entity/catalog/CatalogSemanticMetadataEntity.kt
    src/main/kotlin/riven/core/repository/catalog/ManifestCatalogRepository.kt
    src/main/kotlin/riven/core/repository/catalog/CatalogEntityTypeRepository.kt
    src/main/kotlin/riven/core/repository/catalog/CatalogRelationshipRepository.kt
    src/main/kotlin/riven/core/repository/catalog/CatalogRelationshipTargetRuleRepository.kt
    src/main/kotlin/riven/core/repository/catalog/CatalogFieldMappingRepository.kt
    src/main/kotlin/riven/core/repository/catalog/CatalogSemanticMetadataRepository.kt
    src/main/kotlin/riven/core/entity/integration/IntegrationDefinitionEntity.kt
    src/main/kotlin/riven/core/repository/integration/IntegrationDefinitionRepository.kt
    src/main/kotlin/riven/core/service/integration/IntegrationDefinitionService.kt
  </files>
  <action>
    **2a. Create 6 JPA entity classes** in `src/main/kotlin/riven/core/entity/catalog/`.

    ALL entities follow the `IntegrationDefinitionEntity` pattern EXACTLY:
    - `data class` (not extending any base class)
    - `@Entity @Table(name = "table_name")` with `uniqueConstraints` where applicable
    - `val id: UUID? = null` with `@Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "id", columnDefinition = "uuid")`
    - `val createdAt: ZonedDateTime = ZonedDateTime.now()` with `@Column(name = "created_at", nullable = false, updatable = false)`
    - `@UpdateTimestamp @Column(name = "updated_at") var updatedAt: ZonedDateTime = ZonedDateTime.now()`
    - No `AuditableEntity`, no `SoftDeletable`, no `workspace_id`, no `createdBy`/`updatedBy`
    - `@Type(JsonBinaryType::class) @Column(columnDefinition = "jsonb")` for JSONB columns
    - `@Enumerated(EnumType.STRING)` for enum columns
    - Plain `UUID` for FK columns (no `@ManyToOne`)
    - No `toModel()` methods (deferred to Phase 3)

    Required imports for all catalog entities:
    ```kotlin
    import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
    import jakarta.persistence.*
    import org.hibernate.annotations.Type
    import org.hibernate.annotations.UpdateTimestamp
    import java.time.ZonedDateTime
    import java.util.*
    ```

    **ManifestCatalogEntity:**
    - `key: String` (not null)
    - `name: String` (not null)
    - `description: String?` (nullable)
    - `manifestType: ManifestType` (enum, not null) — import from `riven.core.enums.catalog.ManifestType`
    - `manifestVersion: String?` (nullable)
    - `lastLoadedAt: ZonedDateTime?` (nullable, `var` — updated on each upsert)
    - `@Table` uniqueConstraints: `UniqueConstraint(columnNames = ["key", "manifest_type"])`

    **CatalogEntityTypeEntity:**
    - `manifestId: UUID` (not null, FK)
    - `key: String` (not null)
    - `displayNameSingular: String` (not null)
    - `displayNamePlural: String` (not null)
    - `iconType: IconType = IconType.CIRCLE_DASHED` (enum, not null) — import from `riven.core.enums.common.icon.IconType`
    - `iconColour: IconColour = IconColour.NEUTRAL` (enum, not null) — import from `riven.core.enums.common.icon.IconColour`
    - `semanticGroup: SemanticGroup = SemanticGroup.UNCATEGORIZED` (enum, not null) — import from `riven.core.enums.entity.semantics.SemanticGroup`
    - `identifierKey: String?` (nullable)
    - `readonly: Boolean = false` (not null)
    - `schema: Map<String, Any> = emptyMap()` (JSONB, not null) — use `@Type(JsonBinaryType::class) @Column(name = "schema", columnDefinition = "jsonb", nullable = false)`
    - `columns: List<Map<String, Any>>? = null` (JSONB, nullable) — use `@Type(JsonBinaryType::class) @Column(name = "columns", columnDefinition = "jsonb", nullable = true)`
    - `@Table` uniqueConstraints: `UniqueConstraint(columnNames = ["manifest_id", "key"])`

    **CatalogRelationshipEntity:**
    - `manifestId: UUID` (not null, FK)
    - `key: String` (not null)
    - `sourceEntityTypeKey: String` (not null) — VARCHAR, not UUID
    - `name: String` (not null)
    - `iconType: IconType = IconType.LINK` (enum)
    - `iconColour: IconColour = IconColour.NEUTRAL` (enum)
    - `allowPolymorphic: Boolean = false`
    - `cardinalityDefault: EntityRelationshipCardinality` (enum, not null) — import from `riven.core.enums.entity.EntityRelationshipCardinality`
    - `protected: Boolean = false`
    - `@Table` uniqueConstraints: `UniqueConstraint(columnNames = ["manifest_id", "key"])`

    **CatalogRelationshipTargetRuleEntity:**
    - `catalogRelationshipId: UUID` (not null, FK to catalog_relationships.id)
    - `targetEntityTypeKey: String` (not null) — VARCHAR, not UUID
    - `semanticTypeConstraint: SemanticGroup? = null` (nullable enum) — import from `riven.core.enums.entity.semantics.SemanticGroup`
    - `cardinalityOverride: EntityRelationshipCardinality? = null` (nullable enum)
    - `inverseVisible: Boolean = false`
    - `inverseName: String? = null`

    **CatalogFieldMappingEntity:**
    - `manifestId: UUID` (not null, FK)
    - `entityTypeKey: String` (not null) — VARCHAR
    - `mappings: Map<String, Any> = emptyMap()` (JSONB, not null)
    - `@Table` uniqueConstraints: `UniqueConstraint(columnNames = ["manifest_id", "entity_type_key"])`

    **CatalogSemanticMetadataEntity:**
    - `catalogEntityTypeId: UUID` (not null, FK to catalog_entity_types.id)
    - `targetType: SemanticMetadataTargetType` (enum, not null) — import from `riven.core.enums.entity.semantics.SemanticMetadataTargetType`
    - `targetId: String` (not null) — VARCHAR, not UUID (accommodates string attribute keys)
    - `definition: String? = null`
    - `classification: SemanticAttributeClassification? = null` (nullable enum) — import from `riven.core.enums.entity.semantics.SemanticAttributeClassification`
    - `tags: List<String> = emptyList()` (JSONB, not null)
    - `@Table` uniqueConstraints: `UniqueConstraint(columnNames = ["catalog_entity_type_id", "target_type", "target_id"])`

    **2b. Create 6 repository interfaces** in `src/main/kotlin/riven/core/repository/catalog/`.

    All extend `JpaRepository<EntityClass, UUID>`. Include useful derived query methods:

    **ManifestCatalogRepository:**
    - `findByKey(key: String): List<ManifestCatalogEntity>` (returns list — same key can have different manifest_type)
    - `findByManifestType(manifestType: ManifestType): List<ManifestCatalogEntity>`
    - `findByKeyAndManifestType(key: String, manifestType: ManifestType): ManifestCatalogEntity?`

    **CatalogEntityTypeRepository:**
    - `findByManifestId(manifestId: UUID): List<CatalogEntityTypeEntity>`
    - `findByManifestIdAndKey(manifestId: UUID, key: String): CatalogEntityTypeEntity?`

    **CatalogRelationshipRepository:**
    - `findByManifestId(manifestId: UUID): List<CatalogRelationshipEntity>`
    - `findByManifestIdAndKey(manifestId: UUID, key: String): CatalogRelationshipEntity?`

    **CatalogRelationshipTargetRuleRepository:**
    - `findByCatalogRelationshipId(catalogRelationshipId: UUID): List<CatalogRelationshipTargetRuleEntity>`

    **CatalogFieldMappingRepository:**
    - `findByManifestId(manifestId: UUID): List<CatalogFieldMappingEntity>`
    - `findByManifestIdAndEntityTypeKey(manifestId: UUID, entityTypeKey: String): CatalogFieldMappingEntity?`

    **CatalogSemanticMetadataRepository:**
    - `findByCatalogEntityTypeId(catalogEntityTypeId: UUID): List<CatalogSemanticMetadataEntity>`
    - `findByCatalogEntityTypeIdAndTargetTypeAndTargetId(catalogEntityTypeId: UUID, targetType: SemanticMetadataTargetType, targetId: String): CatalogSemanticMetadataEntity?`

    **2c. Rename `active` to `stale` on IntegrationDefinitionEntity and update dependents:**

    In `IntegrationDefinitionEntity.kt`:
    - Change `var active: Boolean = true` to `var stale: Boolean = false`
    - Change `@Column(name = "active", nullable = false)` to `@Column(name = "stale", nullable = false)`

    In `IntegrationDefinitionRepository.kt`:
    - Rename `findByActiveTrue()` to `findByStaleIsFalse(): List<IntegrationDefinitionEntity>`
    - Update KDoc: "Find all active (non-stale) integrations."

    In `IntegrationDefinitionService.kt`:
    - Rename `getActiveIntegrations()` to `getNonStaleIntegrations()` (or keep the name `getActiveIntegrations` and update the implementation to call `findByStaleIsFalse()`)
    - Preferred: Keep method name `getActiveIntegrations()` for API stability, but update the body: `return repository.findByStaleIsFalse()`
    - Update KDoc: "Get all active (non-stale) integrations."
  </action>
  <verify>
    <automated>cd /home/jared/dev/worktrees/template-manifestation/core && ./gradlew build -x test 2>&1 | tail -5</automated>
  </verify>
  <done>
    - All 6 catalog entity classes compile in `entity/catalog/` package
    - All 6 catalog repository interfaces compile in `repository/catalog/` package
    - No entity extends AuditableEntity or AuditableSoftDeletableEntity
    - No entity has workspace_id, deleted, deletedAt, createdBy, or updatedBy fields
    - All JSONB columns use @Type(JsonBinaryType::class)
    - All enum columns use @Enumerated(EnumType.STRING)
    - CatalogSemanticMetadataEntity.targetId is String (not UUID)
    - IntegrationDefinitionEntity has `stale: Boolean = false` (not `active: Boolean = true`)
    - IntegrationDefinitionRepository has `findByStaleIsFalse()` (not `findByActiveTrue()`)
    - IntegrationDefinitionService compiles with updated repository call
    - `./gradlew build -x test` succeeds (compilation passes)
  </done>
</task>

<task type="auto">
  <name>Task 3: JSON Schema files, manifest directory scaffolding, and README</name>
  <files>
    src/main/resources/manifests/schemas/model.schema.json
    src/main/resources/manifests/schemas/template.schema.json
    src/main/resources/manifests/schemas/integration.schema.json
    src/main/resources/manifests/models/.gitkeep
    src/main/resources/manifests/templates/.gitkeep
    src/main/resources/manifests/integrations/.gitkeep
    src/main/resources/manifests/README.md
    src/test/resources/manifests/schemas/.gitkeep
    src/test/resources/manifests/models/.gitkeep
    src/test/resources/manifests/templates/.gitkeep
    src/test/resources/manifests/integrations/.gitkeep
  </files>
  <action>
    **3a. Create manifest directory structure** with `.gitkeep` placeholder files:

    ```
    src/main/resources/manifests/
    ├── schemas/           (JSON Schema files go here)
    ├── models/.gitkeep
    ├── templates/.gitkeep
    ├── integrations/.gitkeep
    └── README.md

    src/test/resources/manifests/
    ├── schemas/.gitkeep
    ├── models/.gitkeep
    ├── templates/.gitkeep
    └── integrations/.gitkeep
    ```

    **3b. Create `model.schema.json`** in `src/main/resources/manifests/schemas/`.

    Draft 2019-09 (`https://json-schema.org/draft/2019-09/schema`) — matches `SchemaService` which uses `SpecVersion.VersionFlag.V201909`.

    Required top-level fields: `manifestVersion` (const "1.0"), `key` (pattern `^[a-z][a-z0-9-]*$`), `name`, `displayName` (object with `singular` + `plural`).
    Required: `attributes` (object, additionalProperties reference `#/$defs/attribute`).
    Optional: `description`, `icon` (object with `type` + `colour`), `semanticGroup` (enum matching SemanticGroup values), `identifierKey`, `semantics` (reference `#/$defs/entityTypeSemantics`).

    `$defs/attribute` required fields: `key` (enum of SchemaType values: TEXT, OBJECT, NUMBER, CHECKBOX, DATE, DATETIME, RATING, PHONE, EMAIL, URL, CURRENCY, PERCENTAGE, SELECT, MULTI_SELECT, FILE_ATTACHMENT, LOCATION), `type` (enum of DataType values: string, number, boolean, object, array, null).
    Optional: `label`, `format` (enum of DataFormat values: date, date-time, email, phone-number, currency, uri, percentage), `required` (boolean), `unique` (boolean), `protected` (boolean), `icon` (object), `options` (object with `default`, `regex`, `enum`, `enumSorting`, `minLength`, `maxLength`, `minimum`, `maximum`), `semantics` (reference `#/$defs/attributeSemantics`).

    `$defs/entityTypeSemantics`: object with optional `definition` (string), `classification` (enum of SemanticAttributeClassification values), `tags` (array of strings).
    `$defs/attributeSemantics`: same shape as entityTypeSemantics.

    Models do NOT have `entityTypes` (array) — a model file IS a single entity type. Models do NOT have `relationships` — relationships are declared at the template composition layer.

    **IMPORTANT:** Do NOT include `analyticalBriefs` or `exampleQueries` — deferred per CONTEXT.md.

    **3c. Create `template.schema.json`** in `src/main/resources/manifests/schemas/`.

    Same draft version. Required top-level: `manifestVersion`, `key`, `name`.
    Required: `entityTypes` (array of entity type entries, each being either a `$ref` with optional `extend`, or an inline entity type definition matching the model schema structure).
    Optional: `description`, `relationships` (array of relationship definitions).

    Entity type in template: either `{ "$ref": "models/xxx", "extend": { ... } }` or full inline definition (same shape as model.schema.json minus `manifestVersion`/`key` — it has its own `key`, `name`, `displayName`, `attributes`, etc.).

    Relationship definition: `key`, `sourceEntityTypeKey`, `name` required. Optional: `icon`, `allowPolymorphic`, `cardinality` (shorthand — single string enum), `protected`, `targetRules` (array of target rule objects), `semantics` (object with `definition` + `tags` — NO `classification` per CONTEXT.md).

    Shorthand relationship (single target): `{ "key": "...", "sourceEntityTypeKey": "...", "targetEntityTypeKey": "...", "name": "...", "cardinality": "ONE_TO_MANY" }`.
    Full relationship (multiple targets): `{ "key": "...", "sourceEntityTypeKey": "...", "name": "...", "targetRules": [{ "targetEntityTypeKey": "...", "cardinalityOverride": "...", "inverseVisible": true, "inverseName": "..." }] }`.

    Use `oneOf` to express the shorthand vs full format mutual exclusivity if practical, or use `anyOf` with documentation in `description` fields.

    **3d. Create `integration.schema.json`** in `src/main/resources/manifests/schemas/`.

    Same as template schema structure but with additional integration-specific fields:
    Required: `manifestVersion`, `key`, `name`, `entityTypes` (array).
    Optional: `description`, `relationships`, `fieldMappings` (array of field mapping entries: `entityTypeKey` + `mappings` object).

    Integration entity types are always inline (no `$ref`). They typically have `readonly: true`.

    **3e. Create `README.md`** in `src/main/resources/manifests/`.

    Document:
    1. **Overview** — what manifests are, three types (model, template, integration)
    2. **Directory structure** — models/ (flat files), templates/ (directory per template), integrations/ (directory per integration)
    3. **Model format** — single entity type definition, attribute schema structure, key naming conventions (`^[a-z][a-z0-9-]*$`)
    4. **Template format** — `entityTypes` array with `$ref` and `extend` semantics
    5. **`$ref` syntax** — `"$ref": "models/customer"` resolves to `models/customer.json`. Reference must exist in models/ directory. Referenced model is merged as the base entity type.
    6. **`extend` semantics** — shallow additive merge. New attributes are added. Existing attributes from the referenced model are NOT overwritten. No deletion semantics (cannot remove inherited attributes). `extend.semantics.tags` are appended, not replaced.
    7. **Integration format** — similar to template but with `fieldMappings` section
    8. **Relationship shorthand vs full format** — shorthand: `targetEntityTypeKey` + `cardinality` on the relationship itself (single target). Full: `targetRules[]` array for polymorphic/multi-target relationships. Formats are mutually exclusive.
    9. **Attribute schema structure** — `key` (SchemaType), `type` (DataType), `format` (DataFormat), `label`, `required`, `unique`, `protected`, `options`, `semantics`
    10. **Semantics** — entity types and attributes get `definition`, `classification`, `tags`. Relationships get `definition`, `tags` (no `classification`).
    11. **Validation** — manifests are validated against JSON Schema files in `schemas/` on application startup. Invalid manifests are skipped with warning logs.
  </action>
  <verify>
    <automated>
      cd /home/jared/dev/worktrees/template-manifestation/core && test -f src/main/resources/manifests/schemas/model.schema.json && test -f src/main/resources/manifests/schemas/template.schema.json && test -f src/main/resources/manifests/schemas/integration.schema.json && test -f src/main/resources/manifests/README.md && test -d src/main/resources/manifests/models && test -d src/main/resources/manifests/templates && test -d src/main/resources/manifests/integrations && test -d src/test/resources/manifests/models && python3 -c "import json; json.load(open('src/main/resources/manifests/schemas/model.schema.json')); json.load(open('src/main/resources/manifests/schemas/template.schema.json')); json.load(open('src/main/resources/manifests/schemas/integration.schema.json')); print('JSON valid')" && echo "PASS" || echo "FAIL"
    </automated>
  </verify>
  <done>
    - 3 JSON Schema files exist and are syntactically valid JSON in `src/main/resources/manifests/schemas/`
    - All schemas use Draft 2019-09 (`$schema` field)
    - model.schema.json validates single entity type manifests (no `entityTypes` array, no `relationships`)
    - template.schema.json validates manifests with `entityTypes` array supporting `$ref` + `extend`
    - integration.schema.json validates manifests with `entityTypes` + optional `fieldMappings`
    - No `analyticalBriefs` or `exampleQueries` in any schema (deferred per CONTEXT.md)
    - Relationship semantics have `definition` + `tags` but NO `classification` per CONTEXT.md
    - Directory structure: `models/`, `templates/`, `integrations/` exist under both `src/main/resources/manifests/` and `src/test/resources/manifests/`
    - README.md documents manifest format, `$ref` syntax, `extend` semantics, relationship shorthand vs full format
  </done>
</task>

</tasks>

<verification>
After all tasks complete, run the full verification sequence:

1. **Compilation check:** `./gradlew build -x test` must pass (compiles all Kotlin, processes resources)
2. **Existing tests pass:** `./gradlew test` must pass (no regressions from integration_definitions rename)
3. **SQL file structure check:** `catalog.sql` contains exactly 6 CREATE TABLE statements
4. **Entity pattern check:** No catalog entity extends AuditableEntity or has workspace_id
5. **Constraint check:** `catalog.sql` contains `UNIQUE (key, manifest_type)` on manifest_catalog
6. **Rename check:** No remaining references to `active` on IntegrationDefinitionEntity (search for `.active` in integration package)
7. **JSON Schema validity:** All 3 schema files parse as valid JSON
8. **Directory existence:** All manifest directories exist in both main and test resources
</verification>

<success_criteria>
1. `./gradlew build` passes (compilation + resource processing, tests may be excluded if H2 lacks catalog tables)
2. `./gradlew test` passes with no regressions from the integration_definitions rename
3. 6 SQL CREATE TABLE statements exist in `db/schema/01_tables/catalog.sql` with correct columns, types, FKs, and constraints
4. 6 JPA entity data classes exist in `entity/catalog/` following IntegrationDefinitionEntity pattern
5. 6 repository interfaces exist in `repository/catalog/` with useful derived query methods
6. ManifestType enum exists in `enums/catalog/` with MODEL, TEMPLATE, INTEGRATION
7. IntegrationDefinitionEntity.active renamed to stale with inverted default (false)
8. 3 JSON Schema files are valid JSON and use Draft 2019-09
9. Manifest directories and README exist under `src/main/resources/manifests/`
10. Test fixture directories exist under `src/test/resources/manifests/`
</success_criteria>

<output>
After completion, create `.planning/phases/1/01-01-SUMMARY.md`
</output>
