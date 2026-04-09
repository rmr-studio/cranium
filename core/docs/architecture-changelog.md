# Architecture Changelog

## [2026-04-09] — Note Embedding Pipeline + Entity-Spanning Notes

**Domains affected:** Note, Integration, Catalog
**What changed:**

- Notes now support multi-entity attachment via `note_entity_attachments` join table — one note can be attached to multiple entities
- `NoteEntity.entityId` is now nullable (denormalized primary kept for backward compat); authoritative source of truth is the join table
- Added source tracking to notes: `source_type` (USER/INTEGRATION), `source_integration_id`, `source_external_id`, `readonly`, `pending_associations`
- Note model breaking change: `Note.entityId: UUID` → `Note.entityIds: List<UUID>`, `WorkspaceNote` now uses `entityContexts: List<NoteEntityContext>` instead of single entity context fields
- New `NoteEmbeddingService` routes integration note records directly to `NoteEntity` creation, bypassing the entity creation pipeline entirely
- Sync pipeline routing: `fetchAndProcessRecords()` checks for `noteEmbedding` manifest config before entity model resolution; matching models are routed to note embedding
- HubSpot manifest: removed `hubspot-note` entity type, field mappings, and relationships; replaced with `noteEmbedding` config block
- Added `sync_key` column to `integration_sync_state` for disambiguating note embedding sync state from entity sync state
- Post-sync reconciliation: unattached notes (whose target entities hadn't synced yet) are automatically resolved after subsequent model syncs
- `note_count` trigger moved from `notes` table to `note_entity_attachments` table — correctly counts notes per entity when notes span multiple entities
- Added `HtmlToBlockConverter` (Jsoup-based) for converting integration HTML content to BlockNote blocks
- Readonly enforcement: PUT/DELETE on readonly notes throws `AccessDeniedException` (403)
- System user seed (`00000000-...`) for `createdBy`/`updatedBy` on integration-managed records

**New cross-domain dependencies:** yes — Integration → Note via NoteEmbeddingService (sync pipeline routes note models to note domain instead of entity domain)
**New components introduced:**
- `NoteEmbeddingService` — processes integration note records into NoteEntity with BlockNote content and entity attachments
- `HtmlToBlockConverter` / `PlaintextToBlockConverter` — content format converters for note embedding
- `NoteEntityAttachment` / `NoteEntityAttachmentRepository` — join table entity and repository for multi-entity note attachments
- `NoteEmbeddingConfig` / `NoteContentFormat` — manifest config models for note embedding
- `NoteSourceType` enum — USER or INTEGRATION source type for notes

## [2026-03-29] — Entity Projection Pipeline Implementation

**Domains affected:** Entity, Integration, Ingestion (new), Identity, Lifecycle
**What changed:**

- Implemented the projection pipeline: integration entities are now projected into core lifecycle entities via domain-based routing rules
- Created `service.ingestion` package with `EntityProjectionService` and `IdentityResolutionService` — batch identity resolution using 2-query strategy (sourceExternalId match → IDENTIFIER attribute match)
- Added `entity_type_projection_rules` table and `ProjectionRuleEntity` — maps source (integration) entity types to target (core lifecycle) entity types
- Projection rules are installed automatically during integration template materialization in `TemplateMaterializationService`, using `CoreModelRegistry.findModelsAccepting()` to match (lifecycleDomain, semanticGroup) pairs
- Added `projectionAccepts` rules to all 15 core model definitions — each declares which (domain, group) pairs route integration data into it
- Wired `executeProjections` activity stub in `IntegrationSyncActivitiesImpl` to call `EntityProjectionService.processProjections()`
- Added entity visibility filter: `includeInternal` query parameter on entity type listing (default: false) hides integration-sourced entity types
- Added `readonly` parameter to `assembleColumns()` — readonly entity types skip relationship columns
- Added `lastPipelineStep` and `projectionResult` (JSONB) columns to `IntegrationSyncStateEntity` for pipeline observability

**New cross-domain dependencies:** yes — `service.ingestion.EntityProjectionService` depends on `service.identity.IdentityClusterService` and `service.identity.EntityTypeClassificationService` for cluster assignment and identifier resolution
**New components introduced:**
- `EntityProjectionService` — core projection pipeline: rule loading, identity resolution, entity create/update, relationship linking
- `IdentityResolutionService` — batch identity resolution with 2-query strategy
- `ProjectionRuleEntity` / `ProjectionRuleRepository` — projection rule storage
- `ProjectionResult` / `ResolutionResult` — pipeline result models in `models.ingestion`
- `CoreModelRegistry.findModelsAccepting()` — lookup method for projection routing

## [2026-03-27] — Entity Ingestion Pipeline Architecture (Engineering Review)

**Domains affected:** Entity, Integration, Identity Resolution, Catalog
**What changed:**

- Defined the 4-step Entity Ingestion Pipeline: Classify → Route → Map → Resolve. This is the missing bridge between integration entity sync and core entity hub population.
- Confirmed Hub Model architecture: core entity types (Customer, Support Ticket) are the user-facing hub where all interaction happens. Integration entity types + rows are hidden readonly infrastructure for sync fidelity, identity resolution, and audit.
- Established field ownership rules: "Source wins" — mapped fields on projected entities are owned by the integration source and overwritten on next sync. Unmapped fields are user-owned and preserved.
- Established multi-source conflict resolution: "Most recent sync wins" — timestamp-based, no configuration needed. When two integrations project to the same core entity, the most recently synced values for mapped fields win.
- Added field-level audit trail requirement: when sync overwrites user-edited values, log old → new via `activityService.logActivity()`.
- Changed `ProjectionAcceptRule` from nullable single value to `List<ProjectionAcceptRule>` on `CoreModelDefinition` to support multiple projection sources per core type.
- Added `SourceType.PROJECTED` to discriminate auto-created core entities from user-created ones.
- Defined new service layer: `FieldMappingService`, `IdentityResolutionService`, `EntityProjectionService` in `service.ingestion` package. `IntegrationSyncWorkflow` + `IntegrationSyncActivities` in `workflow.integration` package.
- Identified backfill projection need: when a new core model is installed that matches existing unmatched integration entities, retroactive projection required (deferred as P2 TODO).

**New cross-domain dependencies:** yes
- Integration → Entity: ingestion pipeline creates entities in core entity types from integration sync data
- Integration → Identity Resolution: ingestion pipeline's Step 4 (Resolve) uses identity resolution for entity matching
- Catalog → Integration: `CatalogFieldMappingEntity` consumed by `FieldMappingService` during ingestion

**New components introduced:**
- `FieldMappingService` — transforms integration source fields → core entity type schema using catalog field mappings
- `IdentityResolutionService` — matches incoming integration data to existing entities (sourceExternalId + identifier key)
- `EntityProjectionService` — creates/updates core entities from integration data, manages identity clusters via relationships
- `IntegrationSyncWorkflow` — Temporal workflow orchestrating the 4-step ingestion pipeline with activity-level retry and cursor pagination
- `IntegrationSyncActivities` — Temporal activities for each pipeline step

## [2026-03-17] — Nango Webhook Endpoint with HMAC Security

**Domains affected:** Integration
**What changed:**

- Created `NangoWebhookHmacFilter`: OncePerRequestFilter that reads raw request body, computes HMAC-SHA256 using `nangoProperties.secretKey`, and performs constant-time comparison via `MessageDigest.isEqual`. Requests without valid `X-Nango-Hmac-Sha256` header receive 401 without entering the servlet chain. Uses `CachedBodyHttpServletRequest` inner class so downstream handlers can re-read the body.
- Created `NangoWebhookFilterConfiguration`: registers the HMAC filter as a `FilterRegistrationBean` scoped to `/api/v1/webhooks/nango` at order 1.
- Updated `SecurityConfig`: added `permitAll()` for `/api/v1/webhooks/nango` — JWT auth is bypassed because the webhook path uses HMAC verification instead.
- Created `NangoWebhookController`: thin POST `/api/v1/webhooks/nango` endpoint with no `@PreAuthorize`; always returns 200 to Nango. Routes to `NangoWebhookService`.
- Created `NangoWebhookService`: routes `auth` and `sync` events. Auth handler: parses userId/workspaceId/integrationDefinitionId from Nango tags, creates or reconnects `IntegrationConnectionEntity` (CONNECTED), finds or creates/restores `WorkspaceIntegrationInstallationEntity` (ACTIVE), triggers `TemplateMaterializationService.materializeIntegrationTemplates()`; materialization failure sets installation to FAILED without rolling back the connection. Sync event is a Phase 3 stub that logs and returns.

**New cross-domain dependencies:** no — NangoWebhookService calls TemplateMaterializationService which is already in the Integration domain.
**New components introduced:**
- `NangoWebhookHmacFilter` — HMAC-SHA256 request signature verification filter
- `NangoWebhookFilterConfiguration` — filter registration configuration
- `NangoWebhookController` — webhook entry point
- `NangoWebhookService` — webhook event routing and auth event handler

## [2026-03-16] — Integration Data Sync Pipeline Documentation

**Domains affected:** Integration, Entity
**What changed:**

- Created full feature design for Integration Data Sync Pipeline covering webhook ingestion, Temporal sync workflow, batch dedup, two-pass processing, and connection health aggregation
- Created ADR-008: Temporal for Integration Sync Orchestration — chooses durable workflow over Spring @Async or message queues
- Created ADR-009: Unique Index Deduplication over Mapping Table — uses existing entity columns with partial unique index instead of separate mapping table
- Created ADR-010: Webhook-Driven Connection Creation — removes PENDING_AUTHORIZATION/AUTHORIZING states, connections created from auth webhook
- Created Integration Data Sync Pipeline flow document covering auth webhook, sync webhook dispatch, and 3-pass sync workflow execution paths
- Updated Entity Integration Sync sub-domain plan: revised vision (source_external_id dedup replaces tiered IR), updated component table (10 components), simplified data flow, corrected design constraints (raw SQL not Flyway), added 6 new decision entries

**New cross-domain dependencies:** no — all changes within existing integration and entity domain boundaries
**New components introduced:**
- Documentation only — no new code components. Documents the planned architecture for: NangoWebhookController, NangoWebhookService, IntegrationSyncWorkflow, IntegrationSyncActivities, IntegrationConnectionHealthService, IntegrationSyncTemporalConfiguration

## [2026-03-16] — Integration Sync Persistence Foundation

**Domains affected:** Integration, Entity
**What changed:**

- Added `integration_sync_state` table for tracking per-connection per-entity-type sync progress
- Added `status` column (VARCHAR 50, default ACTIVE) to `workspace_integration_installations`
- Added unique partial index `idx_entities_integration_dedup` on entities for integration dedup
- Created `SyncStatus` enum (PENDING, SUCCESS, FAILED) with `@JsonProperty` annotations
- Created `InstallationStatus` enum (PENDING_CONNECTION, ACTIVE, FAILED) with `canTransitionTo()` state machine
- Created `IntegrationSyncStateEntity` — extends `AuditableEntity`, not `SoftDeletable` (system-managed)
- Created `IntegrationSyncState` domain model with `toModel()` mapping
- Added `status: InstallationStatus` field (default ACTIVE) to `WorkspaceIntegrationInstallationEntity`
- Created `IntegrationSyncStateRepository` with derived queries for connection and connection+entity-type lookups
- Added `findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn` batch dedup JPQL query to `EntityRepository`

**New cross-domain dependencies:** no — changes extend existing integration and entity domains
**New components introduced:**
- `IntegrationSyncStateEntity` — JPA entity for sync state tracking (system-managed, no soft-delete)
- `IntegrationSyncStateRepository` — data access for sync state
- `IntegrationSyncState` — domain model mirroring sync state entity
- `SyncStatus` enum — sync run outcome (PENDING, SUCCESS, FAILED)
- `InstallationStatus` enum — installation lifecycle state machine

## [2026-03-14] — Notification Domain

**Domains affected:** notification (new), websocket, activity
**What changed:**

- Added workspace-scoped notification domain with typed content (Information, ReviewRequest, System) using sealed class hierarchy serialized as JSONB via Jackson polymorphic typing
- Created `NotificationService` for CRUD, cursor-paginated inbox, per-user read tracking, resolution lifecycle, and WebSocket event publishing
- Created `NotificationDeliveryService` as a facade for domain event listeners to create notifications and resolve by reference
- Added `NotificationController` with 6 REST endpoints under `/api/v1/notifications/workspace/{workspaceId}`
- Per-user read state tracked in `notification_reads` join table with bulk mark-all-read via native SQL
- Cursor-based pagination on `createdAt` for inbox queries
- `NotificationEvent` added to `WorkspaceEvent` sealed interface for real-time delivery via existing STOMP infrastructure
- Added `NOTIFICATION` to `Activity` and `ApplicationEntityType` enums

**New cross-domain dependencies:** yes — notification depends on activity (audit logging), websocket (event publishing via WorkspaceEvent), auth (workspace security)
**New components introduced:**
- `NotificationService` — CRUD, inbox, read-state, resolution, soft-delete
- `NotificationDeliveryService` — domain event translation facade
- `NotificationController` — REST endpoints for inbox, read-state, create, delete
- `NotificationEntity` / `NotificationReadEntity` — JPA entities
- `NotificationRepository` / `NotificationReadRepository` — data access
- `NotificationContent` sealed class — polymorphic typed content (Information, ReviewRequest, System)
- `NotificationEvent` — WorkspaceEvent subclass for STOMP delivery
- `NotificationType`, `NotificationReferenceType`, `ReviewPriority`, `SystemSeverity` — domain enums

## [2026-03-14] — WebSocket Real-Time Notifications

**Domains affected:** websocket (new), entity, block, workspace
**What changed:**

- Added STOMP over WebSocket infrastructure with in-memory SimpleBroker
- Created `WorkspaceEvent` sealed interface for type-safe domain event publishing
- Added `WebSocketEventListener` that bridges Spring ApplicationEvents to STOMP topics
- Added `WebSocketSecurityInterceptor` for JWT auth on CONNECT and workspace-scoped subscription authorization
- Integrated event publishing into EntityService, BlockEnvironmentService, and WorkspaceService

**New cross-domain dependencies:** yes — entity, block, and workspace services now depend on `models.websocket.WorkspaceEvent` (event model only, not WebSocket infrastructure)
**New components introduced:**
- `WebSocketConfig` — STOMP endpoint and broker configuration
- `WebSocketSecurityInterceptor` — JWT auth + workspace subscription authorization
- `WebSocketEventListener` — event-to-STOMP bridge
- `WebSocketConfigurationProperties` — externalized WebSocket configuration
- `WorkspaceEvent` sealed interface + domain subclasses — type-safe event model
- `WebSocketMessage` — outbound message envelope
- `WebSocketChannel` enum — topic segment mapping
## [2026-03-14] — Integration Enablement Feature

**Domains affected:** Integration, Entity, Catalog
**What changed:**

- New `IntegrationEnablementService` orchestrates enable/disable lifecycle for workspace integrations.
- New `IntegrationController` provides REST endpoints for integration management at `/api/v1/integrations/`.
- New `WorkspaceIntegrationInstallationEntity` tracks which integrations are enabled per workspace with SoftDeletable support.
- `TemplateMaterializationService` augmented with semantic metadata initialization, fallback relationship creation, and ID sequence initialization — now at parity with `TemplateInstallationService`.
- `IntegrationConnectionService` refactored: new `enableConnection()` for single-action Nango flow, KLogger injection fixed.
- `EntityTypeService` gained `softDeleteByIntegration()` and `restoreByIntegration()` for integration lifecycle management.
- Read-only enforcement added across all entity type mutation paths (`EntityTypeService`, `EntityTypeAttributeService`, `EntityTypeRelationshipService`).
- New enums: `SyncScope`, `Activity.INTEGRATION_ENABLEMENT`, `ApplicationEntityType.INTEGRATION_INSTALLATION`.

**New cross-domain dependencies:** yes — Integration domain now depends on Entity domain's `EntityTypeService` for soft-delete/restore operations. Previously only Catalog → Entity dependency existed via `TemplateMaterializationService`.
**New components introduced:**
- `IntegrationEnablementService` — orchestrator for integration enable/disable lifecycle
- `IntegrationController` — REST API for integration management
- `WorkspaceIntegrationInstallationEntity` — installation tracking entity
- `WorkspaceIntegrationInstallationRepository` — installation data access

## [2026-03-12] — Unified Onboarding Endpoint

**Domains affected:** Onboarding (new), Workspace, User, Catalog, Storage
**What changed:**

- Introduced new `onboarding` domain with `OnboardingService` and `OnboardingController`
- Added `POST /api/v1/onboarding/complete` endpoint that atomically creates a workspace + updates user profile, then best-effort installs templates and sends invitations
- Added `onboarding_completed_at` column to `users` table and `UserEntity` for idempotency gating
- Added `ONBOARDING` to `Activity` enum for audit trail
- Extracted `Internal` method variants (no `@PreAuthorize`) on `TemplateInstallationService`, `WorkspaceInviteService`, and `StorageService` for cross-domain calls during onboarding when workspace role is not yet in JWT
- Refactored `WorkspaceService.saveWorkspace` — flattened `userId.let` nesting, extracted named private methods (`createOrUpdateWorkspaceEntity`, `createOwnerMember`, `uploadWorkspaceAvatar`, `publishWorkspaceAnalytics`, `setDefaultWorkspaceIfNeeded`)
- Fixed workspace avatar upload bug: `saveWorkspace` now calls `uploadFileInternal` instead of `uploadFile` (which requires `@PreAuthorize` that fails on newly-created workspaces)
- Restored and fixed `WorkspaceInviteServiceTest` and `WorkspaceFactory` (previously commented out)

**New cross-domain dependencies:** yes — Onboarding → Workspace, User, Catalog (TemplateInstallation), Storage, Activity
**New components introduced:**
- `OnboardingService` — orchestrates the full onboarding flow with TransactionTemplate for atomicity
- `OnboardingController` — single endpoint for complete onboarding
- `CompleteOnboardingRequest` / `CompleteOnboardingResponse` — request/response models
- `TemplateInstallResult` / `InviteResult` — best-effort result models

## [2026-03-10] — Derive Entity Type Columns at Read-Time

**Domains affected:** Entity, Integration/Materialization, Catalog
**What changed:**

- Replaced stored `columns: List<EntityTypeAttributeColumn>` JSONB on `EntityTypeEntity` with `columnConfiguration: ColumnConfiguration?` — stores only ordering and display overrides
- Columns are now derived at read-time via `EntityTypeService.assembleColumns()` from schema attributes + relationship definitions + stored configuration
- Removed ~100 lines of column sync code: `updateColumnOrdering()`, `reorderEntityTypeColumns()`, `addInverseColumnsToTargetTypes()`, `removeInverseColumnsFromTargetTypes()`, `refreshSourceEntityTypeAfterTargetRemoval()`
- Eliminated cross-entity writes on relationship mutations (no more N+1 saves to propagate inverse columns)
- Updated `UpdateEntityTypeConfigurationRequest` to accept `ColumnConfiguration` instead of `List<EntityTypeAttributeColumn>`
- Updated `TemplateMaterializationService` and `TemplateInstallationService` to produce `ColumnConfiguration` instead of column lists
- SQL schema: `entity_types.columns` → `entity_types.column_configuration`

**New cross-domain dependencies:** no
**New components introduced:** `ColumnConfiguration`, `ColumnOverride` — configuration models for column ordering and display overrides

## [2026-03-09] — ID SchemaType and Template Default Value Support

**Domains affected:** Entity, Catalog
**What changed:**

- Added `ID` to `SchemaType` enum — auto-generates prefixed sequential identifiers (e.g. `PKR-1`)
- Added `prefix` field to `SchemaOptions` for configuring ID attribute prefixes
- Created `entity_type_sequences` table and `EntityTypeSequenceService` for atomic counter management
- Sequence increment uses `REQUIRES_NEW` transaction to minimise lock duration (gaps accepted on rollback)
- `TemplateInstallationService.parseSchemaOptions()` now parses `default` and `prefix` from manifests
- Default values validated at definition time via `SchemaService.validateDefault()` — rejects invalid defaults before storage
- `EntityService.saveEntity()` injects defaults and generates IDs on entity creation
- ID attributes are read-only after creation — updates cannot modify them
- Sequence rows initialized during template installation and manual attribute creation

**New cross-domain dependencies:** No — Entity and Catalog already have existing dependency
**New components introduced:**
- `EntityTypeSequenceService` — manages atomic counter increment for ID generation
- `EntityTypeSequenceEntity` / `EntityTypeSequenceRepository` — persistence for sequence counters
- `SchemaService.validateDefault()` — validates default values against attribute schema constraints

## [2026-03-06] — Storage Domain Vault Documentation (Phase 1: Storage Foundation)

**Domains affected:** Storage (new domain)
**What changed:**

- Created full feature design for Provider-Agnostic File Storage in `feature-design/2. Planned/`
- Created ADR-005: Strategy Pattern with Conditional Bean Selection for Storage Providers
- Created ADR-006: HMAC-Signed Download Tokens for File Access
- Created ADR-007: Magic Byte Content Validation via Apache Tika
- Created Flow - File Upload documenting the multipart upload pipeline
- Created Flow - Signed URL Download documenting the unauthenticated download path
- Created File Storage sub-domain plan establishing the Storage domain's vault presence

**New cross-domain dependencies:** Yes — Storage depends on Workspaces & Users (workspace scoping via @PreAuthorize, JWT auth via AuthTokenService) and Activity (audit logging via ActivityService)
**New components introduced:**
- StorageProvider interface — provider-agnostic abstraction for file operations
- LocalStorageProvider — local filesystem implementation, activated via @ConditionalOnProperty
- ContentValidationService — Apache Tika magic byte detection, domain-based validation, SVG sanitization
- SignedUrlService — HMAC-SHA256 token generation/validation for unauthenticated file downloads
- StorageService — orchestrator coordinating validation, storage, metadata, and activity logging
- StorageController — 6 REST endpoints under /api/v1/storage/
- FileMetadataEntity — JPA entity for file metadata persistence
- StorageConfigurationProperties — @ConfigurationProperties for provider and signed URL config

## 2026-03-09 — Simplify Entity Relationship System

**Domains affected:** Entity, Catalog
**What changed:**
- Removed semantic group-based targeting from relationship target rules — all rules now require explicit `targetEntityTypeId`
- Removed `allowPolymorphic` column from relationship definitions — polymorphic behavior is now derived from `systemType` (only system-managed CONNECTED_ENTITIES definitions are polymorphic)
- Removed `relationship_definition_exclusions` table and all exclusion infrastructure (entity, repository, service methods, tests)
- Simplified inverse relationship link queries by removing CTE, semantic matching, and exclusion NOT EXISTS subqueries
- Made `target_entity_type_id` NOT NULL in `relationship_target_rules`
- Updated catalog pipeline (entities, models, resolver, upsert, installation, materialization) to match simplified relationship model
- Removed `SemanticGroup` from relationship targeting context (enum retained for entity type classification)

**New cross-domain dependencies:** no
**New components introduced:** none — this is a pure simplification/removal

## [2026-03-18] — Phase 4 Feature Design: Confirmation and Clusters

**Domains affected:** Identity Resolution, Entities
**What changed:**

- Generated feature design for Identity Cluster Confirmation and Union-Find Management
- Documents confirmation state machine (PENDING → CONFIRMED/REJECTED with ConflictException guard), five-case Union-Find cluster assignment, merge algorithm (smaller → larger), and CONNECTED_ENTITIES relationship creation on confirm
- Documents rejection move from IdentityMatchSuggestionService to new IdentityConfirmationService
- Documents cluster-aware re-suggestion skip in persistSuggestions

**New cross-domain dependencies:** yes — Identity Resolution → Entities (EntityRelationshipService for CONNECTED_ENTITIES creation on confirm), Identity Resolution → Notifications (confirmation broadcast to all workspace members)
**New components introduced:**
- `IdentityConfirmationService` — confirm/reject state machine with cluster orchestration, relationship creation, notification publishing, and activity logging
- `IdentityClusterService` — cluster CRUD, Union-Find merge, auto-naming from NAME-signal attributes

## [2026-03-17] — Identity Resolution Domain

**Domains affected:** identity (new), workflow (queue management, execution engine)
**What changed:**

- Introduced Identity Resolution domain — Temporal-orchestrated pipeline for detecting duplicate entities using pg_trgm trigram similarity, weighted scoring, and human-reviewable match suggestions
- New matching pipeline: IdentityMatchCandidateService (pg_trgm blocking), IdentityMatchScoringService (weighted average), IdentityMatchSuggestionService (idempotent persistence with re-suggestion logic)
- New Temporal workflow/activities on dedicated `identity.match` task queue, isolated from default workflow queue
- Event-driven trigger: EntityService publishes IdentityMatchTriggerEvent → IdentityMatchTriggerListener → queue dispatch → Temporal pipeline
- Queue management services: IdentityMatchQueueService (dedup enqueue), IdentityMatchDispatcherService (ShedLock polling), IdentityMatchQueueProcessorService (REQUIRES_NEW dispatch)
- EntityTypeClassificationService caches IDENTIFIER-classified attributes per entity type
- Scaffolded cluster entities (IdentityClusterEntity, IdentityClusterMemberEntity) for future phase
- New SQL schema: match_suggestions, identity_clusters, identity_cluster_members with pg_trgm extension, canonical UUID ordering constraints, partial unique indexes
- TemporalWorkerConfiguration now registers identity match worker on IDENTITY_MATCH_QUEUE

**New cross-domain dependencies:** yes — Identity Resolution → Entities (native SQL on entity_attributes + entity_type_semantic_metadata), Identity Resolution → Workflows (ExecutionQueueEntity for IDENTITY_MATCH jobs), Identity Resolution → Activity (audit logging)
**New components introduced:**
- `IdentityMatchCandidateService` — two-phase pg_trgm candidate finding
- `IdentityMatchScoringService` — weighted average scoring with configurable signal weights
- `IdentityMatchSuggestionService` — idempotent suggestion persistence with re-suggestion and rejection
- `EntityTypeClassificationService` — cached IDENTIFIER attribute lookup
- `IdentityMatchQueueService` — IDENTITY_MATCH job enqueueing with deduplication
- `IdentityMatchDispatcherService` — scheduled queue polling with ShedLock
- `IdentityMatchQueueProcessorService` — per-item Temporal dispatch with REQUIRES_NEW transactions
- `IdentityMatchWorkflow/Impl` — Temporal workflow orchestrating 3-activity pipeline
- `IdentityMatchActivities/Impl` — Temporal activities delegating to domain services
- `IdentityMatchTriggerListener` — @TransactionalEventListener bridging entity saves to queue
- `MatchSuggestionEntity` — candidate pair entity with JSONB signals and canonical UUID ordering
- `IdentityClusterEntity` / `IdentityClusterMemberEntity` — scaffolded cluster entities
- `MatchSignalType` — signal type enum with default weights
- `MatchSuggestionStatus` — suggestion lifecycle enum

## [2026-03-18] — IdentityConfirmationService with Cluster Management (Phase 04 Plan 02)

**Domains affected:** identity
**What changed:**

- Added `IdentityConfirmationService` as the single owner of the suggestion confirm/reject state machine
- `confirmSuggestion` creates a `CONNECTED_ENTITIES` relationship via `EntityRelationshipService` with `SourceType.IDENTITY_MATCH`, runs 5-case cluster resolution, logs activity, and publishes a `REVIEW_REQUEST` notification to all workspace members
- `rejectSuggestion` transitions `PENDING -> REJECTED` with rejectionSignals snapshot, soft-delete, and activity logging
- 5-case cluster resolution: (1) create new cluster, (2) expand source cluster, (3) expand target cluster, (4) merge clusters (smaller dissolved into larger, tie favors source entity's cluster), (5) same-cluster no-op
- Cluster merge hard-deletes dissolving members, re-inserts them into surviving cluster preserving `joinedAt`/`joinedBy`, and soft-deletes the dissolving cluster
- Removed `rejectSuggestion`, `validateRejectable`, `applyRejection`, and `logRejectionActivity` from `IdentityMatchSuggestionService` — rejection is now fully owned by `IdentityConfirmationService`
- Integration test updated to reject via raw SQL instead of service call (avoids JWT context requirement in minimal integration test config)

**New cross-domain dependencies:** yes — identity domain → entity domain via `EntityRelationshipService.addRelationship` with `SourceType.IDENTITY_MATCH`; identity domain → notification domain via `NotificationService.createInternalNotification`
**New components introduced:**
- `IdentityConfirmationService` — confirm/reject state machine with 5-case cluster management

## [2026-03-16] — Generic Execution Queue with Job Type Discriminator (INFRA-01/02/03)

**Domains affected:** workflow (execution queue), integration (SourceType enum)
**What changed:**

- Renamed `workflow_execution_queue` table to `execution_queue` in SQL schema and all JPA/repository layers
- Added `job_type VARCHAR(30) NOT NULL DEFAULT 'WORKFLOW_EXECUTION'` discriminator column to `execution_queue`
- Added `entity_id UUID` nullable FK column for IDENTITY_MATCH jobs (references entities table)
- Changed `workflow_definition_id` from NOT NULL to nullable on `execution_queue`
- Created `ExecutionJobType` enum with `WORKFLOW_EXECUTION` and `IDENTITY_MATCH` values
- Both native queries (`claimPendingExecutions`, `findStaleClaimedItems`) now filter `AND job_type = 'WORKFLOW_EXECUTION'` ensuring workflow dispatcher never claims identity match jobs
- Added dedup partial unique index `uq_execution_queue_pending_identity_match` on `(workspace_id, entity_id, job_type) WHERE status = 'PENDING' AND entity_id IS NOT NULL`
- Added `IDENTITY_MATCH` to `SourceType` enum (for entity relationship source tracking in Phase 4)

**New cross-domain dependencies:** no — queue change is internal to workflow domain; SourceType is an existing integration enum
**New components introduced:**
- `ExecutionJobType` enum — job type discriminator for the shared execution queue
