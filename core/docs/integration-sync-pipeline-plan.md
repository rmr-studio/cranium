# Integration Sync Pipeline — Implementation Plan

## Context

The integration infrastructure has three layers in place: global catalog (IntegrationDefinitionEntity), workspace connections (IntegrationConnectionEntity), and installation tracking (WorkspaceIntegrationInstallationEntity). Schema mapping (`SchemaMappingService`) and template materialization (`TemplateMaterializationService`) are built. What's missing is the **data sync pipeline** — the mechanism that receives Nango webhook notifications, fetches synced records, maps them to entities, and persists them with deduplication and relationship resolution.

Additionally, the auth flow is being simplified: connections will be created in response to Nango's auth webhook (not pre-created in PENDING_AUTHORIZATION state), and the ConnectionStatus enum will be streamlined.

### Design Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Orchestration | Temporal for all sync operations | Durable execution, built-in retry, visibility |
| Dedup strategy | Existing `entities.source_external_id` + `source_integration_id` columns with unique index | Entities already have these columns — no separate mapping table needed |
| Auth flow | Webhook-driven — connection created on auth webhook | `PENDING_AUTHORIZATION` and `AUTHORIZING` are deprecated dead code |
| Workflow scope | Per-model workflows + connection health aggregator | Granular failure isolation per entity type |
| Temporal queue | Dedicated `integration.sync` queue | Isolates sync load from user-triggered workflows |
| Webhook dedup | Deterministic Temporal workflow ID: `integration-sync-{connectionId}-{syncModel}-{modifiedAfter}` | Nango uses at-least-once delivery; Temporal natively rejects duplicate workflow starts |
| Error handling | Per-record try-catch with error aggregation; Temporal retries with backoff → DEGRADED after max failures | One bad record doesn't fail the batch; matches SchemaMappingService's resilient pattern |
| Update strategy | Full replace of all mapped attributes (external system is source of truth) | Integration entity types are readonly — no user-added attributes to protect |
| Delete strategy | Soft-delete entity only, no cascade | Consistent with existing soft-delete pattern |
| Relationship resolution | Two-pass in-workflow (Pass 1: upsert entities, Pass 2: resolve relationships) | Integrations only relate their own entity types — all targets are in the same sync batch. No persistent queue table needed |
| Sync state granularity | Per-entity-type-per-connection | Enables granular health reporting (e.g., "Contacts healthy, Deals failing") |
| Batch dedup | IN clause batch lookup with Map for O(1) per-record access | Prevents N+1 queries (one per record) during batch processing |
| HMAC verification | Verify `X-Nango-Hmac-Sha256` using `NangoConfigurationProperties.secretKey` | Nango uses the main API secret key for HMAC, not a separate webhook secret |
| Installation status | New `InstallationStatus` enum (PENDING_CONNECTION, ACTIVE, FAILED) on installation entity | Surfaces auth webhook failures to users instead of silent "waiting" state |
| Backfill control | Via Nango connection metadata, read by sync scripts | Script reads `syncScope` and `syncWindowMonths` from metadata |

---

## How Nango Sync Works

Nango operates as a **managed records cache** between external APIs and your backend:

1. **Sync scripts** (TypeScript, deployed on Nango) run on a configurable schedule (min 15s)
2. Scripts handle pagination, rate limits, auth refresh, and checkpointing
3. Records are stored in Nango's cache with **automatic change detection** (ADDED/UPDATED/DELETED via payload hashing)
4. Your backend receives a **webhook notification** that data is ready
5. Your backend **pulls the delta** via `GET /records?modifiedAfter=<timestamp>`
6. Records are retained 30-60 days in cache — you must persist them to your own database

```
External API → Nango Sync Script → Nango Records Cache → Sync Webhook → Your Backend → GET /records → Your Database
```

### Connection Flow

1. Backend calls `createConnectSession()` with tags (`workspaceId`, `integrationDefinitionId`)
2. Frontend opens `nango.openConnectUI({ sessionToken })` — user completes OAuth
3. Nango creates Connection, sends **auth webhook** with connectionId + tags
4. If `autoStart: true`, syncs begin immediately for the new connection
5. When initial sync completes, you receive a **sync webhook** with `syncType: "INITIAL"`

