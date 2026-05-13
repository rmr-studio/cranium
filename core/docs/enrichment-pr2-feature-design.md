# PR2 — Knowledge Backlinks in Embeddings: Feature Design

Companion to [`enrichment-knowledge-plane-assembly-plan.md`](./enrichment-knowledge-plane-assembly-plan.md). Resolves PR2 Phase 1-3 ambiguities surfaced during interactive review (see Decision Log at end).

## Context & goal

PR1 (schema rename + `target_parent_id`) is the precondition. PR2 ships:

1. **Phase 1** — Decompose `EnrichmentService` (19 deps) into four lifecycle services.
2. **Phase 2** — Add the read path for KNOWLEDGE backlinks; introduce `EntityKnowledgeView` as the canonical per-entity knowledge artifact.
3. **Phase 3** — Rewrite `SemanticTextBuilderService` to produce the view; add the embedding-text projector with truncation rules + telemetry.

Outcome: every entity's embedding text incorporates KNOWLEDGE backlinks (notes that mention it, glossary terms that define its type/attributes), and the view is shaped to fan out to future consumers (synthesis layer, JSONB projection) without re-architecture.

**Key reframing from the original plan:** The plan proposed four new repository methods to read backlinks. `EntityRelationshipService.findRelatedEntities` already returns forward + inverse links with the KNOWLEDGE-inverse admission predicate (lines 245-264 of `EntityRelationshipService.kt`). PR2 reuses that path and adds only the single query the existing predicate cannot reach (type/attribute-targeted DEFINES edges).

---

## Architecture decisions (locked)

| #       | Decision                                                                                                                               | Rationale                                                            |
| ------- | -------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------- |
| 1A      | Reuse `findRelatedEntities` + 1 new query for type/attribute DEFINES                                                                   | DRY; KNOWLEDGE-inverse predicate stays in one place                  |
| 2A      | Ship `EnrichmentContextAssembler` at ~10 deps, add constructor-count assertion (≤12)                                                   | Premature to split further before observing read clusters            |
| 3A      | `EntityKnowledgeViewProjector` is a separate service; view stays a pure data class                                                     | Future projections (JSONB, LLM-wiki) become sibling services         |
| 4-i.A   | View transported in-memory between Temporal activities                                                                                 | No premature persistence; size guard log catches drift               |
| 4-ii.A  | Each consumer = its own Temporal activity in `EnrichmentWorkflow`                                                                      | Native fan-out; per-activity retry; visible control flow             |
| 4-iii.A | `EntityKnowledgeView` is the canonical artifact; all projections derive from it                                                        | Single contract                                                      |
| 5A      | Drop `EntityBacklinks` intermediate DTO; assembler buckets `EntityLink`s directly into view sections                                   | One transformation, no orphan shapes                                 |
| 6A      | Eight distinct section data classes; no sealed hierarchy                                                                               | Type-safe; uniformity here is shallow                                |
| 7A      | Projector uses sequential phase methods                                                                                                | Each phase unit-testable; matches CLAUDE.md function-length guidance |
| 8A      | Telemetry ships with `keptCount`/`droppedCount`/`charCount`; `tokenCount` deferred                                                     | Decoupled from embedding provider; honest naming                     |
| 9A      | Two regression snapshots: `EnrichmentContext` byte-identity for Phase 1 commit; `EntityKnowledgeView` structural snapshot post-Phase-3 | Honest framing of contract change                                    |
| 10A     | Single parameterized IT covering 6-case backlink matrix                                                                                | Tests the integration point Issue 1A bets on                         |
| 11A     | Three-tier truncation tests: per-phase + invariant + e2e                                                                               | Catches phase bugs, ordering bugs, realistic regressions             |
| 12A     | Workflow test asserts consumer independence; scaffolding accepts list of consumer activities                                           | Adding Synthesis later doesn't rewrite tests                         |
| 13A     | Batch fetch KNOWLEDGE excerpts via existing `EntityAttributeService.getAttributesForEntities`                                          | Bounded query count; uses existing infrastructure                    |
| 14A     | Truncate at assembler boundary, not projector; size guard log at 1MB                                                                   | View entering Temporal is bounded                                    |
| 15A     | Trust partial index + EXPLAIN gate in IT; re-check at production scale                                                                 | Don't pre-optimize without data                                      |
| 16A     | Per-entity assembler calls + timing telemetry; defer batch variants                                                                    | Telemetry tells us when batching matters                             |

