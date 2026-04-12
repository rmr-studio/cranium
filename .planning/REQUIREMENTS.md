# Requirements: Unified Data Ecosystem — Postgres Adapter

**Defined:** 2026-04-12
**Core Value:** Any data source → unified entity model → trigger → action → measurement loop

## v1 Requirements

### Adapter Foundation (ADPT)

- [ ] **ADPT-01**: `IngestionAdapter` interface defines `introspectSchema()`, `fetchRecords(cursor, limit)`, `syncMode()`
- [ ] **ADPT-02**: `RecordBatch` data class with `records`, `nextCursor`, `hasMore`
- [ ] **ADPT-03**: `SyncMode` enum: POLL, CDC, PUSH, ONE_SHOT
- [ ] **ADPT-04**: `SourceType` enum extended with `CUSTOM_SOURCE` value
- [ ] **ADPT-05**: `NangoAdapter` thin wrapper delegates to existing Nango fetch path (not wired into live sync)

### Custom Source Connection (CONN)

- [ ] **CONN-01**: `CustomSourceConnectionEntity` extends AuditableEntity, implements SoftDeletable
- [ ] **CONN-02**: Credentials stored as encrypted JSONB (AES-256-GCM, app-level key)
- [ ] **CONN-03**: `CustomSourceConnectionService` CRUD with `@PreAuthorize` workspace scoping
- [ ] **CONN-04**: Connection string NEVER logged (KLogger redaction patterns for `postgresql://`, `jdbc:postgresql://`)
- [ ] **CONN-05**: User can create, view, update, soft-delete Postgres connections via `/api/v1/custom-sources/connections`

### Security Gates (SEC)

- [ ] **SEC-01**: SSRF validation rejects localhost, 127.0.0.0/8, 169.254.169.254, RFC1918 ranges, IPv6 loopback
- [ ] **SEC-02**: SSRF validation resolves hostname to IP and checks resolved IP (DNS rebinding defense)
- [ ] **SEC-03**: Read-only role enforcement: reject connection if role has INSERT/UPDATE/DELETE on target tables
- [ ] **SEC-04**: Per-record error isolation in sync (one bad row does not fail batch)
- [ ] **SEC-05**: `CryptoException` at credential read → ConnectionStatus=FAILED, "Config error" message, no key in logs
- [ ] **SEC-06**: `DataCorruptionException` at credential read → ConnectionStatus=FAILED, prompt user to re-enter

### Postgres Adapter (PG)

- [ ] **PG-01**: `PostgresAdapter` connects via JDBC using per-workspace cached HikariCP pool (maxPoolSize=2, idleTimeout=10m, maxLifetime=30m)
- [ ] **PG-02**: `introspectSchema()` queries INFORMATION_SCHEMA for tables/columns/types
- [ ] **PG-03**: `fetchRecords()` uses `WHERE updated_at > :lastSyncCursor` with cursor-based pagination
- [ ] **PG-04**: Fallback cursor strategy for tables without `updated_at`: primary-key-based comparison (inserts only)
- [ ] **PG-05**: Postgres column values mapped directly to `EntityAttributePrimitivePayload` (bypass SchemaMappingService)
- [ ] **PG-06**: `syncMode()` returns `POLL`
- [ ] **PG-07**: FK constraint introspection creates `RelationshipDefinitionEntity` between mapped entity types (best-effort)

### Schema Inference + Mapping (MAP)

- [ ] **MAP-01**: `CustomSourceSchemaInferenceService` exposes introspection results via `/api/v1/custom-sources/connections/{id}/schema`
- [ ] **MAP-02**: `CustomSourceFieldMappingService` persists user column → attribute mappings
- [ ] **MAP-03**: Mapping UI allows assigning LifecycleDomain (or UNCATEGORIZED) per table
- [ ] **MAP-04**: Mapping UI allows assigning SemanticGroup (or CUSTOM) per table
- [ ] **MAP-05**: Mapping UI allows selecting identifier column (email, ID, etc.)
- [ ] **MAP-06**: Mapping step shows warning if chosen sync-cursor column has no `pg_indexes` entry
- [ ] **MAP-07**: NL-assisted mapping: LLM suggests domain, semantic group, identifier from column names + sample data
- [ ] **MAP-08**: Saving mappings creates entity types with `sourceType=CUSTOM_SOURCE`, `readonly=true`

