# Architecture Changelog

## 2026-04-28 — Schema Hash Numeric Canonicalization Format Change

**Domains affected:** Catalog (schema reconciliation)

**What changed:**

- `SchemaHashUtil.canonicalize()` no longer routes numbers through `Number.toDouble()`. Numeric values now collapse to the canonical `BigDecimal(value.toString()).stripTrailingZeros().toPlainString()` representation, preserving precision for integers above 2^53 and eliminating hash collisions between distinct large longs.
- Stored `entity_types.source_schema_hash` values written under the previous representation (e.g. `0.0` style for integral schema constants) will not match newly computed hashes for schemas containing numeric values. Existing reconciliation paths handle this as a legacy mismatch and re-stamp via the established legacy-stamping path.

**New cross-domain dependencies:** No

**New components introduced:** None — internal utility behavior change only.

## 2026-03-17 — Identity Match Dispatch Infrastructure and EntityService Event Publishing

**Domains affected:** Identity, Entity, Workflow

**What changed:**

- Created `IdentityMatchQueueProcessorService`: claims IDENTITY_MATCH items from the execution queue and starts `IdentityMatchWorkflow` on the `identity.match` Temporal task queue; handles `WorkflowExecutionAlreadyStarted` as idempotent success
- Created `IdentityMatchDispatcherService`: `@Scheduled` + `@SchedulerLock` poller for the IDENTITY_MATCH queue and stale item recovery; uses unique ShedLock names (`processIdentityMatchQueue`, `recoverStaleIdentityMatchItems`) to avoid contention with the workflow execution dispatcher
- Removed `@ConditionalOnProperty(riven.workflow.engine.enabled)` from `WorkflowExecutionDispatcherService` and `WorkflowExecutionQueueProcessorService` — Temporal is a required infrastructure dependency, not optional
- `EntityService` now publishes `IdentityMatchTriggerEvent` after every `saveEntity()` call; only IDENTIFIER-classified attribute values are included (via `EntityTypeClassificationService`); event fires within the `@Transactional` boundary so `@TransactionalEventListener(AFTER_COMMIT)` in the listener fires post-commit
- Full pipeline is now wired: `EntityService` → `IdentityMatchTriggerEvent` → `IdentityMatchTriggerListener` → `IdentityMatchQueueService` → execution queue → `IdentityMatchDispatcherService` → `IdentityMatchQueueProcessorService` → Temporal `IdentityMatchWorkflow`

**New cross-domain dependencies:** Yes — `EntityService` (Entity domain) → `EntityTypeClassificationService` (Identity domain) via constructor injection for IDENTIFIER attribute ID lookup

**New components introduced:**

- `IdentityMatchQueueProcessorService` — Per-item REQUIRES_NEW processor that starts IdentityMatchWorkflow via WorkflowClient with retry/fail logic
- `IdentityMatchDispatcherService` — Scheduled batch poller and stale recovery service for the IDENTITY_MATCH execution queue

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

## 2026-03-16 — Identity Resolution Feature Design Populated

**Domains affected:** Entities, Integrations

**What changed:**

- Populated the Identity Resolution feature design document from draft template to full specification, synthesizing content from CEO review (PLAN-REVIEW), engineering review (ENG-REVIEW), PROJECT, ROADMAP, and REQUIREMENTS planning documents
- Moved feature design from `1. Planning/` to `2. Planned/` in the feature design pipeline
- Document covers: data model (3 new tables), component design (5 services, 3 JPA entities, controller), 7 API endpoints, 16 failure modes, 15 architectural decisions, 5-phase implementation roadmap mapped to 30 requirements

**New cross-domain dependencies:** Yes — Entities → Identity (new): `EntityService` will publish `EntitySavedEvent` consumed by `IdentityMatchTriggerService`. Identity → Entities: `IdentityMatchConfirmationService` creates relationships via `EntityRelationshipService` and reads attributes via `entity_attributes` table.

**New components introduced:**

- Feature design document only — no code components introduced in this change. The document specifies components to be built across 5 implementation phases.

## 2026-04-28 — Avatar Resolution Endpoint

**Domains affected:** Storage, User, Workspace

**What changed:**

- Added public read endpoints `/api/v1/avatars/user/{userId}` and `/api/v1/avatars/workspace/{workspaceId}` that 302-redirect to a short-lived signed URL on the storage provider, replacing the previously unimplemented URLs synthesized by `AvatarUrlResolver`
- Introduced `AvatarService` to translate user/workspace entity `avatarUrl` (storage key) into a signed URL via the configured `StorageProvider`, with HMAC fallback through `SignedUrlService` when the provider does not support native signed URLs
- Responses set `Cache-Control: max-age=240, public` so browsers cache the redirect target slightly under the 5-minute signed URL expiry
- Hardened `SupabaseStorageProvider` startup: `@PostConstruct ensureBucketExists` probes the configured bucket and best-effort creates it as private; failures (RLS denial under anon key, network errors) are logged and swallowed so the application still boots

**New cross-domain dependencies:** Yes — Storage → User and Storage → Workspace: `AvatarService` reads `avatarUrl` from `UserRepository` and `WorkspaceRepository` to resolve avatar storage keys.

**New components introduced:**

- `AvatarController` — public REST controller exposing user/workspace avatar redirect endpoints
- `AvatarService` — Spring service resolving user/workspace avatar storage keys to signed download URLs
