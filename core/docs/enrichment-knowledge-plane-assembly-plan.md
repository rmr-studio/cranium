# EnrichmentService as Knowledge-Plane Assembly Point — Unified Backlink Aggregator

## Context

The original framing of this plan was narrow: "should glossary entities subsume EntityTypeSemanticMetadata's `definition` field?" After reading the company-brain CEO plan (`~/.gstack/projects/rmr-studio-riven/ceo-plans/2026-04-29-proactive-company-brain.md`) and surveying the parallel `entity-connotation-extraction-2` worktree, the real architectural question is bigger:

**EnrichmentService is becoming the assembly point for the L1→L2 bridge of the company brain.** Its job is no longer "compose attributes + metadata into embedding text". Its job is to traverse an entity's full backlink graph across CATALOG / KNOWLEDGE / SIGNAL surface_roles and roll the compounded knowledge into one embedding text — so retrieval surfaces the entity *in the full context the brain has accumulated*.

Right now it does ~30% of that:
- ✅ Reads structural metadata (`EntityTypeSemanticMetadata` definitions + classifications)
- ✅ Aggregates CATALOG backlinks (relationship summaries with categorical breakdowns + latest activity)
- ✅ Resolves identity-cluster siblings + RELATIONAL_REFERENCE display values
- ✅ Persists an `EntityMetadataSnapshot` (RELATIONAL + STRUCTURAL metadata today; SENTIMENT metadata activated in worktree-2 via `ConnotationAnalysisService` Tier 1)
- ❌ KNOWLEDGE backlinks (notes mentioning the entity, glossary terms defining its type/attributes, policies referencing it) — **invisible**
- ❌ SIGNAL backlinks (churn risks, cohort drift events, sentiment-driven alerts attached to the entity) — **invisible**
- ❌ Iron Law auto-link parser (Step 5 of company-brain) — **gap**
- ❌ Read-time aggregation surface (`EntityBrainService.getFull()` per company-brain Step 5) — **gap**

The original "metadata vs glossary" question is correct but is **Layer 1** of the answer. The metadata `definition` field is one of many sources of narrative the embedding text could pull from. The right architecture treats them all as backlinks-into-the-entity and composes the embedding text from a prioritized union.

## The unified vision (from company-brain CEO plan, locked)

```
ENRICHMENT TEXT COMPOSITION (per entity, per enrich cycle)
══════════════════════════════════════════════════════════════════════
  Section 1: Identity                    ← entity attrs + identifier
  Section 2: Type narrative              ← entity_type definition
                                            (today: metadata.definition;
                                             target: glossary DEFINES edges
                                             with target_kind=ENTITY_TYPE)
  Section 3: Attributes                  ← attribute values + labels
                                            (today: metadata.definition per
                                             attribute; target: glossary
                                             DEFINES edges with
                                             target_kind=ATTRIBUTE)
  Section 4: CATALOG backlinks           ← relationship summaries
                                            ("3 Orders, latest 2026-04-22,
                                             top categories: shipped (2),
                                             pending (1)")
  Section 5: KNOWLEDGE backlinks         ← MENTION-pointing entities
                                            with surface_role=KNOWLEDGE
                                            ("Note 'Q1 escalation playbook'
                                             mentions; Glossary 'VIP'
                                             defines; SOP 'Refund flow'
                                             references")
                                            cap=3 (conservative; tune from
                                             telemetry post-launch)
  Section 6: SIGNAL backlinks            ← DEFERRED until dtc-ecom merges
                                            and SIGNAL entity types exist
                                            (no infra in PR2; adds in
                                             follow-up plan)
  Section 7: Entity metadata             ← SENTIMENT metadata from
                                            ConnotationAnalysisService
                                            (Tier 1 deterministic, future
                                             Tier 2 LLM)
  Section 8: Identity cluster siblings   ← unchanged
  Section 9: RELATIONAL_REFERENCE        ← unchanged
        ↓
  Truncation budget (FREETEXT, RELATIONAL_REFERENCE first; then trim
   KNOWLEDGE by recency; CATALOG and Section 1-3 are protected.
   Per-entity per-section telemetry: kept_count, dropped_count,
   token_count — emitted on every embedding call.)
        ↓
  Embedding model
```

The principle: **every entity sees what the brain has accumulated about it before it's embedded**. New notes, new signals, new glossary terms automatically improve every neighboring entity's retrieval the next enrich cycle.

## Findings — Current state (with worktree-2 merged)

### Two semantic-description systems

(unchanged from original plan)

| Concept | EntityTypeSemanticMetadata | Glossary entity |
|---|---|---|
| Per-attribute narrative | `definition` text | DEFINES edge w/ targetKind=ATTRIBUTE |
| Per-type narrative | targetType=ENTITY_TYPE row | DEFINES edge w/ targetKind=ENTITY_TYPE |
| Per-relationship narrative | targetType=RELATIONSHIP row | (no target_kind=RELATIONSHIP yet — gap) |
| Classification (enum, identity-critical) | `classification` | none |
| signalType (NAME/COMPANY/PHONE/EMAIL) | `signalType` | none |
| Many-to-many ("one term defines many") | unique-constrained | DEFINES is many-to-many |
| Standalone listable entity | ❌ | ✅ |
| MENTION edges from other content | ❌ | ✅ (write-only today) |

### EnrichmentService dependency growth (post-worktree-2 merge)

Verified by counting the constructor: **19 injections today on this branch** (including KLogger, ObjectMapper, WorkflowClient, EnrichmentConfigurationProperties; ~15 domain-service deps after subtracting framework deps). Plus glossary read-through and KNOWLEDGE backlink reads would add 2-3 more. The plan-stage estimate of 14 was an undercount.

Architecture-suggestion #1 (logged 2026-04-10) flagged this at 10 injections. The trajectory makes the decomposition mandatory, not optional. **The eng-review reshaped this from "single ContextProvider abstraction" into a four-service split by lifecycle stage** (see Phase 1 below) — each service ≤ 6 deps with real domain boundaries matching the four named responsibilities already in EnrichmentService's KDoc.

### Surface-role landscape (from company-brain)

Three `surface_role` values on `entity_types` (denormalized to `entity_types` from `catalog_entity_types` via reconciliation hook):

- **CATALOG** — browsable in main entity catalog (customer, vendor, order, ticket, sku)
- **KNOWLEDGE** — own UI surface; surfaced contextually on entity pages (note, glossary, policy, sop, decision, incident, memo)
- **SIGNAL** — derived/ephemeral; own UI surface (CohortDriftEvent, ChurnRiskSignal, sentiment alerts)

All three participate in embedding / synthesis / connotation / backlinks. UI scoping is read-time filter.

### What's already shipped vs gap

