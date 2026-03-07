# Requirements: Knowledge Layer

**Defined:** 2026-02-17
**Core Value:** Entity data is semantically enriched and embedded so that the system understands what business concepts its data represents

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Semantic Metadata

- [ ] **SMETA-01**: Entity types carry a semantic definition field (natural language description of what the entity represents in the business model), separate from the existing user-facing description
- [ ] **SMETA-02**: Attributes carry a semantic type classification (extensible: identifier, categorical, quantitative, temporal, freetext, relational_reference) stored as a string with validation for known values
- [ ] **SMETA-03**: Attributes carry a natural language semantic description explaining what the attribute's values mean
- [ ] **SMETA-04**: Relationship definitions carry a semantic context field describing the nature of the connection (e.g. "customer purchased product")
- [ ] **SMETA-05**: Semantic metadata is user-editable via API endpoints on entity types, attributes, and relationships
- [ ] **SMETA-06**: All semantic metadata endpoints enforce workspace-scoped security via @PreAuthorize

### Templates

- [ ] **TMPL-01**: Template system can install a complete entity type schema (entity types, attributes, relationships, and semantic metadata) into a workspace in one action
- [x] **TMPL-02**: SaaS Startup template with entity types for customers, subscriptions, MRR, churn, support interactions, and feature usage — all with semantic metadata
- [x] **TMPL-03**: DTC E-commerce template with entity types for customers, orders, products, acquisition channels, support, and returns — all with semantic metadata
- [x] **TMPL-04**: Service Business template with entity types for clients, projects, invoices, communications, and deliverables — all with semantic metadata
- [ ] **TMPL-05**: Template install is idempotent — re-applying does not duplicate entity types or corrupt existing data
- [x] **TMPL-06**: Template semantic metadata serves as the editable baseline — users can modify after install without breaking the template structure

### Enrichment Pipeline

- [ ] **ENRICH-01**: Enriched text construction builds a text string from entity type semantic definition, attribute values with semantic labels, and relationship context with related entity summaries
- [ ] **ENRICH-02**: Enriched text uses semantic type classifications to format attribute values appropriately (dates formatted, enums named, references described by related entity identifiers)
- [ ] **ENRICH-03**: OpenAI text-embedding-3-small generates 1536-dimensional embedding vectors from enriched text
- [ ] **ENRICH-04**: Embeddings stored in pgvector entity_embeddings table (separate from entities table) with HNSW index using vector_cosine_ops
- [ ] **ENRICH-05**: Embedding metadata stored: workspace_id, entity_id, entity_type_id, embedded_at, embedding_model, schema_version
- [ ] **ENRICH-06**: Entity create triggers enrichment queue entry within the same transaction
- [ ] **ENRICH-07**: Entity update (attributes or relationships changed) triggers enrichment queue entry
- [ ] **ENRICH-08**: Relationship create/delete triggers enrichment queue entries for both source and target entities
- [ ] **ENRICH-09**: Queue deduplication prevents multiple pending entries for the same entity_id
- [ ] **ENRICH-10**: Temporal workflow processes queue entries: fetch entity with context → construct enriched text → generate embedding → store in pgvector
- [ ] **ENRICH-11**: OpenAI API calls retry with exponential backoff on transient failures (429, 5xx)
- [ ] **ENRICH-12**: Enrichment failures log the error and mark the entity as embedding_pending for retry
- [ ] **ENRICH-13**: Activity logging for enrichment operations (embed created, embed failed, re-embed triggered)
- [ ] **ENRICH-14**: When enriched text exceeds the token budget (~7,500 tokens), `SemanticTextBuilderService` truncates by section priority (entity type definition → identifier → high-signal attributes → relationships → remaining attributes). A `truncated` flag is stored on the embedding record
- [ ] **ENRICH-15**: Dispatcher claims items round-robin across workspaces, not FIFO — ensures no single workspace monopolises a dispatch cycle
- [ ] **ENRICH-16**: Per-workspace concurrency cap on in-flight embedding items (configurable, default 10). Workspaces at cap are skipped in the current dispatch cycle
- [ ] **ENRICH-17**: Entity mutation queue items have `NORMAL` priority; schema re-embedding items have `BATCH` priority. `NORMAL` dispatches first within each workspace's round-robin share

### Schema Change Handling

- [ ] **SCHMA-01**: Schema change detection identifies which entities are affected when entity type semantic description, attribute semantic description, attribute deletion, or relationship semantic description changes
- [ ] **SCHMA-02**: Re-embedding priority follows the change detection matrix: entity type description changes = high, attribute changes = medium, relationship deletion = low
- [ ] **SCHMA-03**: Schema mutations fire events to a Temporal workflow that queries affected entity IDs and batches them into child embedding workflows (~100 per batch)
- [ ] **SCHMA-04**: schema_migration_jobs table tracks re-embedding progress: status (PENDING, IN_PROGRESS, COMPLETE, FAILED), total_entities, processed_entities, error_count, started_at, completed_at
- [ ] **SCHMA-05**: Re-embedding runs in background without blocking entity CRUD operations
- [ ] **SCHMA-06**: Queries during re-embedding return results from existing (stale) embeddings rather than failing
- [ ] **SCHMA-07**: Schema changes to an entity type with <1000 entities complete re-embedding within 15 minutes
- [ ] **SCHMA-08**: Schema change re-embedding enqueues affected entities into `entity_enrichment_queue` with `BATCH` priority instead of spawning direct Temporal child workflows. `ReBatchingWorkflow` uses the shared queue as the single dispatch path
- [ ] **SCHMA-09**: Staleness is determined at entity-type granularity by checking `schema_migration_jobs` status for the queried entity type. Per-entity and workspace-level staleness checks are not implemented

