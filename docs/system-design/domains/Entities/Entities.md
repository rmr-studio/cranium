---
tags:
  - architecture/domain
  - domain/entity
Created: 2026-02-01
Updated: 2026-03-27
---
# Domain: Entities

---

## Overview

The Entities domain provides a flexible, schema-driven data management system. Entity types define schemas (attributes and relationships), entity instances store structured data against those schemas, and a query subsystem enables filtered retrieval with attribute queries against a normalized attribute table and relationship traversal.

---

# [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/FAQ|FAQ]]

## Boundaries

### This Domain Owns

- Entity type definitions (attributes, relationships, configuration)
- Entity instance CRUD (create, read, update, soft-delete)
- Entity relationship management (type-level definitions and instance-level data)
- Entity validation against schemas (required fields, property types, unique constraints)
- Entity querying with attribute and relationship filters
- Activity logging for entity operations

### This Domain Does NOT Own

- Workflow execution (Workflows domain consumes entities via node actions)
- Workspace scoping enforcement (enforced by Workspaces domain via RLS)
- Block management (separate domain)
- Entity templates (future domain, not yet implemented)

---

## Sub-Domains

| Component | Purpose |
| --------- | ------- |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Type Definitions/Type Definitions]] | Entity type schema management — attributes, relationships, publishing |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/Entity Management]] | Entity instance lifecycle — CRUD with validation and relationship hydration |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Relationships/Relationships]] | Relationship definitions (type-level) and instance data (entity-level) with table-based architecture |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/Querying]] | Query pipeline for filtered entity retrieval with EXISTS-based attribute filters and relationship traversal |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Validation/Validation]] | Schema validation for entity instances before persistence |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Semantics/Entity Semantics]] | Semantic metadata for entity types, attributes, and relationships — definitions, classifications, and tags |

---

## Flows

| Flow        | Type                     | Description |
| ----------- | ------------------------ | ----------- |
| [[riven/docs/system-design/flows/Entity CRUD]] | User-facing | Entity creation, update, and deletion flow (Phase 4) |
| [[riven/docs/system-design/flows/Entity Type Definition]] | User-facing | Entity type schema definition and modification flow (Phase 4) |

---

## Data

### Owned Entities

| Entity | Purpose | Key Fields |
| ------ | ------- | ---------- |
| EntityTypeEntity | Entity type schema definitions | id, key, displayNameSingular, displayNamePlural, workspaceId, schema, columns, identifierKey, lifecycleDomain |
| EntityEntity | Entity instances (attribute data stored in entity_attributes) | id, typeId, typeKey, workspaceId, iconType, iconColour, identifierKey |
| EntityAttributeEntity | Normalized per-attribute values for entity instances | id, entityId, workspaceId, typeId, attributeId, schemaType, value |
| EntityRelationshipEntity | Relationship instances linking entities | id, sourceId, targetId, definitionId, workspaceId, semanticContext, linkSource |
| EntityUniqueValueEntity | Unique constraint tracking for entity attributes | entityId, typeId, fieldId, value, workspaceId |
| RelationshipDefinitionEntity | Relationship definitions (type-level configuration) | id, workspaceId, sourceEntityTypeId, name, iconType, iconColour, cardinalityDefault, protected, systemType |
| RelationshipTargetRuleEntity | Per-target-type configuration for relationship definitions | id, relationshipDefinitionId, targetEntityTypeId, cardinalityOverride, inverseName |
| EntityTypeSemanticMetadataEntity | Semantic metadata records for entity types, attributes, and relationships | id, workspaceId, entityTypeId, targetType, targetId, definition, classification, tags |

### Database Tables

