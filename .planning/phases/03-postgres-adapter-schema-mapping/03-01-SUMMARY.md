---
phase: 03-postgres-adapter-schema-mapping
plan: 01
subsystem: database
tags: [kotlin, jpa, hibernate, postgres, testcontainers, jackson, sha256, ddl, soft-delete, schema-drift]

requires:
  - phase: 01-adapter-foundation
    provides: SchemaIntrospectionResult / ColumnSchema (consumed by SchemaHasher.compute)
  - phase: 02-secure-connection-management
    provides: DataConnectorConnectionEntity (FK target with ON DELETE CASCADE), AuditableSoftDeletableEntity + @SQLRestriction-on-concrete-entity pattern
  - phase: 03-postgres-adapter-schema-mapping
    provides: Plan 03-00 Wave-0 entity shells, factories, and @Disabled test classes flipped on here

provides:
  - PgTypeMapper object (pure fn: pgType + isPrimaryKey + enumOptions -> SchemaType)
  - SchemaHasher object (pure fn: tableName + List<ColumnSchema> -> SHA-256 hex)
  - CustomSourceTableMappingEntity full JPA + @SQLRestriction + toModel()
  - CustomSourceFieldMappingEntity full JPA + @SQLRestriction + toModel()
  - CustomSourceTableMapping + CustomSourceFieldMapping domain models
  - CustomSourceTableMappingRepository + CustomSourceFieldMappingRepository (Spring Data)
  - Declarative DDL for both mapping tables + indexes + FK cascade constraints
  - CustomSourceMappingEntityIntegrationTest (7 Testcontainers cases)

affects: [03-02, 03-03, 03-04]

tech-stack:
  added: []
  patterns:
    - "Stateless utility objects (PgTypeMapper, SchemaHasher) — not Spring beans; callers invoke directly"
    - "Deterministic hash via Jackson canonical JSON + alphabetical key ordering + pre-serialization column sort"
    - "@SQLRestriction declared on the concrete entity (Phase 2 lesson carried forward)"
    - "Integration test applies FK CASCADE via JdbcTemplate mirroring db/schema/04_constraints/ — ddl-auto does not emit ON DELETE CASCADE from JPA annotations"

key-files:
  created:
    - core/src/main/kotlin/riven/core/service/connector/postgres/PgTypeMapper.kt
    - core/src/main/kotlin/riven/core/service/connector/postgres/SchemaHasher.kt
    - core/src/main/kotlin/riven/core/models/connector/CustomSourceTableMapping.kt
    - core/src/main/kotlin/riven/core/models/connector/CustomSourceFieldMapping.kt
    - core/src/main/kotlin/riven/core/repository/connector/CustomSourceTableMappingRepository.kt
    - core/src/main/kotlin/riven/core/repository/connector/CustomSourceFieldMappingRepository.kt
    - core/db/schema/01_tables/_connector_table_mappings.sql
    - core/db/schema/01_tables/_connector_field_mappings.sql
    - core/db/schema/02_indexes/_connector_mappings.sql
    - core/db/schema/04_constraints/_connector_mappings.sql
    - core/src/test/kotlin/riven/core/entity/connector/CustomSourceMappingEntityIntegrationTest.kt
  modified:
    - core/src/main/kotlin/riven/core/entity/connector/CustomSourceTableMappingEntity.kt
    - core/src/main/kotlin/riven/core/entity/connector/CustomSourceFieldMappingEntity.kt
    - core/src/test/kotlin/riven/core/service/connector/postgres/PgTypeMapperTest.kt
    - core/src/test/kotlin/riven/core/service/connector/postgres/SchemaHashTest.kt
    - core/src/main/resources/application.yml