### Backfill & Incremental

- First sync: `getCheckpoint()` returns null → full historical fetch (scope controlled by script)
- Subsequent syncs: checkpoint-based incremental fetch
- Manual re-sync: `POST /sync/trigger { reset: true }` clears checkpoint
- `reset: true, emptyCache: true` = complete fresh start

---

## Phase 1: Database Schema Changes

### 1a. Add unique index to `entities` table

**File:** `db/schema/01_tables/entities.sql` (modify)

Add a unique partial index for integration dedup lookups. This replaces the previously planned `integration_entity_map` table — the entities table already has `source_integration_id` and `source_external_id` columns.

```sql
CREATE UNIQUE INDEX idx_entities_integration_dedup
    ON entities(workspace_id, source_integration_id, source_external_id)
    WHERE source_integration_id IS NOT NULL AND source_external_id IS NOT NULL;
```

### 1b. `integration_sync_state` table

**File:** `db/schema/01_tables/integration_sync_state.sql` (new)

Tracks per-entity-type sync health for the connection health aggregator. One row per `(connection, entity_type)` combination.

```sql
CREATE TABLE IF NOT EXISTS integration_sync_state (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id              UUID         NOT NULL REFERENCES workspaces(id),
    integration_connection_id UUID         NOT NULL REFERENCES integration_connections(id),
    entity_type_id            UUID         NOT NULL REFERENCES entity_types(id),
    last_sync_at              TIMESTAMPTZ,
    last_sync_status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    consecutive_failures      INT          NOT NULL DEFAULT 0,
    last_error                TEXT,
    records_added             INT          DEFAULT 0,
    records_updated           INT          DEFAULT 0,
    records_deleted           INT          DEFAULT 0,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE(integration_connection_id, entity_type_id)
);
```

### 1c. Add `status` column to `workspace_integration_installations` table

**File:** `db/schema/01_tables/integrations.sql` (modify)

Add installation-level status to surface auth webhook failures. Without this, users see "waiting for connection" forever if the webhook fails.

```sql
-- Add to workspace_integration_installations table definition:
"status" VARCHAR(30) NOT NULL DEFAULT 'PENDING_CONNECTION'
```

### 1d. New enums

**`SyncStatus` enum:** `PENDING`, `SUCCESS`, `FAILED` — used by `integration_sync_state.last_sync_status`.

**`InstallationStatus` enum:** `PENDING_CONNECTION`, `ACTIVE`, `FAILED` — used by `workspace_integration_installations.status`.

---

## Phase 2: ConnectionStatus Simplification

### 2a. Simplify ConnectionStatus enum

**File:** `src/main/kotlin/riven/core/enums/integration/ConnectionStatus.kt`

Remove `PENDING_AUTHORIZATION` and `AUTHORIZING` (deprecated dead code — auth is handled entirely by Nango frontend flows). New states:

```
CONNECTED → SYNCING → HEALTHY
                   → DEGRADED
                   → FAILED
HEALTHY → SYNCING | STALE | DEGRADED | DISCONNECTING | FAILED
DEGRADED → HEALTHY | STALE | FAILED | DISCONNECTING
STALE → SYNCING | DISCONNECTING | FAILED
DISCONNECTING → DISCONNECTED | FAILED
DISCONNECTED → CONNECTED
FAILED → CONNECTED | DISCONNECTED
```

Connection is created in `CONNECTED` state directly (from auth webhook).

### 2b. Update IntegrationConnectionService

**File:** `src/main/kotlin/riven/core/service/integration/IntegrationConnectionService.kt`

- Extract the create-or-reconnect + materialize logic from `enableConnection()` into a **reusable private method** (e.g., `createOrReconnectConnection()`)
- Deprecate/remove `enableConnection()` as a public API — connections are now only created via the webhook handler
- Remove `createConnection()` (pre-auth creation path)
- The webhook handler calls the private `createOrReconnectConnection()` method

