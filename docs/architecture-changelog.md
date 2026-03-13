# Architecture Changelog

## 2026-03-09 — Entity Attributes Normalization

**Domains affected:** Entities

**What changed:**

- Extracted entity attribute storage from JSONB `payload` column on `entities` table into normalized `entity_attributes` table (one row per attribute per entity)
- `EntityEntity.payload` JSONB column removed; `EntityEntity.toModel()` now accepts optional `attributes` parameter
- `AttributeSqlGenerator` completely rewritten — JSONB operators (`@>`, `->`, `->>`) replaced with EXISTS/NOT EXISTS subqueries against `entity_attributes`. ObjectMapper dependency removed.
- `EntityService` save flow now persists attributes to `entity_attributes` via `EntityAttributeService` (delete-all + re-insert). All retrieval methods batch-load attributes.
- `EntityService` delete flow soft-deletes attributes via `EntityAttributeService.softDeleteByEntityIds()`
- `EntityQueryService` now hydrates query results with attributes from normalized table
- `EntityValidationService.validateEntity()` and `validateExistingEntitiesAgainstNewSchema()` accept optional attributes parameters (read from normalized table instead of entity payload)
- `EntityTypeAttributeService` loads attributes from normalized table during breaking change validation
- Cross-domain consumers (`BlockReferenceHydrationService`, `EntityContextService`) now inject `EntityAttributeService` for attribute loading

**New cross-domain dependencies:** No (both cross-domain consumers already depended on Entities domain)

**New components introduced:**

- `EntityAttributeService` — Service for normalized attribute CRUD with delete-all + re-insert pattern
- `EntityAttributeRepository` — JPA repository with derived queries, native hard-delete, and native soft-delete
- `EntityAttributeEntity` — JPA entity mapping to `entity_attributes` table with JSONB value column and soft-delete support

## 2026-03-01 — Unified Relationship CRUD (Connection → Relationship refactor)

**Domains affected:** Entities

**What changed:**

- Replaced "connection" CRUD on `EntityRelationshipService` with unified "relationship" CRUD — `addRelationship`, `getRelationships`, `updateRelationship`, `removeRelationship`
- `addRelationship` accepts optional `definitionId` — when provided, creates a typed relationship with target type validation and cardinality enforcement; when omitted, falls back to the system `CONNECTED_ENTITIES` definition
- `getRelationships` returns ALL relationships for an entity (both fallback and typed), with optional `definitionId` filter — previously only returned fallback connections
- `updateRelationship` and `removeRelationship` work on any relationship regardless of definition type — removed `validateIsFallbackConnection` guard
- Controller endpoints renamed: `/connections` → `/relationships` with corresponding method renames
- Request models renamed: `CreateConnectionRequest` → `AddRelationshipRequest`, `UpdateConnectionRequest` → `UpdateRelationshipRequest`
- Response model renamed: `ConnectionResponse` → `RelationshipResponse` — now includes `definitionId` and `definitionName`
- Activity logging uses `Activity.ENTITY_RELATIONSHIP` for new operations — `Activity.ENTITY_CONNECTION` retained in enum for backwards compatibility with existing log entries
- Duplicate detection: bidirectional for fallback definitions (A→B or B→A), directional for typed definitions (A→B only under that definition)

**New cross-domain dependencies:** No

**New components introduced:**

- `AddRelationshipRequest` — Request model with optional `definitionId` for unified relationship creation
- `UpdateRelationshipRequest` — Request model for semantic context updates
- `RelationshipResponse` — Response model including definition metadata

## 2026-02-21 — Entity Relationship Overhaul

**Domains affected:** Entities, Workflows

**What changed:**

- Replaced ORIGIN/REFERENCE bidirectional relationship sync pattern with table-based architecture using `relationship_definitions` and `relationship_target_rules` tables
- Inverse rows are no longer stored — bidirectional visibility is resolved at query time via `inverseVisible` flag on `RelationshipTargetRuleEntity`
- Deleted `EntityTypeRelationshipDiffService` and `EntityTypeRelationshipImpactAnalysisService` — diff logic consolidated into `EntityTypeRelationshipService` target rule diffing, impact analysis uses simple two-pass pattern
- `EntityTypeRelationshipService` entirely rewritten — new CRUD interface managing definitions and target rules directly
- `EntityRelationshipService` entirely rewritten — write-time cardinality enforcement (source-side and target-side), target type validation against definition rules, no more bidirectional sync
- `EntityTypeService` updated — removed DiffService/ImpactAnalysisService dependencies, added `RelationshipDefinitionRepository` and `EntityRelationshipRepository` for direct access
- `EntityService` updated — relationship payload now keyed by definition ID, delegates relationship saves per-definition to `EntityRelationshipService`
- `EntityQueryService` updated — loads relationship definitions to resolve FORWARD/INVERSE query direction before SQL generation
- `RelationshipSqlGenerator` updated — new `direction: QueryDirection` parameter, column renamed from `relationship_field_id` to `relationship_definition_id`
- `AttributeFilterVisitor` updated — passes `relationshipDirections` map through to SQL generator
- `QueryFilterValidator` updated — now uses `RelationshipDefinition` model instead of `EntityRelationshipDefinition`
- `EntityContextService` (Workflows domain) updated — loads definitions via `RelationshipDefinitionRepository` and `RelationshipTargetRuleRepository` instead of reading from EntityType JSONB schema

**New cross-domain dependencies:** Yes — Workflows → Entities: `EntityContextService` now injects `RelationshipDefinitionRepository` and `RelationshipTargetRuleRepository` (deepening existing coupling)

**New components introduced:**

- `RelationshipDefinitionEntity` — JPA entity for schema-level relationship configuration (replaces JSONB `relationships` field on EntityTypeEntity)
- `RelationshipTargetRuleEntity` — JPA entity for per-target-type configuration with cardinality overrides and inverse visibility
- `RelationshipDefinition` — Domain model for relationship definitions
- `RelationshipTargetRule` — Domain model for target rules
- `RelationshipDefinitionRepository` — JPA repository with workspace-scoped queries
- `RelationshipTargetRuleRepository` — JPA repository with inverse-visible queries
- `QueryDirection` enum — FORWARD vs INVERSE for SQL generation
- `DeleteDefinitionImpact` — Simple data class for two-pass impact pattern (replaces complex `EntityTypeRelationshipImpactAnalysis`)
