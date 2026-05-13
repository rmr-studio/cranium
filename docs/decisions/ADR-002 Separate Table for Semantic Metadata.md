---
tags:
  - adr/superseded
  - architecture/decision
Created: 2026-02-18
Updated: 2026-05-13
Superseded By: "[[ADR-013 Thin Per-Kind Frontmatter in Code, Loose Extras]]"
---

# ADR-002: Separate Table for Semantic Metadata

> **Superseded / reworked by the 2026-05 architecture pivot.** There are no user-defined entity types after the pivot — a `Page` is one of 7 fixed kinds. The semantic-metadata machinery (`entity_type_semantic_metadata`, the `?include=semantics` opt-in, `EntityTypeSemanticMetadataService`) is reworked: per-kind frontmatter shapes are declared in Kotlin on the sealed `Page` subclass (loose extra keys stored in `pages.frontmatter` jsonb, not validated), and the `SemanticGroup`/`LifecycleDomain`/`entity_connotation` machinery is repurposed as the engineering classification axis (`pages.classification`). See [[architecture-pivot]] §Reuse / Rework / Replace / Delete, [[ADR-013 Thin Per-Kind Frontmatter in Code, Loose Extras]], and [[ADR-018 One pages Table Family for Synthesis Storage]].

---

## Context

The Knowledge Layer requires semantic metadata (definitions, classifications, tags) to be attached to entity types, their attributes, and their relationship definitions. The existing `entity_types` table stores attribute schemas as a JSONB column (`schema`) keyed by UUID, and relationship definitions as a JSONB array (`relationships`) of `EntityRelationshipDefinition` objects. Both columns are loaded on every entity type read via `EntityTypeEntity` and mapped through `toModel()`.

Entity type CRUD is the hottest read path in the system -- every entity query resolves its parent type to access schema definitions, attribute metadata, and relationship structure. The `EntityTypeService`, `EntityTypeAttributeService`, and `EntityTypeRelationshipService` all operate on the `entity_types` row and its JSONB columns. Any change to the `EntityTypeEntity` data class or the `entity_types` table schema directly impacts these services and their associated test suites.

The question is whether semantic metadata should be embedded into the existing JSONB structures (adding fields to `schema.properties` entries and the relationship definitions array), added as new columns on `entity_types`, or stored in a completely separate table. Requirement INFRA-06 explicitly states: "Semantic metadata stored in separate tables (not in existing entity_types schema JSONB) to avoid impacting existing entity CRUD."

---

## Decision

Store semantic metadata in a new `entity_type_semantic_metadata` table with a foreign key to `entity_types(id)` and `workspaces(id)`, completely separate from the existing entity type JSONB schema and relationship columns. The existing `EntityTypeEntity`, `EntityTypeService`, `EntityTypeAttributeService`, and `EntityTypeRelationshipService` remain unchanged. Semantic metadata is fetched only when explicitly requested via a `?include=semantics` query parameter, handled by a dedicated `EntityTypeSemanticMetadataService`.

---

## Rationale

- **Entity type CRUD is the hot path.** Every entity read resolves its type definition. Adding semantic fields to the `entity_types` row -- whether as JSONB nesting or additional columns -- increases the data loaded on every query, even when semantic metadata is not needed. The separate table ensures zero performance impact on the existing read path.
- **INFRA-06 explicitly requires this separation.** The requirement was established specifically to protect the existing entity CRUD pipeline from side effects of the Knowledge Layer rollout.
- **The existing `EntityTypeEntity` data class is already complex.** It carries `schema` (JSONB map of attribute definitions), `relationships` (JSONB array of relationship definitions), `fieldOrder` (JSONB array), and audit columns. Adding optional semantic fields increases cognitive load and test surface area for a cross-cutting concern that most entity operations do not need.
- **Semantic metadata has a different lifecycle than entity type definitions.** Definitions and classifications are authored and refined iteratively by workspace users. They should not trigger `updated_at` changes on the entity type itself, which would create false signals for any logic that watches entity type modification timestamps (cache invalidation, sync triggers).
- **Independent queryability.** A separate table allows querying semantic metadata directly -- for example, searching across all entity types by classification or tag -- without scanning or deserializing entity type JSONB payloads.
- **The `?include=semantics` opt-in pattern** lets existing API consumers remain completely unaffected. The entity type response shape does not change unless the caller explicitly requests semantic enrichment.

---

## Alternatives Considered

### Alternative 1: Embed Semantic Fields in Existing JSONB Columns

Add a `semanticDefinition` field to the top-level `entity_types` row (or as a nested JSONB field), and extend each attribute entry in `schema.properties` with `semanticDescription` and `classification` fields. Relationship definitions in the `relationships` array would gain similar nested semantic fields.

- **Pros:** Single query fetches entity type with all semantic data. No new table or JPA entity. Simpler initial implementation -- just extend existing JSONB structures. `toModel()` mappings handle everything in one pass.
- **Cons:** Every entity type query loads semantic data regardless of whether it is needed. The `EntityTypeEntity` data class grows with optional fields that most operations ignore. Existing tests for `EntityTypeService`, `EntityTypeAttributeService`, and `EntityTypeRelationshipService` must be updated to handle new fields. Changes to semantic metadata trigger `updated_at` on the entity type, coupling their lifecycles. Attribute semantic data would be nested JSONB inside JSONB (schema.properties[uuid].semanticDescription), making queries against semantic content impractical.
- **Why rejected:** Directly violates INFRA-06. Performance impact on the entity type read hot path. Couples semantic metadata lifecycle to entity type lifecycle. Makes semantic-specific queries (e.g., "find all attributes classified as IDENTIFIER") expensive or impossible without full JSONB traversal.