---

## Service decomposition (Phase 1)

`EnrichmentService.kt` (19 deps) is deleted. Replaced by four services in `cranium.core.service.enrichment.*`:

### `EnrichmentQueueService`

**Responsibility:** queue lifecycle + Temporal dispatch.
**Deps (~5):** `ExecutionQueueRepository`, `WorkflowClient`, `EnrichmentConfigurationProperties`, `EnrichmentAnalysisService`, `KLogger`.
**Public methods:** `enqueueAndProcess(entityId)`, `enqueueByEntityType(typeId)`.

### `EnrichmentContextAssembler`

**Responsibility:** read-side aggregation; produce `EntityKnowledgeView`.
**Deps (~10):** `EntityRepository`, `EntityTypeRepository`, `EntityTypeSemanticMetadataRepository`, `EntityAttributeService`, `EntityRelationshipService` (note: service, not repo — leverages `findRelatedEntities`), `RelationshipDefinitionRepository`, `IdentityClusterMemberRepository`, `RelationshipTargetRuleRepository`, `WorkspaceService`, `KLogger`.
**Public method:** `assemble(entityId, workspaceId): EntityKnowledgeView`.
**Test invariant:** constructor parameter count ≤ 12.

### `EnrichmentAnalysisService`

**Responsibility:** claim queue item, build view via assembler, persist `EntityMetadataSnapshot` to `entity_connotation`, return view.
**Deps (~6):** `EnrichmentContextAssembler`, `ConnotationAnalysisService`, `EntityConnotationRepository`, `ObjectMapper`, `ExecutionQueueRepository`, `KLogger`.
**Public method:** `analyzeSemantics(queueItemId): EntityKnowledgeView`.

### `EnrichmentEmbeddingService`

**Responsibility:** project view to text via projector, embed, upsert vector, complete queue item.
**Deps (~5):** `EntityKnowledgeViewProjector`, `EmbeddingProvider`, `EntityEmbeddingRepository`, `ExecutionQueueRepository`, `KLogger`.
**Public method:** `embedAndStore(view, queueItemId)`.

`SemanticTextBuilderService` is removed in Phase 3 — the assembler builds the view; the projector projects it. No third party.

---

## Data contracts (Phase 2)

### `EntityKnowledgeView` — canonical artifact

```kotlin
data class EntityKnowledgeView(
    val entityId: UUID,
    val workspaceId: UUID,
    val entityTypeId: UUID,
    val schemaVersion: Int,
    val sections: KnowledgeSections,
    val composedAt: ZonedDateTime,
)

data class KnowledgeSections(
    val identity: IdentitySection,
    val typeNarrative: TypeNarrativeSection,
    val attributes: List<AttributeSection>,
    val catalogBacklinks: List<CatalogBacklinkSection>,
    val knowledgeBacklinks: List<KnowledgeBacklinkSection>,
    val entityMetadata: EntityMetadataSection,
    val clusterSiblings: List<ClusterSiblingSection>,
    val relationalReferences: List<RelationalReferenceSection>,
    // signalBacklinks intentionally absent until SIGNAL types ship
)
```

**Section data classes:** distinct types per section (Decision 6A). Each carries section-specific fields (e.g. `CatalogBacklinkSection.relationshipType + latestActivity`; `ClusterSiblingSection.clusterRole + siblingIds`). No sealed hierarchy.

**No `EntityBacklinks` intermediate type** (Decision 5A). Assembler buckets `List<EntityLink>` from `findRelatedEntities` straight into `catalogBacklinks` and `knowledgeBacklinks` lists.

