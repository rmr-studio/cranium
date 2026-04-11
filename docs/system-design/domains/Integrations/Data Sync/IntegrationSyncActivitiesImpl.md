---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-04-11
Domains:
  - "[[Integrations]]"
---
# IntegrationSyncActivitiesImpl

Part of [[Data Sync]]

## Purpose

Core Temporal activity implementation for the integration data sync pipeline. Fetches records from Nango, upserts them as workspace entities with deduplication and per-record error isolation, resolves inter-entity relationships in a second pass, delegates projection execution in a third pass, and evaluates connection health after each sync cycle.

This is a Spring `@Service` that implements the `IntegrationSyncActivities` Temporal activity interface. It is not invoked via Spring MVC controllers -- Temporal's activity worker calls its methods directly based on the workflow definition in `IntegrationSyncWorkflowImpl`.

---

## Responsibilities

- Transition integration connections to `SYNCING` status with guard checks for invalid state transitions
- Resolve model context: look up the catalog entity type, Nango model, field mappings, attribute key mapping, and relationship definitions needed to process a given model's records
- Paginate through Nango records with Temporal heartbeating to prevent activity timeout during long syncs
- Batch-deduplicate incoming records against existing entities via IN-clause query keyed on `sourceExternalId`
- Upsert entities with per-record error isolation -- single record failures do not abort the batch
- Collect pending relationship data during Pass 1 and resolve them in Pass 2 via batch external ID lookups
- Delegate Pass 3 projection execution to [[EntityProjectionService]] for synced entity IDs
- Finalize sync state: advance cursor and reset failure count on success, increment consecutive failure count on failure
- Evaluate connection health by delegating to `IntegrationHealthService`

---

## Dependencies

- `IntegrationConnectionRepository` -- lookup and status transition for integration connections
- [[IntegrationSyncStateRepository]] -- persisted sync state for cursor tracking and failure counts
- `NangoClientWrapper` -- paginated record fetch from Nango API
- [[SchemaMappingService]] -- maps external payloads to entity attributes using resolved field mappings
- `EntityRepository` -- entity CRUD and batch lookup by `sourceExternalId`
- `EntityAttributeService` -- saves mapped attributes for upserted entities
- `EntityRelationshipRepository` -- dedup-check and creation of entity relationships
- `RelationshipDefinitionRepository` -- lookup of relationship definitions for the source entity type
- `IntegrationDefinitionRepository` -- lookup of integration definitions by ID
- `ManifestCatalogRepository` -- lookup of manifest catalog entries by integration slug
- `CatalogFieldMappingRepository` -- lookup of field mappings by manifest ID and Nango model name
- `CatalogEntityTypeRepository` -- lookup of catalog entity types by manifest ID and key
- `EntityTypeRepository` -- lookup of workspace entity types by source integration ID
- `IntegrationHealthService` -- connection health evaluation after sync cycles (not yet documented)
- `EntityProjectionService` -- Pass 3 projection pipeline for synced integration entities
- `TransactionTemplate` -- programmatic transaction boundaries for per-record isolation
- `KLogger` -- structured logging

## Used By

- `IntegrationSyncWorkflowImpl` -- the Temporal workflow that orchestrates the five activity methods in sequence

---

## Key Logic

**Model context resolution (`resolveModelContext`):**

1. Look up `IntegrationDefinitionEntity` by integration ID
2. Find `ManifestCatalogEntity` by the definition's slug
3. Resolve Nango model name to entity type key via `CatalogFieldMappingRepository.findByManifestIdAndNangoModel`
4. Find the workspace `EntityTypeEntity` matching the integration ID, workspace ID, and entity type key
5. Look up the `CatalogEntityTypeEntity` for schema reference
6. Read `attributeKeyMapping` from the entity type -- this maps Nango field names (strings) to entity attribute UUIDs
7. Resolve field mappings into `ResolvedFieldMapping` objects with source paths, transforms, and target schema types
8. Cache relationship definitions for the source entity type into `ModelContext`

Returns `null` if any lookup fails, which the caller treats as a fatal activity result.

**Paginated fetch and process loop (`runFetchAndProcessLoop`):**

1. Fetch a page of records from Nango via `NangoClientWrapper.fetchRecords`
2. Process the batch: deduplicate, upsert, collect relationship pending items
3. Heartbeat to Temporal with the current cursor to prevent timeout and enable resume-on-retry
4. Advance cursor to `nextCursor` and repeat until no more pages
5. After all pages consumed, run Pass 2 relationship resolution
6. Return `SyncProcessingResult` with counts, cursor, and synced entity IDs

**Batch processing (`processBatch`):**

1. Extract external IDs from the page's records
2. Batch-fetch existing entities via `findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn` (O(1) per-record via `associateBy`)
3. For each record, wrapped in try-catch for per-record error isolation:
   - Extract external ID from the payload using `ModelContext.externalIdField`
   - Route to `handleDelete` (soft-delete) or `handleUpsert` (create/update) based on `NangoRecordAction`
