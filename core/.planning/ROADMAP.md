# Roadmap: Integration Sync Pipeline

## Overview

Build the data sync pipeline that receives Nango webhook notifications, fetches synced records, maps them to workspace entities with deduplication and relationship resolution, and makes connection health visible. The pipeline is built bottom-up: schema and persistence first, then the connection model is simplified alongside the Nango client and auth webhook so the frontend connect UI can be built in parallel, then the sync webhook and full Temporal workflow, and finally the health aggregation and auth flow refactor that complete the end-to-end story.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Schema and Persistence Foundation** - Database schema changes, JPA entities, enums — everything downstream depends on this (completed 2026-03-16)
- [ ] **Phase 2: Connection Model, Nango Client, and Auth Webhook** - Simplify ConnectionStatus state machine, refactor connection service, extend NangoClientWrapper, implement auth webhook so frontend connect UI can proceed
- [ ] **Phase 3: Sync Webhook and Temporal Sync Workflow** - Sync webhook dispatch to Temporal, paginated record fetching, two-pass upsert + relationship resolution, per-record error isolation, dedup
- [ ] **Phase 4: Health, Auth Flow Refactor, and Documentation** - Connection health aggregation, installation status transitions, auth flow simplification, sync script guidance

## Phase Details

### Phase 1: Schema and Persistence Foundation
**Goal**: The database schema and JPA layer are in place — sync state tracking, dedup index, installation status, and all required enums exist and are queryable
**Depends on**: Nothing (first phase)
**Requirements**: DB-01, DB-02, DB-03, JPA-01, JPA-02, JPA-03, JPA-04, JPA-05
**Success Criteria** (what must be TRUE):
  1. A unique partial index on `entities(workspace_id, source_integration_id, source_external_id)` exists and prevents duplicate integration records at the database level
  2. The `integration_sync_state` table exists and is queryable per-connection and per-entity-type via the repository
  3. `workspace_integration_installations` has a `status` column and `WorkspaceIntegrationInstallationEntity` exposes it with `InstallationStatus` enum values
  4. `SyncStatus` and `InstallationStatus` enums compile and serialize correctly via `@JsonProperty`
  5. `EntityRepository` has the batch dedup query and returns a map-compatible result for O(1) per-record lookups
**Plans:** 2/2 plans complete

Plans:
- [ ] 01-01-PLAN.md — SQL schema DDL, enums, JPA entity, repositories, and entity modification
- [ ] 01-02-PLAN.md — Test factories and unit tests for all Phase 1 artifacts

### Phase 2: Connection Model, Nango Client, and Auth Webhook
**Goal**: The connection status state machine is simplified, the Nango client supports record fetching and sync triggering, the auth webhook endpoint is live with HMAC verification, and an auth event creates a connection and marks the installation ACTIVE — all the backend the frontend needs to build the connect UI
**Depends on**: Phase 1
**Requirements**: CONN-01, CONN-02, CONN-03, NANGO-01, NANGO-02, NANGO-03, HOOK-01, HOOK-02, HOOK-03, HOOK-05, HOOK-06
**Success Criteria** (what must be TRUE):
  1. `PENDING_AUTHORIZATION` and `AUTHORIZING` states are gone from `ConnectionStatus` — code that referenced them either does not compile or has been removed
  2. A connection can transition CONNECTED -> SYNCING -> HEALTHY, CONNECTED -> SYNCING -> DEGRADED, and CONNECTED -> SYNCING -> FAILED without errors
  3. `IntegrationConnectionService` has a private create-or-reconnect method that both the auth webhook handler and any reconnect path can call
  4. `NangoClientWrapper.fetchRecords()` returns paginated records from the Nango API with cursor support
  5. A POST to `/api/v1/webhooks/nango` with a valid HMAC-SHA256 signature is accepted and routed to the correct handler; an invalid or missing signature returns 401
  6. An auth webhook event creates an `IntegrationConnectionEntity` in CONNECTED state, triggers template materialization, and updates the installation status to ACTIVE
  7. A webhook with missing tags or an unresolvable installation logs the error and returns 200
