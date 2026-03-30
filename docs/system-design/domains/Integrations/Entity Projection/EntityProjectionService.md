---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-03-29
Domains:
  - "[[Integrations]]"
---

# EntityProjectionService

## Purpose

Core projection pipeline that transforms integration entities into core lifecycle entities based on projection rules. Called from Temporal sync activities after integration data is ingested (Pass 3). Runs without JWT auth — workspace isolation is enforced by parameter, not `@PreAuthorize`.

## Responsibilities

- Load projection rules for a source entity type and workspace
- Chunk entity batches (100 per chunk) to prevent OOM on large syncs
- Delegate identity resolution to [[IdentityResolutionService]]
- Route entities to update (existing match) or create (new) based on resolution
- Transfer attributes from integration entity to core entity using source-wins merge
- Create PROJECTED source type entities with copied attributes
- Ensure idempotent relationship links between integration and core entities
- Add integration + core entity pairs to identity clusters (best-effort)
- Track per-entity projection outcomes for result reporting

**NOT responsible for:** Rule installation (owned by [[TemplateMaterializationService]]), sync orchestration (owned by Temporal workflows), schema mapping (owned by SchemaMappingService).

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `EntityRepository` | Core entity persistence and workspace-scoped lookups |
| `EntityTypeRepository` | Fetches target entity type for projected entity creation |
| `EntityAttributeRepository` | Reads source attributes for transfer/copy |
| [[EntityAttributeService]] | Saves merged attributes via delete-all + re-insert pattern |
| `EntityRelationshipRepository` | Creates and checks relationship links |
| [[ProjectionRuleRepository]] | Loads projection rules by source type and workspace |
| [[IdentityResolutionService]] | Batch identity resolution for each chunk |
| [[IdentityClusterService]] | Best-effort cluster membership assignment |
| `KLogger` | Structured logging |

## Consumed By

| Consumer | Context |
|----------|---------|
| [[IntegrationSyncActivitiesImpl]] | Calls `processProjections()` during Pass 3 of sync workflow |

## Key Methods

### `processProjections(syncedEntityIds: List<UUID>, workspaceId: UUID, sourceEntityTypeId: UUID): ProjectionResult`

Entry point. Loads rules via `loadProjectionRules()`, iterates each rule, delegates to `processRuleProjections()`. Returns aggregated result with total created/updated/skipped/error counts across all rules. Marked `@Transactional`. Returns early with `skipped = syncedEntityIds.size` if no rules exist for the source type.

### Private: `processRuleProjections(syncedEntityIds, workspaceId, rule): ProjectionResult`

Chunks entities by `CHUNK_SIZE` (100). For each chunk: loads entities via `entityRepository.findByIdIn()`, runs batch identity resolution via `identityResolutionService.resolveBatch()`, then projects each entity individually. Each entity is wrapped in try/catch — one failure does not block the batch. Returns per-rule result.

### Private: `projectSingleEntity(integrationEntity, resolution, workspaceId, rule): ProjectionDetail`

Routes to `updateExistingEntity()` or `createProjectedEntity()` based on resolution result type. For `ResolutionResult.NewEntity`, respects `rule.autoCreate` flag — returns `SKIPPED_AUTO_CREATE_DISABLED` if false.

### Private: `updateExistingEntity(integrationEntity, coreEntityId, workspaceId, rule): ProjectionDetail`

Fetches core entity via `entityRepository.findByIdAndWorkspaceId()`. Guards: returns `SKIPPED_SOFT_DELETED` if entity is null or soft-deleted. Returns `SKIPPED_STALE_VERSION` if `integrationEntity.syncVersion < coreEntity.syncVersion`. Otherwise: transfers attributes (source-wins merge), updates `lastSyncedAt` and `syncVersion` on core entity, ensures relationship link.

### Private: `createProjectedEntity(integrationEntity, workspaceId, rule): ProjectionDetail`

Creates new `EntityEntity` with `sourceType = SourceType.PROJECTED`. Copies `sourceExternalId` from integration entity for future cross-integration matching. Copies all attributes from integration entity. Creates relationship link. Adds both entities to identity cluster (best-effort). Returns `CREATED`.

### Private: `transferAttributes(integrationEntity, coreEntity)`

Reads both entities' attributes via `entityAttributeRepository.findByEntityId()`. Builds merged map: starts with existing core attributes, overlays integration attributes. Saves via `entityAttributeService.saveAttributes()`. User-owned unmapped fields on the core entity are preserved.

### Private: `copyAttributes(integrationEntity, coreEntityId, workspaceId, targetEntityTypeId)`

Reads integration entity attributes, saves directly to new core entity via `entityAttributeService.saveAttributes()`. Used for newly created projected entities (no merge needed).

### Private: `ensureRelationshipLink(integrationEntityId, coreEntityId, workspaceId, rule)`

Idempotent. Returns early if `rule.relationshipDefId` is null. Checks if link exists via `entityRelationshipRepository.findBySourceIdAndTargetIdAndDefinitionId()`. Creates `EntityRelationshipEntity` with `linkSource = SourceType.PROJECTED` if not found.

### Private: `addToIdentityCluster(integrationEntityId, coreEntityId, workspaceId)`

Calls `identityClusterService.resolveClusterMembership()` with system userId `UUID(0,0)` (no user context in Temporal activities). Wrapped in try/catch — failures are logged at WARN level and swallowed. Cluster assignment is best-effort.

## Key Logic

**Chunked processing:** Entities are processed in chunks of 100 (`CHUNK_SIZE`) to prevent OOM on large syncs. Each chunk independently loads entities and runs identity resolution.

**Source-wins merge:** When updating existing core entities, `transferAttributes()` overlays integration attribute values on top of existing core attributes by `attributeId`. Attributes that only exist on the core entity (user-added, unmapped) are preserved in the merged map.

**syncVersion guard:** Before updating a core entity, checks that `integrationEntity.syncVersion >= coreEntity.syncVersion`. This prevents stale integration data from overwriting newer core data (e.g., if a sync retries after a manual edit).

**Best-effort cluster assignment:** Identity cluster assignment is wrapped in try/catch. If the identity resolution domain fails, projection still succeeds — the cluster can be repaired later by the async matching pipeline.

## Gotchas

- **No @PreAuthorize.** Runs in Temporal activity context without JWT. Workspace isolation is by parameter only.
- **System userId for clusters.** Uses `UUID(0,0)` as userId for cluster operations since there is no user context in Temporal activities.
- **Per-entity error isolation.** Each entity projection is wrapped in try/catch within the chunk loop. One entity failing does not block the batch — it increments the error count and adds an ERROR detail.
- **Transactional scope.** The entire `processProjections()` call is `@Transactional`. A failure after partial writes will roll back all projections for this invocation.

## Testing

- **Location:** `src/test/kotlin/riven/core/service/ingestion/EntityProjectionServiceTest.kt`
- **Key scenarios:** Create projected entity, update existing entity, stale version skip, auto-create disabled skip, soft-deleted target skip, chunked processing, error isolation, relationship idempotency

## Flows

- [[Flow - Entity Projection Pipeline]]

## Related

- [[IdentityResolutionService]] — Identity resolution for projection
- [[ProjectionRuleEntity]] — Routing configuration
- [[TemplateMaterializationService]] — Installs projection rules during materialization
- [[Entity Projection]] — Parent subdomain

## Changelog

| Date | Change | Context |
|------|--------|---------|
| 2026-03-29 | Initial implementation — projection pipeline with chunked processing, source-wins merge, syncVersion guard, best-effort cluster assignment | Entity Ingestion Pipeline |
