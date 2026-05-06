# Architecture Changelog

## 2026-05-05 — Phase 2 Closeout: EnrichmentContext Lifecycle Deleted + EntityKnowledgeView Read Path Complete (Plan 02-03)

**Domains affected:** enrichment, workflow

**What changed:**

- Deleted `EnrichmentContext` and all sub-types (`EnrichmentAttributeContext`, `EnrichmentRelationshipSummary`, `EnrichmentClusterMemberContext`, `EnrichmentRelationshipDefinitionContext`) — Phase 1's transitional context model is gone; `EntityKnowledgeView` is the sole enrichment context type.
- `assembleLegacyContext` bridge method on `EnrichmentContextAssembler` deleted; `assemble(entityId, workspaceId, queueItemId): EntityKnowledgeView` is now the sole public API.
- Signature cascade through the embedding pipeline: `EmbeddingConsumer`, `EnrichmentActivities`, `EnrichmentActivitiesImpl`, `EnrichmentWorkflowImpl`, `EnrichmentEmbeddingService`, `SemanticTextBuilderService.buildText()` all now receive `EntityKnowledgeView` instead of `EnrichmentContext`.
- `EnrichmentAnalysisService.analyzeSemantics` updated to call `assembler.assemble(...)` directly and return `EntityKnowledgeView` to its caller.
- `EnrichmentContextAssembler` constructor dep count: 11 (within the 12-dep ceiling, ENRICH-02).
- `EnrichmentAnalysisService` constructor dep count: 6 (enrichmentContextAssembler, executionQueueRepository, entityRepository, entityConnotationRepository, objectMapper, logger).
- New integration test `EnrichmentBacklinkMatrixIT`: 6-case parameterized matrix covering CATALOG, KNOWLEDGE, cross-workspace isolation, soft-delete invisibility, and EXPLAIN plan verification.
- New snapshot test `EntityKnowledgeViewSnapshotTest`: byte-identical serialization gate for `EntityKnowledgeView`.
- `ConnotationPipelineIntegrationTest` config updated: `SentimentResolutionService` now wired as a real bean (was mocked, returning null), `EntityRelationshipService` mock configured to return `emptyList` for `findRelatedEntities`.

**New cross-domain dependencies:** No new cross-domain dependencies. Changes are internal to the enrichment domain.

**New components introduced:**
- `EnrichmentBacklinkMatrixIT` (`riven.core.service.enrichment`) — 6-case integration test matrix for KNOWLEDGE backlink visibility rules.
- `EntityKnowledgeViewSnapshotTest` (`riven.core.service.enrichment`) — byte-identical serialization snapshot gate for Phase 2 read path.

## 2026-05-05 — Enrichment Backlink Read Path: sourceSurfaceRole + GlossaryDefinitionRow + Assembler EntityKnowledgeView (Plan 02-02)

**Domains affected:** enrichment, entity

**What changed:**

- `EntityLink` model gains `createdAt: ZonedDateTime?` for knowledge backlink recency sorting.
- `EntityLinkProjection` gains `getCreatedAt(): Instant?`; all 4 native entity-link queries now project `r.created_at as createdAt`. Conversion to `ZonedDateTime` happens in `toEntityLink()` via `atOffset(ZoneOffset.UTC)`.
- `SentimentResolutionService` extracted from `EnrichmentAnalysisService` as a dedicated `@Service` (5 deps: ConnotationAnalysisService, WorkspaceRepository, ManifestCatalogService, EntityAttributeService, KLogger). Keeps `EnrichmentContextAssembler` constructor count at 12 (ENRICH-02 ceiling).
- `EnrichmentContextAssembler.assemble(entityId, workspaceId, queueItemId): EntityKnowledgeView` — new signature. Self-loads entity + entity type; delegates sentiment to `SentimentResolutionService`; partitions backlinks by `sourceSurfaceRole` (CATALOG → catalogBacklinks, KNOWLEDGE → knowledgeBacklinks); batch-fetches source attributes for excerpt extraction; applies `knowledgeBacklinkCap` by recency.
- `assembleLegacyContext(...)` bridge retained (returns `EnrichmentContext`) for `EnrichmentAnalysisService.analyzeSemantics` compatibility until Plan 02-03 signature cascade.
- `EnrichmentAnalysisService.analyzeSemantics` updated to call `assembleLegacyContext` instead of the old `assemble` signature.

**New cross-domain dependencies:** No new cross-domain dependencies. `SentimentResolutionService` is internal to the enrichment domain; `EntityRelationshipService.findRelatedEntities` was already used by the assembler.

**New components introduced:**
- `SentimentResolutionService` (`riven.core.service.enrichment`) — isolated sentiment resolution logic lifted from `EnrichmentAnalysisService`; owned lifecycle: workspace opt-in gate, manifest signals gate, attribute key mapping, ConnotationAnalysisService delegation.

