---
phase: 03-postgres-adapter-schema-mapping
plan: 02
subsystem: api
tags: [kotlin, postgres, jdbc, hikaricp, testcontainers, ingestion-adapter, phase-3]

requires:
  - phase: 01-adapter-foundation
    provides: IngestionAdapter contract + AdapterCallContext + AdapterException hierarchy consumed by PostgresAdapter
  - phase: 02-secure-connection-management
    provides: DataConnectorConnectionEntity + CredentialEncryptionService + CredentialPayload (decrypted by adapter before pool build)
  - phase: 03-postgres-adapter-schema-mapping
    provides: Plan 03-01 PgTypeMapper (used by fetcher to type every column) + Wave-0 @Disabled tests flipped on here

provides:
  - ConnectorPoolProperties (@ConfigurationProperties riven.connector.pool)
  - WorkspaceConnectionPoolManager (connectionId-keyed HikariDataSource cache)
  - PostgresCallContext sealed subtype of AdapterCallContext
  - PostgresAdapter @SourceTypeAdapter(SourceType.CONNECTOR)
  - PostgresIntrospector (information_schema + pg_constraint + pg_attribute)
  - PostgresFetcher (server-side cursor + cursor-or-PK-fallback SQL + typed payload)
  - ForeignKeyMetadata surface data class
  - IntrospectionResult sibling (schema + FK metadata for plan 03-03)
  - Pool eviction wiring in DataConnectorConnectionService (credential update + soft-delete)

affects: [03-03, 03-04, 04-ingestion-orchestrator]

tech-stack:
  added: []
  patterns:
    - "connectionId-keyed HikariCP cache via ConcurrentHashMap.computeIfAbsent (not per-workspace — one pool per connection)"
    - "Lazy pool initialization — HikariConfig.initializationFailTimeout = -1 so pool construction never eagerly connects"
    - "Server-side Postgres cursor for bounded memory: autoCommit = false + PreparedStatement.fetchSize = defaultBatchSize"
    - "Adapter exposes contract via IngestionAdapter but surfaces FK metadata via introspectWithFkMetadata sibling method (Phase 1 contract unchanged)"
    - "SQLState-based exception translator: 28xxx/57014/08xxx/default → AdapterAuth/Unavailable/ConnectionRefused/Unavailable"
    - "Cursor SQL variants: (a) null cursor → omit WHERE entirely, (b) timestamp cursor → cast as ?::timestamptz, (c) text/uuid PK → cast column with ::text for cross-type compare with bound string"

key-files:
  created:
    - core/src/main/kotlin/riven/core/configuration/properties/ConnectorPoolProperties.kt
    - core/src/main/kotlin/riven/core/service/connector/pool/WorkspaceConnectionPoolManager.kt
    - core/src/main/kotlin/riven/core/service/ingestion/adapter/PostgresCallContext.kt
    - core/src/main/kotlin/riven/core/service/connector/postgres/PostgresAdapter.kt
    - core/src/main/kotlin/riven/core/service/connector/postgres/PostgresIntrospector.kt
    - core/src/main/kotlin/riven/core/service/connector/postgres/PostgresFetcher.kt
    - core/src/main/kotlin/riven/core/service/connector/postgres/ForeignKeyMetadata.kt
    - core/src/main/kotlin/riven/core/service/connector/postgres/IntrospectionResult.kt
  modified:
    - core/src/main/kotlin/riven/core/service/connector/DataConnectorConnectionService.kt
    - core/src/test/kotlin/riven/core/service/connector/pool/WorkspaceConnectionPoolManagerTest.kt
    - core/src/test/kotlin/riven/core/service/connector/postgres/PostgresAdapterTest.kt
    - core/src/test/kotlin/riven/core/service/connector/DataConnectorConnectionServiceTest.kt

key-decisions:
  - "Pool keying by connectionId, NOT workspaceId — 'WorkspaceConnectionPoolManager' name is historic. A workspace may own many connections; each gets its own pool."
  - "HikariConfig.initializationFailTimeout = -1 (never fail at build time). The first getConnection() surfaces any connection error. Required so the bean/pool construction flow is unit-testable against fake credentials."
  - "Server-side cursor via autoCommit=false + fetchSize is mandatory for Postgres streaming — the JDBC driver otherwise buffers the entire ResultSet in memory (would OOM on 10M-row tables)."
  - "FK metadata exposed via a sibling method introspectWithFkMetadata rather than extending the Phase 1 IngestionAdapter contract — keeps Nango + future adapters from gaining a surface they do not implement."
  - "Cursor SQL casts the comparison column with ::text so the adapter binds a single String parameter regardless of underlying type (uuid, bigint, text). Works for ordered UUID pagination; timestamp cursors use the explicit ?::timestamptz variant."
  - "Null cursor triggers a WHERE-less variant of the fetch SQL rather than synthesizing a zero-value for arbitrary column types — previously a zero-string against a uuid column raised 'operator does not exist: uuid > character varying'."
  - "Bytea representation in EntityAttributePrimitivePayload.value = {\"_bytea\": base64-string} object. JSON-safe, reversible, clearly distinguished from plain Map payloads."
  - "Pool eviction wired via direct service call (DataConnectorConnectionService → WorkspaceConnectionPoolManager.evict), not events. Credential-touching update and soft-delete both evict; cosmetic (name-only) update does NOT."
  - "PostgresAdapter is @ConditionalOnProperty(riven.connector.enabled=true) — matches the Phase 2 opt-in pattern so deployments that do not use the connector subsystem still start cleanly."

