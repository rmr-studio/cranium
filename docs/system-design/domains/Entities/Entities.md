---
tags:
  - architecture/domain
  - domain/entity
Created: 2026-02-01
Updated: 2026-02-21
---
# Domain: Entities

---

## Overview

The Entities domain provides a flexible, schema-driven data management system. Entity types define schemas (attributes and relationships), entity instances store structured data against those schemas, and a query subsystem enables filtered retrieval with JSONB-based attribute queries and relationship traversal.

---

# [[2. Areas/2.1 Startup & Business/Riven/2. System Design/domains/Entities/FAQ|FAQ]]

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
| [[Type Definitions]] | Entity type schema management — attributes, relationships, publishing |
| [[Entity Management]] | Entity instance lifecycle — CRUD with validation and relationship hydration |
| [[Relationships]] | Relationship definitions (type-level) and instance data (entity-level) with table-based architecture |
| [[Querying]] | Query pipeline for filtered entity retrieval with JSONB attribute filters and relationship traversal |
| [[Validation]] | Schema validation for entity instances before persistence |
| [[Entity Semantics]] | Semantic metadata for entity types, attributes, and relationships — definitions, classifications, and tags |

---

## Flows

| Flow        | Type                     | Description |
| ----------- | ------------------------ | ----------- |
| [[Entity CRUD]] | User-facing | Entity creation, update, and deletion flow (Phase 4) |
| [[Entity Type Definition]] | User-facing | Entity type schema definition and modification flow (Phase 4) |

---

## Data

### Owned Entities

| Entity | Purpose | Key Fields |
| ------ | ------- | ---------- |
| EntityTypeEntity | Entity type schema definitions | id, key, displayNameSingular, displayNamePlural, workspaceId, schema, columns, identifierKey |
| EntityEntity | Entity instances with JSONB attribute data | id, typeId, typeKey, workspaceId, payload, iconType, iconColour, identifierKey |
| EntityRelationshipEntity | Relationship instances linking entities | id, sourceId, targetId, definitionId, workspaceId, semanticContext, linkSource |
| EntityUniqueValueEntity | Unique constraint tracking for entity attributes | entityId, typeId, fieldId, value, workspaceId |
| RelationshipDefinitionEntity | Relationship definitions (type-level configuration) | id, workspaceId, sourceEntityTypeId, name, iconType, iconColour, allowPolymorphic, cardinalityDefault, protected, systemType |
| RelationshipTargetRuleEntity | Per-target-type configuration for relationship definitions | id, relationshipDefinitionId, targetEntityTypeId, semanticTypeConstraint, cardinalityOverride, inverseName |
| RelationshipDefinitionExclusionEntity | Target-side opt-out records for relationship definitions | id, relationshipDefinitionId, entityTypeId |
| EntityTypeSemanticMetadataEntity | Semantic metadata records for entity types, attributes, and relationships | id, workspaceId, entityTypeId, targetType, targetId, definition, classification, tags |

### Database Tables

| Table | Entity | Notes |
| ----- | ------ | ----- |
| entity_types | EntityTypeEntity | Entity type schemas with JSONB schema column. Relationship definitions moved to dedicated tables. |
| entities | EntityEntity | Entity instances with JSONB payload column for attribute data |
| entity_relationships | EntityRelationshipEntity | Relationship instance data. References relationship_definitions via definition_id column. |
| entity_unique_values | EntityUniqueValueEntity | Normalized unique value tracking for uniqueness constraints |
| relationship_definitions | RelationshipDefinitionEntity | Relationship type-level configuration. Indexed on (workspace_id, source_entity_type_id) |
| relationship_target_rules | RelationshipTargetRuleEntity | Per-target-type rules with cardinality overrides. Indexed on definition_id and target_entity_type_id |
| relationship_definition_exclusions | RelationshipDefinitionExclusionEntity | Target-side opt-out records. Unique on (definition_id, entity_type_id). Hard-deleted when reversed or when definition is deleted |
| entity_type_semantic_metadata | EntityTypeSemanticMetadataEntity | Single-table discriminator pattern for type/attribute/relationship metadata. JSONB tags column. Partial indexes on soft-delete flag |

---

## External Dependencies

None. The Entities domain operates entirely within the application database (PostgreSQL) and does not integrate with external systems.

---

## Domain Interactions

### Depends On

| Domain | What We Consume | Via Component | Related Flow |
|--------|----------------|---------------|--------------|
| [[Workspaces & Users]] | Workspace scoping via RLS | PostgreSQL RLS policies | [[Auth & Authorization]] |
| [[Workspaces & Users]] | @PreAuthorize authorization checks | [[WorkspaceSecurity]] | [[Auth & Authorization]] |
| [[Workspaces & Users]] | User context for activity logging | [[AuthTokenService]] | [[Entity CRUD]] |

### Consumed By

| Consumer | What They Consume | Via Component | Related Flow |
|----------|------------------|---------------|--------------|
| [[Workflows]] | Entity CRUD for workflow node actions | [[EntityService]], [[EntityContextService]] | [[Workflow Execution]] |
| REST API | Entity and entity type management | EntityController, EntityTypeController | [[Entity CRUD]], [[Entity Type Definition]] |
| [[Knowledge]] | Semantic metadata CRUD endpoints | [[EntityTypeSemanticMetadataService]] via [[KnowledgeController]] | |

---

## Key Decisions

| Decision | Summary |
| -------- | ------- |
| JSONB for attribute storage | Entity attributes stored in JSONB payload column for schema flexibility |
| Always bidirectional | All relationships are always bidirectional. Inverse visibility resolved at query time by matching target types against rules. Target-side exclusions allow entity types to opt out without affecting other types. |
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
| Semantic type constraint matching stubbed | EntityRelationshipService.findMatchingRule() only matches by explicit type ID; semantic constraint lookup not yet implemented | Low |
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
| 2026-03-06 | Always bidirectional — removed `inverse_visible` flag. Added target-side exclusion mechanism (`relationship_definition_exclusions` table) allowing entity types to opt out of implicit relationship definitions. Exclusions respected in definition resolution and native inverse link queries. | Target-Side Exclusions / Always Bidirectional |
