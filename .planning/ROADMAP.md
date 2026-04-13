# Roadmap: Unified Data Ecosystem — Postgres Adapter

**Created:** 2026-04-12
**Granularity:** standard (7 phases)
**Parallelization:** enabled
**Coverage:** 68/68 v1 requirements mapped

## Overview

Brownfield additive milestone: introduce the IngestionAdapter abstraction, build the first non-Nango adapter (Postgres), and formalize the two-layer (Source / Projection) data model without disturbing the existing Nango integration pipeline. Phases follow the dependency chain: foundation contracts → secure connection management → Postgres adapter + schema mapping → orchestration + sync workflow → projection + identity generalization → health → UI → test consolidation.

## Phases

- [ ] **Phase 1: Adapter Foundation** — Define `IngestionAdapter` contract, `SourceType.CUSTOM_SOURCE`, and `NangoAdapter` wrapper stub
- [ ] **Phase 2: Secure Connection Management** — `CustomSourceConnectionEntity` + SSRF + RO-role + encrypted credentials
- [ ] **Phase 3: Postgres Adapter & Schema Mapping** — `PostgresAdapter`, introspection, column→attribute mapping (incl. NL assist), FK inference
- [ ] **Phase 4: Ingestion Orchestration & Sync Workflow** — `IngestionOrchestrator` service + `CustomSourceSyncWorkflow` Temporal workflow
- [ ] **Phase 5: Projection & Identity Generalization** — Generalize projection/identity to any source type; cross-source + standalone paths
- [ ] **Phase 6: Health Monitoring** — `CustomSourceHealthService` + health API + staleness on table drop
- [ ] **Phase 7: Source Data UI** — Sidebar split, connection flow, mapping UI, readonly viewer, health badges
- [ ] **Phase 8: Test Consolidation & Failure-Mode Coverage** — SSRF/RO/credential/sync/health/cross-source E2E suite

## Phase Details

### Phase 1: Adapter Foundation
**Goal**: The codebase has a unified ingestion contract and a `CUSTOM_SOURCE` source type, with a thin NangoAdapter wrapper ready for future unification — without changing existing Nango runtime behavior.
**Depends on**: Nothing (foundation)
**Requirements**: ADPT-01, ADPT-02, ADPT-03, ADPT-04, ADPT-05
**Success Criteria** (what must be TRUE):
  1. A developer can implement a new source type by implementing `IngestionAdapter` with `introspectSchema()`, `fetchRecords(cursor, limit)`, and `syncMode()`.
  2. `RecordBatch(records, nextCursor, hasMore)` and `SyncMode` enum (POLL, CDC, PUSH, ONE_SHOT) are callable from any service.
  3. `SourceType.CUSTOM_SOURCE` is a valid value on `EntityTypeEntity` and persisted through the JPA layer.
  4. `NangoAdapter` exists and delegates to existing Nango fetch path; the live `IntegrationSyncWorkflowImpl` is unchanged and all existing integration syncs continue to work.
**Plans**: 3 plans
  - [ ] 01-01-PLAN.md — Contract data types (RecordBatch, SourceRecord, SyncMode, SchemaIntrospectionResult) + SourceType.CUSTOM_SOURCE
  - [ ] 01-02-PLAN.md — IngestionAdapter interface + AdapterCallContext sealed hierarchy + sealed AdapterException tree
  - [ ] 01-03-PLAN.md — NangoAdapter delegate + SourceTypeAdapterRegistry bean map