**Truncation applied at assembler** (Decision 14A): `KNOWLEDGE` cap from `EnrichmentConfigurationProperties.knowledgeBacklinkCap` (default 3) applied during section construction. View entering Temporal is bounded.

### `EntityKnowledgeViewProjector` — separate service

```kotlin
@Service
class EntityKnowledgeViewProjector(
    private val config: EnrichmentConfigurationProperties,
    private val logger: KLogger,
) {
    fun toEmbeddingText(view: EntityKnowledgeView, budget: TruncationBudget): TruncationResult
}

data class TruncationResult(
    val text: String,
    val telemetry: List<SectionTelemetry>,
)

data class SectionTelemetry(
    val section: String,
    val keptCount: Int,
    val droppedCount: Int,
    val charCount: Int,  // not tokenCount; rename per Decision 8A
)
```

**Internal structure (Decision 7A):** sequential phase methods.

```kotlin
fun toEmbeddingText(view, budget): TruncationResult {
    val state = MutableProjectionState(view)
    protectIdentity(state)               // sections 1-3 marked non-truncatable
    dropFreetextIfOverBudget(state, budget)
    dropRelationalRefsIfOverBudget(state, budget)
    trimKnowledgeByRecency(state, budget)
    return state.render()
}
```

Each phase has its own private method, its own unit test, and contributes to the telemetry list.

---

## Read path (Phase 2)

### Backlink read — single source

Assembler calls `entityRelationshipService.findRelatedEntities(entityId, workspaceId)`. Returns `List<EntityLink>` with forward + inverse, including KNOWLEDGE-inverse via the existing predicate (`ATTACHMENT/MENTION/DEFINES` from KNOWLEDGE-surface-role sources).

Bucketing in-memory:

```kotlin
val (catalogLinks, knowledgeLinks) = links.partition {
    it.sourceSurfaceRole == SurfaceRole.CATALOG  // requires projection field
}
```

**Required addition:** `EntityLink` projection must carry `sourceSurfaceRole` (joined from `entity_types.surface_role` of the source entity). If not already present, add to the projection JPQL in `EntityRelationshipRepository`.

### Type/attribute DEFINES — one new repository query

The KNOWLEDGE-inverse predicate covers ENTITY-target backlinks. Glossary entities defining an entity_type, attribute, or relationship use `target_kind ∈ {ENTITY_TYPE, ATTRIBUTE, RELATIONSHIP}` and don't surface through entity-target reads.

```kotlin
// EntityRelationshipRepository
@Query("""
    SELECT new cranium.core.projection.entity.GlossaryDefinitionRow(
        r.sourceId, r.targetKind, r.targetId, r.targetParentId, r.createdAt
    )
    FROM EntityRelationshipEntity r
    WHERE r.workspaceId = :workspaceId
      AND r.targetKind IN (
          cranium.core.enums.entity.RelationshipTargetKind.ENTITY_TYPE,
          cranium.core.enums.entity.RelationshipTargetKind.ATTRIBUTE,
          cranium.core.enums.entity.RelationshipTargetKind.RELATIONSHIP
      )
      AND (
        (r.targetKind = cranium.core.enums.entity.RelationshipTargetKind.ENTITY_TYPE
         AND r.targetId = :entityTypeId)
        OR
        (r.targetKind IN (
           cranium.core.enums.entity.RelationshipTargetKind.ATTRIBUTE,
           cranium.core.enums.entity.RelationshipTargetKind.RELATIONSHIP
         )
         AND r.targetParentId = :entityTypeId)
      )
""")
fun findGlossaryDefinitionsForType(workspaceId: UUID, entityTypeId: UUID): List<GlossaryDefinitionRow>
```

Hits the partial index from PR1 §Phase 0. EXPLAIN ANALYZE gate in IT.

### KNOWLEDGE excerpt batch fetch (Decision 13A)

After bucketing, assembler collects `knowledgeLinks.map { it.sourceId }.toSet()` and calls `entityAttributeService.getAttributesForEntities(ids)` (existing batch method). Excerpt extraction is per-source-type: note → body, glossary → definition. Type-specific extraction is a small private function in the assembler.