### 2c. Update IntegrationEnablementService

**File:** `src/main/kotlin/riven/core/service/integration/IntegrationEnablementService.kt`

- `enableIntegration()` no longer creates a connection — it creates the installation record (in `PENDING_CONNECTION` status) and stores sync config
- Materialization and initial sync are triggered by the auth webhook handler, not enablement
- `disableIntegration()` remains largely the same (soft-delete + disconnect via Nango API)

---

## Phase 3: Nango Webhook Endpoint

### 3a. Webhook DTOs

**File:** `src/main/kotlin/riven/core/models/integration/webhook/` (new package)

- `NangoAuthWebhookPayload` — connectionId, providerConfigKey, operation (creation/override/refresh), success, authMode, tags (workspaceId, integrationDefinitionId)
- `NangoSyncWebhookPayload` — connectionId, providerConfigKey, syncName, model, syncType (INITIAL/INCREMENTAL/WEBHOOK), modifiedAfter, responseResults (added/updated/deleted counts)
- `NangoWebhookPayload` — discriminated union wrapper

### 3b. Webhook Controller

**File:** `src/main/kotlin/riven/core/controller/integration/NangoWebhookController.kt` (new)

```
POST /api/v1/webhooks/nango
```

- Verify `X-Nango-Hmac-Sha256` signature using `NangoConfigurationProperties.secretKey` (Nango uses the main API secret key for HMAC — not a separate webhook secret)
- HMAC verification: compute HMAC-SHA256 of raw request body using `secretKey`, compare against header value
- Parse payload type (auth vs sync vs forward)
- Delegate to `NangoWebhookService`
- Return 200 immediately (async processing via Temporal)
- Reject requests with invalid/missing signatures → 401

```kotlin
// HMAC verification (Java crypto API):
val mac = Mac.getInstance("HmacSHA256")
val secretKey = SecretKeySpec(nangoProperties.secretKey.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
mac.init(secretKey)
val computed = mac.doFinal(rawBody.toByteArray(StandardCharsets.UTF_8)).toHexString()
require(computed == request.getHeader("X-Nango-Hmac-Sha256")) { "Invalid webhook signature" }
```

### 3c. Webhook Service

**File:** `src/main/kotlin/riven/core/service/integration/NangoWebhookService.kt` (new)

**Auth webhook handler:**
1. Extract `workspaceId` and `integrationDefinitionId` from tags
2. Validate tags present — if missing, log error and return (do not throw — webhook must return 200)
3. Validate installation exists and is in `PENDING_CONNECTION` status
4. Create connection in CONNECTED state via `IntegrationConnectionService.createOrReconnectConnection()` (extracted private method)
5. Trigger materialization via `TemplateMaterializationService`
6. Update installation status to `ACTIVE`
7. Log activity

**Sync webhook handler:**
1. Look up connection by `nangoConnectionId` (new repository query: `findByNangoConnectionId()`)
2. If not found → log error and return
3. Determine workspace and integration from connection
4. Start `IntegrationSyncWorkflow` via Temporal client with **deterministic workflow ID**: `integration-sync-{connectionId}-{syncModel}-{modifiedAfter}` (Temporal rejects duplicate starts)
5. Log activity

---

## Phase 4: Temporal Sync Workflow

### Data flow diagram

