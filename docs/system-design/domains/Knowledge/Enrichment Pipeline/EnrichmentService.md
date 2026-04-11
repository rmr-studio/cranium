---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-04-10
Domains:
  - "[[riven/docs/system-design/domains/Knowledge/Knowledge]]"
---

Part of [[riven/docs/system-design/domains/Knowledge/Enrichment Pipeline/Enrichment Pipeline]]

# EnrichmentService

---

## Purpose

Orchestrates the enrichment lifecycle — enqueues entities for embedding, claims queue items for activity execution, assembles the `EnrichmentContext` snapshot from PostgreSQL state, and persists final embeddings to `entity_embeddings`. It is the bridge between callers (entity mutation lifecycle) and the Temporal-driven activity layer.

---

## Responsibilities

- Enqueue entities into the central `execution_queue` with job type `ENRICHMENT` and dispatch a Temporal workflow to process them.
- Gate INTEGRATION entities out of the pipeline silently (they are not embeddable by design).
- Claim queue items for activity execution with idempotent retry semantics.
- Assemble the full `EnrichmentContext` snapshot — entity, entity type, semantic metadata, attributes, relationships, cluster members, and relationship semantic definitions — in batch queries.
- Resolve `RELATIONAL_REFERENCE` attribute values from UUID to human-readable identifier strings.
- Compute relationship summaries with categorical breakdowns and recency timestamps.
- Upsert the final embedding record into `entity_embeddings` and mark the queue item `COMPLETED`.

**Explicitly NOT responsible for:**

- Building the enriched text body for the embedding model — that is [[SemanticTextBuilderService]].
- Calling the embedding HTTP API — that is [[EmbeddingProvider]].
- Scheduling activity retries or workflow-level control flow — that is [[EnrichmentWorkflow]].
- User-level authorisation checks — this service runs in Temporal activity context, not in a request thread.

---

## Dependencies

### Internal Dependencies

- [[ExecutionQueueRepository]] — persists and claims enrichment queue items.
- [[EntityEmbeddingRepository]] — stores the final embedding vector and metadata.
- [[EntityRepository]] — loads the source entity.
- [[EntityTypeRepository]] — loads the entity type for semantic context.
- `EntityTypeSemanticMetadataRepository` — loads semantic metadata targeted at the entity type and its relationships.
- [[EntityAttributeService]] — loads typed attribute values for the entity.
- `EntityRelationshipRepository` — loads outbound relationships for summary aggregation. (Not separately documented in the vault; see [[EntityRelationshipService]].)
- [[RelationshipDefinitionRepository]] — loads relationship definitions keyed by id.
- `IdentityClusterMemberRepository` — loads other members of the entity's identity cluster. (Not separately documented; see [[IdentityClusterService]].)
- `RelationshipTargetRuleRepository` — used during relationship summary assembly.
- [[EmbeddingProvider]] — abstracts the embedding HTTP call (invoked indirectly via the activity layer).

### External Dependencies

- `io.temporal.client.WorkflowClient` — dispatches [[EnrichmentWorkflow]] executions.
- `KLogger` — structured logging.

### Injected Dependencies

```kotlin
@Service
class EnrichmentService(
    private val executionQueueRepository: ExecutionQueueRepository,
    private val entityEmbeddingRepository: EntityEmbeddingRepository,
    private val entityRepository: EntityRepository,
    private val entityTypeRepository: EntityTypeRepository,
    private val semanticMetadataRepository: EntityTypeSemanticMetadataRepository,
    private val entityAttributeService: EntityAttributeService,
    private val entityRelationshipRepository: EntityRelationshipRepository,
    private val relationshipDefinitionRepository: RelationshipDefinitionRepository,
    private val identityClusterMemberRepository: IdentityClusterMemberRepository,
    private val relationshipTargetRuleRepository: RelationshipTargetRuleRepository,
    private val embeddingProvider: EmbeddingProvider,
    private val workflowClient: WorkflowClient,
    private val logger: KLogger,
)
```

---

## Used By

- Entity mutation lifecycle hooks — call `enqueueAndProcess` after create/update to trigger re-embedding.
- [[EnrichmentActivitiesImpl]] — calls `fetchContext` and `storeEmbedding` from within Temporal activities.

---

## Public Interface

The service is organised into three section blocks: `// ------ Public Entry Point ------`, `// ------ Activity-Called Methods ------`, and `// ------ Private Helpers ------`.

### Key Methods

#### `fun enqueueAndProcess(entityId: UUID, workspaceId: UUID): Unit`