## 2026-05-04 — Enrichment Pipeline Service Decomposition Complete (Plan 01-03)

**Domains affected:** enrichment, workflow

**What changed:**

- Deleted `EnrichmentService.kt` — all remaining logic extracted to named single-responsibility services.
- Created `EnrichmentAnalysisService` (11 deps: 10 business + logger, ceiling ≤ 11): owns semantic analysis phase — queue claim, sentiment resolution (gated on workspace flag + manifest signals + attributeKeyMapping), context assembly delegation, and connotation snapshot persistence.
- Created `EnrichmentEmbeddingService` (6 deps, ceiling ≤ 8): owns embedding phase — semantic text construction via `SemanticTextBuilderService`, vector generation, embedding upsert (delete + insert), queue item completion. Collapses former `constructEnrichedText + generateEmbedding + storeEmbedding` Temporal activities into one service method.
- Created `EmbeddingConsumer` (not a Spring bean): `ConsumerActivity` wrapper constructed in workflow body from Temporal activity stub; delegates `run(context, queueItemId)` to `activities.embedAndStore`.
- `EnrichmentActivities` interface collapsed from 4 to 2 `@ActivityMethod` declarations: `analyzeSemantics` + `embedAndStore`. Former `constructEnrichedText` and `generateEmbedding` activities eliminated.
- `EnrichmentActivitiesImpl` rewired to 3-param final shape: `(enrichmentAnalysisService, enrichmentEmbeddingService, logger)`.
- `EnrichmentWorkflowImpl.embed` rewired to consumer fan-out: `analyzeSemantics(queueItemId)` → `buildConsumers(stub): List<ConsumerActivity>` → `consumers.forEach { runCatching { it.run(context, queueItemId) }.onFailure { ... } }`. Behavior delta: consumer terminal failures are now swallowed at workflow level (pre-Phase-1: propagated as workflow failure); approved per Decision 4-ii.A / ENRICH-05.

**New cross-domain dependencies:** No new cross-domain dependencies. Changes are internal to the enrichment domain.

**New components introduced:**
- `EnrichmentAnalysisService` (`riven.core.service.enrichment`) — analysis half of enrichment pipeline.
- `EnrichmentEmbeddingService` (`riven.core.service.enrichment`) — embedding half of enrichment pipeline.
- `EmbeddingConsumer` (`riven.core.service.enrichment`) — Phase 1 ConsumerActivity (first fan-out point; future siblings: Synthesis, JSONB projection).

## 2026-05-04 — Enrichment Consumer-Independence IT + Temporal Testing Patterns (Plan 01-04)

**Domains affected:** enrichment, workflow (test-only changes)

**What changed:**

- Added `EnrichmentWorkflowIT` (383 lines, 4 tests) using Temporal's `TestWorkflowEnvironment` to prove ENRICH-05 consumer-independence contracts end-to-end.
- Established `DelegatingActivitiesImpl` pattern: concrete class registered with `TestWorkflowEnvironment` that delegates to a plain (non-annotated) interface mock — required because Temporal's `registerActivitiesImplementations` rejects Mockito dynamic proxy classes that inherit `@ActivityMethod` annotations.
- Established Kotlin-aware `TestWorkflowEnvironment` factory: `ObjectMapper().findAndRegisterModules()` auto-discovers `com.fasterxml.jackson.module.kotlin` at runtime (brought by `temporal-sdk`) for Kotlin data class deserialization; injected via `WorkflowClientOptions.setDataConverter`.
- Upgraded `temporal-testing` from `1.24.1` to `1.34.0` to match `temporal-sdk:1.34.0`.

**New cross-domain dependencies:** No new cross-domain dependencies. Test-only changes.

**New components introduced:**
- `EnrichmentWorkflowIT` (`src/test/.../enrichment`) — integration test class proving ENRICH-05; contains `ActivityDelegate` and `DelegatingActivitiesImpl` test helpers and `TestEnrichmentWorkflowImpl` workflow subclass.

## 2026-05-04 — Workspace full-text search (Notion-style ranked tsvector)

**Domains affected:** Entity, Knowledge (downstream beneficiary)

**What changed:**