```
                    TEMPORAL WORKFLOW (integration.sync queue)
                    ══════════════════════════════════════════
                    Workflow ID: integration-sync-{connId}-{model}-{modifiedAfter}

┌─────────────────────────────────────────────────────────────────────┐
│ PASS 1: Fetch + Upsert Entities                                     │
│                                                                     │
│  Activity: fetchRecords(nango API, paginated, heartbeating)         │
│   ├─ 429 → retry with backoff (NangoClientWrapper handles)          │
│   ├─ 5xx → retry (Temporal retry policy)                            │
│   └─ Success → List<NangoRecord>                                    │
│                                                                     │
│  Activity: processRecordBatch (per page of ~100 records)            │
│   │                                                                 │
│   │  Batch dedup: SELECT WHERE source_integration_id = ?            │
│   │    AND source_external_id IN (?) → Map<externalId, Entity>      │
│   │                                                                 │
│   │  For each record (per-record try-catch):                        │
│   │   ├─ ADDED + not exists  → create entity via EntityService      │
│   │   ├─ ADDED + exists      → treat as UPDATE (idempotent)         │
│   │   ├─ UPDATED + exists    → full replace mapped attributes       │
│   │   ├─ UPDATED + not found → treat as ADD (self-healing)          │
│   │   ├─ DELETED + exists    → soft-delete entity                   │
│   │   ├─ DELETED + not found → no-op                                │
│   │   └─ Error → catch, add to ProcessBatchResult.errors            │
│   │                                                                 │
│   └─ Returns: ProcessBatchResult(success, failures, errors[])       │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ PASS 2: Resolve Relationships                                       │
│                                                                     │
│  Activity: resolveRelationships                                     │
│   For each entity type with relationship definitions:               │
│    ├─ For each record with external reference IDs:                  │
│    │   ├─ Lookup target entity by source_external_id                │
│    │   │   ├─ FOUND → create relationship via EntityRelationshipSvc │
│    │   │   └─ NOT FOUND → log warning in result                     │
│    └─ Returns: RelationshipResolutionResult(resolved, unresolved)   │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ PASS 3: Update Sync State + Health                                  │
│                                                                     │
│  Activity: updateSyncState                                          │
│   ├─ Write per-entity-type metrics to integration_sync_state        │
│   └─ Evaluate connection health:                                    │
│       ├─ All types SUCCESS → HEALTHY                                │
│       ├─ Any type 3+ consecutive failures → DEGRADED                │
│       └─ All types FAILED → FAILED                                  │
└─────────────────────────────────────────────────────────────────────┘
```

### 4a. Workflow Interface

**File:** `src/main/kotlin/riven/core/service/integration/sync/IntegrationSyncWorkflow.kt` (new)

```kotlin
@WorkflowInterface
interface IntegrationSyncWorkflow {
    @WorkflowMethod
    fun execute(input: IntegrationSyncInput): IntegrationSyncResult
}
```

**Input:** workspaceId, integrationDefinitionId, connectionId, nangoConnectionId, syncModel, syncType (INITIAL/INCREMENTAL), modifiedAfter

**Result:** recordsCreated, recordsUpdated, recordsDeleted, relationshipsResolved, relationshipsUnresolved, errors

### 4b. Sync Activities Interface

**File:** `src/main/kotlin/riven/core/service/integration/sync/IntegrationSyncActivities.kt` (new)

```kotlin
@ActivityInterface
interface IntegrationSyncActivities {
    @ActivityMethod
    fun fetchRecords(connectionId: String, model: String, modifiedAfter: String?): List<NangoRecord>

    @ActivityMethod
    fun processRecordBatch(input: ProcessBatchInput): ProcessBatchResult

    @ActivityMethod
    fun resolveRelationships(input: ResolveRelationshipsInput): RelationshipResolutionResult

    @ActivityMethod
    fun updateSyncState(input: UpdateSyncStateInput)
}
```

`ProcessBatchResult` includes `successCount`, `failureCount`, and `errors: List<RecordError(externalId, errorMessage)>` for per-record error aggregation.

### 4c. Workflow Implementation

**File:** `src/main/kotlin/riven/core/service/integration/sync/IntegrationSyncWorkflowImpl.kt` (new)

```
1. Activity: fetchRecords (paginated, with heartbeating for large backfills)
2. For each page of records:
   a. Activity: processRecordBatch
      - Batch dedup lookup: IN clause on source_external_id → Map<String, EntityEntity>
      - Per-record processing with try-catch error isolation
      - ADDED/UPDATED/DELETED handling (see data flow diagram above)
3. Activity: resolveRelationships (two-pass: all entities now exist from Pass 1)
4. Activity: updateSyncState (write per-entity-type health to integration_sync_state)
5. Activity: evaluateConnectionHealth (aggregate across entity types → ConnectionStatus transition)
```

