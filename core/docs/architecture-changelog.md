# Architecture Changelog

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
