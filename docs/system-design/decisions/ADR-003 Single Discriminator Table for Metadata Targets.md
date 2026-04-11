---
tags:
  - adr/proposed
  - architecture/decision
Created: 2026-02-18
---
# ADR-003: Single Discriminator Table for Metadata Targets

---

## Context

[[2. Areas/2.1 Startup & Content/Riven/2. System Design/decisions/ADR-002 Separate Table for Semantic Metadata]] establishes that semantic metadata lives in a dedicated table, separate from the existing `entity_types` table. Semantic metadata must be attached to three distinct target types:

1. **Entity types** -- a natural-language definition describing what the entity type represents in the workspace's domain.
2. **Attributes** -- a description and classification (e.g., IDENTIFIER, DESCRIPTIVE, TEMPORAL) for each attribute defined in the entity type's `schema.properties` JSONB map.
3. **Relationship definitions** -- semantic context describing what each relationship means in the domain model, stored alongside the relationship definitions in the entity type's `relationships` JSONB array.

All three targets share an identical metadata field shape: `definition` (TEXT), `classification` (TEXT, nullable), and `tags` (JSONB array). This was a locked design decision during the research phase -- "one metadata shape, not tailored per target type." The question is whether to use a single table with a discriminator column to distinguish target types, or three separate purpose-specific tables.

A complicating factor is that attribute and relationship target IDs are not foreign keys to separate tables. Attributes are UUID keys within the `entity_types.schema` JSONB map. Relationship definitions are UUID-identified objects within the `entity_types.relationships` JSONB array. Neither has its own database table -- they exist only as JSONB structures within the `entity_types` row.

---

## Decision

Use a single `entity_type_semantic_metadata` table with a `target_type` TEXT column as a discriminator and a `target_id` UUID column identifying the specific target within the entity type. The `target_type` column accepts three values: `ENTITY_TYPE`, `ATTRIBUTE`, and `RELATIONSHIP`. A UNIQUE constraint on `(entity_type_id, target_type, target_id)` enforces exactly one metadata record per target. A CHECK constraint on `target_type` restricts values to the three valid discriminator values.

---

## Rationale

- **Identical field shape across all targets.** All three target types use the same columns: `definition`, `classification`, and `tags`. When the data shape is identical, separate tables create redundancy without adding expressiveness. The discriminator column captures the only structural difference -- what the metadata is attached to.
- **Simplified service layer.** A single table means one JPA entity (`EntityTypeSemanticMetadataEntity`), one repository (`EntityTypeSemanticMetadataRepository`), and one service (`EntityTypeSemanticMetadataService`). Three tables would triple the repository and entity count for what is effectively the same CRUD logic with a type filter.
- **Efficient batch queries for `?include=semantics`.** The primary read pattern is "fetch all semantic metadata for an entity type" -- used when the API caller requests `?include=semantics` on an entity type endpoint. With a single table, this is one query: `SELECT * FROM entity_type_semantic_metadata WHERE entity_type_id = ? AND deleted = false`. With three tables, it requires three separate queries and result merging in the service layer.
- **Natural UNIQUE constraint.** The composite `(entity_type_id, target_type, target_id)` constraint prevents duplicate metadata records without application-level uniqueness checks. This is cleaner than three separate tables each needing their own unique constraint on `(entity_type_id, target_id)`.
- **Consistent with codebase patterns.** The existing codebase uses discriminator-style approaches for variant data (e.g., `ActivityEntity` with `entityType` and `operation` fields distinguishing different activity records in a single table). A single table with a type discriminator is a familiar pattern for the team.

---

## Alternatives Considered

### Alternative 1: Three Separate Tables

Create `entity_type_definitions` (for entity type semantic definitions), `entity_type_attribute_semantics` (for attribute metadata), and `entity_type_relationship_semantics` (for relationship metadata). Each table has its own FK to `entity_types(id)` and identical columns for `definition`, `classification`, and `tags`.