key-decisions:
  - "SHA-256 hex (64 chars) chosen over xxHash for schema-hash: deterministic, JDK-standard MessageDigest, no extra dependency, collision space sufficient for drift detection"
  - "FK from connector_(table|field)_mappings.connection_id -> data_connector_connections(id) ON DELETE CASCADE — mappings are connection-internal state with no historical value once parent is hard-purged; soft-delete on the connection does NOT trigger cascade because @SQLRestriction operates at the JPA query layer, not the SQL layer"
  - "Array detection in PgTypeMapper runs BEFORE the scalar `when` branches — any element type presented as `_<name>` or `<name>[]` becomes OBJECT regardless of whether the element scalar has a specific mapping"
  - "enumOptions non-null is the SELECT signal, not the pgType literal — the pg type string for a user-defined enum is user-supplied (e.g. `order_status`) and cannot be enumerated in a `when`; caller resolves pg_enum rows and passes the list"
  - "Integration test applies FK CASCADE via JdbcTemplate mirroring the production DDL — Hibernate ddl-auto does not emit ON DELETE CASCADE from JPA annotations alone; the declarative SQL file remains the source of truth"
  - "@SQLRestriction declared on each concrete mapping entity (Phase 2 02-01 lesson); repository derived queries inherit `deleted = false` filtering; native SQL (cascade verification) bypasses the restriction as expected"
  - "Test-side drop/add FK is idempotent via DROP CONSTRAINT IF EXISTS so the Testcontainers container can be reused across runs"

patterns-established:
  - "Pure-function utility objects under service/connector/postgres/ — single public `fun` with normalized input (lowercase + length-suffix strip for pg types); deterministic output; no Spring bean registration"
  - "Canonical-JSON hash: short field names (t, c, n, p, u) + MapperFeature.SORT_PROPERTIES_ALPHABETICALLY + pre-serialization column sort by name"
  - "Downstream plan for connector entities: scan only riven.core.entity.connector, hand-create workspaces table, apply production cascade constraints via JdbcTemplate in @BeforeAll"

requirements-completed:
  - PG-02
  - PG-05
  - MAP-02
  - MAP-08

duration: 9 min
completed: 2026-04-13
---

# Phase 3 Plan 01: PostgreSQL Mapping Persistence + Schema Utilities Summary

**Deterministic SHA-256 schema hash + exhaustive pg_type -> SchemaType mapping + JPA-persisted table/field mapping entities with FK CASCADE to Phase 2 connections, all verified against real Postgres via Testcontainers.**

## Performance

- **Duration:** ~9 min
- **Started:** 2026-04-13T03:29:56Z
- **Completed:** 2026-04-13T03:38:53Z
- **Tasks:** 2 (both TDD: RED -> GREEN)
- **Files created:** 11
- **Files modified:** 5 (2 entity shells upgraded; 2 @Disabled tests populated; 1 application.yml conflict fix)

## Accomplishments

- PgTypeMapper covers every branch from 03-CONTEXT.md: text/number/checkbox/date/datetime families, UUID PK vs non-PK split, user-defined enum -> SELECT via `enumOptions` signal, json/bytea/array/geometry/fallback -> OBJECT/LOCATION. 12 parameterised tests pass.
- SchemaHasher produces deterministic 64-char lowercase-hex SHA-256 over canonical JSON, column-order-independent via pre-serialization sort, and changes on every probed mutation (add, type-change, drop). 5 tests pass.
- CustomSourceTableMappingEntity + CustomSourceFieldMappingEntity ship with full JPA annotations, `@SQLRestriction` on the concrete entity, `@Enumerated(STRING)` on all enum columns, UUID columnDefinition, unique constraints, and `toModel()` using `requireNotNull(id)`.
- Repositories expose derived queries exercised by the integration test: `findByConnectionId`, `findByConnectionIdAndTableName`, `findByEntityTypeId`.
- Declarative DDL shipped for both tables + indexes + FK cascade constraints under `db/schema/{01_tables,02_indexes,04_constraints}/`.
- Integration test (7 cases) passes against Testcontainers `pgvector/pgvector:pg16`: round-trip (2), soft-delete via @SQLRestriction on derived queries (2), unique constraint (2), FK cascade delete (1).

## Task Commits

1. **Task 1 RED: failing tests for PgTypeMapper + SchemaHasher** — `b37d941f9` (test)
2. **Task 1 GREEN: implement PgTypeMapper + SchemaHasher** — `39a736a50` (feat)
3. **Task 2 RED: integration test for mapping entities** — `307f9da28` (test)
4. **Task 2 GREEN: full JPA entities + models + repos + DDL + blocking fix** — `41ed0a4d6` (feat)

**Plan metadata:** _(created by final_commit step)_

## Files Created/Modified

### Created