Total backlink read cost: 1 (`findRelatedEntities`) + 1 (`findGlossaryDefinitionsForType`) + 1 (batch attrs for KNOWLEDGE excerpts) = 3 roundtrips, regardless of N.

---

## Multi-consumer fan-out (Phase 1 + future)

```
EnrichmentWorkflow (Temporal)
  │
  ├─ Activity: EnrichmentAnalysisService.analyzeSemantics(queueItemId)
  │     returns: EntityKnowledgeView
  │     side effects: persists EntityMetadataSnapshot to entity_connotation
  │
  ├─ Activity: EnrichmentEmbeddingService.embedAndStore(view, queueItemId)   ← PR2
  │     parallel to Synthesis; independent retry; failure does not roll back
  │     the persisted EntityMetadataSnapshot
  │
  └─ Activity: SynthesisService.compile(view)   ← FUTURE (out of PR2 scope)
        added without rewriting Analysis or Embedding contracts
```

**Properties enforced by tests (Decision 12A):**

- Analysis activity success is independent of any consumer's success.
- Each consumer activity has its own retry policy.
- Adding a consumer = adding an activity in the workflow definition; no service-layer change.

**Payload size guard (Decision 14A):** `EnrichmentAnalysisService` logs a structured warning `enrichment.view_payload_size` with `entityId, charCount` if the serialized view exceeds 1MB (50% of Temporal's default 2MB limit). Hard limit hit = exception bubbles up; soft limit logged so we see drift before it breaks.

---

## Configuration

`EnrichmentConfigurationProperties` (existing) gains:

```kotlin
@ConfigurationProperties(prefix = "cranium.enrichment")
data class EnrichmentConfigurationProperties(
    // ...existing fields...
    val knowledgeBacklinkCap: Int = 3,
    val viewPayloadWarnBytes: Long = 1_048_576L,  // 1MB warn; 2MB Temporal limit
)
```

---

## Test plan

### Phase 1 — pure-refactor regression (Decision 9A, 12A)

1. **Snapshot test (committed at Phase 1 commit, deleted at Phase 3 commit):** assert `EnrichmentContext` JSON for fixture entity is byte-identical pre/post 4-service split. Pure-refactor contract.
2. **Per-service unit tests:** `EnrichmentQueueServiceTest`, `EnrichmentContextAssemblerTest`, `EnrichmentAnalysisServiceTest`, `EnrichmentEmbeddingServiceTest`. Each loads only its own service + security config + `@MockitoBean` deps.
3. **Constructor-parameter assertion:** each service test asserts `≤ N` deps where N is the documented ceiling. Assembler ceiling: 12.
4. **Workflow consumer-independence test:** `EnrichmentWorkflowIT` asserts:
   - Analysis success persists snapshot regardless of Embedding outcome.
   - Embedding failure does not retry Analysis.
   - Test scaffold accepts `List<ConsumerActivity>`; today list size = 1, future Synthesis adds without test rewrite.

### Phase 2 — backlink coverage (Decision 10A)

5. **Parameterized IT — 6-case backlink matrix.** Single test class `EnrichmentBacklinkMatrixIT` with one fixture per case:

| Case                                     | Source role | Target shape | Surfaces in section                                  |
| ---------------------------------------- | ----------- | ------------ | ---------------------------------------------------- |
| Note `MENTION` → customer                | KNOWLEDGE   | ENTITY       | `knowledgeBacklinks`                                 |
| Glossary `DEFINES` → entity_type         | KNOWLEDGE   | ENTITY_TYPE  | `typeNarrative`                                      |
| Glossary `DEFINES` → attribute           | KNOWLEDGE   | ATTRIBUTE    | `attributes[].narrative`                             |
| Glossary `DEFINES` → relationship        | KNOWLEDGE   | RELATIONSHIP | (assembler decides, likely `typeNarrative` adjacent) |
| Customer `SYSTEM_CONNECTION` → vendor    | CATALOG     | ENTITY       | `catalogBacklinks`                                   |
| Order inverse via target_rule → customer | CATALOG     | ENTITY       | `catalogBacklinks`                                   |

6. **Cross-workspace isolation test:** glossary in workspace A defining a customer entity_type that also exists in workspace B — workspace B's view is unaffected.
7. **Soft-delete filter test:** glossary entity with `deleted=true` is excluded automatically (`@SQLRestriction` baseline).
8. **EXPLAIN ANALYZE in IT:** `findGlossaryDefinitionsForType` uses the partial index (`Index Scan`, not `Seq Scan`).
9. **`EntityKnowledgeView` contract test:** all 8 sections present after assembly; `signalBacklinks` field absent (compile-time, asserted by test that imports `KnowledgeSections::class.declaredMemberProperties`).

### Phase 3 — truncation + projection (Decision 11A)

10. **Per-phase unit tests** (one per phase):
    - `protectIdentity` — sections 1-3 always retained even when budget = 0.
    - `dropFreetextIfOverBudget` — drops FREETEXT attributes only when over; deterministic order.
    - `dropRelationalRefsIfOverBudget` — drops RELATIONAL_REFERENCE only after FREETEXT exhausted.
    - `trimKnowledgeByRecency` — most-recent kept; respects `knowledgeBacklinkCap`.
11. **Invariant property tests** (over multiple fixtures):
    - Sections 1-3 always present in `TruncationResult.text`.
    - Sum of `keptCount + droppedCount` per section = input section length.
    - No section appears twice in telemetry list.
    - Telemetry entries cover all 8 section names, even when section is empty.
12. **End-to-end fixture tests** — three scenarios:
    - Under-budget: no truncation; all sections rendered fully.
    - At-budget: FREETEXT trims; KNOWLEDGE retained at cap.
    - Grossly-over-budget: FREETEXT + RELATIONAL_REFERENCE drop; KNOWLEDGE truncated to cap; identity intact.
13. **Glossary parity IT (Decision 9A second snapshot):** `EntityKnowledgeView` for a fixture customer with glossary `DEFINES` edges renders type/attribute narratives sourced from the glossary, not `metadata.definition`. Asserts the read path is glossary-primary so PR3 column drop is safe.
14. **Telemetry log assertion:** `embedding.section_budget` log line emitted per embedding call with `entity_id, section, kept_count, dropped_count, char_count`.

---

## Performance guards

- **Bounded query count per assembler call:** ~6-8 reads, all using existing batch repo methods. Timing log per assembler call (Decision 16A): structured `enrichment.assemble_timing` with `entity_id, query_count, total_ms`.
- **View payload size guard:** structured warn at 1MB (Decision 14A).
- **KNOWLEDGE cap applied at assembler:** view entering Temporal is bounded by `knowledgeBacklinkCap × max_excerpt_chars`.
- **Partial index gate:** EXPLAIN in IT (Decision 15A).

---

## Critical files

### New (Phase 1)

- `src/main/kotlin/cranium/core/service/enrichment/EnrichmentQueueService.kt`
- `src/main/kotlin/cranium/core/service/enrichment/EnrichmentContextAssembler.kt`
- `src/main/kotlin/cranium/core/service/enrichment/EnrichmentAnalysisService.kt`
- `src/main/kotlin/cranium/core/service/enrichment/EnrichmentEmbeddingService.kt`
- `src/main/kotlin/cranium/core/service/enrichment/EntityKnowledgeViewProjector.kt`

### Deleted (Phase 1)

- `src/main/kotlin/cranium/core/service/enrichment/EnrichmentService.kt`
- `src/main/kotlin/cranium/core/service/enrichment/SemanticTextBuilderService.kt` (Phase 3)

### Modified (Phase 1)

- `src/main/kotlin/cranium/core/service/workflow/enrichment/EnrichmentActivitiesImpl.kt` — re-wire activities to the new service boundaries; add per-consumer activity scheduling.

### New (Phase 2)

- `src/main/kotlin/cranium/core/models/entity/knowledge/EntityKnowledgeView.kt`
- `src/main/kotlin/cranium/core/models/entity/knowledge/KnowledgeSections.kt`
- `src/main/kotlin/cranium/core/models/entity/knowledge/SectionTypes.kt` (8 section data classes; one file or split per section)
- `src/main/kotlin/cranium/core/models/entity/knowledge/TruncationBudget.kt`
- `src/main/kotlin/cranium/core/models/entity/knowledge/TruncationResult.kt`
- `src/main/kotlin/cranium/core/projection/entity/GlossaryDefinitionRow.kt`

### Modified (Phase 2)

- `src/main/kotlin/cranium/core/repository/entity/EntityRelationshipRepository.kt` — add `findGlossaryDefinitionsForType`; add `sourceSurfaceRole` to existing `EntityLink` projection if absent.
- `src/main/kotlin/cranium/core/configuration/properties/EnrichmentConfigurationProperties.kt` — add `knowledgeBacklinkCap`, `viewPayloadWarnBytes`.

---

## Sequencing within PR2

Three commits, each independently green:

1. **Phase 1 commit:** 4-service split. `EnrichmentContext` snapshot test passes byte-identical. No behavior change.
2. **Phase 2 commit:** `EntityKnowledgeView` + sections + projector + 1 new repo query. Assembler returns view; projector exists but isn't yet wired to `EnrichmentEmbeddingService`. Tests for view contract, backlink matrix, projector phases pass.
3. **Phase 3 commit:** Wire projector into `EnrichmentEmbeddingService`. Delete `SemanticTextBuilderService`. Replace Phase 1's snapshot with the post-Phase-3 view structural snapshot. Telemetry log emission active.

---

## Out of scope (deferred per plan)

- `EntityKnowledgeView.toSnapshotJsonb()` projection — adds when first synthesis consumer arrives.
- `signalBacklinks` section — adds when dtc-ecom merges and SIGNAL types exist.
- Reconciliation hooks (knowledge-graph dirty flag) — Entity Reconsumption project.
- `tokenCount` telemetry — adds when embedding-provider tokenizer abstraction lands.
- Batch assembler variant — adds when timing telemetry shows it matters.
- PR3 cutover (drop `metadata.definition` column) — gated on entity-type creation lifecycle TODO.

---

## Decision Log

Resolved via interactive `/feature-plan` review on 2026-05-04. All 16 decisions accepted as recommended.

| Section      | Issue                       | Decision                                     |
| ------------ | --------------------------- | -------------------------------------------- |
| Architecture | 1 Backlink read path        | 1A reuse + 1 new query                       |
| Architecture | 2 Assembler dep count       | 2A ship at ~10, ceiling 12                   |
| Architecture | 3 Projector location        | 3A separate service                          |
| Architecture | 4-i View transport          | 4-i.A in-memory                              |
| Architecture | 4-ii Consumer wiring        | 4-ii.A Temporal activities                   |
| Architecture | 4-iii Canonical artifact    | 4-iii.A `EntityKnowledgeView`                |
| Code Quality | 5 `EntityBacklinks` DTO     | 5A drop                                      |
| Code Quality | 6 Section type uniformity   | 6A distinct classes                          |
| Code Quality | 7 Truncation projector      | 7A sequential phase methods                  |
| Code Quality | 8 Telemetry granularity     | 8A `charCount`, defer `tokenCount`           |
| Tests        | 9 Regression snapshot scope | 9A two snapshots, phase-gated                |
| Tests        | 10 Backlink coverage        | 10A 6-case parameterized IT                  |
| Tests        | 11 Truncation tests         | 11A three-tier (per-phase + invariant + e2e) |
| Tests        | 12 Fan-out test contract    | 12A consumer-independence assertion          |
| Performance  | 13 KNOWLEDGE excerpt fetch  | 13A batch via existing service               |
| Performance  | 14 Temporal payload size    | 14A truncate at assembler + size guard       |
| Performance  | 15 Index selection          | 15A trust plan + EXPLAIN gate                |
| Performance  | 16 Batch enrichment         | 16A timing telemetry, defer batching         |