- **Pros:** Each table is purpose-specific with a clear name. No discriminator column needed. Database schema is more self-documenting. Can add target-specific columns later without nullable columns affecting other target types. Simpler mental model -- "attribute semantics are in the attribute semantics table."
- **Cons:** Three JPA entities with identical fields, three repositories with near-identical query methods, three sets of indexes to maintain. The `?include=semantics` feature requires three separate queries merged in the service layer. Activity logging for semantic metadata operations would need to handle three entity types instead of one. Test coverage triples for what is structurally identical logic. If the metadata shape ever changes (e.g., adding a `confidence` score), the change must be applied to three tables.
- **Why rejected:** The identical field shape makes three tables redundant infrastructure. The additional complexity in the service layer, repository layer, and test suite is not justified when all targets share the same columns. The batch query inefficiency for `?include=semantics` -- which is the primary read pattern -- is the strongest practical argument against this approach.

### Alternative 2: JPA Single Table Inheritance

Use Hibernate's `@Inheritance(strategy = InheritanceType.SINGLE_TABLE)` with `@DiscriminatorColumn(name = "target_type")` on a base `SemanticMetadataEntity` class, with three subclasses: `EntityTypeDefinitionMetadata`, `AttributeSemanticMetadata`, and `RelationshipSemanticMetadata`.

- **Pros:** Hibernate manages the discriminator value automatically. Type-safe subclasses allow compile-time distinction between target types. Repository queries can be typed to specific subclasses. Spring Data JPA supports `findByType` patterns with inheritance.
- **Cons:** Creates a class hierarchy for what is effectively identical data -- all three subclasses would have zero additional fields beyond the base class. The project's JPA entities all extend `AuditableSoftDeletableEntity`; adding an intermediate inheritance layer (AuditableSoftDeletableEntity -> SemanticMetadataEntity -> subclass) creates a three-level hierarchy not used anywhere else. Hibernate single-table inheritance has known quirks with `@SQLRestriction` and soft-delete patterns. Three subclass entities, three repositories, and the associated Spring Data JPA inheritance wiring add complexity without functional benefit.
- **Why rejected:** Over-engineering for identical field shapes. Introduces a JPA inheritance pattern not used anywhere else in the codebase, violating the consistency principle. The manual discriminator with a Kotlin enum (`SemanticMetadataTargetType`) is simpler, more explicit, and easier to debug than Hibernate's inheritance machinery. The team would need to learn and maintain a new JPA pattern for zero structural benefit.

### Alternative 3: Polymorphic JSONB Column

Store all semantic metadata for an entity type in a single JSONB column on a lightweight linking table: one row per entity type with a JSONB payload containing nested objects keyed by target type and target ID.

- **Pros:** Single row per entity type. All metadata fetched in one query with no discriminator filtering. Simple table structure.
- **Cons:** JSONB updates require read-modify-write cycles for any single metadata change. No row-level locking for concurrent edits to different targets on the same entity type. Cannot use SQL indexes on individual metadata fields (classification, tags) without GIN indexes on nested paths. Violates the project's pattern of using JSONB for flexible payloads but relational columns for queryable fields. Audit columns (`created_at`, `updated_at`) cannot track per-target changes.
- **Why rejected:** Trades queryability and concurrency for storage compactness. The primary use case includes updating individual attribute metadata independently -- JSONB read-modify-write on a shared row creates write contention. Per-target audit trails are lost. This approach moves in the opposite direction from the project's general pattern of pulling queryable data out of JSONB into relational columns.

---

## Consequences

### Positive

- Single repository and service for all semantic metadata operations. One `EntityTypeSemanticMetadataRepository` with query methods filtered by `targetType` when needed, and one `EntityTypeSemanticMetadataService` handling all CRUD.
- Batch query for all metadata of an entity type is a single SQL query: `findByEntityTypeIdAndDeletedFalse`. No result merging across multiple tables.
- The UNIQUE constraint on `(entity_type_id, target_type, target_id)` prevents duplicate metadata at the database level without application-side uniqueness checks.
- Adding a new target type in the future (e.g., if entity type "views" or "layouts" gain semantic metadata) requires only a new enum value and a new discriminator value in the CHECK constraint -- no new table, entity, or repository.

