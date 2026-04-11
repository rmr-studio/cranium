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

# EntityEmbeddingRepository

## Purpose
Spring Data JPA repository over `entity_embeddings`. Exposes the minimal query surface the enrichment pipeline needs today: exact lookup by entity, workspace-scoped listing, and hard delete by entity.

## Responsibilities
- Provide exact-match lookup of an entity's single embedding row.
- Provide workspace-scoped retrieval of all embeddings for maintenance and inspection flows.
- Provide the hard-delete half of the delete-then-insert upsert used by [[EnrichmentService]].
- Inherit standard `save`, `saveAll`, and `findById` from `JpaRepository`.

## Interface

```kotlin
@Repository
interface EntityEmbeddingRepository : JpaRepository<EntityEmbeddingEntity, UUID> {

    fun findByEntityId(entityId: UUID): EntityEmbeddingEntity?

    fun findByWorkspaceId(workspaceId: UUID): List<EntityEmbeddingEntity>

    fun deleteByEntityId(entityId: UUID)
}
```

## Public Methods

- `findByEntityId(entityId: UUID): EntityEmbeddingEntity?` — exact lookup for a single entity's embedding. Returns `null` if no row exists. This is the hot path for "does this entity already have an embedding?".
- `findByWorkspaceId(workspaceId: UUID): List<EntityEmbeddingEntity>` — returns every embedding in a workspace. Used for maintenance and inspection; there is no pagination.
- `deleteByEntityId(entityId: UUID)` — hard-deletes the row keyed by entity. Used by `EnrichmentService.storeEmbedding` as the first half of a transactional delete-then-insert upsert.
- Inherited from `JpaRepository`: `save`, `saveAll`, `findById`, `existsById`, etc. `save` is the second half of the upsert.

## Gotchas

> [!warning] No similarity-search query yet
> The HNSW index on `entity_embeddings.embedding` exists, but the repository does not yet expose a `findSimilar(...)` method. Vector similarity queries will land in a follow-up using pgvector's `<=>` cosine distance operator via either a JPA `@Query` or native SQL.

> [!warning] findByWorkspaceId is unpaginated
> Returning every embedding in a workspace is fine for development and small workspaces. Large customers will need cursor pagination before this method is safe to call from user-facing code.

> [!warning] Delete is hard, not soft
> Consistent with the system-managed nature of [[EntityEmbeddingEntity]] — soft-delete does not apply to embeddings. A deleted row is gone, and the expectation is that the enrichment workflow will re-create it when the entity is re-embedded.

## Used By
- [[EnrichmentService]] — calls `findByEntityId`, `deleteByEntityId`, and `save` (inherited from `JpaRepository`) to implement the delete-then-insert upsert.

## Related
- [[EntityEmbeddingEntity]]
- [[EnrichmentService]]
- [[Flow - Entity Enrichment Pipeline]]