| Capability | Status | Lives in |
|---|---|---|
| Universal entity model (EAV) | ✅ shipped | `entities` table |
| `surface_role` discriminator | ✅ shipped (CATALOG/KNOWLEDGE/SIGNAL on entity_types) | this branch |
| Note graduation (entity_type=note, KNOWLEDGE) | ✅ shipped Phase B | this branch |
| Glossary graduation (entity_type=glossary, KNOWLEDGE) | ✅ shipped Phase C | this branch |
| `target_kind` on entity_relationships (ENTITY/ENTITY_TYPE/ATTRIBUTE) | ✅ shipped Phase C | this branch |
| EntityMetadataSnapshot (RELATIONAL + STRUCTURAL metadata) | ✅ shipped Phase A | this branch |
| EntityMetadataSnapshot SENTIMENT metadata (Tier 1 deterministic) | ✅ shipped in worktree-2 | `entity-connotation-extraction-2` |
| Per-workspace `connotation_enabled` flag + per-type `connotationSignals` manifest | ✅ shipped in worktree-2 | `entity-connotation-extraction-2` |
| `ConnotationAdminService` (reanalyze on metadata version mismatch) | ✅ shipped in worktree-2 | `entity-connotation-extraction-2` |
| `entity_synthesis` (compiled-truth) | 🚧 Synthesis Phase 1 in flight | synthesis project |
| `entity_synthesis_history` (timeline) | 🚧 Synthesis Phase 3 planned | synthesis project |
| Iron Law auto-link parser (BlockNote → entity_relationships) | ❌ gap (Step 5 of company-brain) | not yet planned |
| Glossary `DEFINES` edges feeding embeddings | ❌ gap | **THIS PLAN** |
| MENTION-driven KNOWLEDGE backlinks in embeddings | ❌ gap | **THIS PLAN** |
| SIGNAL backlinks in embeddings | ❌ gap | **THIS PLAN** |
| `EntityBrainService.getFull()` canonical aggregation | ❌ gap (Step 5 of company-brain) | not yet planned |
| `target_kind=RELATIONSHIP` on entity_relationships | ❌ gap | **PR1** (this plan) |
| `target_id` rename + `target_parent_id` for sub-reference target_kinds (ATTRIBUTE + RELATIONSHIP) | ❌ gap | **PR1** (this plan) |
| EnrichmentService 4-service split (Queue / ContextAssembler / Analysis / Embedding) | ❌ gap (architecture-suggestion #1) | **PR2** (this plan) |

## Recommended Approach — Approach B-Plus, split into 3 PRs

Approach B (hybrid layered split — schema-internal traits stay in metadata; narrative migrates to glossary) was the right insight at the wrong altitude. Generalize it: **the embedding text composes from a prioritized union of all backlinks across all surface_roles, with glossary as the highest-signal narrative source for KNOWLEDGE and metadata as the highest-signal source for STRUCTURAL traits**.

Eng review (D1) split the plan into **three PRs**, each independently verifiable. Eng review (D2/D3) reshaped the architecture to remove abstraction theater. Eng review (D4) cut Phase 4 entirely — knowledge-graph re-enrichment belongs to the larger Entity Reconsumption project, not this plan. Eng review (D5) corrected `target_parent_id` semantics. Eng review (D6) replaced parity-gate testing with creation-side enforcement. Eng review (D7) added telemetry + conservative caps.

```
PR1 ──── Phase -1 (rename) + Phase 0 (schema cleanup)
   │     Two parallel lanes; rename merges first, then schema.
   ▼
PR2 ──── Phase 1 (4-service split) + Phase 2 (repo methods + DTO)
   │     + Phase 3 (SemanticTextBuilder rewrite, embedding-text projection only)
   │     JSONB projection deferred. SIGNAL section infra deferred.
   ▼
PR3 ──── Phase 5 (cutover) — gated on entity-type-creation lifecycle TODO
         landing first (auto-creates inbound DEFINES glossary entities so
         dropping metadata.definition does not leave entity_types empty).
```

**Out of this plan's scope (deferred):**
- Phase 4 reconciliation hooks for knowledge-graph edge changes — owned by Entity Reconsumption project (per Notion doc). Mark-and-sweep dirty-flag pattern is the right shape but lives elsewhere.
- `EntityKnowledgeView.toSnapshotJsonb()` — speculative scaffolding for entity_synthesis Phase 1 / Temporal Synthesis Layer / LLM-wiki, all unlanded. Reactivate when first real consumer arrives.
- SIGNAL section (Section 6) infrastructure — adds when dtc-ecom branch lands SIGNAL types.
- Iron Law auto-link parser — separate plan.

---

## PR1 — Schema cleanup

### Phase -1 — Terminology rename (precondition, lands first)

Existing code uses "envelope" / "axis" / `ConnotationMetadataSnapshot` / `ConnotationMetadata` framing. Rename to the new vocabulary before any structural work:

- `riven.core.models.connotation.ConnotationMetadataSnapshot` → `EntityMetadataSnapshot`
- `riven.core.models.connotation.ConnotationMetadata` → `EntityMetadata`
- `ConnotationAdminService.reanalyzeAxisWhereVersionMismatch` → `reanalyzeWhereMetadataVersionMismatch`
- `ExecutionQueueRepository.enqueueByAxisVersionMismatch` → `enqueueByMetadataVersionMismatch`
- KDoc / comments / log messages: drop "envelope" and "axis" / "axes"; replace with "metadata snapshot" or "metadata" or "metadata category" as appropriate
- Sentiment/Relational/Structural keep their `*Metadata` suffix (already correct vocabulary)
- `entity_connotation` table name + `connotation_metadata` JSONB column: leave as-is (rename separately if desired — schema-level decision out of this plan's scope)

**Effort:** S (CC ~30 min — IDE-driven mechanical rename + test update)

### Phase 0 — Schema polymorphism cleanup

- Rename `entity_relationships.target_entity_id` → `target_id`
- Add `target_parent_id UUID NULL` — uniformly the **owning entity_type** for sub-reference target_kinds. Populated when `target_kind IN ('ATTRIBUTE', 'RELATIONSHIP')` (both are sub-references owned by an entity_type); NULL when `target_kind IN ('ENTITY', 'ENTITY_TYPE')`. FK → entity_types(id) ON DELETE RESTRICT.
- CHECK constraint:
  ```sql
  CHECK (
      (target_kind IN ('ATTRIBUTE', 'RELATIONSHIP') AND target_parent_id IS NOT NULL)
   OR (target_kind IN ('ENTITY', 'ENTITY_TYPE')      AND target_parent_id IS NULL)
  )
  ```
- Add `RELATIONSHIP` to `RelationshipTargetKind` enum
- Add partial index `(workspace_id, target_id, target_kind) WHERE target_kind <> 'ENTITY' AND deleted = false`
- Update `KnowledgeRelationshipBatch`, `GlossaryEntityIngestionService`, `GlossaryEntityProjector`, `EntityService.replaceRelationshipsInternal` to populate `target_parent_id` on ATTRIBUTE rows; RELATIONSHIP rows similarly when those edges are written
- All repository JPQL queries referencing `target_entity_id` / `targetEntityId` rename to `target_id` / `targetId`
- DDL comment on `target_parent_id` documents the contract

**Effort:** M (CC ~1 hour); blocks PR2

**PR1 lanes:** Phase -1 and Phase 0 touch disjoint files (connotation models vs. entity_relationships) — can land as parallel worktrees, merging rename first (smaller diff), schema second.

---

## PR2 — Knowledge backlinks in embeddings

### Phase 1 — Four-service split of EnrichmentService

Replaces the original "ContextProvider abstraction" idea (D2). EnrichmentService at 19 deps decomposes along its KDoc-named responsibilities into four services with 4–6 deps each. No single-impl interface ceremony — services are concrete classes.

| New service | Owns | Approx deps |
|---|---|---|
| `EnrichmentQueueService` | `enqueueAndProcess`, `enqueueByEntityType`, queue-lifecycle, Temporal dispatch | ExecutionQueueRepository, WorkflowClient, EnrichmentConfigurationProperties, EnrichmentAnalysisService, KLogger (~5) |
| `EnrichmentContextAssembler` | Read-side aggregation: entity, type, metadata, relationships, clusters, glossary backlinks | EntityRepository, EntityTypeRepository, EntityTypeSemanticMetadataRepository, EntityAttributeService, EntityRelationshipRepository, RelationshipDefinitionRepository, IdentityClusterMemberRepository, RelationshipTargetRuleRepository, ManifestCatalogService, WorkspaceService, KLogger (~11 — this is the heaviest service; further decomposition is a future concern, not this plan's) |
| `EnrichmentAnalysisService` | `analyzeSemantics`: claim queue item, build `EntityKnowledgeView`, persist snapshot to `entity_connotation`, return `EnrichmentContext` | EnrichmentContextAssembler, SemanticTextBuilderService, ConnotationAnalysisService, EntityConnotationRepository, ObjectMapper, ExecutionQueueRepository, KLogger (~7) |
| `EnrichmentEmbeddingService` | `storeEmbedding`: invoke EmbeddingProvider, upsert `entity_embedding`, complete queue item | EmbeddingProvider, EntityEmbeddingRepository, ExecutionQueueRepository, KLogger (~4) |

- Existing `EnrichmentService.kt` is deleted; the four services live in `riven.core.service.enrichment.*`
- `EnrichmentActivities` callers (Temporal) re-wire to call `EnrichmentQueueService` / `EnrichmentAnalysisService` / `EnrichmentEmbeddingService` per their lifecycle stage — public method names preserved where possible
- **REGRESSION TEST (mandatory):** snapshot the `EnrichmentContext` JSON output of `analyzeSemantics` for a fixture entity BEFORE the split; persist as test resource; assert byte-equality AFTER the split. Pure-refactor contract.

**Effort:** L (CC ~2.5 hours); foundational — must land before Phase 2/3 changes the read shape

### Phase 2 — Backlink reads via repository methods

D3 collapsed the planned `EntityBacklinkReader` service into `EntityRelationshipRepository` query methods + a small in-memory grouping layer. No new service tier.

**New repository methods on `EntityRelationshipRepository`** (returning DTO projections):
- `findIncomingMentionsByEntity(entityId, workspaceId): List<EntityBacklinkRow>` — incoming MENTION edges with source entity's surface_role joined
- `findOutgoingDefinitionsByEntity(entityId, workspaceId): List<EntityBacklinkRow>` — outgoing DEFINES edges (for glossary entities)
- `findInboundDefinitionsByEntityType(entityTypeId, workspaceId): List<EntityBacklinkRow>` — uses Phase 0 partial index
- `findInboundDefinitionsByAttribute(attributeDefinitionId, ownerEntityTypeId, workspaceId): List<EntityBacklinkRow>` — uses Phase 0 partial index + target_parent_id filter

**New domain model `EntityBacklinks`** in `riven.core.models.entity` — bucketed by source entity surface_role (CATALOG / KNOWLEDGE):

```kotlin
data class EntityBacklinks(
    val knowledge: List<EntityBacklinkRow>,   // surface_role = KNOWLEDGE
    val catalog: List<EntityBacklinkRow>,     // surface_role = CATALOG
    // SIGNAL bucket DEFERRED until dtc-ecom merges; do not add a placeholder field
)

data class EntityBacklinkRow(
    val sourceEntityId: UUID,
    val sourceEntityTypeKey: String,
    val sourceIdentifier: String?,
    val sourceExcerpt: String?,
    val relationshipType: String,    // MENTION, DEFINES
    val targetKind: RelationshipTargetKind,
    val createdAt: ZonedDateTime,
)
```

Bucketing happens in-memory via Kotlin `groupBy { it.surfaceRole }` — no service-layer ceremony needed.

**`EntityKnowledgeView` data class** (new, in `riven.core.models.entity`):

```kotlin
data class EntityKnowledgeView(
    val entityId: UUID,
    val workspaceId: UUID,
    val entityTypeId: UUID,
    val schemaVersion: Int,
    val sections: KnowledgeSections,
    val composedAt: ZonedDateTime,
) {
    fun toEmbeddingText(budget: TruncationBudget): TruncationResult
    // toSnapshotJsonb() DEFERRED — no real consumer exists yet (entity_synthesis,
    //   Temporal Synthesis Layer, LLM-wiki are all unlanded). Add when first
    //   consumer arrives. See "Out of scope" above.
}

data class KnowledgeSections(
    val identity: IdentitySection,                              // attrs + identifier
    val typeNarrative: TypeNarrativeSection,                    // entity_type definition (glossary-sourced)
    val attributes: List<AttributeSection>,                     // attribute values + labels (glossary-sourced)
    val catalogBacklinks: List<CatalogBacklinkSection>,         // existing relationship summaries
    val knowledgeBacklinks: List<KnowledgeBacklinkSection>,     // notes/glossary/policies/SOPs MENTION-targeting
    // signalBacklinks DEFERRED — re-introduce when SIGNAL types exist
    val entityMetadata: EntityMetadataSection,                  // SENTIMENT + RELATIONAL + STRUCTURAL metadata
    val clusterSiblings: List<ClusterSiblingSection>,           // identity cluster members
    val relationalReferences: List<RelationalReferenceSection>, // RELATIONAL_REFERENCE attribute resolutions
)

data class TruncationResult(
    val text: String,
    val telemetry: List<SectionTelemetry>,   // emitted to log + (optional) embedding-call metadata
)

data class SectionTelemetry(
    val section: String,
    val keptCount: Int,
    val droppedCount: Int,
    val tokenCount: Int,
)
```

**Effort:** L (CC ~2.5–3 hours). EntityKnowledgeView contract design is the load-bearing part; repo methods are mechanical.

### Phase 3 — `SemanticTextBuilderService` rewrite: produce `EntityKnowledgeView`

- `SemanticTextBuilderService.build()` returns `EntityKnowledgeView` instead of a string
- Composes sections from `EnrichmentContextAssembler` outputs + new `EntityRelationshipRepository` backlink reads
- **Section 5 (KNOWLEDGE):** for each MENTION-pointing entity with surface_role=KNOWLEDGE, populate `[knowledge_type]: [identifier_attr]: [definition or excerpt]`
- **Section 6 (SIGNAL): NOT BUILT in this PR.** Add when dtc-ecom merges and SIGNAL types exist. The KnowledgeSections data class does not carry a SIGNAL field — adding one when SIGNAL ships is a backwards-compatible additive change.
- **Section 2/3:** glossary inbound-DEFINES edges are the primary source for entity_type and attribute narratives. `metadata.definition` IS still readable (the column stays until PR3) but the read path no longer falls back to it — the test suite asserts the glossary path is the primary source so PR3 can drop the column without surprise.
- **`EntityKnowledgeView.toEmbeddingText(budget)` projection:**
  - Protect Sections 1–3 (identity, type narrative, attributes) — never truncated
  - Drop FREETEXT / RELATIONAL_REFERENCE attribute values first when budget exceeded
  - Trim Section 5 (KNOWLEDGE) by recency (most-recent kept), respecting per-section cap
  - **Per-section cap defaults:** `KNOWLEDGE = 3` (conservative; D7 chose this over the original guess of 5). Configurable via `EnrichmentConfigurationProperties`. Tune up post-launch from telemetry, not before.
  - Returns `TruncationResult(text, telemetry)` — telemetry list is one entry per section with `(keptCount, droppedCount, tokenCount)`
- **Telemetry emission:** `EnrichmentAnalysisService` logs the `TruncationResult.telemetry` per embedding call as a structured log line `embedding.section_budget` with fields `entity_id, section, kept_count, dropped_count, token_count`. Format pinned for downstream observability ingestion.
- **`EnrichmentAnalysisService.analyzeSemantics()` updated:**
  - Builds the `EntityKnowledgeView` once via `SemanticTextBuilderService.build()`
  - Calls `EmbeddingProvider` (via `EnrichmentEmbeddingService`) with `toEmbeddingText(budget).text`
  - Persists existing snapshot fields to `entity_connotation` as today (no JSONB-projection persistence in this PR)
  - Returns `EnrichmentContext` derived from `EntityKnowledgeView` — backward-compatible for `EnrichmentActivities` callers (snapshot test gates this)

**Effort:** L (CC ~3.5–4 hours). The architectural pivot, slimmed to its embedding-text-only deliverable.

---

## Phase 4 — REMOVED from this plan (D4)

Reconciliation hooks for knowledge-graph edge changes (glossary edit / note edit / SIGNAL edit → re-enrich the affected entities) are NOT in this plan's scope. They belong to the larger Entity Reconsumption / Schema Reconciliation / Breaking Change Detection project per the Notion doc.

The right shape is mark-and-sweep: glossary/note/signal mutations stamp `entity_types.knowledge_dirty_at = now()` (or a per-entity equivalent), and a periodic worker batches re-enrich enqueues. This avoids the queue-stampede risk of fanning out one job per affected entity on every edit.

`SchemaReconciliationService` stays as-is (catalog/manifest schema-diff invalidation only); the rename suggestion in the original plan's Open Decision #8 is moot — knowledge-graph invalidation is no longer bolted onto it.

---

## PR3 — Definition-column cutover

### Phase 5 — Direct cutover: glossary owns narratives, metadata.definition dropped

**Gate:** Ships only AFTER the entity-type creation lifecycle TODO has shipped (see TODO #1 below). Without that, dropping the column risks empty Section 2/3 narrative for any entity_type whose definition was authored only via metadata.definition.

- DB will be wiped before this lands (no production data, no backfill workflow needed)
- `KnowledgeController` upsert routes for type / attribute / relationship narrative text write directly to glossary entity create/update (DEFINES edges with appropriate target_kind)
- `EntityTypeSemanticMetadataService.upsertMetadata`: rejects payloads carrying `definition` text (compile-time enforcement via separated request DTOs); accepts `classification` and `signalType` only
- `entity_type_semantic_metadata.definition` column dropped from schema
- JPA entity, model, service, repository updated to match
- Read path uses repository inbound-DEFINES queries exclusively (no fallback)

**Effort:** M (CC ~1.5 hours)

---

### Total scope

- **PR1**: ~1.5 hours CC (Phase -1 + Phase 0)
- **PR2**: ~8.5–9.5 hours CC (Phase 1 + Phase 2 + Phase 3)
- **PR3**: ~1.5 hours CC (Phase 5) + dependency on entity-type-creation lifecycle TODO
- **Combined**: ~11.5–12.5 hours CC compressed (human-team scale: 3–5 weeks).
- Reduced from the original plan's 7-phase bundle by removing Phase 4 (out of scope), the JSONB projection (no consumer), and SIGNAL section infrastructure (no source data).

## What this enables (the dream-state delta)

After Phase 3 ships, every KNOWLEDGE entity created (notes, glossary terms, policies, SOPs, incidents) automatically improves the retrieval quality of every entity it MENTIONs. Every SIGNAL entity attached to a customer compounds into that customer's embedding the next enrich cycle. The brain learns from every connection without manual sync.

This is the company-brain's compounding moat made structural. Generic-RAG-on-customer-data competitors don't have this because they don't have a unified-entity substrate to anchor the backlinks.

## The synthesis-layer dimension (reconciled — two different "synthesis" concepts)

After fetching the Notion "Temporal Synthesis Layer" doc, there are TWO distinct things in the architecture both labeled "synthesis":

### Concept A — `entity_synthesis` (company-brain CEO plan)
- Sibling table off `entities`, one row per entity
- Holds compiled-truth (`enriched_text`, aggregations) + metadata (sentiment, relational, structural)
- Append-on-change history in `entity_synthesis_history` (page-version primitive)
- Equivalent to gbrain's `page.compiled_truth` + `page_versions`
- **Upstream:** EnrichmentService output
- **Status:** Synthesis Phase 1 in flight (separate GSD project)

### Concept B — Temporal Synthesis Layer (per Notion doc, NOT YET IMPLEMENTED)
- Time-series cohort/feature engine
- Tables: `entity_state_snapshots` (CDC of entity_attributes over time), `cohort_definitions` (DSL predicates), `cohort_features` (DSL-derived metrics), `cohort_feature_values` (materialized output)
- Owns: cohort lifecycle features (time-to-value, retention, cohort drift)
- **Declared upstream:** Activity Audit Trail CDC on `entity_attributes`
- **Downstream:** analytics + agent runtime (Decision Engine)
- Equivalent to: a time-series feature store, not a per-entity brain page

### The reconciliation question

The Notion doc's `entity_state_snapshots` declares its upstream as raw audit-log CDC of `entity_attributes` — a thin slice (only attribute values over time, not the compounded backlink graph). After this plan ships, that slice will look painfully shallow next to what EnrichmentService produces. Cohort predicates like "contacts whose entity metadata shows churn risk in Q1" or "customers mentioned in 3+ KNOWLEDGE entities last month" cannot be expressed against attribute-only snapshots.

The cleanest move: **EnrichmentService's output becomes the canonical per-entity-per-timestep snapshot artifact**, and BOTH synthesis concepts read from it. **PR2 ships the structured `EntityKnowledgeView` and the embedding-text projection. The JSONB projection is deferred** until the first real consumer arrives (entity_synthesis Phase 1 / Temporal Synthesis Layer). At that point, adding `toSnapshotJsonb()` is an additive change to `EntityKnowledgeView` — the architecture below is forward-compatible:

```
                ┌───────────── EnrichmentService ──────────────────┐
                │  composes per-entity multi-surface backlink view │
                │  → structured EntityKnowledgeView (PR2)          │
                │  → text projection for embedding model (PR2)     │
                │  → JSONB projection for time-series (DEFERRED)   │
                └────────────────────┬─────────────────────────────┘
                                     │
        ┌────────────────────────────┼─────────────────────────────┐
        ▼                            ▼                             ▼
 ┌──────────────┐  ┌──────────────────────────┐  ┌────────────────────────┐
 │ Embedding    │  │ entity_synthesis (A)     │  │ Temporal Synthesis (B) │
 │ generator    │  │ + entity_synthesis_      │  │ entity_state_snapshots │
 │              │  │ history                  │  │                        │
 │ pgvector row │  │ compiled-truth row +     │  │ CDC time-series of     │
 │ for retrieval│  │ append-on-change history │  │ EntityKnowledgeView,   │
 │              │  │ (gbrain page primitive)  │  │ readable by cohort DSL │
 └──────────────┘  └──────────────────────────┘  └────────────────────────┘
                                                          │
                                                          ▼
                                                  ┌────────────────┐
                                                  │ cohort_features│
                                                  │ + analytics    │
                                                  │ + agent runtime│
                                                  └────────────────┘
```

This changes the upstream story for the Notion doc's Temporal Synthesis Layer: instead of "audit-log CDC of entity_attributes" (thin), it becomes "snapshot of EntityKnowledgeView at each timestep" (rich). The Notion doc's v0 contract gets a quiet upgrade — same shape, richer content, no DSL grammar changes needed.

This is the architectural lever the user named: EnrichmentService becomes the backbone. Its output contract is the substrate that the embedding model, gbrain compiled-truth, and the cohort feature engine ALL build on.

Choice surfaced and ratified.

## Decision log

### CEO review (prior session) — D1–D10

- **D1:** Approach B (hybrid layered split) — split metadata-vs-glossary along structural-traits-vs-narrative seam
- **D2:** SCOPE EXPANSION (revised from initial SELECTIVE EXPANSION when user reframed scope)
- **D3:** target_entity_id rename → target_id + target_parent_id + CHECK + FK to entity_types(id) ON DELETE RESTRICT
- **D4:** RELATIONSHIP added to RelationshipTargetKind enum + CHECK constraint update
- **D5:** EnrichmentService decomposition (later reshaped by eng-review D2 below)
- **D6:** Plan reshape ratified — phases collapsed since DB will be wiped pre-deploy
- **D7:** worktree-2 already merged into this branch; no sequencing coordination needed
- **D8:** Auto-link parser assumed to land soon as separate plan; Phase 3 designed against post-auto-link state
- **D9:** Structured EntityKnowledgeView artifact ratified
- **D10:** SIGNAL section ready-on-arrival (later reshaped by eng-review D1 below — SIGNAL infra deferred entirely)

### Eng review (this session) — D1–D7

- **Eng-D1:** Plan split into **3 PRs** (PR1 schema, PR2 feature, PR3 cutover). Reduces blast radius and applies YAGNI to speculative artifact pieces.
- **Eng-D2:** Phase 1 reshaped from "single ContextProvider interface + impl" → **four services split by lifecycle stage**: `EnrichmentQueueService`, `EnrichmentContextAssembler`, `EnrichmentAnalysisService`, `EnrichmentEmbeddingService`. Real seams matching the four named responsibilities; no single-impl-interface ceremony.
- **Eng-D3:** Phase 2 reshaped — **drop the `EntityBacklinkReader` service**. Add `@Query` methods to `EntityRelationshipRepository` returning DTO projections; bucketing via Kotlin `groupBy`. Existing layering pattern preserved (services orchestrate, repos query).
- **Eng-D4:** Phase 4 **removed from this plan**. Knowledge-graph re-enrichment belongs to the Entity Reconsumption / Schema Reconciliation / Breaking Change Detection project (per Notion doc). `SchemaReconciliationService` stays for catalog/manifest schema-diff invalidation only.
- **Eng-D5:** `target_parent_id` semantics corrected — **uniformly the owning entity_type for sub-reference target_kinds (ATTRIBUTE + RELATIONSHIP)**, not ATTRIBUTE only. CHECK constraint widened.
- **Eng-D6:** Phase 5 cutover safety — DB-wipe makes parity-comparison moot. Right gate is **creation-side enforcement**: entity-type creation lifecycle auto-creates inbound DEFINES glossary entities. Captured as TODO #1.
- **Eng-D7:** Phase 3 truncation caps — ship with **per-section telemetry** `(kept_count, dropped_count, token_count)`. Conservative default `KNOWLEDGE = 3` (was 5). SIGNAL deferred. Tune from data post-launch.

## Critical files (per PR)

### PR1 — Schema cleanup

**Phase -1 (rename):**
- `src/main/kotlin/riven/core/models/connotation/ConnotationMetadataSnapshot.kt` → `EntityMetadataSnapshot.kt`
- `src/main/kotlin/riven/core/models/connotation/ConnotationMetadata.kt` → `EntityMetadata.kt`
- `src/main/kotlin/riven/core/service/connotation/ConnotationAdminService.kt` (method rename)
- `src/main/kotlin/riven/core/repository/workflow/ExecutionQueueRepository.kt` (method rename)
- All test files referencing the renamed types

**Phase 0 (schema):**
- `db/schema/01_tables/entities.sql` — `entity_relationships` column rename + new `target_parent_id` column + CHECK constraint + DDL comment
- `db/schema/02_indexes/entity_indexes.sql` — partial index for reverse-DEFINES
- `src/main/kotlin/riven/core/entity/entity/EntityRelationshipEntity.kt` — JPA mapping for rename + new column
- `src/main/kotlin/riven/core/models/entity/EntityRelationship.kt` — domain model
- `src/main/kotlin/riven/core/enums/entity/RelationshipTargetKind.kt` — add `RELATIONSHIP` value
- `src/main/kotlin/riven/core/repository/entity/EntityRelationshipRepository.kt` — JPQL rename across all queries
- `src/main/kotlin/riven/core/service/knowledge/KnowledgeIngestionInput.kt`
- `src/main/kotlin/riven/core/service/knowledge/AbstractKnowledgeEntityIngestionService.kt`
- `src/main/kotlin/riven/core/service/knowledge/GlossaryEntityIngestionService.kt` — populate `target_parent_id` for ATTRIBUTE rows; populate for new RELATIONSHIP rows
- `src/main/kotlin/riven/core/service/knowledge/GlossaryEntityProjector.kt`
- `src/main/kotlin/riven/core/service/entity/EntityService.kt` — `replaceRelationshipsInternal` updates

### PR2 — Knowledge backlinks in embeddings

**Phase 1 (4-service split):**
- DELETE `src/main/kotlin/riven/core/service/enrichment/EnrichmentService.kt`
- NEW `src/main/kotlin/riven/core/service/enrichment/EnrichmentQueueService.kt`
- NEW `src/main/kotlin/riven/core/service/enrichment/EnrichmentContextAssembler.kt`
- NEW `src/main/kotlin/riven/core/service/enrichment/EnrichmentAnalysisService.kt`
- NEW `src/main/kotlin/riven/core/service/enrichment/EnrichmentEmbeddingService.kt`
- `src/main/kotlin/riven/core/service/workflow/enrichment/EnrichmentActivitiesImpl.kt` — re-wire callers to the four new services

**Phase 2 (backlink reads + EntityKnowledgeView):**
- `src/main/kotlin/riven/core/repository/entity/EntityRelationshipRepository.kt` — add four `@Query` methods returning DTO projections
- NEW `src/main/kotlin/riven/core/models/entity/EntityBacklinks.kt` — data class with CATALOG and KNOWLEDGE buckets (no SIGNAL field; additive when SIGNAL types ship)
- NEW `src/main/kotlin/riven/core/models/entity/EntityBacklinkRow.kt` — DTO for repository projections
- NEW `src/main/kotlin/riven/core/models/entity/EntityKnowledgeView.kt` — view, sections, telemetry types

**Phase 3 (SemanticTextBuilder rewrite):**
- `src/main/kotlin/riven/core/service/enrichment/SemanticTextBuilderService.kt` — return `EntityKnowledgeView`; build all sections except deferred SIGNAL
- `src/main/kotlin/riven/core/configuration/properties/EnrichmentConfigurationProperties.kt` — add `knowledgeBacklinkCap` (default 3)
- `src/main/kotlin/riven/core/service/enrichment/EnrichmentAnalysisService.kt` — emit `embedding.section_budget` structured log per section

### PR3 — Definition-column cutover

**Phase 5:**
- `db/schema/01_tables/entities.sql` — drop `definition` column on `entity_type_semantic_metadata`
- `src/main/kotlin/riven/core/entity/entity/EntityTypeSemanticMetadataEntity.kt` — remove `definition` field
- `src/main/kotlin/riven/core/models/entity/EntityTypeSemanticMetadata.kt` — remove `definition` field
- `src/main/kotlin/riven/core/service/entity/EntityTypeSemanticMetadataService.kt` — request DTO no longer carries `definition`; reject mode
- `src/main/kotlin/riven/core/controller/knowledge/KnowledgeController.kt` — narrative upserts route to glossary entity create/update
- All call-sites that read `metadata.definition` — switch to inbound-DEFINES repository read

### Docs (per PR, into `../docs/` repo)
- `docs/architecture-changelog.md` — entry per PR with new components and structural changes
- `docs/architecture-suggestions.md` — flag vault update for the unified backlink-aggregator pattern; mark item #1 (cross-domain coupling) resolved after PR2 lands

## Verification

End-to-end (every PR):
1. `./gradlew test` — full suite passes
2. `./gradlew build` — compilation gate

### PR1

**Phase -1 (rename):**
- `grep -r "ConnotationMetadataSnapshot\|ConnotationMetadata\b\|reanalyzeAxisWhereVersionMismatch\|enqueueByAxisVersionMismatch" src/` returns zero hits post-rename (excluding renamed `entity_connotation`/`connotation_metadata` which are scoped out)
- All existing tests under `src/test/` compile + pass without behavioral change
- KDoc/log-message audit: no remaining references to "envelope" or "axis"/"axes" in renamed-type touchpoints

**Phase 0 (schema):**
- `grep -r "targetEntityId\|target_entity_id" src/` returns zero hits post-rename
- New `EntityRelationshipRepositoryTest` cases:
  - ATTRIBUTE row with `target_parent_id NULL` → CHECK violation rejects insert
  - RELATIONSHIP row with `target_parent_id NULL` → CHECK violation rejects insert
  - ENTITY row with `target_parent_id` set → CHECK violation rejects insert
  - ENTITY_TYPE row with `target_parent_id` set → CHECK violation rejects insert
  - FK RESTRICT: delete an `entity_types` row referenced by an active `target_parent_id` is blocked
- `GlossaryEntityIngestionService` integration test: ingestion with `attributeRefs` populates `target_parent_id` correctly; ingestion with `relationshipRefs` populates `target_parent_id` for RELATIONSHIP rows
- `EXPLAIN ANALYZE` in IT confirms reverse-DEFINES queries use the partial index (`Index Scan`, not `Seq Scan`)

### PR2

**Phase 1 (4-service split):**
- **REGRESSION TEST (mandatory):** snapshot `EnrichmentContext` JSON for a fixture entity BEFORE refactor (commit as test resource); assert byte-equality AFTER refactor. Pure-refactor contract — any drift is a bug.
- Each new service has its own focused unit test loading only that service + security config + `@MockitoBean` deps:
  - `EnrichmentQueueServiceTest`
  - `EnrichmentContextAssemblerTest`
  - `EnrichmentAnalysisServiceTest`
  - `EnrichmentEmbeddingServiceTest`
- Constructor-parameter-count assertion in each new service test: ≤ 7 deps (the heaviest is ContextAssembler)
- `EnrichmentActivitiesImpl` integration test: existing pipeline still produces an embedding for a fixture entity end-to-end (Temporal worker harness)

**Phase 2 (backlink reads + EntityKnowledgeView):**
- `EntityRelationshipRepositoryTest` new cases: each of the four new query methods returns expected rows for a fixture covering CATALOG-source MENTION, KNOWLEDGE-source MENTION, ENTITY_TYPE-target DEFINES, ATTRIBUTE-target DEFINES
- `EXPLAIN ANALYZE` in IT confirms inbound-DEFINES queries hit the partial index from Phase 0
- Cross-workspace isolation test: glossary in workspace A defining an entity_type in workspace B does not leak into workspace B's reads
- Soft-delete filter test: glossary entities with `deleted=true` are excluded automatically (relies on `@SQLRestriction` baseline)
- `EntityKnowledgeViewTest`: contract test asserting current sections present (identity, typeNarrative, attributes, catalogBacklinks, knowledgeBacklinks, entityMetadata, clusterSiblings, relationalReferences); `toEmbeddingText(budget)` truncation order verified (Sections 1-3 protected, FREETEXT/RELATIONAL_REFERENCE drop first, KNOWLEDGE trimmed by recency); telemetry list returned matches kept/dropped/token counts

**Phase 3 (SemanticTextBuilderService rewrite):**
- Glossary parity IT: when a glossary entity has DEFINES edges to entity_type=customer's name attribute, the resulting `EntityKnowledgeView.attributes[name]` narrative is sourced from the glossary text, not `metadata.definition` (asserts the read path is glossary-primary so PR3 column drop is safe)
- KNOWLEDGE backlink IT: create a note with a MENTION edge to a customer; enrich the customer; assert Section 5 includes the note's identifier + excerpt; assert deterministic recency ordering
- KnowledgeSections **does not contain a SIGNAL field** until SIGNAL types ship — compile-time guarantee
- Truncation telemetry IT: synthesize an over-budget entity with 10 KNOWLEDGE mentions; assert telemetry log line `embedding.section_budget` is emitted with `kept_count=3, dropped_count=7, token_count=…` for the KNOWLEDGE section; assert Sections 1-3 are present in full
- `EnrichmentContext` backward-compat: snapshot test of `EnrichmentAnalysisService.analyzeSemantics()` output structure matches what `EnrichmentActivities` callers expect

### PR3

**Phase 5 (cutover):**
- **Pre-flight gate:** TODO #1 (entity-type creation lifecycle auto-creates inbound DEFINES glossary entities) is shipped and verified before PR3 opens
- `KnowledgeController` upsert with narrative text writes to glossary entity (DEFINES edge), not `entity_type_semantic_metadata.definition`
- `EntityTypeSemanticMetadataService.upsertMetadata` request DTO no longer carries `definition` field (compile-time enforcement); request including `definition` is a deserialization error at controller boundary; service-level test confirms classification/signalType-only payload succeeds
- `db/schema/01_tables/entities.sql` — `entity_type_semantic_metadata` table no longer declares `definition` column
- All existing tests pass — no test depends on `metadata.definition` since the read path was glossary-primary as of PR2
- Architectural-suggestion follow-ups:
  - `docs/architecture-suggestions.md` item #1 (cross-domain coupling) marked resolved
  - `docs/architecture-changelog.md` entry for the cutover

## Success Criteria

### After PR1 ships
- All renames applied; no remaining references to `ConnotationMetadataSnapshot` / `ConnotationMetadata` / `axis*` method names; `entity_connotation` table + `connotation_metadata` JSONB column intentionally untouched
- `entity_relationships` table is polymorphism-clean: `target_id` + `target_parent_id` + `target_kind ∈ {ENTITY, ENTITY_TYPE, ATTRIBUTE, RELATIONSHIP}` with CHECK constraint on conditional nullability + FK to entity_types ON DELETE RESTRICT
- Reverse-DEFINES queries hit the partial index (single-digit ms p99 in IT)
- All existing tests green; no behavioral change

### After PR2 ships
- `EnrichmentService.kt` no longer exists; four services in `riven.core.service.enrichment.*` each ≤ ~7 deps with clean responsibility boundaries
- Pure-refactor contract honored: `EnrichmentContext` output for fixture entity is byte-identical to pre-refactor
- `EntityKnowledgeView` is the canonical per-entity knowledge artifact (text-projection only — JSONB projection deferred until first real consumer arrives)
- Embeddings include compounded knowledge: every customer's embedding sees its CATALOG backlinks (orders, support tickets) + KNOWLEDGE backlinks (notes mentioning, glossary terms defining type)
- Section 2/3 narrative is glossary-sourced as the primary path; `metadata.definition` is still readable but not the read path
- Per-section telemetry emitted on every embedding call: `embedding.section_budget` log line with `entity_id, section, kept_count, dropped_count, token_count`
- Conservative cap `KNOWLEDGE = 3` shipped as default; configurable; tunable from telemetry post-launch
- Architecture-suggestions.md item #1 (cross-domain coupling) marked resolved

### After PR3 ships (gated on TODO #1)
- Glossary owns all narrative description (entity-type, attribute, relationship); `entity_type_semantic_metadata` is structural-traits-only (classification, signalType, tags)
- `entity_type_semantic_metadata.definition` column dropped from schema
- `KnowledgeController` narrative upserts route exclusively to glossary entity create/update
- All entity_types created via the catalog/manifest path automatically have inbound DEFINES glossary entities — no entity_type ships with empty Section 2/3 narrative

### Out-of-scope for this plan (tracked elsewhere)
- Reconciliation hooks for knowledge-graph edges (glossary/note/signal edit → re-enrich) — owned by Entity Reconsumption project
- `EntityKnowledgeView.toSnapshotJsonb()` and downstream `entity_synthesis` / Temporal Synthesis Layer / LLM-wiki integrations — owned by their respective projects
- SIGNAL section infrastructure — additive plan when dtc-ecom merges and SIGNAL types exist
- Iron Law auto-link parser — separate plan

## Open Decisions / TODOs (carried beyond this plan)

These surfaced during review and warrant tracking. Items 1, 2, 3 are gated by this plan or its PR3.

1. **Entity-type creation lifecycle: auto-create inbound DEFINES glossary entities.** **GATES PR3.** When a new entity_type is created (catalog/manifest path), automatically create:
   - One glossary entity DEFINES-targeting the entity_type (Section 2 narrative source)
   - One glossary entity DEFINES-targeting each `classification=NAME` attribute (Section 3 narrative source for the identifier)
   Without this, dropping `metadata.definition` in PR3 risks empty Section 2/3 for entity_types whose narrative was never glossary-authored. Lives in catalog/manifest entity-type-creation path; not in this plan but blocks its final PR.

2. **Post-launch retrieval P@k measurement.** Conservative cap `KNOWLEDGE = 3` is a starting point. Once production traffic exists, sample retrieval P@5 with cap at 3 / 5 / 10 to calibrate. Phase 3's `embedding.section_budget` telemetry is the input. Tune `EnrichmentConfigurationProperties.knowledgeBacklinkCap` from data, not folklore.

3. **`EntityKnowledgeView.schemaVersion` bump-and-migrate policy.** The view ships with `schemaVersion: Int` but no policy for when downstream consumers depend on a specific schema. Define when first real consumer (entity_synthesis Phase 1 / Temporal Synthesis Layer) starts persisting the JSONB projection.

4. **Iron Law auto-link parser** (company-brain Step 5) — assumed to land soon. Without it, KNOWLEDGE backlinks are limited to explicit-attachment + glossary-payload-supplied MENTION edges. Phase 3 design works in either world; pre-auto-link production will see a muted version of the compounding effect.

5. **`entity_synthesis` integration** (synthesis project Phase 1) — after PR2 ships, the synthesis project's Phase 1 should consume `EntityKnowledgeView` directly rather than re-composing from sources. JSONB projection becomes additive when it lands.

6. **Temporal Synthesis Layer upgrade** (Notion doc, NOT YET IMPLEMENTED) — when built, its `entity_state_snapshots` should consume `EntityKnowledgeView` rather than CDC of `entity_attributes`. Notion doc's v0 contract should be updated to reflect this richer upstream once the implementation begins.

7. **dtc-ecom branch SIGNAL types** — when dtc-ecom merges and SIGNAL entity types exist, add `signalBacklinks: List<SignalBacklinkSection>` to `KnowledgeSections` + a corresponding repository query + Section 6 truncation rule with conservative cap (suggested: 2). Backwards-compatible additive plan.

8. **LLM-wiki view** — speculative future consumer. If/when built, should consume `EntityKnowledgeView` rather than re-composing.

9. **Knowledge-graph re-enrichment** (formerly Phase 4) — owned by the Entity Reconsumption / Schema Reconciliation / Breaking Change Detection project (per Notion doc). Mark-and-sweep dirty-flag pattern + periodic worker is the right shape. Coordinate when that project plans its phases — `EntityKnowledgeView` is the substrate it should re-compute.

## GSTACK REVIEW REPORT

| Review | Trigger | Why | Runs | Status | Findings |
|--------|---------|-----|------|--------|----------|
| CEO Review | `/plan-ceo-review` | Scope & strategy | 1 | clean | Mode SCOPE EXPANSION; 3 cherry-picks accepted (target_id rename + target_parent_id, RELATIONSHIP target_kind, EnrichmentContextProvider abstraction); D1–D10 resolved |
| Codex Review | `/codex review` | Independent 2nd opinion | 0 | — | not run |
| Eng Review | `/plan-eng-review` | Architecture & tests (required) | 1 | issues_open | Plan REDUCED — split into 3 PRs (D1); 4-service split replaces ContextProvider abstraction (D2); EntityBacklinkReader collapsed into repo methods (D3); Phase 4 deferred to Entity Reconsumption project (D4); target_parent_id semantics widened to ATTRIBUTE+RELATIONSHIP (D5); cutover-safety solved via entity-type-creation lifecycle TODO (D6); per-section caps ship with telemetry, conservative defaults (D7) |
| Design Review | `/plan-design-review` | UI/UX gaps | 0 | n/a | Backend-only |
| DX Review | `/plan-devex-review` | Developer experience gaps | 0 | n/a | Backend-only |

### Plan amendments accepted in eng review

**PR split (D1):**
- **PR1** = Phase -1 (rename) + Phase 0 (schema cleanup). Lands first, verified independently.
- **PR2** = Phase 1 (4-service split) + Phase 2 (backlink reads) + Phase 3 (SemanticTextBuilder rewrite, embedding-text projection only). JSONB projection deferred. SIGNAL section infra deferred until dtc-ecom merges.
- **PR3** = Phase 5 cutover. Ships only after the entity-type-creation-lifecycle TODO ships (auto-creates inbound DEFINES glossary entities so dropping `metadata.definition` doesn't leave entity_types narrative-empty).

**Phase 1 reshape (D2):**
- Replace single `EnrichmentContextProvider` interface + `EntityEnrichmentSnapshotService` impl with **four services split by lifecycle stage**:
  - `EnrichmentQueueService` — enqueueAndProcess, enqueueByEntityType, queue/dispatch concerns.
  - `EnrichmentContextAssembler` — read-side orchestration: entity, type, metadata, relationships, clusters.
  - `EnrichmentAnalysisService` — analyzeSemantics: snapshot assembly + persistence to `entity_connotation`.
  - `EnrichmentEmbeddingService` — storeEmbedding, embedding repo, provider.
- Each service ≤ 6 deps. No single-impl interface ceremony.

**Phase 2 reshape (D3):**
- Drop new `EntityBacklinkReader` service. Add three `@Query` methods to `EntityRelationshipRepository` (`findIncomingMentionsByEntity`, `findOutgoingDefinitionsByEntity`, `findInbound[ByEntityType|ByAttribute]`) returning DTO projections. Surface_role bucketing via in-memory Kotlin `groupBy`.

**Phase 4 deferred (D4):**
- Reconciliation hooks for knowledge-graph edits (glossary/note/signal edit → re-enrich) are NOT in scope for this plan. They belong to the larger Entity Reconsumption / Schema Reconciliation / Breaking Change Detection project (per Notion doc). Right shape is mark-and-sweep dirty-flag + periodic worker; design lives in that project.
- The plan's existing Open Decision #8 (rename `SchemaReconciliationService.invalidateConnotationSnapshots`) is moot — the service stays as-is because knowledge-graph invalidation is no longer bolted onto it.

**Phase 0 schema correction (D5):**
- `target_parent_id` is uniformly the **owning entity_type** for sub-reference target_kinds (both ATTRIBUTE and RELATIONSHIP) — the plan's Phase 0 text said ATTRIBUTE only.
- CHECK constraint widens: `NOT NULL when target_kind IN ('ATTRIBUTE', 'RELATIONSHIP')`, `NULL when target_kind IN ('ENTITY', 'ENTITY_TYPE')`. FK to entity_types ON DELETE RESTRICT stays correct.

**Phase 5 cutover gate (D6):**
- DB-wipe pre-deploy makes parity-comparison vs existing rows moot. The right gate is **creation-side enforcement**: when a new entity_type is created via the catalog/manifest path, the system auto-creates the inbound DEFINES glossary entities (one for the entity_type narrative, one per classification=NAME attribute). Captured as TODO.

**Phase 3 telemetry (D7):**
- Per-entity per-section truncation telemetry: log `(entity_id, section, kept_count, dropped_count, token_count)` on every embedding call.
- Conservative defaults: KNOWLEDGE max **3** (was 5). SIGNAL deferred. Tune post-launch from telemetry.

### Tests / failure modes

- **REGRESSION (mandatory):** PR2 Phase 1 4-service split — snapshot `EnrichmentContext` for fixture entity before refactor; assert byte-equality after.
- Schema CHECK constraint coverage: every (target_kind, target_parent_id-presence) combination tested for accept/reject.
- Reverse-DEFINES partial-index usage via EXPLAIN ANALYZE in IT.
- Truncation-budget telemetry asserted in unit tests (kept/dropped counts predictable for synthetic over-budget fixture).
- Critical gap remaining: PR3 cutover — without the entity-type-creation TODO landed, retrieval Section 2/3 silently regresses for any entity_type whose narrative wasn't manually glossary-authored. Gate accordingly.

### TODOs (carry into TODOS.md)

1. **Entity-type creation lifecycle auto-creates inbound DEFINES glossary entities.** Blocks PR3 cutover safety. Belongs in catalog/manifest entity-type-creation path.
2. **Post-launch retrieval P@k measurement** to calibrate KNOWLEDGE cap from initial 3.
3. **EntityKnowledgeView schema-versioning policy** — only relevant once JSONB projection reactivates with a real consumer.

- **UNRESOLVED:** 0 (D1-D7 in eng review all resolved)
- **VERDICT:** Eng review complete — plan amended. Ready to split into PR1/PR2/PR3 phase plans. Eng-review status `issues_open` because plan now requires re-spawning per-PR plans before /gsd:execute-phase. CEO + Eng cleared once amendments are reflected in plan body.

## Worktree parallelization strategy

Lanes after the 3-PR split:

| Step | Modules touched | Depends on |
|------|----------------|------------|
| PR1 — Phase -1 rename | `models/connotation/`, every test referencing renamed types | — |
| PR1 — Phase 0 schema | `db/schema/01_tables/`, `db/schema/02_indexes/`, `entity/entity/EntityRelationshipEntity.kt`, all repos/services using target_entity_id | — (parallel with rename) |
| PR2 — Phase 1 4-service split | `service/enrichment/` (split file), `service/workflow/enrichment/` (callers) | PR1 schema |
| PR2 — Phase 2 repo methods | `repository/entity/EntityRelationshipRepository.kt`, `models/entity/EntityBacklinks.kt` | PR1 schema |
| PR2 — Phase 3 SemanticTextBuilder rewrite | `service/enrichment/SemanticTextBuilderService.kt`, `models/entity/EntityKnowledgeView.kt` (text-projection only), `configuration/properties/EnrichmentConfigurationProperties.kt` | Phase 1 + Phase 2 |
| PR3 — Phase 5 cutover | `service/entity/EntityTypeSemanticMetadataService.kt`, `controller/knowledge/KnowledgeController.kt`, `db/schema/01_tables/` | PR2 + entity-type-creation TODO |

**Within PR1**: Phase -1 rename and Phase 0 schema can land in parallel worktrees (Phase -1 touches connotation models + tests; Phase 0 touches entity_relationships + repos). Merge order: rename first (smaller), schema second.
**Within PR2**: Phase 1 service split and Phase 2 repo methods are independent — parallel. Phase 3 depends on both. Lane A: Phase 1. Lane B: Phase 2. Sync, then Phase 3 sequentially.
**Conflict flag**: PR2 Phase 1 (split EnrichmentService.kt) and PR2 Phase 3 (rewrite SemanticTextBuilderService.kt) both touch the same package — keep them in the same PR but as sequential commits to avoid merge conflict against parallel feature branches.

## Outside Voice (deferred)

Not run in this session. If desired, user can launch `/codex review` against the amended plan once the body is updated to reflect D1–D7 resolutions; cross-model agreement on the 3-PR split + 4-service refactor would strengthen confidence before spawning per-PR phase plans.
