---
Created: 2026-04-10
Domains:
  - "[[riven/docs/system-design/domains/Knowledge/Knowledge]]"
tags:
  - layer/utility
  - component/active
  - architecture/component
---

Part of [[riven/docs/system-design/domains/Knowledge/Enrichment Pipeline/Enrichment Pipeline]]

# EntityEmbeddingEntity

## Purpose
JPA entity and matching domain model for a single entity's semantic embedding. Each row holds one 1536-dimension `vector` plus the metadata needed to detect staleness and trace provenance back to the model that produced it.

## Responsibilities
- Persist exactly one embedding per entity via a unique constraint on `entity_id`.
- Carry the embedding model name and schema version forward so downstream code can detect when a row is stale.
- Carry a `truncated` flag so callers know whether the semantic text was clipped at build time.
- Expose a 1:1 `toModel()` mapping to the plain Kotlin `EntityEmbeddingModel` for use outside the JPA boundary.

## System-Managed Entity

This is a **system-managed** entity. It does NOT extend `AuditableEntity` and does NOT implement `SoftDeletable`. Per project convention, system-managed entities (catalog entries, manifest data, embeddings) opt out of audit and soft-delete because they are not user-curated content.

Lifecycle is tracked via `embedded_at`. Rows are hard-deleted and re-inserted on every re-enrichment — see the upsert pattern in [[EnrichmentService]].

## Entity Class

```kotlin
@Entity
@Table(name = "entity_embeddings")
data class EntityEmbeddingEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
    val workspaceId: UUID,

    @Column(name = "entity_id", nullable = false, columnDefinition = "uuid")
    val entityId: UUID,

    @Column(name = "entity_type_id", nullable = false, columnDefinition = "uuid")
    val entityTypeId: UUID,

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1536)
    @Column(name = "embedding", nullable = false)
    val embedding: FloatArray,

    @Column(name = "embedded_at", nullable = false, columnDefinition = "timestamptz")
    val embeddedAt: ZonedDateTime = ZonedDateTime.now(),

    @Column(name = "embedding_model", nullable = false)
    val embeddingModel: String,

    @Column(name = "schema_version", nullable = false)
    val schemaVersion: Int = 1,

    @Column(name = "truncated", nullable = false)
    val truncated: Boolean = false,
)
```

## Domain Model

```kotlin
data class EntityEmbeddingModel(
    val id: UUID,
    val workspaceId: UUID,
    val entityId: UUID,
    val entityTypeId: UUID,
    val embedding: FloatArray,
    val embeddedAt: ZonedDateTime,
    val embeddingModel: String,
    val schemaVersion: Int,
    val truncated: Boolean,
)
```

The `toModel()` mapping is 1:1. `requireNotNull(id) { ... }` enforces that the row has been persisted before it can cross the boundary.

## Field Reference

| Field | Type | Column | Notes |
|---|---|---|---|
| `id` | `UUID?` | `id` | Generated via `GenerationType.UUID` — leave null on construction. |
| `workspaceId` | `UUID` | `workspace_id` | FK → `workspaces.id`, ON DELETE CASCADE. |
| `entityId` | `UUID` | `entity_id` | FK → `entities.id`, ON DELETE CASCADE. **UNIQUE** — one embedding per entity. |
| `entityTypeId` | `UUID` | `entity_type_id` | FK → `entity_types.id`, ON DELETE CASCADE. |
| `embedding` | `FloatArray` | `embedding` | `vector(1536)` via `@JdbcTypeCode(SqlTypes.VECTOR)` + `@Array(length = 1536)`. |
| `embeddedAt` | `ZonedDateTime` | `embedded_at` | `timestamptz`, default `CURRENT_TIMESTAMP`. |
| `embeddingModel` | `String` | `embedding_model` | Sourced from `EmbeddingProvider.getModelName()` (e.g. `text-embedding-3-small`). |
| `schemaVersion` | `Int` | `schema_version` | Default `1`. Stored for staleness detection on schema drift; not yet read by any code path. |
| `truncated` | `Boolean` | `truncated` | Default `false`. Set `true` when `SemanticTextBuilderService` hits the 27,000 char budget. |

## SQL DDL

```sql
CREATE TABLE IF NOT EXISTS public.entity_embeddings
(
    "id"               UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    "workspace_id"     UUID         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    "entity_id"        UUID         NOT NULL REFERENCES entities (id) ON DELETE CASCADE,
    "entity_type_id"   UUID         NOT NULL REFERENCES entity_types (id) ON DELETE CASCADE,
    "embedding"        vector(1536) NOT NULL,
    "embedded_at"      TIMESTAMPTZ  NOT NULL     DEFAULT CURRENT_TIMESTAMP,
    "embedding_model"  TEXT         NOT NULL,
    "schema_version"   INTEGER      NOT NULL     DEFAULT 1,
    "truncated"        BOOLEAN      NOT NULL     DEFAULT FALSE,
    UNIQUE (entity_id)
);
```

## Index

- `idx_entity_embeddings_hnsw` — HNSW (cosine) on `embedding vector_cosine_ops` with `m = 16, ef_construction = 64`. This is the first pgvector index in the system.

## RLS Policies

- `entity_embeddings_select` — `SELECT` to `authenticated`, `USING workspace_id IN (SELECT workspace_id FROM workspace_members WHERE user_id = auth.uid())`.
- `entity_embeddings_write` — `ALL` to `authenticated`, with the same `USING` and `WITH CHECK` predicates.

## Gotchas

> [!warning] Dimension is hard-coded in two places
> `vector(1536)` lives in both the SQL schema and the `@Array(length = 1536)` annotation. Changing dimension requires a schema migration AND re-embedding every row. The `vectorDimensions` config property is not currently connected to the column type.

> [!warning] Cascade delete handles cleanup automatically
> FKs on `workspace_id`, `entity_id`, and `entity_type_id` all cascade. Deleting an entity drops its embedding row for free — no separate cleanup path is needed.

> [!warning] Upsert is delete + insert
> The unique constraint on `entity_id` is what makes `EnrichmentService.storeEmbedding` viable as a delete-then-insert inside a single transaction. There is no `ON CONFLICT DO UPDATE` path.

> [!warning] schemaVersion is written but not yet read
> The column is persisted on every insert but no code path currently consults it. The intent is staleness detection on schema drift via a future re-batching workflow.

## Used By
- [[EntityEmbeddingRepository]] — CRUD contract.
- [[EnrichmentService]] — owns all reads and writes via the repository.

## Related
- [[EntityEmbeddingRepository]]
- [[EnrichmentService]]
- [[EmbeddingProvider]]
- [[Flow - Entity Enrichment Pipeline]]