- **Purpose:** Enqueues an entity for enrichment and dispatches a Temporal workflow to drive it through the pipeline.
- **Side effects:**
  - Persists an `ExecutionQueueEntity` with status `PENDING` and job type `ENRICHMENT`.
  - Starts [[EnrichmentWorkflow]] via `WorkflowClient` with workflow ID `enrichment-embed-{queueItemId}` on the `TemporalWorkerConfiguration.ENRICHMENT_EMBED_QUEUE` task queue.
  - Logs INFO `Enqueued entity {id} for enrichment, queue item: {id}`.
  - Logs DEBUG `Skipping enrichment for INTEGRATION entity {id}` and returns early if the entity's `sourceType` is `INTEGRATION`.
- **Throws:** `NotFoundException` if the entity cannot be loaded.

#### `fun fetchContext(queueItemId: UUID): EnrichmentContext`

- **Purpose:** Claims a queue item and assembles an `EnrichmentContext` snapshot for the pipeline activities.
- **Side effects:**
  - Marks the queue item as `CLAIMED` with the current timestamp. Idempotent on retry — accepts already-`CLAIMED` status and updates `claimedAt` in place rather than throwing.
  - Loads entity, entity type, semantic metadata, attributes, and relationships in batch queries to avoid N+1 patterns.
  - Loads identity cluster members, resolves `RELATIONAL_REFERENCE` attribute values to display strings, loads relationship semantic definitions, and enriches relationship summaries with categorical breakdowns and recency timestamps.
- **Throws:**
  - `NotFoundException` if the queue item or entity is not found.
  - `IllegalArgumentException` if the queue item has no `entityId` (invariant violation for an `ENRICHMENT` job).

#### `fun storeEmbedding(queueItemId: UUID, context: EnrichmentContext, embedding: FloatArray, truncated: Boolean): Unit`

- **Purpose:** Upserts the embedding record for an entity and marks the queue item as `COMPLETED`.
- **Side effects:**
  - Deletes any existing embedding for the entity, then inserts the new embedding with all required metadata fields (delete + insert upsert pattern).
  - Marks the queue item `COMPLETED`.
  - Logs INFO `Stored embedding for entity {id}, queue item {id} completed`.
- **Throws:** `NotFoundException` if the queue item is not found.

---

## Key Logic

### Embeddability gating

`isEmbeddable(entity)` returns `true` for every source type except `INTEGRATION`. INTEGRATION entities are derived projections of external system state; they have no independent semantic surface worth embedding, and attempting to do so would produce noisy vectors tied to transient upstream shapes. The gate is enforced in `enqueueAndProcess` and emits a DEBUG log but no error.

### Idempotent queue claim

`claimQueueItem(queueItem)` tolerates an already-`CLAIMED` status by updating `claimedAt` to the current timestamp and returning successfully. This makes the method safe to re-invoke when Temporal redelivers an activity task after a worker crash or timeout. If the status is anything else (`PENDING`, `COMPLETED`, `FAILED`), the transition follows normal state-machine rules.

### N+1 avoidance via batch queries

Context assembly fans out across several related tables: semantic metadata, attributes, relationships, relationship definitions, cluster members, and referenced entity identifiers. All cross-entity lookups use `findBy*In()` batch queries keyed by collected ID sets, never per-row lookups inside loops. This keeps context assembly at a constant number of round-trips regardless of how many attributes or relationships an entity has.

### Relationship categorical summaries

For each relationship definition attached to the entity, `loadRelationshipSummaries` groups the outbound relationships by definition id, computes the latest activity timestamp across the group, and calls `loadTopCategoriesForRelationship` to pull the top 3 `CATEGORICAL` attribute values from the related entity types. The output string is formatted as:

```
Label: Value1 (count), Value2 (count), Value3 (count)
```

This gives the downstream text builder a compact signal about the distribution of related entities without embedding every relationship individually.

### Cluster member resolution

`loadClusterMembers(entityId)` loads other members of the entity's identity cluster, explicitly filtering out the source entity itself. Member entity type names are batch-loaded in a single query. The cluster members feed into Section 5 of the enriched text in [[SemanticTextBuilderService]].

### RELATIONAL_REFERENCE resolution

Attributes classified as `RELATIONAL_REFERENCE` store UUIDs pointing at other entities. `resolveReferencedEntityIdentifiers` walks those UUIDs, fetches the target entities' `IDENTIFIER` attributes in a single batch query, and builds a `UUID -> displayString` map. Unresolvable references fall back to `[reference not resolved]` so the pipeline never crashes on dangling pointers.

### Relationship semantic definition filtering

`loadRelationshipDefinitions` filters the full semantic metadata set to entries where `targetType == RELATIONSHIP`, then joins each entry with its corresponding `RelationshipDefinition`. This produces the Section 6 payload (relationship name + definition text pairs) that feeds into the enriched text.