| Table | Entity | Notes |
| ----- | ------ | ----- |
| entity_types | EntityTypeEntity | Entity type schemas with JSONB schema column. Relationship definitions moved to dedicated tables. |
| entities | EntityEntity | Entity instances (attribute data moved to entity_attributes table) |
| entity_attributes | EntityAttributeEntity | Normalized per-attribute storage with per-row values. Indexed on (entity_id, attribute_id), (attribute_id, type_id, value), and workspace_id |
| entity_relationships | EntityRelationshipEntity | Relationship instance data. References relationship_definitions via definition_id column. |
| entity_unique_values | EntityUniqueValueEntity | Normalized unique value tracking for uniqueness constraints |
| relationship_definitions | RelationshipDefinitionEntity | Relationship type-level configuration. Indexed on (workspace_id, source_entity_type_id) |
| relationship_target_rules | RelationshipTargetRuleEntity | Per-target-type rules with cardinality overrides. Indexed on definition_id and target_entity_type_id |
| entity_type_semantic_metadata | EntityTypeSemanticMetadataEntity | Single-table discriminator pattern for type/attribute/relationship metadata. JSONB tags column. Partial indexes on soft-delete flag |

---

## External Dependencies

None. The Entities domain operates entirely within the application database (PostgreSQL) and does not integrate with external systems.

---

## Domain Interactions

### Depends On

| Domain | What We Consume | Via Component | Related Flow |
|--------|----------------|---------------|--------------|
| [[riven/docs/system-design/domains/Workspaces & Users/Workspaces & Users]] | Workspace scoping via RLS | PostgreSQL RLS policies | [[riven/docs/system-design/flows/Auth & Authorization]] |
| [[riven/docs/system-design/domains/Workspaces & Users/Workspaces & Users]] | @PreAuthorize authorization checks | [[riven/docs/system-design/domains/Workspaces & Users/Auth & Authorization/WorkspaceSecurity]] | [[riven/docs/system-design/flows/Auth & Authorization]] |
| [[riven/docs/system-design/domains/Workspaces & Users/Workspaces & Users]] | User context for activity logging | [[riven/docs/system-design/domains/Workspaces & Users/Auth & Authorization/AuthTokenService]] | [[riven/docs/system-design/flows/Entity CRUD]] |

### Consumed By

| Consumer | What They Consume | Via Component | Related Flow |
|----------|------------------|---------------|--------------|
| [[riven/docs/system-design/domains/Workflows/Workflows]] | Entity CRUD for workflow node actions | [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Management/EntityService]], [[riven/docs/system-design/domains/Workflows/State Management/EntityContextService]] | [[riven/docs/system-design/flows/Workflow Execution]] |
| REST API | Entity and entity type management | EntityController, EntityTypeController | [[riven/docs/system-design/flows/Entity CRUD]], [[riven/docs/system-design/flows/Entity Type Definition]] |
| [[riven/docs/system-design/domains/Knowledge/Knowledge]] | Semantic metadata CRUD endpoints | [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Semantics/EntityTypeSemanticMetadataService]] via [[riven/docs/system-design/domains/Knowledge/KnowledgeController]] | |

---

## Key Decisions

| Decision | Summary |
| -------- | ------- |
| Normalized attribute storage | Entity attributes stored in dedicated `entity_attributes` table (one row per attribute per entity) for indexed cross-entity queries, EXISTS-based filtering, and future trigram fuzzy matching. Replaced earlier JSONB payload approach. |
| Always bidirectional | All relationships are always bidirectional. Inverse visibility resolved at query time by matching target types against explicit target rules. |
| Table-based relationship definitions | Relationship configuration stored in dedicated relationship_definitions and relationship_target_rules tables instead of JSONB field on entity_types |
| Write-time cardinality enforcement | Cardinality limits enforced at relationship insert time, not just at schema level |
| Fallback connection definitions | System-managed CONNECTED_ENTITIES definitions auto-created per entity type enable lightweight linking without user-defined relationship schemas |
| Mutable entity types | Entity types update in place (unlike BlockTypes which are versioned) |
| Query pipeline architecture | Filter validation → AST traversal → SQL generation → parameterized execution |
| Separate table for semantic metadata | Semantic metadata stored in dedicated table (not embedded in entity_types JSONB) to protect entity CRUD hot path |
| Single discriminator table for metadata | One table with target_type discriminator (ENTITY_TYPE, ATTRIBUTE, RELATIONSHIP) rather than three separate tables |

---

## Technical Debt

| Issue | Impact | Effort |
| ----- | ------ | ------ |
| Missing cross-type attribute validation in query filters | Relationship filters validate against root entity type attributes instead of target entity type | Low |
| CountMatches filter unsupported | RelationshipSqlGenerator throws UnsupportedOperationException for CountMatches filter variant | Low |