**Plans:** 1/3 plans executed

Plans:
- [ ] 02-01-PLAN.md — ConnectionStatus cleanup (10->8 states), service refactoring, enablement removal, repository query
- [ ] 02-02-PLAN.md — Nango client extensions (fetchRecords, triggerSync) and webhook/record DTOs
- [ ] 02-03-PLAN.md — Webhook HMAC filter, controller, service, auth handler, and tests

### Phase 3: Sync Webhook and Temporal Sync Workflow
**Goal**: Sync webhook events dispatch Temporal workflows, and the workflow fetches all changed records from Nango, applies upsert and delete semantics with deduplication, resolves relationships in a second pass, and isolates per-record errors so one bad record never fails the batch
**Depends on**: Phase 2
**Requirements**: HOOK-04, SYNC-01, SYNC-02, SYNC-03, SYNC-04, SYNC-05, SYNC-06, SYNC-07, SYNC-08
**Success Criteria** (what must be TRUE):
  1. A sync webhook event starts a Temporal workflow with a deterministic workflow ID; a duplicate delivery of the same event does not start a second workflow
  2. The workflow fetches records page by page from the Nango API with heartbeating — large result sets are fully consumed without timeout
  3. ADDED records create entities; ADDED records that already exist are treated as updates (idempotent)
  4. UPDATED records fully replace mapped attributes; UPDATED records that don't exist are created
  5. DELETED records soft-delete the entity; DELETED records with no matching entity are a no-op
  6. A single record that throws during processing is logged and skipped — the rest of the batch continues and completes
  7. After all upserts complete, a second pass resolves relationships between the newly persisted entities
  8. The workflow runs on the `integration.sync` Temporal queue with retry policy: 3 attempts, 30s initial interval, 2x backoff
**Plans:** 1/2 plans executed

Plans:
- [ ] 03-01-PLAN.md — DTOs, workflow/activity interfaces, Temporal config, webhook dispatch + tests
- [ ] 03-02-PLAN.md — Activity implementation (fetch, process, upsert, relationship resolution, finalize) + tests

### Phase 4: Health, Auth Flow Refactor, and Documentation
**Goal**: Connection health is computed from per-entity-type sync state, the auth flow no longer pre-creates connections, installation status reflects real auth outcomes, and sync script design guidance is documented
**Depends on**: Phase 3
**Requirements**: HLTH-01, HLTH-02, HLTH-03, AUTH-01, AUTH-02, AUTH-03, DOCS-01
**Success Criteria** (what must be TRUE):
  1. When all entity types for a connection have SUCCESS sync state, the connection health reports HEALTHY
  2. When any entity type has 3 or more consecutive failures, the connection health reports DEGRADED
  3. When all entity types are in FAILED state, the connection health reports FAILED
  4. Calling `enableIntegration()` creates an installation in PENDING_CONNECTION status and does not create any connection entity
  5. The Nango sync script design guidance document exists and covers sync config, checkpointing, batchSave, and relationship ID patterns
**Plans:** 1/2 plans executed

Plans:
- [ ] 04-01-PLAN.md — IntegrationHealthService, ConnectionStatus state machine fix, finalizeSyncState wiring + tests
- [ ] 04-02-PLAN.md — AUTH-01/02/03 superseded in REQUIREMENTS.md, Nango sync script design guidance doc

## Progress

**Execution Order:**
Phases execute in numeric order: 1 -> 2 -> 3 -> 4

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Schema and Persistence Foundation | 2/2 | Complete   | 2026-03-16 |
| 2. Connection Model, Nango Client, and Auth Webhook | 1/3 | In Progress|  |
| 3. Sync Webhook and Temporal Sync Workflow | 1/2 | In Progress|  |
| 4. Health, Auth Flow Refactor, and Documentation | 1/2 | In Progress|  |