### Phase 2: Secure Connection Management
**Goal**: A workspace owner can create, view, update, and soft-delete a Postgres connection with encrypted credentials, SSRF-safe hostnames, and a verified read-only role — with shipping-blocker security gates enforced.
**Depends on**: Phase 1 (SourceType.CUSTOM_SOURCE for downstream linkage; no runtime dependency, but conceptual)
**Requirements**: CONN-01, CONN-02, CONN-03, CONN-04, CONN-05, SEC-01, SEC-02, SEC-03, SEC-05, SEC-06
**Success Criteria** (what must be TRUE):
  1. A user can POST `/api/v1/custom-sources/connections` with a public Postgres host and succeed; the connection is persisted with credentials encrypted via AES-256-GCM.
  2. A user attempting to connect to localhost, 127.0.0.0/8, 169.254.169.254, RFC1918 ranges, or IPv6 loopback is rejected — including when a hostname resolves to a blocklisted IP (DNS rebinding defense).
  3. A user attempting to connect with a role that has INSERT/UPDATE/DELETE on target tables is rejected with a clear error.
  4. No connection string ever appears in logs (regex redaction for `postgresql://` and `jdbc:postgresql://`), and credential decryption/corruption failures surface as `ConnectionStatus=FAILED` with user-safe messages.
  5. All connection operations are workspace-scoped via `@PreAuthorize` and support soft-delete.
**Plans**: 5 plans
  - [x] 02-00-PLAN.md — Wave 0 test scaffolding + CustomSourceConnectionEntityFactory stub
  - [ ] 02-01-PLAN.md — Entity + repository + SQL DDL + sealed ConnectionException hierarchy + response model
  - [ ] 02-02-PLAN.md — CredentialEncryptionService (AES-256-GCM) + Logback redaction (PatternConverter)
  - [ ] 02-03-PLAN.md — SsrfValidatorService (resolve-once, pin-IP) + ReadOnlyRoleVerifierService (SAVEPOINT probe + privilege sweep)
  - [ ] 02-04-PLAN.md — DTOs + CustomSourceConnectionService (gate chain) + CustomSourceConnectionController (6 REST endpoints) + ExceptionHandler mappings

### Phase 3: Postgres Adapter & Schema Mapping
**Goal**: A user can introspect a connected Postgres database, map columns to entity-type attributes (with NL-assisted suggestions and index warnings), and have readonly `CUSTOM_SOURCE` entity types produced — with FK-inferred relationships where possible.
**Depends on**: Phase 1 (adapter contract), Phase 2 (connection entity)
**Requirements**: PG-01, PG-02, PG-03, PG-04, PG-05, PG-06, PG-07, MAP-01, MAP-02, MAP-03, MAP-04, MAP-05, MAP-06, MAP-07, MAP-08
**Success Criteria** (what must be TRUE):
  1. `GET /api/v1/custom-sources/connections/{id}/schema` returns tables/columns/types drawn live from the target DB via INFORMATION_SCHEMA, using a per-workspace cached HikariCP pool (maxPoolSize=2, idleTimeout=10m, maxLifetime=30m).
  2. A user can persist column-to-attribute mappings per table, assigning LifecycleDomain (or UNCATEGORIZED), SemanticGroup (or CUSTOM), and an identifier column — optionally accepting LLM-suggested values.
  3. Saving mappings creates `EntityTypeEntity` rows with `sourceType=CUSTOM_SOURCE` and `readonly=true`, and FK constraints discovered at introspection produce best-effort `RelationshipDefinitionEntity` rows between mapped types.
  4. `PostgresAdapter.fetchRecords()` honors `WHERE updated_at > :cursor` when present, falls back to PK-based comparison (inserts-only) when not, and returns `RecordBatch` with typed values mapped directly to `EntityAttributePrimitivePayload` (no SchemaMappingService transform).
  5. At the mapping step the UI/API surfaces a warning if the chosen sync-cursor column has no `pg_indexes` entry for the target table.
**Plans**: 5 plans
  - [ ] 03-00-PLAN.md — Wave 0 test scaffolding + entity shells + factories
  - [ ] 03-01-PLAN.md — SQL DDL + mapping entities + repositories + PgTypeMapper + SchemaHasher
  - [ ] 03-02-PLAN.md — WorkspaceConnectionPoolManager + PostgresAdapter + PostgresCallContext + @SourceTypeAdapter(CONNECTOR)
  - [ ] 03-03-PLAN.md — CustomSourceSchemaInferenceService + CustomSourceFieldMappingService + Controller + ExceptionHandler wiring
  - [ ] 03-04-PLAN.md — Spring AI dependency approval + NlMappingSuggestionService (hash-cached, silent-degrade)