### Negative

- The `classification` column is semantically meaningful only for ATTRIBUTE targets. Entity type and relationship metadata records leave this column NULL. This is a minor schema impurity -- the column exists on rows where it has no meaning -- but the cost is negligible (nullable TEXT column, no storage overhead for NULL).
- `target_id` is not a true foreign key. For ATTRIBUTE targets, it references a UUID key inside the `entity_types.schema` JSONB map. For RELATIONSHIP targets, it references a UUID inside the `entity_types.relationships` JSONB array. Referential integrity cannot be enforced at the database level -- it is enforced by the service layer during lifecycle sync. If an attribute or relationship UUID is removed from the JSONB without the corresponding metadata being soft-deleted, orphaned metadata rows result. The [[riven/docs/system-design/flows/Flow - Semantic Metadata Lifecycle Sync]] design addresses this.
- Queries for a specific target type must always include `WHERE target_type = ?` to be efficient. Omitting the discriminator filter returns mixed results across all target types, which is correct for the `?include=semantics` batch read but requires filtering for single-target operations.

### Neutral

- The CHECK constraint on `target_type` ensures only valid discriminator values (`ENTITY_TYPE`, `ATTRIBUTE`, `RELATIONSHIP`) are stored. Invalid values are rejected at the database level.
- Partial indexes on `(entity_type_id) WHERE deleted = false` and `(target_type, target_id) WHERE deleted = false` optimize the two primary query patterns: batch-by-entity-type and lookup-by-target.
- The Kotlin enum `SemanticMetadataTargetType` in `riven.core.enums.entity` mirrors the database CHECK constraint values, providing compile-time safety in application code.

---

## Implementation Notes

- **`target_id` semantics vary by `target_type`:**
    - `ENTITY_TYPE`: `target_id` equals `entity_type_id`. The entity type is its own target -- the metadata describes the entity type itself. This keeps the UNIQUE constraint shape consistent across all target types.
    - `ATTRIBUTE`: `target_id` is the attribute UUID key from `entity_types.schema.properties`. This UUID is generated when an attribute is added to the entity type schema and is stable across schema updates.
    - `RELATIONSHIP`: `target_id` is the relationship definition UUID from the `entity_types.relationships` JSONB array (the `id` field of `EntityRelationshipDefinition`).
- **`classification` validation:** A CHECK constraint allows NULL but rejects non-null values outside the predefined classification set: `IDENTIFIER`, `CATEGORICAL`, `QUANTITATIVE`, `TEMPORAL`, `FREETEXT`, `RELATIONAL_REFERENCE`. The corresponding Kotlin enum is `SemanticAttributeClassification` in `riven.core.enums.entity`.
- **Primary query patterns:**
    - Batch read: `findByEntityTypeIdAndDeletedFalse(entityTypeId)` -- returns all metadata for an entity type across all target types. Used by `?include=semantics`.
    - Single target read: `findByEntityTypeIdAndTargetTypeAndTargetIdAndDeletedFalse(entityTypeId, targetType, targetId)` -- returns metadata for a specific target. Used by individual attribute/relationship semantic endpoints.
    - Batch by entity type list: `findByEntityTypeIdInAndDeletedFalse(entityTypeIds)` -- returns metadata for multiple entity types. Used by list endpoints with `?include=semantics`.
- **Index strategy:** Composite index on `(entity_type_id, target_type, target_id)` serves the UNIQUE constraint and covers all query patterns. Partial index `WHERE deleted = false` avoids scanning soft-deleted rows.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/decisions/ADR-002 Separate Table for Semantic Metadata]]
- [[riven/docs/system-design/feature-design/2. Planned/Semantic Metadata Foundation]]
- [[riven/docs/system-design/feature-design/_Sub-Domain Plans/Knowledge Layer]]
- [[riven/docs/system-design/domains/Entities/Entity Semantics/Entity Semantics]]
- [[riven/docs/system-design/domains/Entities/Entities]]
- [[riven/docs/system-design/domains/Entities/Type Definitions/Type Definitions]]