### 4d. Activities Implementation

**File:** `src/main/kotlin/riven/core/service/integration/sync/IntegrationSyncActivitiesImpl.kt` (new)

Spring bean with injected dependencies:
- `NangoClientWrapper` — for fetching records from Nango API
- `SchemaMappingService` — for transforming external payloads to entity attributes
- `EntityService` — for creating/updating entities
- `EntityRepository` — for batch dedup lookups (`findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn()`)
- `EntityRelationshipService` — for relationship creation in Pass 2
- `IntegrationSyncStateRepository` — for health tracking
- `IntegrationConnectionHealthService` — for connection health evaluation

### 4e. Temporal Worker Registration

**File:** `src/main/kotlin/riven/core/configuration/integration/IntegrationSyncTemporalConfiguration.kt` (new)

- Define new task queue: `INTEGRATION_SYNC_QUEUE = "integration.sync"`
- Register `IntegrationSyncWorkflowImpl` with factory (inject activities)
- Register `IntegrationSyncActivitiesImpl` as activity implementation
- Configure retry policy: 3 attempts, 30s initial interval, 2x backoff, 5min max interval

---

## Phase 5: Connection Health Aggregator

### 5a. Health Service

**File:** `src/main/kotlin/riven/core/service/integration/IntegrationConnectionHealthService.kt` (new)

- `evaluateConnectionHealth(connectionId)` — reads `integration_sync_state` for all entity types on a connection
  - All entity types SUCCESS → transition connection to HEALTHY
  - Any entity type has `consecutiveFailures >= 3` → transition to DEGRADED
  - All entity types FAILED → transition to FAILED
- Called by the sync workflow's `updateSyncState` activity after each sync completes

---

## Phase 6: NangoClientWrapper Extensions

### 6a. Add record fetching to NangoClientWrapper

**File:** `src/main/kotlin/riven/core/service/integration/NangoClientWrapper.kt`

Add methods:
- `fetchRecords(connectionId: String, model: String, modifiedAfter: String?, cursor: String?, limit: Int): NangoRecordsResponse`
- `triggerSync(connectionId: String, syncName: String, fullResync: Boolean)`

These use Nango's REST API:
- `GET /records?model={model}&connection_id={connectionId}&modified_after={timestamp}`
- `POST /sync/trigger` with `{ connectionId, syncNames, fullResync }`

### 6b. Add `findByNangoConnectionId` to IntegrationConnectionRepository

**File:** `src/main/kotlin/riven/core/repository/integration/IntegrationConnectionRepository.kt`

```kotlin
fun findByNangoConnectionId(nangoConnectionId: String): IntegrationConnectionEntity?
```

Required by the sync webhook handler to resolve Nango's connection ID to the internal connection entity.

---

## Phase 7: JPA Entities & Repositories

### 7a. IntegrationSyncStateEntity + Repository

- **Entity:** `src/main/kotlin/riven/core/entity/integration/IntegrationSyncStateEntity.kt`
  - Does NOT extend `AuditableEntity` or implement `SoftDeletable` (system-managed, not user-facing)
  - Uses `SyncStatus` enum with `@Enumerated(EnumType.STRING)` for `lastSyncStatus`
- **Repository:** `src/main/kotlin/riven/core/repository/integration/IntegrationSyncStateRepository.kt`
  - `findByIntegrationConnectionId(connectionId: UUID): List<IntegrationSyncStateEntity>`
  - `findByIntegrationConnectionIdAndEntityTypeId(connectionId: UUID, entityTypeId: UUID): IntegrationSyncStateEntity?`

### 7b. Update WorkspaceIntegrationInstallationEntity

- **File:** `src/main/kotlin/riven/core/entity/integration/WorkspaceIntegrationInstallationEntity.kt`
  - Add `status: InstallationStatus` field with `@Enumerated(EnumType.STRING)`, default `PENDING_CONNECTION`

### 7c. New enums

- **File:** `src/main/kotlin/riven/core/enums/integration/SyncStatus.kt` (new)
  - Values: `PENDING`, `SUCCESS`, `FAILED`