### Ingestion Orchestrator (ORCH)

- [ ] **ORCH-01**: `IngestionOrchestrator` is a plain Spring service (not a Temporal activity)
- [ ] **ORCH-02**: `process(RecordBatch)` pipeline: map → upsert → identity resolve → projection trigger
- [ ] **ORCH-03**: Per-record idempotent (resolved by sourceType + externalId before insert)
- [ ] **ORCH-04**: `PersistenceException` caught per-record, logged with source row ID + entity type, recordsFailed incremented
- [ ] **ORCH-05**: Returns `SyncProcessingResult` with recordsSynced, recordsFailed, cursor

### Sync Workflow (SYNC)

- [ ] **SYNC-01**: `CustomSourceSyncWorkflowImpl` Temporal workflow scheduled per-connection (default 15 min)
- [ ] **SYNC-02**: Activities: `fetchRecords` (PostgresAdapter), `processRecords` (IngestionOrchestrator), `evaluateHealth` (CustomSourceHealthService)
- [ ] **SYNC-03**: Temporal retry policy matches existing integration pattern
- [ ] **SYNC-04**: Manual sync trigger via `POST /api/v1/custom-sources/connections/{id}/sync`
- [ ] **SYNC-05**: `max_concurrent_syncs` per workspace configurable (default 3)
- [ ] **SYNC-06**: Rapid re-trigger queued, not duplicated
- [ ] **SYNC-07**: Existing `IntegrationSyncWorkflowImpl` unchanged

### Projection + Identity Resolution (PROJ)

- [ ] **PROJ-01**: `EntityProjectionService.processProjections()` generalized to fire after any sync (not just integration)
- [ ] **PROJ-02**: `IdentityResolutionService.resolveBatch()` accepts any source type (already agnostic — verify + cover in tests)
- [ ] **PROJ-03**: Cross-source projection: Shopify Customer + Postgres customers → same CUSTOMER core model row
- [ ] **PROJ-04**: Standalone source entities (no core model match) stay in Source Layer, queryable, trigger-able
- [ ] **PROJ-05**: Field ownership on projected entities: most-recent-sync-wins; field-level audit trail tracks provenance

### Health Monitoring (HLTH)

- [ ] **HLTH-01**: `CustomSourceHealthService` evaluates ConnectionStatus from sync results (consecutiveFailureCount, lastSyncedAt)
- [ ] **HLTH-02**: Reuses existing `ConnectionStatus` enum (HEALTHY / DEGRADED / FAILED)
- [ ] **HLTH-03**: Consecutive failures beyond threshold → DEGRADED → FAILED (mirrors IntegrationHealthService)
- [ ] **HLTH-04**: Table-dropped detection during introspection → DEGRADED, entity type marked stale (not deleted)
- [ ] **HLTH-05**: `GET /api/v1/custom-sources/connections/{id}/health` returns status

### Frontend UI (UI)

