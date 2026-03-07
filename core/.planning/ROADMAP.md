# Roadmap: Knowledge Layer

## Overview

This project grafts a semantic enrichment and vector embedding layer onto the existing Riven entity system. The work proceeds in four phases with hard dependencies: semantic metadata must exist before templates can configure it, templates must exist before the enrichment pipeline has realistic data to validate against, and the per-entity enrichment workflow must be complete and proven before the schema-change re-embedding batch system can use it as a building block.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Semantic Metadata Foundation** - Extend entity types, attributes, and relationships with semantic metadata fields and CRUD API
- [ ] **Phase 2: Template System** - Installable domain schema packages that bootstrap a workspace with semantically-enriched entity types in one action
- [ ] **Phase 3: Enrichment Pipeline** - Queue-triggered Temporal workflow that builds enriched text from entity data (with token budget truncation) and stores vector embeddings in pgvector, with workspace-fair round-robin dispatch and priority lanes
- [ ] **Phase 4: Schema Change Re-embedding** - Detection of schema mutations that affect embedding quality and batch re-embedding via shared enrichment queue (BATCH priority), with entity-type-level staleness tracking

## Phase Details

### Phase 1: Semantic Metadata Foundation
**Goal**: Entity types, attributes, and relationships carry user-editable semantic metadata that captures business meaning
**Depends on**: Nothing (first phase)
**Requirements**: SMETA-01, SMETA-02, SMETA-03, SMETA-04, SMETA-05, SMETA-06, INFRA-01, INFRA-04, INFRA-06
**Success Criteria** (what must be TRUE):
  1. A user can set a natural language semantic definition on an entity type via the API and retrieve it back without affecting existing entity CRUD operations
  2. A user can assign a semantic type classification (identifier, categorical, quantitative, temporal, freetext, relational_reference) and natural language description to an attribute, and the system validates known classification values
  3. A user can set a semantic context string on a relationship definition describing the nature of the connection
  4. All semantic metadata endpoints reject requests from users who do not have access to the target workspace
  5. Semantic metadata is stored in a separate table from entity_types so that existing entity type queries are unchanged
**Plans**: 3 plans

Plans:
- [x] 01-01-PLAN.md — Database schema, pgvector extension, enums, JPA entity, domain model, repository, Testcontainers pgvector image
- [x] 01-02-PLAN.md — EntityTypeSemanticMetadataService with CRUD, lifecycle hooks in existing services, unit tests
- [x] 01-03-PLAN.md — KnowledgeController endpoints, ?include=semantics on EntityTypeController, end-to-end verification

### Phase 2: Template System
**Goal**: Users can install a complete, semantically-enriched entity type schema into their workspace in one action using a named domain template
**Depends on**: Phase 1
**Requirements**: TMPL-01, TMPL-02, TMPL-03, TMPL-04, TMPL-05, TMPL-06
**Success Criteria** (what must be TRUE):
  1. A user can install the SaaS Startup, DTC E-commerce, or Service Business template and immediately have entity types, attributes, relationships, and semantic metadata created in their workspace
  2. Installing a template twice does not create duplicate entity types or corrupt existing data
  3. After installation, a user can modify the semantic metadata on any template-created entity type or attribute without breaking the template structure
  4. Each template includes entity types that cover the core domain (customers, transactions, and supporting objects) with semantic type classifications on every attribute
**Plans**: 2 plans

Plans:
- [ ] 02-01-PLAN.md — Shared model authoring (customer, invoice, support-ticket) and three template manifests (saas-startup, dtc-ecommerce, service-business) with full semantic metadata
- [ ] 02-02-PLAN.md — TemplateInstallationService with catalog-to-workspace translation, TemplateController with list/install endpoints, unit tests