- `core/src/main/kotlin/riven/core/service/connector/postgres/PgTypeMapper.kt` — pure fn pgType -> SchemaType
- `core/src/main/kotlin/riven/core/service/connector/postgres/SchemaHasher.kt` — canonical-JSON SHA-256 schema hasher
- `core/src/main/kotlin/riven/core/models/connector/CustomSourceTableMapping.kt` — response DTO for table-level mapping
- `core/src/main/kotlin/riven/core/models/connector/CustomSourceFieldMapping.kt` — response DTO for column-level mapping
- `core/src/main/kotlin/riven/core/repository/connector/CustomSourceTableMappingRepository.kt` — JpaRepository with derived queries
- `core/src/main/kotlin/riven/core/repository/connector/CustomSourceFieldMappingRepository.kt` — JpaRepository with derived queries
- `core/db/schema/01_tables/_connector_table_mappings.sql` — DDL + UNIQUE(ws, conn, table)
- `core/db/schema/01_tables/_connector_field_mappings.sql` — DDL + UNIQUE(ws, conn, table, col)
- `core/db/schema/02_indexes/_connector_mappings.sql` — connection / workspace / (conn, table) indexes
- `core/db/schema/04_constraints/_connector_mappings.sql` — FK ON DELETE CASCADE on both tables
- `core/src/test/kotlin/riven/core/entity/connector/CustomSourceMappingEntityIntegrationTest.kt` — 7 Testcontainers cases

### Modified

- `core/src/main/kotlin/riven/core/entity/connector/CustomSourceTableMappingEntity.kt` — replaced Wave-0 shell with full JPA annotations, `@SQLRestriction`, `toModel()`
- `core/src/main/kotlin/riven/core/entity/connector/CustomSourceFieldMappingEntity.kt` — replaced Wave-0 shell with full JPA annotations, `@SQLRestriction`, `toModel()`
- `core/src/test/kotlin/riven/core/service/connector/postgres/PgTypeMapperTest.kt` — flipped @Disabled off; 12 placeholder bodies replaced with real assertions (parameterised)
- `core/src/test/kotlin/riven/core/service/connector/postgres/SchemaHashTest.kt` — flipped @Disabled off; 5 placeholder bodies replaced with real assertions
- `core/src/main/resources/application.yml` — Rule-3 blocking fix (unresolved merge-conflict markers around websocket block)

## Decisions Made

Key decisions recorded in the frontmatter `key-decisions` block. Highlights:

- **SHA-256 over xxHash.** JDK built-in, deterministic, no new dependency; collision space is ample for drift detection.
- **FK ON DELETE CASCADE (not SET NULL / no action).** Mappings are connection-internal state; once the parent is hard-purged they have no historical value. Soft-delete on the connection does NOT trigger the cascade (SQL layer vs JPA-layer filter), which is the correct semantic — soft-deleted connections can be restored and their mapping rows re-associated.
- **`@SQLRestriction` on each concrete entity.** Explicitly re-declared beyond the `@MappedSuperclass` inheritance per the Phase 2 02-01 lesson (Hibernate 6 does not reliably propagate SQLRestriction from mapped superclasses to derived-query SQL).
- **`enumOptions != null` is the SELECT signal.** Caller-resolved enum options are the source of truth, not the pg type literal, because user-defined enum type names are user-supplied.
- **Integration test applies FK CASCADE via JdbcTemplate.** Hibernate `ddl-auto=create-drop` does not emit `ON DELETE CASCADE` from annotations alone; we mirror the production DDL in `@BeforeAll` so the cascade semantic is actually exercised.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Resolved unresolved Git merge-conflict markers in `application.yml`**

- **Found during:** Task 2 integration-test bootstrap (SnakeYAML `ScannerException` at `application.yml:88`)
- **Issue:** The `postgres-ingestion` HEAD contained a raw `<<<<<<< Updated upstream` / `=======` / `>>>>>>> Stashed changes` triad around the websocket block in `core/src/main/resources/application.yml`. Spring Boot's YAML loader rejects the file, so `@SpringBootTest` could not boot and every integration test in Phase 3 was blocked.
- **Fix:** Removed conflict markers; kept the "Updated upstream" side (websocket section is the new configuration recently added in this worktree).
- **Files modified:** `core/src/main/resources/application.yml`
- **Verification:** `./gradlew test --tests riven.core.entity.connector.CustomSourceMappingEntityIntegrationTest` BUILD SUCCESSFUL with 7/7 cases passing.
- **Committed in:** `41ed0a4d6` (Task 2 GREEN commit)