- **File:** `src/main/kotlin/riven/core/enums/integration/InstallationStatus.kt` (new)
  - Values: `PENDING_CONNECTION`, `ACTIVE`, `FAILED`

### 7d. Add batch dedup query to EntityRepository

**File:** `src/main/kotlin/riven/core/repository/entity/EntityRepository.kt`

```kotlin
fun findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(
    workspaceId: UUID,
    sourceIntegrationId: UUID,
    externalIds: Collection<String>
): List<EntityEntity>
```

---

## Phase 8: Nango Sync Script Design (Guidance)

Nango sync scripts are TypeScript, deployed on Nango's infrastructure. Not part of this Spring Boot codebase, but the design must align.

### Key principles for sync scripts:

1. **Read sync config from connection metadata:**
   - On enablement, your backend sets connection metadata via Nango API: `{ syncScope: "ALL"|"RECENT", syncWindowMonths: 3 }`
   - Script reads: `const metadata = await nango.getMetadata<SyncConfig>()`

2. **Checkpoint-based incremental sync:**
   - First run: `getCheckpoint()` returns null → fetch all (or limited by syncWindowMonths)
   - Subsequent runs: use checkpoint (timestamp/cursor) to fetch only changes

3. **Use `batchSave()` for change detection:**
   - Nango computes payload hashes automatically
   - Records marked ADDED/UPDATED/DELETED in `_nango_metadata`

4. **One script per model per integration:**
   - `hubspot-contacts.ts`, `hubspot-deals.ts`, `hubspot-companies.ts`
   - Each defines its own frequency, model schema (Zod), and fetch logic

5. **External relationship IDs:**
   - Include association IDs in the record payload (e.g., `associatedContactIds` on deals)
   - These are resolved on your backend in the two-pass workflow (Pass 2)

---

## Phase 9: Auth Flow Refactor — EnableIntegration Endpoint

### Current flow:
1. `POST /enable` → creates installation + connection (PENDING_AUTH) + materializes templates

### New flow:
1. `POST /enable` → creates installation record in `PENDING_CONNECTION` status, stores sync config
2. Frontend opens Nango Connect UI with `tags: { workspaceId, integrationDefinitionId }`
3. Nango auth webhook → creates connection (CONNECTED) + materializes templates + updates installation to `ACTIVE`
4. Nango sync starts automatically (`autoStart: true`)
5. Nango sync webhook → triggers Temporal sync workflow

### Changes:
- `IntegrationEnablementService.enableIntegration()` — remove connection creation and materialization calls; set installation status to `PENDING_CONNECTION`
- `IntegrationController` — response indicates installation is in `PENDING_CONNECTION` state; frontend polls for `ACTIVE`
- Auth webhook handler updates installation to `ACTIVE` on successful connection creation
- If auth webhook fails (bad tags, installation not found), installation remains `PENDING_CONNECTION` — frontend can timeout and show error; a scheduled job or manual check can transition to `FAILED` after N minutes

---

## Verification Plan

### Test infrastructure (create first):
- `IntegrationConnectionFactory` — factory for `IntegrationConnectionEntity` test data
- `IntegrationDefinitionFactory` — factory for `IntegrationDefinitionEntity` test data
- `IntegrationSyncStateFactory` — factory for `IntegrationSyncStateEntity` test data
- `WorkspaceIntegrationInstallationFactory` — factory for installation test data

### Unit test matrix:

**NangoWebhookControllerTest / NangoWebhookServiceTest:**

| # | Test case | Verifies |
|---|---|---|
| T1 | HMAC verification — valid signature accepted | Webhook security |
| T2 | HMAC verification — invalid/missing signature → 401 | Webhook security |
| T3 | Auth webhook — valid tags → connection created + materialized + installation ACTIVE | Auth happy path |
| T4 | Auth webhook — missing tags → error logged, 200 returned | Graceful failure |
| T5 | Auth webhook — installation not found → error logged, 200 returned | Graceful failure |
| T6 | Auth webhook — existing connection → idempotent reconnect | Reconnection |
| T7 | Sync webhook — connection found → workflow started with deterministic ID | Sync happy path |
| T8 | Sync webhook — connection not found → error logged, 200 returned | Graceful failure |