### Phase 3: Enrichment Pipeline
**Goal**: Every entity create and update automatically produces a vector embedding stored in pgvector that captures the entity's business meaning using its semantic metadata, with fair multi-tenant dispatch and token-aware text construction
**Depends on**: Phase 1
**Requirements**: ENRICH-01, ENRICH-02, ENRICH-03, ENRICH-04, ENRICH-05, ENRICH-06, ENRICH-07, ENRICH-08, ENRICH-09, ENRICH-10, ENRICH-11, ENRICH-12, ENRICH-13, ENRICH-14, ENRICH-15, ENRICH-16, ENRICH-17, INFRA-02, INFRA-03, INFRA-05
**Success Criteria** (what must be TRUE):
  1. Creating or updating an entity causes an enrichment queue entry to appear within the same transaction, and the queue deduplicates multiple pending entries for the same entity
  2. A Temporal workflow processes queue entries end-to-end: fetches entity with context, constructs enriched text using semantic labels and descriptions, calls OpenAI text-embedding-3-small, and stores the resulting vector in entity_embeddings
  3. OpenAI API failures (429, 5xx) are retried with exponential backoff; persistent failures mark the entity as embedding_pending and log an activity record rather than crashing the workflow
  4. Changing a relationship (create or delete) triggers enrichment queue entries for both the source and target entities
  5. The entity_embeddings table uses an HNSW index with vector_cosine_ops and stores workspace_id, entity_type_id, embedded_at, embedding_model, schema_version, and truncated metadata per embedding
  6. When enriched text exceeds the token budget (~7,500 tokens), SemanticTextBuilderService truncates by section priority, preserving entity type definition and identifier before attributes and relationships, and sets a truncated flag on the embedding record
  7. The dispatcher claims queue items round-robin across workspaces (not FIFO) with a per-workspace concurrency cap (default 10), and NORMAL priority items dispatch before BATCH priority items within each workspace's share
**Plans**: TBD

Plans:
- [ ] 03-01: Database schema — entity_embeddings table with HNSW index, entity_enrichment_queue table, pgvector-java dependency
- [ ] 03-02: EmbeddingClientService (OpenAI WebClient wrapper) and SemanticTextBuilderService
- [ ] 03-03: EnrichmentQueueService, EnrichmentDispatcherService, and enrichment trigger hooks in EntityService and EntityRelationshipService
- [ ] 03-04: EnrichmentCoordinationActivity, EnrichmentWorkflowImpl, EntityEmbeddingService, and Temporal worker registration

### Phase 4: Schema Change Re-embedding
**Goal**: When semantic metadata changes on an entity type, all affected entities are automatically re-embedded in the background via the shared enrichment queue without blocking entity operations or returning errors to users
**Depends on**: Phase 3
**Requirements**: SCHMA-01, SCHMA-02, SCHMA-03, SCHMA-04, SCHMA-05, SCHMA-06, SCHMA-07, SCHMA-08, SCHMA-09
**Success Criteria** (what must be TRUE):
  1. Changing an entity type's semantic definition, an attribute's semantic description, or a relationship's semantic context triggers a background re-embedding job scoped to only the affected entity type
  2. Re-embedding progress is tracked in schema_migration_jobs with status (PENDING, IN_PROGRESS, COMPLETE, FAILED), entity counts, and timestamps
  3. Entity CRUD operations and queries continue working normally during re-embedding; queries return results from existing (stale) embeddings rather than failing
  4. Schema changes to an entity type with fewer than 1000 entities complete re-embedding within 15 minutes
  5. Re-embedding enqueues affected entities into `entity_enrichment_queue` with `BATCH` priority instead of spawning direct Temporal child workflows. The shared round-robin dispatcher handles all embedding dispatch
  6. Staleness is determined at entity-type granularity by checking `schema_migration_jobs` status for the queried entity type — no per-entity staleness checks or workspace-level banners
**Plans**: TBD

Plans:
- [ ] 04-01: Database schema — schema_migration_jobs table and SchemaMigrationJobService
- [ ] 04-02: Schema change detection hook in EntityTypeService and ReBatchingWorkflowImpl with child workflow fan-out

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Semantic Metadata Foundation | 3/3 | Complete | 2026-02-19 |
| 2. Template System | 0/2 | Not started | - |
| 3. Enrichment Pipeline | 0/4 | Not started | - |
| 4. Schema Change Re-embedding | 0/2 | Not started | - |