patterns-established:
  - "Lazy-pool HikariCP: `initializationFailTimeout = -1` + `addDataSourceProperty(\"options\", \"-c statement_timeout=...\")` covers statement-timeout-per-connection without running code on every checkout"
  - "JDBC SQLState dispatcher pattern for adapter translators: `when { state.startsWith(\"28\") -> …; state == \"57014\" -> …; state.startsWith(\"08\") -> …; else -> … }`"
  - "Adapter-level Mockito + Testcontainers hybrid: mock the credentials-resolution plane (repository + encryptionService), use live Testcontainers Postgres for the pool + JDBC plane — catches real SQL errors without exploding mock surface area"

requirements-completed:
  - PG-01
  - PG-02
  - PG-03
  - PG-04
  - PG-05
  - PG-06
  - PG-07

duration: 11 min
completed: 2026-04-13
---

# Phase 3 Plan 02: PostgresAdapter + Connection Pool Manager Summary

**PostgresAdapter is a working IngestionAdapter: cursor-or-PK-fallback record streaming via HikariCP pools per connectionId, FK-aware schema introspection, and full SQLState→AdapterException translation — all exercised against Testcontainers Postgres.**

## Performance

- **Duration:** ~11 min
- **Started:** 2026-04-13T03:44:28Z
- **Completed:** 2026-04-13T03:55:00Z (approx)
- **Tasks:** 3 (TDD hybrid — implementation + tests together)
- **Files created:** 8
- **Files modified:** 4

## Accomplishments

- `WorkspaceConnectionPoolManager` caches one HikariDataSource per connectionId via ConcurrentHashMap.computeIfAbsent; 6 pool tests cover caching, eviction, evictAll, @PreDestroy, credentials-cached-on-first-build, and HikariConfig assertions (maxPoolSize=2, idleTimeout=10m, maxLifetime=30m, connectionTimeout=10s, statement_timeout=5m).
- `PostgresAdapter` implements IngestionAdapter, registered with `@SourceTypeAdapter(SourceType.CONNECTOR)`, discoverable by Phase 1's `SourceTypeAdapterRegistry`. 11 adapter tests against Testcontainers pgvector/pg16 cover: cursor SQL, PK-fallback, limit enforcement, jsonb→Map round-trip, empty batch, FK metadata (including composite detection scaffold), annotation wiring, non-PG-context rejection, auth-failure translation (SQLState 28xxx), and timeout translation (SQLState 57014).
- `PostgresIntrospector` queries information_schema + pg_constraint + pg_attribute to produce `SchemaIntrospectionResult` plus a List<ForeignKeyMetadata> side-channel; composite FKs are surfaced with `isComposite=true` and logged (plan 03-03 skips them).
- `PostgresFetcher` uses a server-side cursor (autoCommit=false + fetchSize=props.defaultBatchSize) so arbitrarily large tables don't OOM; builds typed payload via `PgTypeMapper.toSchemaType` + jsonb/bytea conversion.
- Pool eviction wired into `DataConnectorConnectionService.update` (credential-touching branch only) and `softDelete`; cosmetic updates do NOT evict. 3 new service tests + 13 prior tests now execute cleanly (the missing @MockitoBean previously prevented Spring context load).

## Task Commits

1. **Task 1: ConnectorPoolProperties + WorkspaceConnectionPoolManager + PostgresCallContext** — `e317056ba` (feat)
2. **Task 2: PostgresAdapter + Introspector + Fetcher + ForeignKeyMetadata + 11 tests** — `8ad0e521f` (feat)
3. **Task 3: Wire pool eviction into DataConnectorConnectionService** — `0bfbc255b` (feat)

**Plan metadata:** _(created by final_commit step)_

## Files Created/Modified

### Created