- Added `entities.search_vector tsvector` column + GIN index `idx_entities_search_vector`. Recomputed on every entity write — `setweight(to_tsvector(identifier), 'A') || setweight(to_tsvector(body), 'B')` where the identifier attribute is always weighted A and the body half concatenates every TEXT / EMAIL / URL / PHONE attribute on the type that is not flagged `excludeFromSearch`. Ranks via `ts_rank_cd` against `websearch_to_tsquery`.
- New `EntitySearchService` (Spring service) owns both the recompute path and the workspace-scoped `search(workspaceId, query, typeIds?, limit)` query. Recompute reads attribute values directly from `entity_attributes` after persistence so the index source-of-truth is the post-save state, not the request payload. Recompute exceptions are swallowed and logged at debug — a search-index failure must never abort the surrounding entity save (notably matters on H2 in unit tests, which has no `tsvector`).
- `EntityIngestionService.saveEntityInternal` calls `entitySearchService.recompute(...)` after `entityAttributeService.saveAttributes(...)` and the unique-value enforcement step.
- `SchemaOptions` gains `excludeFromSearch: Boolean = false`. Convention-with-opt-out: every text-bearing attribute on an entity type feeds the body half by default; authors opt out per-attribute. The identifier attribute is always indexed regardless of the flag.
- Subsumes the legacy `notes.search_vector` FTS path that was lost during the note → entity cutover (the in-memory `plaintext.contains(...)` fallback in `NoteEntityProjector.listNotes` can now be replaced by an `entitySearchService.search(...)` call scoped to the note type).

**New cross-domain dependencies:** No (search is internal to the Entity domain; consumers call `EntitySearchService` directly).

**New components introduced:**

- `entities.search_vector tsvector` column + GIN index `idx_entities_search_vector` (partial on `deleted = false`).
- `EntitySearchService` (`riven.core.service.entity.EntitySearchService`) — recompute + workspace-scoped FTS query. Single owner of the `tsvector` lifecycle.
- `SchemaOptions.excludeFromSearch` — per-attribute opt-out flag honoured by the recompute path.

## 2026-05-04 — Knowledge link projection on entity reads (replaces note_count)

**Domains affected:** Entity, Knowledge

**What changed:**