---

## Recent Changes

| Date       | Change                                                                                                               | Feature/ADR                  |
| ---------- | -------------------------------------------------------------------------------------------------------------------- | ---------------------------- |
| 2026-02-01 | Domain structure created                                                                                             | Phase 2 initialization       |
| 2026-02-08 | Domain overview and subdomain docs created                                                                           | [[02-01-PLAN]]               |
| 2026-02-19 | Entity Semantics subdomain implemented — semantic metadata service, repository, lifecycle hooks, KnowledgeController | Semantic Metadata Foundation |
| 2026-02-21 | Entity relationship overhaul — replaced ORIGIN/REFERENCE sync with table-based architecture, added relationship_definitions and relationship_target_rules tables, write-time cardinality enforcement, query-time inverse resolution | Entity Relationships |
| 2026-03-01 | Entity Connections — system-managed CONNECTED_ENTITIES definitions, unified relationship CRUD API (addRelationship, getRelationships, updateRelationship, removeRelationship), IS_RELATED_TO query filter, bidirectional existence queries | Entity Connections / Unified Relationship CRUD |
| 2026-03-06 | Always bidirectional — removed `inverse_visible` flag. Inverse visibility resolved at query time via explicit target rules. | Always Bidirectional |
| 2026-03-09 | Relationship simplification — removed `allowPolymorphic` field (replaced with computed `isPolymorphic` derived from `systemType != null`), removed `semanticTypeConstraint` from target rules, removed `relationship_definition_exclusions` table and exclusion mechanism, made `targetEntityTypeId` non-nullable. Only system definitions (CONNECTED_ENTITIES) are polymorphic. Repository queries converted from native SQL to JPQL. | Relationship Simplification |
| 2026-03-09 | Entity attributes normalization — extracted attribute storage from JSONB `payload` column on `entities` table into normalized `entity_attributes` table. AttributeSqlGenerator rewritten from JSONB operators to EXISTS subqueries. New EntityAttributeService, EntityAttributeRepository, EntityAttributeEntity components. | Entity Attributes Normalization |
| 2026-03-26 | lifecycle_domain column added to entity_types — classifies entity types into lifecycle stages (ACQUISITION, ONBOARDING, USAGE, SUPPORT, BILLING, RETENTION, UNCATEGORIZED) | Lifecycle Spine |
| 2026-03-27 | Projected entities and hub model — PROJECTED source type, field ownership model, timestamp-based conflict resolution | Entity Ingestion Pipeline |

---

## Projected Entities and the Hub Model

Core entity types serve as the user-facing **hub** — all user interaction (viewing, editing, querying) happens against core entities. The ingestion pipeline can now automatically create entity instances from integration data.

### Source Types

Entities can have `sourceType = PROJECTED` (auto-created by the ingestion pipeline from integration data). PROJECTED entities coexist alongside `USER_CREATED` entities in the same entity type table. The `SourceType` enum has been extended with the `PROJECTED` value.

### Field Ownership

> [!info] Field ownership model
> **Mapped fields** on projected entities are owned by the integration source — they are overwritten on each sync cycle. **Unmapped fields** are user-owned and are never touched by the sync pipeline.

This allows users to enrich projected entities with custom data while keeping integration-sourced fields in sync.

### Conflict Resolution

When two integrations project to the same core entity (via identity resolution), **most recent sync wins** — timestamp-based conflict resolution determines which source's values are persisted for overlapping mapped fields.

### Audit Trail

Field-level change logging via `activityService` tracks when sync overwrites user-edited values on mapped fields, providing visibility into data provenance.

### Model Changes

- `SourceType` enum extended with `PROJECTED` value
- `ProjectionAcceptRule` changed from nullable single value to `List<ProjectionAcceptRule>` on `CoreModelDefinition`

### References

- [[2. Areas/2.1 Startup & Content/Riven/7. Todo/Entity Ingestion Pipeline]]
- [[riven/docs/system-design/feature-design/1. Planning/Smart Projection Architecture]]
