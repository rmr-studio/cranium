# Requirements: Integration Sync Pipeline

**Defined:** 2026-03-16
**Core Value:** External data flows reliably into workspace entities — records are deduplicated, relationships are resolved, and connection health is visible.

## v1 Requirements

### Database Schema

- [x] **DB-01**: Unique partial index on `entities(workspace_id, source_integration_id, source_external_id)` for integration dedup
- [x] **DB-02**: `integration_sync_state` table tracks per-entity-type sync health per connection
- [x] **DB-03**: `status` column on `workspace_integration_installations` with `InstallationStatus` enum

### Connection Status

- [ ] **CONN-01**: ConnectionStatus enum removes PENDING_AUTHORIZATION and AUTHORIZING states
- [ ] **CONN-02**: ConnectionStatus state machine supports CONNECTED → SYNCING → HEALTHY/DEGRADED/FAILED transitions
- [ ] **CONN-03**: IntegrationConnectionService extracts reusable create-or-reconnect logic as private method

### Webhook

- [ ] **HOOK-01**: `POST /api/v1/webhooks/nango` endpoint with HMAC-SHA256 signature verification
- [ ] **HOOK-02**: Auth webhook creates connection in CONNECTED state and triggers materialization
- [ ] **HOOK-03**: Auth webhook updates installation status to ACTIVE
- [ ] **HOOK-04**: Sync webhook starts Temporal workflow with deterministic ID for dedup
- [ ] **HOOK-05**: Invalid/missing webhook signatures return 401
- [ ] **HOOK-06**: Missing tags or missing installation log errors and return 200 (graceful failure)

### Sync Workflow

- [ ] **SYNC-01**: Temporal workflow fetches records from Nango API with pagination and heartbeating
- [ ] **SYNC-02**: Batch dedup via IN clause lookup with Map for O(1) per-record access
- [ ] **SYNC-03**: ADDED records create new entities; ADDED + exists treats as UPDATE (idempotent)
- [ ] **SYNC-04**: UPDATED records fully replace mapped attributes; UPDATED + not found treats as ADD
- [ ] **SYNC-05**: DELETED records soft-delete entity; DELETED + not found is no-op
- [ ] **SYNC-06**: Per-record try-catch error isolation — one bad record doesn't fail the batch
- [ ] **SYNC-07**: Two-pass relationship resolution (Pass 1: upsert entities, Pass 2: resolve relationships)
- [ ] **SYNC-08**: Dedicated `integration.sync` Temporal queue with retry policy (3 attempts, 30s initial, 2x backoff)

### Health

- [ ] **HLTH-01**: Connection health aggregates across entity types: all SUCCESS → HEALTHY
- [ ] **HLTH-02**: Any entity type with 3+ consecutive failures → DEGRADED
- [ ] **HLTH-03**: All entity types FAILED → FAILED

### Nango Client

- [x] **NANGO-01**: `fetchRecords()` method on NangoClientWrapper for paginated record retrieval
- [x] **NANGO-02**: `triggerSync()` method on NangoClientWrapper
- [ ] **NANGO-03**: `findByNangoConnectionId()` repository query on IntegrationConnectionRepository

### JPA / Enums

- [x] **JPA-01**: IntegrationSyncStateEntity with repository (findByIntegrationConnectionId, findByIntegrationConnectionIdAndEntityTypeId)
- [x] **JPA-02**: SyncStatus enum (PENDING, SUCCESS, FAILED)
- [x] **JPA-03**: InstallationStatus enum (PENDING_CONNECTION, ACTIVE, FAILED)
- [x] **JPA-04**: WorkspaceIntegrationInstallationEntity gains `status` field
- [x] **JPA-05**: EntityRepository gains batch dedup query (findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn)

### Auth Flow

- [ ] **AUTH-01**: `enableIntegration()` creates installation in PENDING_CONNECTION status without creating a connection
- [ ] **AUTH-02**: Materialization and initial sync triggered by auth webhook handler, not enablement
- [ ] **AUTH-03**: Auth webhook handler updates installation to ACTIVE on successful connection

### Documentation

- [ ] **DOCS-01**: Nango sync script design guidance documented (sync config, checkpointing, batchSave, relationship IDs)

## v2 Requirements

### Sync Extensions

- **SYNCX-01**: Bidirectional/outbound sync support
- **SYNCX-02**: Manual re-sync trigger endpoint for admin tooling
- **SYNCX-03**: Batch entity insert optimization for high-volume syncs
- **SYNCX-04**: Connection metrics/observability dashboard

## Out of Scope

| Feature | Reason |
|---------|--------|
| Bidirectional/outbound sync | Different architecture needed — plan covers inbound only |
| Manual re-sync trigger endpoint | Admin tooling, not needed for automated pipeline |
| Webhook retry/dead-letter queue | Nango + Temporal retries are sufficient |
| Nango sync script implementation | TypeScript on Nango infra, separate from Spring Boot repo |
| Connection metrics dashboard | State transitions only — no metrics endpoint needed yet |
| Batch entity insert optimization | Per-entity saves via EntityService for correctness first |
| Remove unused webhookSecret property | Separate cleanup PR |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| DB-01 | Phase 1 | Complete |
| DB-02 | Phase 1 | Complete |
| DB-03 | Phase 1 | Complete |
| JPA-01 | Phase 1 | Complete |
| JPA-02 | Phase 1 | Complete |
| JPA-03 | Phase 1 | Complete |
| JPA-04 | Phase 1 | Complete |
| JPA-05 | Phase 1 | Complete |
| CONN-01 | Phase 2 | Pending |
| CONN-02 | Phase 2 | Pending |
| CONN-03 | Phase 2 | Pending |
| NANGO-01 | Phase 2 | Complete |
| NANGO-02 | Phase 2 | Complete |
| NANGO-03 | Phase 2 | Pending |
| HOOK-01 | Phase 2 | Pending |
| HOOK-02 | Phase 2 | Pending |
| HOOK-03 | Phase 2 | Pending |
| HOOK-05 | Phase 2 | Pending |
| HOOK-06 | Phase 2 | Pending |
| HOOK-04 | Phase 3 | Pending |
| SYNC-01 | Phase 3 | Pending |
| SYNC-02 | Phase 3 | Pending |
| SYNC-03 | Phase 3 | Pending |
| SYNC-04 | Phase 3 | Pending |
| SYNC-05 | Phase 3 | Pending |
| SYNC-06 | Phase 3 | Pending |
| SYNC-07 | Phase 3 | Pending |
| SYNC-08 | Phase 3 | Pending |
| HLTH-01 | Phase 4 | Pending |
| HLTH-02 | Phase 4 | Pending |
| HLTH-03 | Phase 4 | Pending |
| AUTH-01 | Phase 4 | Pending |
| AUTH-02 | Phase 4 | Pending |
| AUTH-03 | Phase 4 | Pending |
| DOCS-01 | Phase 4 | Pending |

**Coverage:**
- v1 requirements: 35 total
- Mapped to phases: 35
- Unmapped: 0

---
*Requirements defined: 2026-03-16*
*Last updated: 2026-03-16 after roadmap revision (4-phase restructure)*