### Alternative 2: Add Dedicated Columns to entity_types Table

Add `semantic_definition TEXT`, `attribute_semantics JSONB`, and `relationship_semantics JSONB` as nullable columns directly on the `entity_types` table. These columns would store semantic metadata separately from the existing `schema` and `relationships` columns but on the same row.

- **Pros:** Single table, simple FK relationships. Direct column access without JSONB nesting. Entity type and its semantics are co-located, making transactional consistency trivial.
- **Cons:** Still loaded on every entity type query unless SELECT projection is used -- but `EntityTypeEntity` with JPA loads full rows by default. Modifies the `entity_types` table schema, requiring migration coordination. Three new nullable columns on a central table that most operations do not use. `EntityTypeEntity` data class still grows.
- **Why rejected:** Violates the spirit of INFRA-06 even if technically separate columns. The data is still on the same row and loaded by default in JPA. Requires either SELECT projection (breaking the standard repository pattern used throughout the codebase) or accepting that semantic data is loaded on every entity type read.

---

## Consequences

### Positive

- Zero impact on existing entity type query performance. The `entity_types` table, `EntityTypeEntity` data class, and all existing services remain untouched.
- Clean separation of concerns. Semantic metadata has its own JPA entity, repository, and service, following the project's pattern of splitting services by sub-domain concern.
- Semantic metadata can be added, modified, and queried independently without write contention on entity type rows. Concurrent semantic edits do not conflict with entity type schema changes.
- The `?include=semantics` pattern provides backward-compatible API evolution. Existing API consumers see no change.
- Enables semantic-specific queries (search by classification, filter by tag) with standard indexed SQL rather than JSONB path queries.

### Negative

- Requires an additional query when semantics are requested. This is mitigated by batch loading via `findByEntityTypeIdIn` for list endpoints, but single entity type reads with semantics require two queries.
- Slightly more complex data model: a new table, a new JPA entity (`EntityTypeSemanticMetadataEntity`), a new repository (`EntityTypeSemanticMetadataRepository`), and a new service (`EntityTypeSemanticMetadataService`).
- Lifecycle synchronization between entity types and their semantic metadata requires explicit hooks. When an attribute is removed from an entity type's schema, the corresponding semantic metadata record must be soft-deleted. This requires event hooks in `EntityTypeAttributeService` and `EntityTypeRelationshipService`.

### Neutral

- FK with `ON DELETE CASCADE` handles cleanup if entity types are hard-deleted. Since the project uses soft-delete by default, cascade soft-delete is handled at the service layer via lifecycle hooks rather than database constraints.
- The separate table follows the same workspace isolation pattern (`workspace_id` FK, RLS policy) as all other tables in the system.
- The new table uses the same `AuditableSoftDeletableEntity` base class and `@SQLRestriction("deleted = false")` pattern as all other JPA entities.

---

## Implementation Notes

- **Table:** `entity_type_semantic_metadata` with columns: `id` (UUID PK), `entity_type_id` (FK to `entity_types`), `workspace_id` (FK to `workspaces`), `target_type` (TEXT discriminator), `target_id` (UUID), `definition` (TEXT), `classification` (TEXT, nullable), `tags` (JSONB array), plus audit columns from `AuditableSoftDeletableEntity`. See [[2. Areas/2.1 Startup & Content/Cranium/2. System Design/decisions/ADR-003 Single Discriminator Table for Metadata Targets]] for the discriminator design.
- **JPA entity:** `EntityTypeSemanticMetadataEntity` in `cranium.core.entity.entity` package, extending `AuditableSoftDeletableEntity`, with `@SQLRestriction("deleted = false")`.
- **Repository:** `EntityTypeSemanticMetadataRepository` in `cranium.core.repository.entity` with queries scoped by `workspaceId` and `entityTypeId`.
- **Service:** `EntityTypeSemanticMetadataService` in `cranium.core.service.entity` as the sole writer. All reads and writes go through this service. `@PreAuthorize` on all methods for workspace access control.
- **RLS policy** on `entity_type_semantic_metadata` matching the existing workspace isolation pattern used by `entity_types` and other workspace-scoped tables.
- **Lifecycle hooks** in [[cranium/docs/system-design/domains/Entities/Type Definitions/EntityTypeAttributeService]] (attribute removal triggers metadata soft-delete) and [[cranium/docs/system-design/domains/Entities/Relationships/EntityTypeRelationshipService]] (relationship removal triggers metadata soft-delete). See [[cranium/docs/system-design/flows/Flow - Semantic Metadata Lifecycle Sync]] for the full cascade design.
- **SQL file** added to `db/schema/` in the appropriate numbered subdirectory following the existing execution order.

---

## Related

- [[2. Areas/2.1 Startup & Content/Cranium/2. System Design/decisions/ADR-003 Single Discriminator Table for Metadata Targets]]
- [[cranium/docs/system-design/feature-design/2. Planned/Semantic Metadata Foundation]]
- [[cranium/docs/system-design/domains/Entities/Entity Semantics/Entity Semantics]]
- [[cranium/docs/system-design/domains/Entities/Type Definitions/Type Definitions]]
- [[cranium/docs/system-design/feature-design/_Sub-Domain Plans/Knowledge Layer]]