- `Entity` model gains a top-level `knowledgeRefs: Map<typeKey, List<EntityLink>>`. Populated from inverse `ATTACHMENT` / `MENTION` / `DEFINES` edges where the source entity type carries `surface_role = KNOWLEDGE`. Replaces the dropped `entities.note_count` aggregate (commit `c82df2ec`) without re-introducing a maintenance trigger.
- `EntityLink` extended with `direction: RelationshipDirection` (`FORWARD` / `INVERSE`) and `systemType: SystemRelationshipType?` so callers can disambiguate inbound knowledge edges from outbound user-defined relationships without re-fetching the relationship definition.
- `EntityRelationshipService.findRelatedEntities` now returns a flat `List<EntityLink>` (single) / `Map<UUID, List<EntityLink>>` (batched). Partition into `relationships` vs. `knowledgeRefs` happens in `EntityEntity.toModel` via the new `List<EntityLink>.partitionForEntityProjection()` helper. The relationship-picker map remains scoped to forward edges + inverse `CONNECTED_ENTITIES` (no semantic shift for existing UI consumers).
- `EntityRelationshipRepository.findInverseEntityLinksByTargetId{,In}` widened: predicate admits inverse rows when a `relationship_target_rule` matches *or* `system_type = CONNECTED_ENTITIES` *or* (`system_type IN (ATTACHMENT, MENTION, DEFINES)` AND the source entity type's `surface_role = KNOWLEDGE`). `target_kind = 'ENTITY'` is enforced so structural DEFINES edges (targeting `ENTITY_TYPE` / `ATTRIBUTE` / `RELATIONSHIP`) cannot leak into entity-instance lookups. Both forward and inverse queries now project `direction` + `systemType` columns.
- Frontend `Entity.noteCount` retired in favour of deriving the count off `knowledgeRefs.note` filtered by `systemType === 'ATTACHMENT'`. `entity-data-table.tsx` updated; note save/delete mutation invalidations comment-corrected to reference `knowledgeRefs`.
- `RelationshipDirection` enum added (`riven.core.enums.entity`) and surfaced in the frontend type bundle alongside the expanded `SystemRelationshipType` (`Attachment`, `Mention`, `Defines`, `Includes` now exported in addition to `ConnectedEntities`).

**New cross-domain dependencies:** No (the existing Entity ↔ Knowledge edge already flowed through `entity_relationships`; this change widens the read projection only).

**New components introduced:**

- `riven.core.enums.entity.RelationshipDirection` — direction discriminator on relationship link rows.
- `riven.core.models.entity.partitionForEntityProjection` (extension on `List<EntityLink>`) + `PartitionedEntityLinks` data class — single-pass partition used by `EntityEntity.toModel` and `EntityQueryFacadeService.hydrateRelationships` so the same routing rule is applied everywhere.

## 2026-05-04 — Dev-mode legacy decommission (Phases D + F collapsed)

**Domains affected:** Note, Knowledge, Entity, Workflow (migration removal), Integration sync

**What changed:**

- Project moved to dev-mode posture: `core/CLAUDE.md` Database section now explicitly forbids data migrations / backfill workflows / dual-write paths unless requested. Schema files are the source of truth and the database is wipeable. Legacy code superseded by a refactor must be deleted in the same change rather than left behind a "Phase F decommission" placeholder.
- Deleted the entire `riven.core.workflow.migration` package: `NoteBackfillWorkflow*`, `NoteBackfillActivities*`, `GlossaryBackfillWorkflow*`, `GlossaryBackfillActivities*`, `BackfillIntegrityErrors`, plus the matching tests. `TemporalWorkerConfiguration` no longer registers the `migration` task queue or its activity beans.
- Deleted the legacy notes / glossary tables and JPA scaffolding: `notes` + `note_entity_attachments` SQL files, `NoteEntity`, `NoteEntityAttachment`, `NoteRepository`, `NoteEntityAttachmentRepository`, `workspace_business_definitions` SQL file, `WorkspaceBusinessDefinitionEntity`, `WorkspaceBusinessDefinitionRepository`. Knowledge data is now exclusively entity-backed.
- Phase D Task 20 done in the same change: `NoteEmbeddingService` rewritten to flow through `NoteEntityIngestionService` instead of `NoteRepository` / `NoteEntityAttachmentRepository`. Integration notes upsert as `entities` rows with `ATTACHMENT` relationships pointing at resolved targets; soft-delete replaces the previous hard-delete. Activity logging contract preserved.
- New `entities.pending_associations` JSONB column persists unresolved foreign references captured at sync time. `NoteEntityIngestionService.NoteIngestionInput` carries `pendingAssociations`; `postSave` writes it to the saved entity. `NoteEntityIngestionService.reconcileAttachments` provides a relationship-only update path used by `NoteEmbeddingService.reconcileUnattachedNotes` so reconciliation retries don't blank attribute payloads.
- `EntityRepository.findPendingAssociationsByTypeKey` returns integration-sourced entities of a given type-key whose `pending_associations` is non-null — replaces the old `notes.findUnattachedIntegrationNotes` derived query.
- Deleted parity tests (`NoteParityIT`, `GlossaryParityIT`) and the legacy `NoteEmbeddingServiceTest` + `NoteFactory` + `BusinessDefinitionFactory` — all coupled to deleted JPA classes. Remaining knowledge-domain coverage (NoteServiceTest, WorkspaceBusinessDefinitionServiceTest, NoteControllerE2EIT, KnowledgeControllerE2EIT, NoteEntityIngestionServiceTest, GlossaryEntityIngestionServiceTest) exercises the entity-backed paths.

**New cross-domain dependencies:** No (existing edges retained, legacy edges removed).

**New components introduced:**

- `entities.pending_associations` JSONB column + `EntityEntity.pendingAssociations` field — generic per-entity unresolved-reference store, consumed first by integration note sync but available to any future integration-sourced entity type.
- `EntityRepository.findPendingAssociationsByTypeKey` — JPQL lookup for reconciliation passes.
- `NoteEntityIngestionService.reconcileAttachments` — relationship-only update path that preserves attribute payload.

## 2026-05-02 — PR #198 Review Fixes (Knowledge Plane Hardening)

**Domains affected:** Entity, Knowledge, Workflow (migration), Core models

**What changed:**

- Hardened the system-bus boundary on `EntityIngestionService`: `saveEntityInternal` now requires the resolved `EntityType.workspaceId == request workspaceId`, mirroring the existing guard in `EntityTypeRelationshipService.createSystemDefinitionInternal`. Method also split into named private helpers (`resolveAndAuthorizeType`, `loadPreviousEntity`, `wrapPayload`, `buildEntity`, `enforceUniqueValues`) per CLAUDE.md function-length rule.
- New `EntityIngestionService.clearRelationshipsByKindInternal` + `EntityRelationshipService.clearAllOfKindForDefinition` + repository-level `deleteAllBySourceIdAndDefinitionIdAndTargetKind` JPQL delete: closes the empty-set reconciliation gap for parent-scoped kinds (`ATTRIBUTE` / `RELATIONSHIP`). `replaceForDefinition` now strictly requires `targetParentId` for parent-scoped kinds; the new clear path is the supported way to drop all rows of a kind regardless of parent.
- `AbstractKnowledgeEntityIngestionService` extended with `clearParentScopedKinds(input)` hook so subclasses can declare which parent-scoped kinds need full clearance when no refs of that kind are supplied. `GlossaryEntityIngestionService` overrides it to clear `DEFINES/ATTRIBUTE` rows when `attributeRefs` is empty.
- `EntityRelationshipService.replaceForDefinition` renamed to `replaceForDefinitionInternal` and marked `internal` to align with the system-bus naming convention — only `EntityIngestionService.replaceRelationshipsInternal` calls it.
- `EntityTypeRelationshipService.getOrCreateSystemDefinition` now retrieves `userId` from the JWT and threads it through to `getOrCreateSystemDefinitionInternal`, enabling activity logging on the public path while preserving null-userId for `TemplateInstallationService`'s pre-auth onboarding flow.
- `GlossaryEntityIngestionService.GlossaryIngestionInput` swapped raw `String` `category`/`source` for `DefinitionCategory`/`DefinitionSource` enums; corresponds with the existing enum-backed model and removes the implicit string boundary at the ingestion edge.
- `NoteEntityProjector.unwrapNoteAttributes` and `matchesPlaintext` now throw `SchemaValidationException` instead of silently coercing missing required mappings (`title`/`content`/`plaintext`) to empty values. `listNotes` resolves the plaintext / title attribute IDs once and passes them into `matchesPlaintext` to avoid the per-row `findById` lookup that previously masked corrupt notes during search.
- `NoteBackfillActivitiesImpl` and `GlossaryBackfillActivitiesImpl` narrowed `DataIntegrityViolationException` handling: only PostgreSQL `SQLState 23505` (unique-constraint races) is treated as `skipped`; FK / NOT NULL / check-constraint violations now route to the `failed` counter so real integrity bugs surface in batch metrics. New shared helper `BackfillIntegrityErrors.isUniqueViolation(e)`.
- `EntityRelationshipRepository`: replaced two derived `deleteAllBySourceIdAndDefinitionIdAndTargetKind*` methods with explicit `@Modifying @Query` JPQL `DELETE` statements per CLAUDE.md JPQL rule.
- `CommunicationModel.type` SchemaOption now derives its enum list from the new `riven.core.enums.core.CommunicationType` enum, preserving the existing kebab-case wire contract via `@JsonProperty` annotations.

**New cross-domain dependencies:** No (existing dependencies tightened, no new edges).

**New components introduced:**

- `riven.core.enums.core.CommunicationType` — enum backing `CommunicationModel.type` SchemaOptions.
- `EntityIngestionService.clearRelationshipsByKindInternal` + `EntityRelationshipService.clearAllOfKindForDefinition` — system-bus methods for clearing parent-scoped relationship rows when reconciliation cannot supply a `targetParentId`.
- `BackfillIntegrityErrors.isUniqueViolation` — shared SQLState-aware helper for backfill activities.

## 2026-04-29 — Glossary Graduation (Phase C)

**Domains affected:** Knowledge, Entity, Workflow (migration)

**What changed:**

- Materialised the `RelationshipTargetKind` enum on a new `entity_relationships.target_kind` column (declarative schema edit on `db/schema/01_tables/entities.sql`). The FK on `target_entity_id → entities(id)` was relaxed because non-`ENTITY` targets reference `entity_types` rows or attribute UUIDs — referential integrity is now enforced at the service layer.
- `EntityRelationshipService.replaceForDefinition` is now kind-aware: reconciliation only sweeps existing rows whose `target_kind` matches the supplied value, and a new repo method `deleteAllBySourceIdAndDefinitionIdAndTargetKindAndTargetIdIn` keeps glossary `DEFINES` batches at different kinds on the same definition row from clobbering each other.
- `AbstractKnowledgeEntityIngestionService.idempotentLookup` extended to support workspace-internal inputs: when `sourceIntegrationId` is null, the base falls back to `(workspaceId, sourceExternalId)` keyed against `entityRepository.findByWorkspaceIdAndSourceExternalId`. This makes the abstract base usable for the glossary backfill (which has no integration) without forcing every subclass to override the lookup.
- Introduced `GlossaryEntityIngestionService` (subclass of the abstract base) as the single emission point for glossary ingestion — used by both the cutover service and the legacy backfill workflow. Maps the six glossary attributes (term / normalized_term / definition / category / source / is_customised) and emits three relationship batches per upsert: `DEFINES/ENTITY_TYPE`, `DEFINES/ATTRIBUTE`, `MENTION/ENTITY`.
- Cut over `WorkspaceBusinessDefinitionService` to entity-backed reads + writes. Service body fully rewritten — `WorkspaceBusinessDefinitionRepository` is no longer a constructor dependency. New `GlossaryEntityProjector` reshapes glossary entity rows + DEFINES relationship rows (split by `target_kind`) back into the existing `WorkspaceBusinessDefinition` DTO. `KnowledgeController` and `OnboardingService.createDefinitionInternal` call sites are unchanged. Legacy JPA scaffolding stays live (Phase F deletes it).
- Added `GlossaryBackfillWorkflow` + `GlossaryBackfillActivities[Impl]` — paginated, idempotent Temporal workflow that walks `workspace_business_definitions` and upserts each row through `GlossaryEntityIngestionService`. Registered on the same `migration` task queue as the note backfill. Idempotency rides on `sourceExternalId = "legacy:{definitionId}"`.
- Fields with no direct entity-layer storage (`compiledParams`, `status`, `version`) project to fixed defaults (`null` / `ACTIVE` / `0`); the optimistic-locking `version` field on update is silently ignored, and a non-null `status` filter requesting `SUGGESTED` returns empty.

**New cross-domain dependencies:** Yes — Knowledge → Entity (new): `WorkspaceBusinessDefinitionService` and `GlossaryEntityIngestionService` now persist glossary state through `EntityService` rather than `WorkspaceBusinessDefinitionRepository`. Workflow.migration → Knowledge (new): `GlossaryBackfillActivitiesImpl` reads from the legacy `WorkspaceBusinessDefinitionRepository` and writes through `GlossaryEntityIngestionService`.

**New components introduced:**

- `GlossaryEntityIngestionService` (Spring service) — concrete `AbstractKnowledgeEntityIngestionService` subclass for glossary terms; single emission point shared by the user-facing service and the backfill workflow.
- `GlossaryEntityProjector` (Spring service) — read-only translator from glossary entity rows + DEFINES/MENTION relationships into `WorkspaceBusinessDefinition` DTOs.
- `GlossaryBackfillWorkflow` + `GlossaryBackfillActivitiesImpl` (Temporal workflow + Spring activity bean) — one-shot maintenance backfill from legacy `workspace_business_definitions` to entity-backed glossary terms.
- `entity_relationships.target_kind` column + supporting `EntityRelationshipEntity.targetKind` field — promoted from the Phase B forward-compat enum into a persisted, kind-aware reconciliation column.

## 2026-04-29 — Note Graduation (Phase B)

**Domains affected:** Note, Entity, Knowledge, Workflow (migration)

**What changed:**

- Introduced `AbstractKnowledgeEntityIngestionService` — extensible base for every knowledge-domain entity (Note, Glossary, future Memo / SOP / Policy / Decision / Meeting / Incident). Subclasses provide an entity-type key, an attribute-payload mapper, and the relationship batches; the base owns workspace lookup, idempotent upsert by `(workspaceId, sourceIntegrationId, sourceExternalId)`, and relationship reconciliation.
- Introduced `NoteEntityIngestionService` (subclass of the abstract base) as the single emission point for note ingestion, used by `NoteService` (user-authored) and earmarked for the integration sync path in Phase D.
- Cut over `NoteService` to entity-backed reads + writes. New `NoteEntityProjector` reshapes entity rows + `ATTACHMENT` relationship rows back into the existing `Note` / `WorkspaceNote` DTO contract; `NoteController` JSON wire format is unchanged. Legacy `NoteRepository` / `NoteEntityAttachmentRepository` JPA scaffolding stays live (Phase F deletes it) but is no longer referenced from `NoteService`.
- Added system-driven entry points on `EntityService` (`saveEntityInternal`, `softDeleteEntityInternal`, `replaceRelationshipsInternal`, `findByIdInternal`, `findByTypeKeyInternal`) and a corresponding `EntityRelationshipService.replaceForDefinition` that bypass the JWT-bound auth path so background contexts (Temporal activities, ingestion services) can drive entity mutations.
- Introduced `RelationshipTargetKind` enum (`ENTITY` / `ENTITY_TYPE` / `ATTRIBUTE`) and threaded it through the abstract base + `replaceRelationshipsInternal` for forward-compat with Phase C glossary `DEFINES` edges. Phase C (Task 16) materialises the corresponding `entity_relationships.target_kind` column.
- Added `NoteBackfillWorkflow` + `NoteBackfillActivities[Impl]` — paginated, idempotent Temporal workflow that walks the legacy `notes` table for a workspace and upserts each row into the entity layer. Registered on a new `migration` task queue in `TemporalWorkerConfiguration`. Idempotency contract: `sourceExternalId = "legacy:{noteId}"` for user-authored rows; duplicate-key violations from the ingestion path report `skipped`, not `failed`.

**New cross-domain dependencies:** Yes — Note → Entity (new): `NoteService` and `NoteEntityIngestionService` now persist note state through `EntityService` rather than `NoteRepository`. Note → Knowledge (new): `NoteEntityIngestionService` extends `AbstractKnowledgeEntityIngestionService` in `riven.core.service.knowledge`. Workflow.migration → Note (new): `NoteBackfillActivitiesImpl` reads from the legacy `NoteRepository` and writes through `NoteEntityIngestionService`.

**New components introduced:**

- `AbstractKnowledgeEntityIngestionService` (Spring abstract base) — extensible ingestion seam for every knowledge-domain entity type.
- `KnowledgeIngestionInput` interface + `KnowledgeRelationshipBatch` data class — the cross-cutting input + relationship-batch contract subclasses bind to.
- `NoteEntityIngestionService` (Spring service) — concrete subclass for notes; single emission point used by user-facing and integration paths.
- `NoteEntityProjector` (Spring service) — read-only translator from entity rows + `ATTACHMENT` relationships into `Note` / `WorkspaceNote` DTOs.
- `NoteBackfillWorkflow` + `NoteBackfillActivitiesImpl` (Temporal workflow + Spring activity bean) — one-shot maintenance backfill from legacy `notes` to entity-backed notes.
- `RelationshipTargetKind` enum — declares `ENTITY` / `ENTITY_TYPE` / `ATTRIBUTE` for forward-compat with Phase C glossary `DEFINES` edges.

## 2026-04-29 — Entity Connotation Pipeline Phase B (Tier 1)

**Domains affected:** Connotation, Enrichment, Catalog, Workspace, Workflow

**What changed:**

- `ConnotationAnalysisService` (workspace-scoped, `@PreAuthorize`) routes analysis to the tier declared in `ConnotationSignals.tier`. Tier 1 implemented; Tier 2/3 throw `NotImplementedError`. The service is a pure router — caller pre-resolves attribute values to keep it free of repository dependencies.
- `ConnotationTier1Mapper` is a pure deterministic LINEAR/THRESHOLD scale mapper from a manifest-declared source attribute to the unified `[-1.0, +1.0]` sentiment score with a 5-bucket `SentimentLabel`. Failures encoded as typed `SentimentAnalysisOutcome.Failure`; caller persists `SentimentAxis(status = FAILED, ...)` rather than aborting.
- `ConnotationAdminService.reanalyzeAxisWhereVersionMismatch(axis, tier, workspaceId)` enqueues every entity in a workspace whose persisted SENTIMENT-axis `analysisVersion` differs from the active config value. Backed by new `ExecutionQueueRepository.enqueueByAxisVersionMismatch` native query using `IS DISTINCT FROM` over `connotation_metadata->'axes'->'SENTIMENT'->>'analysisVersion'`.
- `integration.schema.json` extended with optional `connotationSignals` block on `integrationEntityType` (`tier`, `sentimentAttribute`, `sentimentScale {sourceMin, sourceMax, targetMin, targetMax, mappingType: LINEAR|THRESHOLD}`, `themeAttributes`). `ManifestResolverService` cross-validates `sentimentAttribute` and `themeAttributes` against the entity type's declared attribute keys; failures log warn and drop the field rather than rejecting the manifest (consistent with existing relationship/field-mapping handling). Persisted via new `catalog_entity_types.connotation_signals` JSONB column.
- New per-workspace `connotation_enabled` boolean column (default `false`) on `workspaces` gates SENTIMENT axis enrichment; RELATIONAL and STRUCTURAL axes always populate. Accessor `WorkspaceService.isConnotationEnabled(workspaceId)`.
- `ConnotationAnalysisConfigurationProperties` (`riven.connotation.tier1-current-version`, default `v1`) controls the analysis-version stamp. Auto-discovered via existing `@ConfigurationPropertiesScan`.
- `EnrichmentService.persistConnotationEnvelope` resolves the SENTIMENT axis through `ConnotationAnalysisService` when the workspace flag is enabled and the entity type has manifest signals. Otherwise leaves the axis at `NOT_APPLICABLE`. The pre-computed axis flows into both the persisted envelope and the transient `EnrichmentContext.sentiment` (only when status is `ANALYZED`).
- `SemanticTextBuilderService` emits a "Connotation Context" section (≤ 300 chars) when the context carries an `ANALYZED` SENTIMENT axis. Lowest-priority optional section — last in section order, first to drop under truncation.
- `activity_logs.operation` CHECK constraint extended to allow `ANALYZE` and `REANALYZE`; column widened to `VARCHAR(20)`. The previous CHECK would have blocked all connotation activity logs in production.
- `ConnotationAxisName` enum (`SENTIMENT`, `RELATIONAL`, `STRUCTURAL`) — used by the admin op and future axis-targeted ops. Names match the UPPERCASE JSONB keys persisted in `entity_connotation.connotation_metadata` (per `@JsonProperty` on `ConnotationAxes`).
- `OperationType.ANALYZE` and `OperationType.REANALYZE` added; `Activity.ENTITY_CONNOTATION` and `ApplicationEntityType.ENTITY_CONNOTATION` added.
- Tier model `tier: AnalysisTier` (enum) on `ConnotationSignals` rather than `String` — applied the project's "enums over string literals" rule despite the plan typing it as `String`.

**New cross-domain dependencies:** Yes
- Enrichment → Connotation: `EnrichmentService` injects `ConnotationAnalysisService`.
- Enrichment → Catalog: `EnrichmentService` injects `ManifestCatalogService` for `getConnotationSignalsForEntityType`.
- Enrichment → Workspace: `EnrichmentService` injects `WorkspaceService.isConnotationEnabled`.
- Catalog → Entity: `ManifestCatalogService` now injects `EntityTypeRepository` to resolve `entityTypeId → (sourceManifestId, key) → catalog_entity_type` for connotation-signal lookup. New edge from a previously global-only catalog service.
- Connotation → Activity: `ConnotationAnalysisService` and `ConnotationAdminService` log via `ActivityService`.

**New components introduced:**

- `ConnotationAnalysisService` — workspace-scoped tier dispatcher; routes Tier 1, stubs 2/3.
- `ConnotationTier1Mapper` — pure deterministic Tier 1 mapper (LINEAR/THRESHOLD).
- `ConnotationAdminService` — admin op for SENTIMENT version-mismatch backfill.
- `ConnotationAnalysisConfigurationProperties` — active versions per tier.
- `ConnotationAxisName` enum.
- `SentimentAnalysisOutcome` sealed class + `SentimentFailureReason` enum.
- New repository method `ExecutionQueueRepository.enqueueByAxisVersionMismatch`.
- New manifest catalog method `ManifestCatalogService.getConnotationSignalsForEntityType`.
- New service accessor `WorkspaceService.isConnotationEnabled`.

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

## 2026-05-01 — Entity system-bus boundary + knowledge-domain PR feedback

**Domains affected:** Entity, Note, Knowledge (Glossary), Catalog (Template Installation)

**What changed:**

- Extracted system-driven entity persistence out of `EntityService` into a new `EntityIngestionService` bean. The `*Internal` methods (`saveEntityInternal`, `softDeleteEntityInternal`, `replaceRelationshipsInternal`, `findByIdInternal`, `findByTypeKeyInternal`) now live there. `EntityService` retains the JWT-fronted CRUD path with `@PreAuthorize` / `@PostAuthorize`. The split establishes an architectural boundary: controllers go through `EntityService`; background callers (Temporal activities, knowledge ingestion, projectors) go through `EntityIngestionService`.
- Updated callers to inject `EntityIngestionService`: `AbstractKnowledgeEntityIngestionService` (and subclasses `NoteEntityIngestionService`, `GlossaryEntityIngestionService`), `NoteService`, `WorkspaceBusinessDefinitionService`.
- Dropped the unused `readonly` parameter from the system-bus save path. Readonly state is derived from `sourceType` (`SourceType.INTEGRATION`); there is no separate `readonly` column on `entities`.
- Added an internal `getOrCreateSystemDefinitionInternal` variant on `EntityTypeRelationshipService` (no `@PreAuthorize`) so the template installer can idempotently seed knowledge-domain system relationship edges (`ATTACHMENT`, `MENTION`, `DEFINES`) on **reused** entity types during install, not just newly created ones.
- Tightened `EntityTypeRelationshipService.getOrCreateSystemDefinition` to reject existing rows whose `workspace_id` differs from the caller's `workspaceId` — closes a theoretical cross-tenant lookup leak.
- `EntityRelationshipService.replaceForDefinition` now scopes reconciliation by `target_parent_id` for sub-reference target kinds (`ATTRIBUTE`, `RELATIONSHIP`); a new repository method `deleteAllBySourceIdAndDefinitionIdAndTargetKindAndTargetParentIdAndTargetIdIn` carries the parent guard into the delete query.
- Extended the `entity_relationships` unique index to include `target_kind` and `COALESCE(target_parent_id, '00000000-0000-0000-0000-000000000000'::uuid)` so polymorphic targets cannot collide on `target_id` alone.
- Added `@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")` to public methods on `NoteEntityProjector` and `GlossaryEntityProjector`. `GlossaryEntityProjector` now throws `NotFoundException` / `SchemaValidationException` instead of `IllegalStateException` / `error()` so misconfigured workspaces map to 404 / 422 instead of 500.
- Knowledge-domain string literals (`"note"`, `"glossary"`) replaced with a new `KnowledgeEntityTypeKey` enum in `riven.core.enums.knowledge`.
- Moved 12 core model definitions from `riven.core.models.core.models.base` to `riven.core.models.core.base` (package path no longer doubles the `models` segment).

**New cross-domain dependencies:** No — the new boundary is internal to the Entity domain. Knowledge / Note services already depended on `EntityService`; they now depend on `EntityIngestionService` instead. Net dependency surface is unchanged.

**New components introduced:**

- `EntityIngestionService` (`riven.core.service.entity.EntityIngestionService`) — system-bus persistence for entities. Owns `saveEntityInternal` / `softDeleteEntityInternal` / `replaceRelationshipsInternal` / `findByIdInternal` / `findByTypeKeyInternal`. Documented at class level as system-only; must not be injected into JWT-fronted controllers.
- `KnowledgeEntityTypeKey` enum (`riven.core.enums.knowledge.KnowledgeEntityTypeKey`) — typed wrapper for the workspace entity-type keys `note` / `glossary` used by the catalog manifest, knowledge ingestion services, and projectors.
