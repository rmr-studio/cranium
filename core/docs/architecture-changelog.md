# Architecture Changelog

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