### Infrastructure

- [ ] **INFRA-01**: pgvector extension enabled in PostgreSQL (CREATE EXTENSION IF NOT EXISTS vector)
- [ ] **INFRA-02**: entity_embeddings table with vector(1536) column, HNSW index, and RLS policy for workspace isolation
- [ ] **INFRA-03**: OpenAI API client using existing WebClient pattern (no external SDK), configured via OPENAI_API_KEY environment variable
- [ ] **INFRA-04**: Testcontainers pgvector-enabled PostgreSQL image for integration tests
- [ ] **INFRA-05**: Enrichment queue table following existing workflow_execution_queue pattern
- [ ] **INFRA-06**: Semantic metadata stored in separate tables (not in existing entity_types schema JSONB) to avoid impacting existing entity CRUD

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Templates

- **TMPL-07**: 3-5 pre-configured analytical briefs per template demonstrating cross-domain querying
- **TMPL-08**: Example queries per template that work with minimal data (<50 records)

### Enrichment Pipeline

- **ENRICH-18**: Priority-based re-embedding ordering (recently-active entities first)
- **ENRICH-19**: Manual re-embedding request endpoint for specific entities or entity types

### Schema Change Handling

- **SCHMA-10**: Re-embedding progress visibility via read API endpoint (polling-based)

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Natural language query interface | Retrieval and reasoning engine is a separate future project. Build embeddings first, validate quality, then build retrieval. |
| Proactive insights / analytical brief execution | Requires query engine and LLM chain calling. Neither exists yet. Store briefs as structured text only. |
| Synchronous embedding on entity save | Would add 200-500ms latency to every entity write. Queue-based near-real-time is sufficient. |
| Workspace-level API key management | Single platform-level key is sufficient at initial scale. Adds secrets management and billing complexity. |
| Custom embedding model support | Multiple providers multiply testing surface and introduce dimension-mismatch risks. OpenAI text-embedding-3-small only. |
| Semantic search / vector similarity endpoint | Belongs to retrieval project. Embedding quality should be validated first. |
| Automatic semantic type inference | Inferring types from attribute names produces false positives. Templates pre-configure, users set manually. |
| Cross-workspace semantic similarity | Breaks multi-tenant isolation. All queries filter by workspace_id. |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| SMETA-01 | Phase 1 | Pending |
| SMETA-02 | Phase 1 | Pending |
| SMETA-03 | Phase 1 | Pending |
| SMETA-04 | Phase 1 | Pending |
| SMETA-05 | Phase 1 | Pending |
| SMETA-06 | Phase 1 | Pending |
| TMPL-01 | Phase 2 | Pending |
| TMPL-02 | Phase 2 | Complete |
| TMPL-03 | Phase 2 | Complete |
| TMPL-04 | Phase 2 | Complete |
| TMPL-05 | Phase 2 | Pending |
| TMPL-06 | Phase 2 | Complete |
| ENRICH-01 | Phase 3 | Pending |
| ENRICH-02 | Phase 3 | Pending |
| ENRICH-03 | Phase 3 | Pending |
| ENRICH-04 | Phase 3 | Pending |
| ENRICH-05 | Phase 3 | Pending |
| ENRICH-06 | Phase 3 | Pending |
| ENRICH-07 | Phase 3 | Pending |
| ENRICH-08 | Phase 3 | Pending |
| ENRICH-09 | Phase 3 | Pending |
| ENRICH-10 | Phase 3 | Pending |
| ENRICH-11 | Phase 3 | Pending |
| ENRICH-12 | Phase 3 | Pending |
| ENRICH-13 | Phase 3 | Pending |
| ENRICH-14 | Phase 3 | Pending |
| ENRICH-15 | Phase 3 | Pending |
| ENRICH-16 | Phase 3 | Pending |
| ENRICH-17 | Phase 3 | Pending |
| SCHMA-01 | Phase 4 | Pending |
| SCHMA-02 | Phase 4 | Pending |
| SCHMA-03 | Phase 4 | Pending |
| SCHMA-04 | Phase 4 | Pending |
| SCHMA-05 | Phase 4 | Pending |
| SCHMA-06 | Phase 4 | Pending |
| SCHMA-07 | Phase 4 | Pending |
| SCHMA-08 | Phase 4 | Pending |
| SCHMA-09 | Phase 4 | Pending |
| INFRA-01 | Phase 1 | Pending |
| INFRA-02 | Phase 3 | Pending |
| INFRA-03 | Phase 3 | Pending |
| INFRA-04 | Phase 1 | Pending |
| INFRA-05 | Phase 3 | Pending |
| INFRA-06 | Phase 1 | Pending |

**Coverage:**
- v1 requirements: 44 total
- Mapped to phases: 44
- Unmapped: 0

---
*Requirements defined: 2026-02-17*
*Last updated: 2026-02-18 — added ENRICH-14 through ENRICH-17, SCHMA-08, SCHMA-09; renumbered v2 IDs to avoid conflicts*