### Delete-then-insert upsert

`storeEmbedding` implements upsert by deleting any existing row for the entity and inserting a fresh one. This runs inside a single transaction, but during that transaction the row is briefly absent.

---

## Data Access

- **Reads:** `entities`, `entity_types`, `entity_type_semantic_metadata`, `entity_attributes`, `entity_relationships`, `relationship_definitions`, `identity_cluster_members`, `relationship_target_rules`, `execution_queue`.
- **Writes:** `execution_queue` (insert `PENDING`, update to `CLAIMED`, update to `COMPLETED`); `entity_embeddings` (delete + insert upsert).
- **Workspace scoping:** every query carries `workspaceId` explicitly. There is no `@PreAuthorize` — workspace isolation is enforced at the query level because the service runs inside Temporal activity contexts, not user-scoped request threads.

---

## Configuration

References `TemporalWorkerConfiguration.ENRICHMENT_EMBED_QUEUE` as the task queue name when starting the enrichment workflow. See [[TemporalWorkerConfiguration]] for the constant definition.

---

## Error Handling

| Exception | Source | Meaning |
| --- | --- | --- |
| `NotFoundException` | `ServiceUtil.findOrThrow` | Queue item or entity not found during `fetchContext` or `storeEmbedding`. |
| `IllegalArgumentException` | `requireNotNull(id)` on persisted entities | Invariant violation — a JPA-managed entity returned without its id. |
| `IllegalArgumentException` | `requireNotNull(queueItem.entityId)` | An `ENRICHMENT` job was enqueued without an `entityId` — should be impossible from normal code paths. |

All exceptions propagate to the Temporal activity layer, which handles retries and dead-lettering.

---

## Gotchas & Edge Cases

> [!warning] INTEGRATION entities silently skipped
> Calling `enqueueAndProcess` on an `INTEGRATION` entity is a no-op — it logs at DEBUG and returns. No error is thrown, no row is written to `entity_embeddings`, and no workflow is started. If you need to confirm an entity was enriched, query `entity_embeddings` directly; a successful return from `enqueueAndProcess` is not proof.

> [!warning] Idempotent claim semantics are a contract
> A redelivered Temporal activity task updates `claimedAt` and proceeds — it does not throw. If you change downstream side effects in `fetchContext` or `storeEmbedding` to be non-idempotent (e.g. appending rather than replacing), this contract will produce silent duplication on every retry.

> [!warning] Delete-then-insert upsert has a visibility window
> `storeEmbedding` deletes the existing embedding row before inserting the new one. Both happen in the same transaction, but any concurrent read inside that transaction window will observe a missing row. Consumers that read embeddings in a separate read-committed transaction are safe; concurrent writers touching the same row are not.

> [!warning] No `@PreAuthorize` — workspace isolation is query-enforced
> This service does not use `@PreAuthorize` because it runs inside Temporal activity contexts, not user request threads. Workspace isolation is enforced by every query carrying `workspaceId` explicitly. If you add new queries to this service, make sure they all filter by `workspaceId` — a missing filter would cross workspace boundaries with no authorisation layer to catch it.

---

## Testing

- **File:** `core/src/test/kotlin/riven/core/service/enrichment/EnrichmentServiceTest.kt` (964 lines)
- **Key scenarios:**
  - Embeddability gating — INTEGRATION entities are silently skipped, other source types proceed.
  - Idempotent claim — redelivered activities update `claimedAt` without throwing.
  - Cluster member resolution — source entity is excluded, type names resolved.
  - `RELATIONAL_REFERENCE` resolution — UUIDs map to display strings, missing entries fall back to `[reference not resolved]`.
  - Top-categories aggregation — top 3 values per relationship, correct counts.
  - `storeEmbedding` upsert — existing row deleted before insert, queue item marked `COMPLETED`.

---

## Related

- [[SemanticTextBuilderService]] — the text-builder activity called between fetch and store
- [[EnrichmentWorkflow]] — the Temporal workflow this service dispatches
- [[EnrichmentActivitiesImpl]] — calls `fetchContext` and `storeEmbedding` from activities
- [[EmbeddingProvider]] — abstracts the actual embedding HTTP call (used indirectly via the activity)
- [[EntityEmbeddingEntity]] — the persisted output
- [[EntityEmbeddingRepository]] — the repository used for embedding upsert
- [[ExecutionQueueRepository]] — the central queue used for enrichment jobs
- [[TemporalWorkerConfiguration]] — defines the task queue constant
- [[Flow - Entity Enrichment Pipeline]] — end-to-end flow