- [ ] **UI-01**: Sidebar splits "Your Models" (projection layer + user-created) from "Source Data" (source layer)
- [ ] **UI-02**: Source Data section groups entity types by connection (Shopify / Mac's Postgres / etc.)
- [ ] **UI-03**: Postgres connection creation flow (hostname, port, db, user, password) with client-side hostname sanity check
- [ ] **UI-04**: Schema introspection UI displays discovered tables/columns
- [ ] **UI-05**: Column mapping UI with domain/semantic/identifier selectors + NL suggestions
- [ ] **UI-06**: Source Data entity types render readonly in entity viewer (no edit affordance)
- [ ] **UI-07**: Connection health badge in sidebar reflecting ConnectionStatus

### Testing (TEST)

- [ ] **TEST-01**: SSRF test suite: localhost, 127.0.0.1, 10.0.0.1, 169.254.169.254, IPv6 loopback all rejected (REQUIRED)
- [ ] **TEST-02**: DNS rebinding test: hostname resolving to blocklisted IP rejected (REQUIRED)
- [ ] **TEST-03**: Read-only role test: superuser / write-capable role rejected
- [ ] **TEST-04**: Credential decryption failure → ConnectionStatus=FAILED + no key in logs
- [ ] **TEST-05**: Corrupted credential data → user re-entry prompt
- [ ] **TEST-06**: Happy path E2E: connect → introspect → map → sync → project (CUSTOMER populated)
- [ ] **TEST-07**: Cross-source projection E2E: Shopify + Postgres email match → single CUSTOMER with per-field provenance
- [ ] **TEST-08**: Standalone source entity E2E: brands table → BRANDS type, triggers fire on reorder_rate change
- [ ] **TEST-09**: Sync idempotency: same sync twice → no duplicate entities
- [ ] **TEST-10**: Insert/Update detection via `updated_at` cursor
- [ ] **TEST-11**: PK fallback cursor detects inserts (no update detection)
- [ ] **TEST-12**: Table dropped between syncs → ConnectionStatus=DEGRADED, entity type stale
- [ ] **TEST-13**: Column type change → per-row transform warnings, sync continues
- [ ] **TEST-14**: FK pointing to unmapped table → relationship skipped gracefully
- [ ] **TEST-15**: Concurrent syncs across 3+ workspaces honor max_concurrent_syncs
- [ ] **TEST-16**: Large table (>1M rows) cursor pagination holds, no OOM
- [ ] **TEST-17**: Mid-sync DB kill → Temporal retries → DEGRADED → next success → HEALTHY
- [ ] **TEST-18**: Empty database (no tables) → "no tables found" message
- [ ] **TEST-19**: Auth failure on connect → "Auth failed" message, ConnectionStatus=FAILED
- [ ] **TEST-20**: Rapid manual sync re-trigger → queued, not duplicated

## v2 Requirements

### Change Data Capture (CDC)

- **CDC-01**: Logical replication adapter for Postgres 10+ with `wal_level=logical`
- **CDC-02**: Real-time INSERT/UPDATE/DELETE detection via replication slot
- **CDC-03**: Adapter variant selection (POLL vs CDC) per connection

### Cross-Source Identity (XSID)

- **XSID-01**: Direct source↔source entity linking when matching identifiers found across sources
- **XSID-02**: "Show all source records for this customer" view
- **XSID-03**: Extend IdentityMatchCandidateService for TYPE A ↔ TYPE B join-based matching

### Other Adapters

- **CSV-01**: CSV one-shot adapter
- **WHK-01**: Webhook push adapter
- **API-01**: Programmatic API push adapter

### Security Hardening

- **SECV2-01**: Supabase vault / external secret manager for credentials
- **SECV2-02**: Credential key rotation strategy
- **SECV2-03**: SSH tunnel connection topology
- **SECV2-04**: Agent-based connection topology

### Operations

- **OPSV2-01**: Schema reconciliation for custom source drift
- **OPSV2-02**: Aggregation columns on custom source entities
- **OPSV2-03**: Unify IntegrationSyncWorkflowImpl onto IngestionOrchestrator

## Out of Scope

| Feature | Reason |
|---------|--------|
| CDC adapter | Deferred — poll-based validated first (TODO-CS-002) |
| CSV adapter | No current demand |
| Webhook adapter | No current demand |
| Cross-source source↔source identity | Deferred to v2 (TODO-CS-001) |
| Full Nango pipeline refactor | Risk; thin wrapper only |
| Schema reconciliation for custom sources | No drift signal yet |
| Aggregation columns on custom source entities | Depends on separate feature |
| Credential key rotation | Security review track |
| SSH tunnel / agent topology | Direct TCP only for MVP |
| DELETE detection via polling | Too expensive; deferred to CDC |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| ADPT-01 | Phase 1 | Pending |
| ADPT-02 | Phase 1 | Pending |
| ADPT-03 | Phase 1 | Pending |
| ADPT-04 | Phase 1 | Pending |
| ADPT-05 | Phase 1 | Pending |
| CONN-01 | Phase 2 | Pending |
| CONN-02 | Phase 2 | Pending |
| CONN-03 | Phase 2 | Pending |
| CONN-04 | Phase 2 | Pending |
| CONN-05 | Phase 2 | Pending |
| SEC-01 | Phase 2 | Pending |
| SEC-02 | Phase 2 | Pending |
| SEC-03 | Phase 2 | Pending |
| SEC-04 | Phase 4 | Pending |
| SEC-05 | Phase 2 | Pending |
| SEC-06 | Phase 2 | Pending |
| PG-01 | Phase 3 | Pending |
| PG-02 | Phase 3 | Pending |
| PG-03 | Phase 3 | Pending |
| PG-04 | Phase 3 | Pending |
| PG-05 | Phase 3 | Pending |
| PG-06 | Phase 3 | Pending |
| PG-07 | Phase 3 | Pending |
| MAP-01 | Phase 3 | Pending |
| MAP-02 | Phase 3 | Pending |
| MAP-03 | Phase 3 | Pending |
| MAP-04 | Phase 3 | Pending |
| MAP-05 | Phase 3 | Pending |
| MAP-06 | Phase 3 | Pending |
| MAP-07 | Phase 3 | Pending |
| MAP-08 | Phase 3 | Pending |
| ORCH-01 | Phase 4 | Pending |
| ORCH-02 | Phase 4 | Pending |
| ORCH-03 | Phase 4 | Pending |
| ORCH-04 | Phase 4 | Pending |
| ORCH-05 | Phase 4 | Pending |
| SYNC-01 | Phase 4 | Pending |
| SYNC-02 | Phase 4 | Pending |
| SYNC-03 | Phase 4 | Pending |
| SYNC-04 | Phase 4 | Pending |
| SYNC-05 | Phase 4 | Pending |
| SYNC-06 | Phase 4 | Pending |
| SYNC-07 | Phase 4 | Pending |
| PROJ-01 | Phase 5 | Pending |
| PROJ-02 | Phase 5 | Pending |
| PROJ-03 | Phase 5 | Pending |
| PROJ-04 | Phase 5 | Pending |
| PROJ-05 | Phase 5 | Pending |
| HLTH-01 | Phase 6 | Pending |
| HLTH-02 | Phase 6 | Pending |
| HLTH-03 | Phase 6 | Pending |
| HLTH-04 | Phase 6 | Pending |
| HLTH-05 | Phase 6 | Pending |
| UI-01 | Phase 7 | Pending |
| UI-02 | Phase 7 | Pending |
| UI-03 | Phase 7 | Pending |
| UI-04 | Phase 7 | Pending |
| UI-05 | Phase 7 | Pending |
| UI-06 | Phase 7 | Pending |
| UI-07 | Phase 7 | Pending |
| TEST-01 | Phase 8 | Pending |
| TEST-02 | Phase 8 | Pending |
| TEST-03 | Phase 8 | Pending |
| TEST-04 | Phase 8 | Pending |
| TEST-05 | Phase 8 | Pending |
| TEST-06 | Phase 8 | Pending |
| TEST-07 | Phase 8 | Pending |
| TEST-08 | Phase 8 | Pending |
| TEST-09 | Phase 8 | Pending |
| TEST-10 | Phase 8 | Pending |
| TEST-11 | Phase 8 | Pending |
| TEST-12 | Phase 8 | Pending |
| TEST-13 | Phase 8 | Pending |
| TEST-14 | Phase 8 | Pending |
| TEST-15 | Phase 8 | Pending |
| TEST-16 | Phase 8 | Pending |
| TEST-17 | Phase 8 | Pending |
| TEST-18 | Phase 8 | Pending |
| TEST-19 | Phase 8 | Pending |
| TEST-20 | Phase 8 | Pending |

**Coverage:**
- v1 requirements: 68 total
- Mapped to phases: 68
- Unmapped: 0

---
*Requirements defined: 2026-04-12*
*Last updated: 2026-04-12 after roadmap creation (traceability populated)*