- `core/src/main/kotlin/riven/core/configuration/properties/ConnectorPoolProperties.kt` — @ConfigurationProperties riven.connector.pool
- `core/src/main/kotlin/riven/core/service/connector/pool/WorkspaceConnectionPoolManager.kt` — @Service, ConcurrentHashMap pool cache
- `core/src/main/kotlin/riven/core/service/ingestion/adapter/PostgresCallContext.kt` — sealed subtype of AdapterCallContext
- `core/src/main/kotlin/riven/core/service/connector/postgres/PostgresAdapter.kt` — @Component @SourceTypeAdapter(CONNECTOR)
- `core/src/main/kotlin/riven/core/service/connector/postgres/PostgresIntrospector.kt` — @Component, information_schema + pg_constraint queries
- `core/src/main/kotlin/riven/core/service/connector/postgres/PostgresFetcher.kt` — @Component, server-side-cursor SQL + typed payload
- `core/src/main/kotlin/riven/core/service/connector/postgres/ForeignKeyMetadata.kt` — surface for plan 03-03
- `core/src/main/kotlin/riven/core/service/connector/postgres/IntrospectionResult.kt` — schema + FK metadata bundle

### Modified

- `core/src/main/kotlin/riven/core/service/connector/DataConnectorConnectionService.kt` — inject WorkspaceConnectionPoolManager, call evict() on credential-update + softDelete
- `core/src/test/kotlin/riven/core/service/connector/pool/WorkspaceConnectionPoolManagerTest.kt` — flipped @Disabled off, 6 real assertions
- `core/src/test/kotlin/riven/core/service/connector/postgres/PostgresAdapterTest.kt` — flipped @Disabled off, 11 assertions against Testcontainers
- `core/src/test/kotlin/riven/core/service/connector/DataConnectorConnectionServiceTest.kt` — @MockitoBean poolManager, 3 new eviction tests

## Decisions Made

All decisions recorded in frontmatter `key-decisions`. Highlights:

- **Pool keying by connectionId.** A workspace may own many DB connections; each gets its own pool. The class name retains "Workspace" for consistency with 03-CONTEXT.md.
- **initializationFailTimeout = -1.** Pool construction MUST NOT eagerly connect — otherwise every test that builds a pool against a mocked credential resolver fails. The first `getConnection()` call surfaces any connection error, which is exactly when the adapter wants to translate it into `AdapterAuthException` / `AdapterConnectionRefusedException`.
- **FK metadata via sibling method.** `introspectSchema` returns the Phase 1 `SchemaIntrospectionResult`; FK metadata surfaces via `introspectWithFkMetadata` — Phase 1 contract unchanged.
- **Cursor column `::text` cast.** Binding a single-column `String` cursor parameter against arbitrary pg types (uuid, bigint, text) requires a cross-type comparison. Casting the *column* to text lets `'uuid > character varying'` become `'text > text'`. Timestamp cursors use the dedicated `?::timestamptz` variant.
- **Null-cursor WHERE-less variant.** First fetch omits `WHERE` entirely rather than synthesizing a zero value — avoids `'operator does not exist: uuid > character varying'` when the cursor column is uuid and cursor is the empty string.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] HikariConfig default eagerly connected — pool unit tests failed against fake credentials**

