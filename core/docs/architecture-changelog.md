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
