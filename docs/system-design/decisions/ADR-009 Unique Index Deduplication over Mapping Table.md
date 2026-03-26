---
tags:
  - adr/proposed
  - architecture/decision
Created: 2026-03-16
---
# ADR-009: Unique Index Deduplication over Mapping Table

---

## Context

The sync pipeline must deduplicate incoming records — when Nango delivers a record with a known external ID, the system must find the existing entity rather than create a duplicate. The original design included a dedicated `integration_entity_map` table to track the mapping between external record IDs and internal entity UUIDs. This table would have columns for workspace ID, integration connection ID, external ID, entity ID, and sync metadata.

However, the `entities` table already has two columns that serve this purpose: `source_integration_id` (UUID FK to `integration_connections`) and `source_external_id` (VARCHAR storing the external system's record ID). These columns were added during the Entity Provenance Tracking feature and are populated on every integration-created entity. They already contain the exact information the mapping table would duplicate.

The question is whether to introduce a separate mapping table for deduplication or to leverage the existing columns with a database-level uniqueness constraint.

---

## Decision

Use the existing `source_integration_id` and `source_external_id` columns on the `entities` table with a unique partial index for deduplication. Do not create a separate `integration_entity_map` table. The partial index applies only to rows where both `source_integration_id` and `source_external_id` are non-null, ensuring the constraint affects only integration-sourced entities.

---

## Rationale

- **The columns already exist and are populated** — adding an index is a single DDL statement versus creating a new table, JPA entity, repository, and synchronization logic
- **Eliminates a consistency risk** — a mapping table must be kept in sync with entity creates and deletes, introducing a failure mode where the map and entities diverge (e.g., entity deleted but map entry remains, or map entry created but entity insert fails)
- **Single-query dedup** — the batch query `findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn` returns the actual entities, not intermediate mapping rows; this avoids a two-step lookup (query map, then query entities by IDs)
- **Partial index scope** — `WHERE source_integration_id IS NOT NULL AND source_external_id IS NOT NULL` ensures the uniqueness constraint only applies to integration-sourced entities, leaving user-created entities (which have null source columns) unconstrained
- **Net reduction in codebase surface** — eliminates 1 table, 1 JPA entity class, 1 repository interface, and approximately 50 lines of mapping synchronization logic from the sync pipeline

---

## Alternatives Considered

### Option 1: Dedicated `integration_entity_map` Table

A separate table with columns `(workspace_id, integration_connection_id, external_id, entity_id)` plus optional sync metadata (last sync timestamp, sync version).

- **Pros:** Clear separation of concerns between entity data and integration mapping metadata. Can store additional per-record sync metadata (e.g., last modified timestamp from external system, sync hash for change detection). Independent lifecycle from entities.
- **Cons:** Consistency risk — the map must be updated atomically with entity creates and deletes, and any failure in synchronization leaves orphaned or stale entries. Extra join on every dedup lookup. Extra write on every entity create and delete. Adds a JPA entity, repository, and service logic to maintain.
- **Why rejected:** The consistency burden outweighs the separation-of-concerns benefit. The existing entity columns already contain the mapping data, making the separate table redundant. If per-record sync metadata is needed in the future, it can be added as columns on the entity or in a purpose-built audit table.

### Option 2: Application-Level Deduplication (No Index)

Query for existing entities by source fields before inserting, without a database-level uniqueness constraint.

- **Pros:** No schema change required. Works with the existing columns as-is.
- **Cons:** Race condition under concurrent syncs — two workers processing the same external ID simultaneously can both pass the existence check and both insert, creating duplicates. No database-level guarantee of uniqueness. Requires application-level locking or serialization to prevent duplicates.
- **Why rejected:** Does not provide a database-level guarantee. The sync pipeline processes records in batches and may have concurrent executions for the same connection, making the race condition a realistic failure mode.

### Option 3: Full Unique Constraint (Non-Partial)

A standard unique constraint on `(workspace_id, source_integration_id, source_external_id)` without a partial index condition.

- **Pros:** Simpler DDL. Standard SQL constraint understood by all tools.
- **Cons:** PostgreSQL treats NULL as distinct in unique constraints, so multiple rows with NULL `source_integration_id` would be allowed (which is the desired behavior for user-created entities). However, the semantics are fragile and database-specific. A partial index makes the intent explicit and portable.
- **Why rejected:** While PostgreSQL's NULL handling in unique constraints happens to produce the correct behavior, the partial index makes the design intent explicit — the constraint applies only to integration-sourced entities. This is clearer for future developers and avoids reliance on database-specific NULL semantics.

---

## Consequences

### Positive

- Simpler architecture with no mapping table to maintain or keep in sync
- Database-enforced uniqueness prevents duplicate entities even under concurrent sync execution
- Single-query dedup lookup returns actual entity objects, eliminating an intermediate mapping layer
- Fewer files to maintain — no mapping entity, repository, or synchronization service

### Negative

- Cannot store additional per-record mapping metadata (e.g., external system timestamps, sync hashes) without adding columns to the entities table or introducing a separate table later
- The dedup query requires an `IN` clause with the batch of external IDs, which has a practical limit on batch size (mitigated by chunking in the sync pipeline)

### Neutral

- The existing `source_integration_id` and `source_external_id` columns gain a new partial index — the write overhead is negligible for the uniqueness guarantee it provides
- If per-record sync metadata is needed in the future, the decision can be revisited without affecting the dedup mechanism itself

---

## Implementation Notes

- Index DDL added to `db/schema/01_tables/entities.sql`:
  ```sql
  CREATE UNIQUE INDEX IF NOT EXISTS idx_entities_source_dedup
  ON entities (workspace_id, source_integration_id, source_external_id)
  WHERE source_integration_id IS NOT NULL AND source_external_id IS NOT NULL;
  ```
- Batch dedup query on `EntityRepository`:
  ```kotlin
  fun findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(
      workspaceId: UUID,
      sourceIntegrationId: UUID,
      sourceExternalIds: List<String>
  ): List<EntityEntity>
  ```
- The returned list is converted to `Map<String, EntityEntity>` keyed by `sourceExternalId` for O(1) per-record lookup during sync processing
- Batch size is controlled by the sync pipeline to stay within practical `IN` clause limits

---

## Related

- [[Integration Data Sync Pipeline]] — Feature design for the sync pipeline that uses this dedup mechanism
- [[Entity Integration Sync]] — Sub-domain plan for entity sync processing
- [[Entity Provenance Tracking]] — Feature that introduced the `source_integration_id` and `source_external_id` columns
- [[Entities]] — Domain containing the entities table and repository