- **Found during:** Task 1 verification (`./gradlew test --tests WorkspaceConnectionPoolManagerTest`)
- **Issue:** `HikariDataSource` by default runs an initial connection test when the first connection is checked out (and Hikari's lazy-init default is inconsistent across versions). The 6 pool unit tests build pools against `db.example.com:5432` (non-existent) to assert on HikariConfig values — without explicit lazy-init they fail with `HikariPool$PoolInitializationException` caused by `UnknownHostException` / `ConnectException`.
- **Fix:** Added `initializationFailTimeout = -1` to `buildPool`. Hikari now defers actual socket creation until `getConnection()` — which is what the adapter's JDBC call triggers, at which point the exception translator maps it into `AdapterAuthException` / `AdapterConnectionRefusedException` as designed.
- **Files modified:** `WorkspaceConnectionPoolManager.kt`
- **Verification:** All 6 pool tests pass; adapter tests still correctly observe `AdapterAuthException` when real bad credentials hit the Testcontainers Postgres.
- **Committed in:** `e317056ba` (Task 1 commit)

**2. [Rule 1 - Bug] PK-fallback SQL failed with `operator does not exist: uuid > character varying` when cursor was null**

- **Found during:** Task 2 verification (`./gradlew test --tests PostgresAdapterTest`) — 4 of 11 tests failed on first run
- **Issue:** The initial implementation bound an empty string when cursor was null, producing `WHERE "id" > ''` where `id` is `uuid`. Postgres has no `uuid > varchar` operator, so 4 fetch tests failed even though their expected result set was non-empty.
- **Fix:** Two changes: (a) when cursor is null, omit `WHERE` entirely (first-fetch variant); (b) when cursor is non-null, cast the comparison column to text: `WHERE "id"::text > ?`. This lets a single `setString` parameter compare against any column type, while still preserving natural ordering within a single cursor scan.
- **Files modified:** `PostgresFetcher.kt`
- **Verification:** All 11 adapter tests pass against Testcontainers pgvector/pg16.
- **Committed in:** `8ad0e521f` (Task 2 commit)

**3. [Rule 3 - Blocking] `@Suppress("UNCHECKED_CAST")` is not allowed as a call-argument annotation in Kotlin**

- **Found during:** Task 2 compile
- **Issue:** `payload = @Suppress("UNCHECKED_CAST") payload.toMap() as Map<String, Any?>` is a compile error — Kotlin rejects annotations in that position ("Only expressions are allowed in this context").
- **Fix:** Extracted the cast into a local `val payloadAsAny = @Suppress(...) ... as Map<String, Any?>` and passed the val into `SourceRecord.copy`.
- **Files modified:** `PostgresFetcher.kt`
- **Verification:** `./gradlew compileKotlin` green; Task 2 tests run.
- **Committed in:** `8ad0e521f` (Task 2 commit)

---

**Total deviations:** 3 auto-fixed (2 bug + 1 blocking). All strictly necessary for correctness. No scope creep.

## Issues Encountered

- **Initial approach proposed a separate `PostgresAdapterIntegrationTest` on top of unit tests with heavy Mockito JDBC chains.** Switched to a single Testcontainers-backed test class mixing mocked credentials plane + live Postgres JDBC plane — same coverage (11 named tests), far less mock scaffolding, catches real SQL errors that JDBC mocks would hide. This is the "hybrid" pattern referenced in the new `patterns-established` list.
- **DataConnectorConnectionServiceTest was listed as having 13 pre-existing failures** (deferred-items.md from plan 03-00). Adding the `@MockitoBean poolManager` unblocked Spring context load — all 13 previously-failing tests now execute and pass cleanly. This is a happy side effect of Task 3's wiring, not a planned fix.

## User Setup Required

None — plan 03-02 adds no environment variables, infrastructure, or external services. Testcontainers pulls `pgvector/pgvector:pg16` on first test run (same image as plan 03-01).

## Next Phase Readiness

- **Plan 03-03** can call `PostgresAdapter.introspectWithFkMetadata(ctx)` and consume `List<ForeignKeyMetadata>` to materialise `RelationshipDefinitionEntity` rows. Composite FKs arrive with `isComposite=true` pre-flagged — plan 03-03 skips them.
- **Plan 03-04** has a working adapter to drive natural-language mapping suggestions over real introspection output.
- **Phase 4** orchestrator resolves the adapter from `SourceTypeAdapterRegistry.sourceTypeAdapterMap[SourceType.CONNECTOR]` with no further wiring. `syncMode() = SyncMode.POLL` tells the orchestrator to schedule polled fetches.
- **Known concern:** pre-existing 13 `DataConnectorConnectionServiceTest` failures referenced in plan 03-00's `deferred-items.md` are now green as a side effect of Task 3 (missing @MockitoBean was root cause, not a service bug). Can be removed from deferred-items.md in a future housekeeping pass.

## Self-Check: PASSED

- [x] `core/src/main/kotlin/riven/core/configuration/properties/ConnectorPoolProperties.kt` — FOUND
- [x] `core/src/main/kotlin/riven/core/service/connector/pool/WorkspaceConnectionPoolManager.kt` — FOUND
- [x] `core/src/main/kotlin/riven/core/service/ingestion/adapter/PostgresCallContext.kt` — FOUND
- [x] `core/src/main/kotlin/riven/core/service/connector/postgres/PostgresAdapter.kt` — FOUND
- [x] `core/src/main/kotlin/riven/core/service/connector/postgres/PostgresIntrospector.kt` — FOUND
- [x] `core/src/main/kotlin/riven/core/service/connector/postgres/PostgresFetcher.kt` — FOUND
- [x] `core/src/main/kotlin/riven/core/service/connector/postgres/ForeignKeyMetadata.kt` — FOUND
- [x] `core/src/main/kotlin/riven/core/service/connector/postgres/IntrospectionResult.kt` — FOUND
- [x] Commit `e317056ba` — FOUND
- [x] Commit `8ad0e521f` — FOUND
- [x] Commit `0bfbc255b` — FOUND
- [x] `./gradlew test --tests riven.core.service.connector.pool.* --tests riven.core.service.connector.postgres.* --tests DataConnectorConnectionServiceTest` — BUILD SUCCESSFUL

---
*Phase: 03-postgres-adapter-schema-mapping*
*Completed: 2026-04-13*
