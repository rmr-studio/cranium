# Roadmap: Declarative Manifest Catalog and Consumption Pipeline

## Overview

Three phases with hard sequential dependencies. The database schema and JPA layer must exist before any service can write to it. The loader pipeline must run before there is anything to query. The read surface and integration tests close the loop by providing the downstream-facing API and verifying the full startup cycle end-to-end. Each phase delivers a coherent, independently verifiable capability.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Database Foundation** - Catalog tables, JPA entities, repositories, JSON Schema files, and directory scaffolding
- [x] **Phase 2: Loader Pipeline** - Full startup load pipeline: scan, validate, resolve, normalize, upsert, reconcile, and unit tests
- [x] **Phase 3: Read Surface and Integration Tests** - ManifestCatalogService query API and end-to-end integration test verification (completed 2026-03-05)

## Phase Details

### Phase 1: Database Foundation
**Goal**: The catalog schema, JPA layer, and authoring infrastructure exist and are ready for the loader pipeline to build on
**Depends on**: Nothing (first phase)
**Requirements**: DB-01, DB-02, DB-03, DB-04, DB-05, VAL-01, SCAF-01, SCAF-02
**Success Criteria** (what must be TRUE):
  1. The application starts cleanly with the 6 new catalog tables present in the database and no migration errors
  2. All 6 catalog JPA entities compile, follow the IntegrationDefinitionEntity pattern (no AuditableEntity, manual timestamps), and have working Spring Data repositories
  3. The manifest_catalog table enforces UNIQUE(key, manifest_type) — inserting a model and a template with the same key succeeds, but inserting two models with the same key fails
  4. JSON Schema files exist for model, template, and integration manifest formats and are loadable from the classpath
  5. The models/, templates/, and integrations/ directories exist under src/main/resources/manifests/ with a README documenting manifest format, $ref syntax, extend semantics, and relationship shorthand vs full format
**Plans:** 1 plan
Plans:
- [x] PLAN.md — SQL schema, JPA entities, repositories, enum, integration_definitions rename, JSON Schemas, manifest scaffolding, README

### Phase 2: Loader Pipeline
**Goal**: On every application startup, manifest files are scanned, validated, resolved, normalized, and upserted into the catalog database with full idempotency and per-manifest transaction isolation
**Depends on**: Phase 1
**Requirements**: VAL-02, VAL-03, LOAD-01, LOAD-02, LOAD-03, LOAD-04, LOAD-05, LOAD-06, LOAD-07, PERS-01, PERS-02, PERS-03, PERS-04, TEST-01, TEST-02, TEST-03, TEST-04, TEST-05
**Success Criteria** (what must be TRUE):
  1. On application startup, all valid manifest fixtures in src/test/resources/manifests/ are loaded into catalog tables — verified by a post-startup row count assertion
  2. An invalid manifest (fails JSON Schema) logs a WARN with the manifest key and validation errors, is skipped, and the application starts successfully with all other manifests loaded
  3. Template manifests with $ref entries have those references fully resolved from the in-memory model index — no DB reads occur during resolution
  4. A relationship defined in shorthand format (single target + cardinality) is stored in the database in full targetRules[] format
  5. All unit tests for $ref resolution, extend merge, relationship normalization, and relationship validation pass
**Plans:** 3/3 plans complete
Plans:
- [x] 02-01-PLAN.md — Schema evolution (stale column), pipeline data classes, JSON Schema update, test fixtures
- [x] 02-02-PLAN.md — ManifestScannerService + ManifestResolverService with unit tests
- [x] 02-03-PLAN.md — ManifestUpsertService + ManifestLoaderService orchestrator with unit tests

### Phase 3: Read Surface and Integration Tests
**Goal**: Downstream services can query the catalog, and the full startup pipeline is verified end-to-end by integration tests covering load, idempotent reload, and manifest removal reconciliation
**Depends on**: Phase 2
**Requirements**: QUERY-01, QUERY-02, QUERY-03, QUERY-04, TEST-06, TEST-07, TEST-08
**Success Criteria** (what must be TRUE):
  1. ManifestCatalogService.getAvailableTemplates() and getAvailableModels() return only active (non-deleted) catalog entries
  2. ManifestCatalogService.getManifestByKey(key) returns a single manifest with all nested entity types and relationships hydrated
  3. Integration test: running the loader twice against the same fixture set produces identical catalog row counts (idempotent reload verified)
  4. Integration test: removing a manifest from the fixture set and re-running startup results in that catalog entry having deleted=true, not a missing row (soft-delete reconciliation verified)
**Plans:** 2/2 plans complete
Plans:
- [x] 03-01-PLAN.md — ManifestCatalogService query models, repository additions, toModel() mappings, and unit tests
- [x] 03-02-PLAN.md — ManifestScannerService configurable path refactor and end-to-end integration tests

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Database Foundation | 1/1 | Complete | 2026-03-04 |
| 2. Loader Pipeline | 3/3 | Complete | 2026-03-05 |
| 3. Read Surface and Integration Tests | 2/2 | Complete   | 2026-03-05 |