### Phase 4: Ingestion Orchestration & Sync Workflow
**Goal**: A Postgres connection syncs on a Temporal schedule (and on manual trigger) through an idempotent orchestrator, producing populated readonly entities with per-record error isolation — without touching the existing Nango workflow.
**Depends on**: Phase 3 (adapter + entity types)
**Requirements**: ORCH-01, ORCH-02, ORCH-03, ORCH-04, ORCH-05, SYNC-01, SYNC-02, SYNC-03, SYNC-04, SYNC-05, SYNC-06, SYNC-07, SEC-04
**Success Criteria** (what must be TRUE):
  1. `IngestionOrchestrator.process(RecordBatch)` runs the pipeline map → upsert → identity-resolve → projection-trigger, resolves records by (sourceType, externalId) before insert, and is safe to re-run on Temporal retry.
  2. A per-record `PersistenceException` is caught, logged with source row ID and entity type, increments `recordsFailed` in the returned `SyncProcessingResult`, and does not abort the batch.
  3. `CustomSourceSyncWorkflowImpl` runs on a per-connection schedule (default 15 min) with activities `fetchRecords`, `processRecords`, `evaluateHealth`, following the existing integration retry policy.
  4. `POST /api/v1/custom-sources/connections/{id}/sync` enqueues a manual sync; rapid re-triggers are queued rather than duplicated, and `max_concurrent_syncs` (default 3) is honored per workspace.
  5. `IntegrationSyncWorkflowImpl` is unchanged and existing SaaS integrations continue syncing exactly as before.
**Plans**: TBD