**IntegrationSyncActivitiesImplTest:**

| # | Test case | Verifies |
|---|---|---|
| T9 | fetchRecords — successful paginated fetch | Nango API integration |
| T10 | fetchRecords — Nango 429 → retry | Rate limit handling |
| T11 | fetchRecords — Nango 5xx → retry + eventual failure | Transient error handling |
| T12 | ADDED record — new entity created with source fields set | Entity creation |
| T13 | ADDED record — already exists → treated as UPDATE (idempotent) | Dedup / idempotency |
| T14 | UPDATED record — entity found → attributes fully replaced | Attribute update |
| T15 | UPDATED record — not found → treated as ADD (self-healing) | Self-healing |
| T16 | DELETED record — entity found → soft-deleted | Soft delete |
| T17 | DELETED record — not found → no-op | Idempotent delete |
| T18 | Per-record error isolation — one bad record doesn't fail batch | Error aggregation |
| T19 | Batch dedup lookup uses IN clause (not N+1) | Performance |

**Relationship resolution (Pass 2):**

| # | Test case | Verifies |
|---|---|---|
| T20 | Target entity exists → relationship created | Happy path |
| T21 | Target entity missing → warning logged in result | Graceful handling |

**IntegrationConnectionHealthServiceTest:**

| # | Test case | Verifies |
|---|---|---|
| T22 | All entity types SUCCESS → HEALTHY | Health aggregation |
| T23 | Any entity type 3+ failures → DEGRADED | Degradation threshold |
| T24 | All entity types FAILED → FAILED | Complete failure |

**ConnectionStatus state machine:**

| # | Test case | Verifies |
|---|---|---|
| T25 | Valid transitions accepted (CONNECTED→SYNCING, SYNCING→HEALTHY, etc.) | State machine |
| T26 | Invalid transitions rejected | State machine |

### Integration tests:

| # | Test case | Verifies |
|---|---|---|
| T27 | E2E: auth webhook → connection + materialization + installation ACTIVE | Full auth flow |
| T28 | E2E: sync webhook → entities created with correct attributes | Full sync flow |
| T29 | Gap recovery: disable → re-enable → modifiedAfter uses lastSyncedAt | Reconnection sync |

### Manual verification:
- `./gradlew build` — compilation check
- `./gradlew test` — full test suite
- Review Temporal workflow visibility in Temporal UI for sync workflow executions

---

## Implementation Order

1. **Phase 1** — Database schema (unique index on entities + integration_sync_state table + installation status column + new enums)
2. **Phase 7** — JPA entities, repositories, enums (depends on schema)
3. **Phase 2** — ConnectionStatus simplification + IntegrationConnectionService refactor (standalone)
4. **Phase 6** — NangoClientWrapper extensions + findByNangoConnectionId repository query
5. **Phase 3** — Webhook endpoint + service (depends on repositories + connection service)
6. **Phase 4** — Temporal sync workflow + activities (depends on everything above)
7. **Phase 5** — Connection health aggregator (depends on sync state entity)
8. **Phase 9** — Auth flow refactor (depends on webhook handler being ready)
9. **Phase 8** — Nango sync script guidance (documentation, separate from backend)

### Critical files to modify:
- `db/schema/01_tables/entities.sql` — add unique dedup index
- `db/schema/01_tables/integrations.sql` — add installation status column
- `src/main/kotlin/riven/core/enums/integration/ConnectionStatus.kt` — remove deprecated states
- `src/main/kotlin/riven/core/service/integration/IntegrationConnectionService.kt` — extract private method, deprecate enableConnection()
- `src/main/kotlin/riven/core/service/integration/IntegrationEnablementService.kt` — remove connection/materialization, add installation status
- `src/main/kotlin/riven/core/service/integration/NangoClientWrapper.kt` — add fetchRecords(), triggerSync()
- `src/main/kotlin/riven/core/repository/integration/IntegrationConnectionRepository.kt` — add findByNangoConnectionId()
- `src/main/kotlin/riven/core/repository/entity/EntityRepository.kt` — add batch dedup query
- `src/main/kotlin/riven/core/entity/integration/WorkspaceIntegrationInstallationEntity.kt` — add status field