**2. [Rule 3 - Blocking] Integration test applies FK CASCADE via JdbcTemplate**

- **Found during:** Task 2 integration-test run (`cascadeDeleteOfConnectionDeletesMappingRows` failed initially — mapping rows remained after `DELETE` on `data_connector_connections`).
- **Issue:** Hibernate's `ddl-auto=create-drop` auto-generates the FK without `ON DELETE CASCADE`; the declarative file `04_constraints/_connector_mappings.sql` is production-only and not consumed by the test container.
- **Fix:** Added `DROP CONSTRAINT IF EXISTS` + `ADD CONSTRAINT ... ON DELETE CASCADE` statements in the test's `@BeforeAll`, mirroring the production DDL exactly. The FK semantic is therefore still exercised against real Postgres, and the production source of truth is a single file.
- **Files modified:** `CustomSourceMappingEntityIntegrationTest.kt` (before first green commit, so part of the RED test commit)
- **Verification:** Re-ran the test — 7/7 pass.
- **Committed in:** `307f9da28` (Task 2 RED commit — the updated test with FK mirror was the final form before GREEN)

---

**Total deviations:** 2 auto-fixed (2 blocking)
**Impact on plan:** Both fixes strictly unblocking — no scope creep. The application.yml conflict predated Phase 3 work entirely; the FK-CASCADE-in-test pattern is established here for downstream Phase 3 plans (03-02, 03-03) to reuse.

## Issues Encountered

- **Initial cascade-delete test failure.** Diagnosed as Hibernate auto-FK missing CASCADE — resolved via the JdbcTemplate mirror (documented above as Deviation 2). No runtime/persistence issue in the entity itself.
- **SnakeYAML ScannerException on `application.yml:88`.** Pre-existing merge-conflict markers on HEAD; resolved as Deviation 1.

## User Setup Required

None — plan adds no environment variables, infrastructure, or external services.

## Next Phase Readiness

Plans 03-02, 03-03, 03-04 can now call:

- `PgTypeMapper.toSchemaType(...)` — pure, no Spring wiring required.
- `SchemaHasher.compute(...)` — pure.
- `CustomSourceTableMappingRepository` + `CustomSourceFieldMappingRepository` — Spring Data interfaces discoverable via the existing `@EnableJpaRepositories(basePackages = ["riven.core.repository.connector"])` scan used in Phase 2.
- `CustomSourceTableMappingEntity.toModel()` + `CustomSourceFieldMappingEntity.toModel()` — exposed to service layer via `requireNotNull(id)` safe mapping.

**No blockers.** Deferred pre-existing `DataConnectorConnectionServiceTest` failures logged in `deferred-items.md` from Plan 03-00 remain deferred and out of scope.

## Self-Check: PASSED

- [x] `core/src/main/kotlin/riven/core/service/connector/postgres/PgTypeMapper.kt` — FOUND
- [x] `core/src/main/kotlin/riven/core/service/connector/postgres/SchemaHasher.kt` — FOUND
- [x] `core/src/main/kotlin/riven/core/models/connector/CustomSourceTableMapping.kt` — FOUND
- [x] `core/src/main/kotlin/riven/core/models/connector/CustomSourceFieldMapping.kt` — FOUND
- [x] `core/src/main/kotlin/riven/core/repository/connector/CustomSourceTableMappingRepository.kt` — FOUND
- [x] `core/src/main/kotlin/riven/core/repository/connector/CustomSourceFieldMappingRepository.kt` — FOUND
- [x] `core/db/schema/01_tables/_connector_table_mappings.sql` — FOUND
- [x] `core/db/schema/01_tables/_connector_field_mappings.sql` — FOUND
- [x] `core/db/schema/02_indexes/_connector_mappings.sql` — FOUND
- [x] `core/db/schema/04_constraints/_connector_mappings.sql` — FOUND
- [x] `core/src/test/kotlin/riven/core/entity/connector/CustomSourceMappingEntityIntegrationTest.kt` — FOUND
- [x] Commit `b37d941f9` — FOUND
- [x] Commit `39a736a50` — FOUND
- [x] Commit `307f9da28` — FOUND
- [x] Commit `41ed0a4d6` — FOUND

---
*Phase: 03-postgres-adapter-schema-mapping*
*Completed: 2026-04-13*