### Phase 5: Projection & Identity Generalization
**Goal**: Source-layer entities from any source type trigger projection and identity resolution, producing unified core-model rows where applicable, keeping standalone source entities fully usable where not — with per-field provenance preserved.
**Depends on**: Phase 4 (orchestrator calls projection trigger)
**Requirements**: PROJ-01, PROJ-02, PROJ-03, PROJ-04, PROJ-05
**Success Criteria** (what must be TRUE):
  1. `EntityProjectionService.processProjections()` fires after a `CUSTOM_SOURCE` sync just as it does after an integration sync.
  2. `IdentityResolutionService.resolveBatch()` correctly handles entities regardless of their `sourceType`, verified by passing tests covering mixed source types.
  3. A Shopify Customer and a Postgres `customers` row with matching email project into the same CUSTOMER core-model row, with field-level audit showing which source last wrote each field ("most-recent-sync-wins").
  4. A source entity with no core-model match (e.g. Mac's `brands`) stays in the Source Layer, is queryable via the entity query engine, and fires workflow triggers on attribute changes.
**Plans**: TBD

### Phase 6: Health Monitoring
**Goal**: Operators and end users can observe connection health for custom sources using the same pattern as integrations, with degraded/failed transitions driven by sync outcomes and schema drift.
**Depends on**: Phase 4 (sync results feed health)
**Requirements**: HLTH-01, HLTH-02, HLTH-03, HLTH-04, HLTH-05
**Success Criteria** (what must be TRUE):
  1. `CustomSourceHealthService` computes `ConnectionStatus` (HEALTHY / DEGRADED / FAILED) from `consecutiveFailureCount` and `lastSyncedAt`, mirroring `IntegrationHealthService` thresholds.
  2. Consecutive sync failures beyond the configured threshold transition a connection from HEALTHY → DEGRADED → FAILED; a successful sync restores HEALTHY.
  3. If a previously-mapped table disappears between syncs, introspection marks the corresponding entity type stale (not deleted) and sets ConnectionStatus=DEGRADED.
  4. `GET /api/v1/custom-sources/connections/{id}/health` returns the current status and last-sync metadata.
**Plans**: TBD

### Phase 7: Source Data UI
**Goal**: A workspace user can visually distinguish their working models from source data, create and map a Postgres connection end-to-end in the dashboard, and see readonly source entities and connection health at a glance.
**Depends on**: Phases 2, 3, 6 (backend APIs) — can develop against stubs in parallel with Phases 4–6
**Requirements**: UI-01, UI-02, UI-03, UI-04, UI-05, UI-06, UI-07
**Success Criteria** (what must be TRUE):
  1. The workspace sidebar renders two groups — "Your Models" (projection + user-created) and "Source Data" (source layer) — with Source Data sub-grouped by connection (Shopify, Mac's Postgres, etc.).
  2. A user can complete the connection flow (hostname, port, db, user, password) with a client-side hostname sanity check, then view introspected tables/columns and map columns with domain/semantic/identifier selectors and NL suggestions.
  3. Source-Data entity types render in the entity viewer with no edit affordance (readonly).
  4. Each connection in the sidebar displays a health badge reflecting `ConnectionStatus`.
**Plans**: TBD

### Phase 8: Test Consolidation & Failure-Mode Coverage
**Goal**: Every shipping-blocker failure mode (SSRF, read-only enforcement, credential corruption, sync failure/retry, schema drift, cross-source projection, idempotency, concurrency, large-table pagination) has automated coverage before Mac gets access.
**Depends on**: Phases 1–7 (test subjects exist)
**Requirements**: TEST-01, TEST-02, TEST-03, TEST-04, TEST-05, TEST-06, TEST-07, TEST-08, TEST-09, TEST-10, TEST-11, TEST-12, TEST-13, TEST-14, TEST-15, TEST-16, TEST-17, TEST-18, TEST-19, TEST-20
**Success Criteria** (what must be TRUE):
  1. REQUIRED security tests pass: localhost / 127.0.0.1 / 10.0.0.1 / 169.254.169.254 / IPv6 loopback SSRF rejection, DNS-rebinding rejection, and superuser-role rejection.
  2. Happy-path and cross-source E2E tests pass: connect→introspect→map→sync→project populates CUSTOMER; Shopify + Postgres email match yields a single CUSTOMER with per-field provenance; `brands` standalone entity fires a trigger on `reorder_rate` change.
  3. Sync-behavior tests pass: idempotency (no duplicates on re-sync), `updated_at` cursor detects updates, PK fallback detects inserts only, rapid manual re-triggers queue without duplication, `max_concurrent_syncs` honored across 3+ workspaces, >1M-row pagination holds without OOM.
  4. Failure-recovery tests pass: mid-sync DB kill → DEGRADED → next success → HEALTHY; table dropped → DEGRADED + stale type; column type change → per-row warnings + sync continues; FK to unmapped table → skipped gracefully; empty DB → "no tables found"; auth failure → "Auth failed" + FAILED; credential decryption failure + corruption flows surface correct UX with no key in logs.
**Plans**: TBD

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Adapter Foundation | 0/3 | Planned | - |
| 2. Secure Connection Management | 0/5 | Planned | - |
| 3. Postgres Adapter & Schema Mapping | 0/5 | Planned | - |
| 4. Ingestion Orchestration & Sync Workflow | 0/? | Not started | - |
| 5. Projection & Identity Generalization | 0/? | Not started | - |
| 6. Health Monitoring | 0/? | Not started | - |
| 7. Source Data UI | 0/? | Not started | - |
| 8. Test Consolidation & Failure-Mode Coverage | 0/? | Not started | - |

## Parallelization Notes

- Phase 7 (UI) can develop against stubbed backend APIs in parallel with Phases 4–6.
- Within Phase 3, MAP-* (mapping services + UI contracts) can progress in parallel with PG-* (adapter read path) once Phase 2 lands.
- Phase 8 test authoring can begin incrementally as each prior phase lands; the phase itself exists to consolidate and enforce the full matrix before user rollout.

---
*Last updated: 2026-04-12 at roadmap creation*