4. Return `BatchResult` with synced/failed counts and synced entity IDs

**Upsert logic (`handleUpsert`):**

1. Map the external payload to entity attributes via `SchemaMappingService.mapPayload`
2. If mapping produces errors, log a warning and return null (skip record)
3. Create a new `EntityEntity` with `SourceType.INTEGRATION` or update timestamps on existing
4. Save attributes via `EntityAttributeService.saveAttributes`
5. Collect pending relationships from the payload for Pass 2

**Pass 2 relationship resolution (`resolveRelationships`):**

1. Group pending relationships by definition key
2. For each group: look up the relationship definition, batch-fetch target entities by external ID
3. Dedup-check each (source, target, definition) triple before creating `EntityRelationshipEntity`
4. Individual failures are caught and logged without aborting the sync

**Field mapping resolution (`resolveFieldMappings`):**

1. Iterate raw JSONB mappings from `CatalogFieldMappingEntity`
2. Skip reserved keys prefixed with `_` (e.g. `_externalIdField`)
3. Resolve each attribute key to a UUID via `attributeKeyMapping`
4. Look up the `SchemaType` from the entity type's schema properties
5. Parse transform blocks into `FieldTransform` variants: `Direct`, `TypeCoercion`, `DefaultValue`, `JsonPathExtraction`

---

## Public Methods

### `transitionToSyncing(connectionId: UUID, workspaceId: UUID)`

Transitions the integration connection to `SYNCING` status. Guards against missing connections, workspace mismatches, already-syncing connections, and non-transitionable states. All guard failures are logged and skipped (no exceptions thrown) so the workflow can proceed.

### `fetchAndProcessRecords(input: IntegrationSyncWorkflowInput): SyncProcessingResult`

Main pipeline entry point. Resolves model context, paginates through all Nango records with heartbeating, processes each batch with deduplication and per-record error isolation, then resolves pending relationships. Returns a `SyncProcessingResult` containing entity type ID, cursor, record counts, synced entity IDs, and success/failure status.

### `finalizeSyncState(connectionId: UUID, entityTypeId: UUID, result: SyncProcessingResult)`

Persists sync state after processing completes. Lazy-creates the `IntegrationSyncStateEntity` if not found. On success: advances cursor and resets consecutive failure count. On failure: preserves last cursor and increments failure count. Runs within `@Transactional`.

### `executeProjections(connectionId: UUID, workspaceId: UUID, entityTypeId: UUID, syncedEntityIds: List<UUID>)`

Pass 3 of the sync pipeline. Delegates to [[EntityProjectionService]] to project integration entities into core lifecycle entities. No-ops if `syncedEntityIds` is empty.

### `evaluateHealth(connectionId: UUID)`

Delegates to `IntegrationHealthService` to evaluate connection health based on sync state history. Runs in its own transaction boundary, separate from `finalizeSyncState`, so health evaluation failures do not roll back sync state.

---

## Gotchas

- **Not a standard Spring service lifecycle:** Although annotated with `@Service`, this class is invoked by Temporal's activity worker, not by controllers or other services. Its methods must be idempotent because Temporal may retry them on failure.
- **Heartbeating is critical for long syncs:** Without `Activity.getExecutionContext().heartbeat(cursor)` after each page, Temporal will time out the activity if pagination takes longer than the configured `startToCloseTimeout`. The heartbeat also enables resume from the last cursor on retry.
- **Per-record error isolation via try-catch:** Each record in `processBatch` is wrapped in try-catch so a single malformed record does not abort the entire batch. This is a deliberate trade-off -- some records may be silently skipped.
- **`attributeKeyMapping` must exist:** If `EntityTypeEntity.attributeKeyMapping` is null (entity type was materialized before this field was introduced), `resolveModelContext` returns null and the sync fails for that model. This is a hard requirement.
- **Relationship resolution is best-effort:** Missing target entities (not yet synced) or undefined relationship definitions are skipped silently. Relationships will be resolved on the next sync cycle when both source and target entities exist.
- **Transaction boundaries are per-record, not per-batch:** `TransactionTemplate.execute` wraps each record's `processRecord` call individually, so a database failure on one record does not roll back previously upserted records in the same batch.
- **External ID field is configurable:** The `_externalIdField` reserved key in field mappings overrides the default `"id"` field. If misconfigured, all records in the batch will fail with "Missing externalId field" errors.

---

## Related

- `IntegrationSyncWorkflowImpl` -- the Temporal workflow that orchestrates these activities
- [[IntegrationSyncStateEntity]] -- persisted sync state entity
- [[IntegrationSyncStateRepository]] -- data access for sync state
- [[SchemaMappingService]] -- payload-to-attribute mapping with field transforms
- [[EntityProjectionService]] -- Pass 3 projection pipeline
- `IntegrationHealthService` -- connection health evaluation
- [[Data Sync]] -- parent subdomain

---

## Changelog

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-04-11 | Documented as part of integration-definitions branch | Data Sync Pipeline |