### Critical files to create:
- Webhook controller + service + DTOs (3 files)
- Temporal workflow interface + impl (2 files)
- Temporal activities interface + impl (2 files)
- IntegrationSyncStateEntity + repository (2 files)
- SyncStatus + InstallationStatus enums (2 files)
- Health service (1 file)
- Temporal sync queue configuration (1 file)
- Test factories (4 files)

**Total new files: ~17 (including test factories)**

### Existing utilities to reuse:
- `SchemaMappingService` (`service.integration.mapping`) — payload → entity attribute mapping
- `TemplateMaterializationService` (`service.integration.materialization`) — catalog → workspace entity types
- `NangoClientWrapper` (`service.integration`) — Nango API client with retry logic
- `EntityService` (`service.entity`) — entity CRUD
- `EntityRelationshipService` (`service.entity`) — relationship management
- `ActivityService` (`service.activity`) — audit logging
- `ServiceUtil.findOrThrow` — repository lookup pattern
- `@WithUserPersona` — test security context
- Factory classes in `service/util/factory/` — test data construction

---

## NOT in Scope

| Deferred item | Rationale |
|---|---|
| Bidirectional sync (outbound) | Plan only covers inbound. `SyncDirection.OUTBOUND` and `BIDIRECTIONAL` enums exist but are unused. Different architecture needed. |
| Manual re-sync trigger endpoint | `POST /sync/trigger` to Nango. Useful for admin tooling but not needed for automated pipeline. |
| Webhook retry/dead-letter for failed processing | Nango retries webhook delivery. If processing fails, Temporal retries. No separate DLQ needed yet. |
| Nango sync script implementation | Phase 8 is guidance only. Scripts are TypeScript deployed on Nango's infra, not this repo. |
| Connection metrics/observability dashboard | Health aggregator produces state transitions, but no metrics endpoint or dashboard integration. |
| Batch entity insert optimization | Per-entity saves via `EntityService` are correct but slower than raw batch inserts. Optimize if sync volumes demand it. |
| Rate limit handling for Nango record fetching | `NangoClientWrapper` already has retry logic for 429s. No additional rate limiter needed unless Nango changes limits. |
| Remove `NangoConfigurationProperties.webhookSecret` | Nango uses `secretKey` for HMAC. The unused `webhookSecret` property should be removed in a separate PR. |

---

## Review Changelog

Changes from engineering review (2026-03-16):

1. **Eliminated `integration_entity_map` table** — entities already have `source_external_id` + `source_integration_id`; added unique index instead
2. **Eliminated `pending_relationship_queue` table** — replaced with two-pass in-workflow resolution (Pass 1: upsert entities, Pass 2: resolve relationships)
3. **HMAC verification uses `secretKey`** — Nango uses the main API key, not a separate webhook secret
4. **Dedicated `integration.sync` Temporal queue** — isolates sync load from user workflows
5. **Per-entity-type-per-connection sync state** — granular health vs. per-connection
6. **Extracted reusable connection logic** — private method from `enableConnection()` for webhook handler reuse
7. **Per-record error isolation** — try-catch per record in batch processing, error aggregation in result
8. **Batch IN clause dedup** — prevents N+1 queries during record processing
9. **Deterministic Temporal workflow ID** — native webhook dedup via `integration-sync-{connectionId}-{syncModel}-{modifiedAfter}`
10. **Installation-level status** — `InstallationStatus` enum (PENDING_CONNECTION, ACTIVE, FAILED) surfaces auth failures
11. **`SyncStatus` enum** — replaces string literals in sync state table
12. **Expanded test matrix** — 29 test cases (was 8), including test factories
13. **Net reduction: 2 tables, ~6 files** eliminated from original plan
