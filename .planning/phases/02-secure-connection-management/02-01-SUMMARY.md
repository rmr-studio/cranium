---
phase: 02-secure-connection-management
plan: 01
subsystem: customsource
tags: [phase-2, wave-1, entity, jpa, testcontainers]
dependency_graph:
  requires:
    - 02-00 (test scaffolding + factory stub)
  provides:
    - CustomSourceConnectionEntity (JPA, bytea credentials + iv + key_version)
    - CustomSourceConnectionRepository (Spring Data JPA)
    - ConnectionException sealed hierarchy (Crypto/DataCorruption/SsrfRejected/ReadOnlyVerification)
    - CustomSourceConnectionModel (redacted response DTO)
    - CustomSourceConnectionEntityFactory.create(...)
    - custom_source_connections table DDL
  affects:
    - Downstream plans 02-02..04 can import entity + exceptions + model directly
tech_stack:
  added: []
  patterns:
    - "@SQLRestriction on concrete entity (not only on @MappedSuperclass) — Hibernate 6 does not propagate mapped-superclass SQLRestriction to derived queries reliably"
    - "Testcontainers pgvector/pg16 + minimal hand-built workspaces table via JdbcTemplate to keep the test persistence unit scoped to one package"
    - "Sibling sealed exception hierarchy (ConnectionException) rather than extending Phase 1 AdapterException — keeps Temporal do-not-retry policies decoupled"
    - "Redacted response DTO: toModel() takes decrypted connection params; entity never hands out its ciphertext, IV, or key version"
key_files:
  created:
    - core/db/schema/01_tables/custom_source_connections.sql
    - core/src/main/kotlin/riven/core/entity/customsource/CustomSourceConnectionEntity.kt
    - core/src/main/kotlin/riven/core/repository/customsource/CustomSourceConnectionRepository.kt
    - core/src/main/kotlin/riven/core/exceptions/customsource/ConnectionExceptions.kt
    - core/src/main/kotlin/riven/core/models/customsource/CustomSourceConnectionModel.kt
  modified:
    - core/src/test/kotlin/riven/core/service/util/factory/customsource/CustomSourceConnectionEntityFactory.kt
    - core/src/test/kotlin/riven/core/entity/customsource/CustomSourceConnectionEntityTest.kt
decisions:
  - "FK target is existing `workspaces` table (verified via core/db/schema/01_tables/workspace.sql). ON DELETE CASCADE matches integration_connections precedent."
  - "ConnectionException extends RuntimeException (via sealed parent), not checked — required for Spring @Transactional default rollback semantics (research pitfall 6)."
  - "@SQLRestriction must be declared on the concrete entity, not only inherited from AuditableSoftDeletableEntity — matches WorkspaceEntity/EntityEntity/etc. project-wide pattern."
  - "Integration test scans only `riven.core.entity.customsource`; the `workspaces` FK target is hand-created via JdbcTemplate in a @BeforeAll hook to avoid dragging the full entity graph (UserEntity, WorkspaceInviteEntity) into the test persistence unit."
  - "Factory signature: `create(workspaceId, name, connectionStatus, encryptedCredentials, iv, keyVersion, lastVerifiedAt, lastFailureReason)` — all optional with sensible defaults; downstream tests can override any field."
metrics:
  duration: "~20 min"
  completed: "2026-04-12"
  tasks: 2
  files_created: 5
  files_modified: 2
requirements:
  - CONN-01
---

# Phase 02 Plan 01: CustomSourceConnection Entity + Foundation Summary

Lay the JPA entity, repository, SQL DDL, sealed exception hierarchy, and redacted response
DTO that all downstream Phase 2 plans (encryption, SSRF, read-only verifier, service,
controller) depend on. CONN-01 round-trip integration test green against Testcontainers
Postgres with bytea credentials preserved byte-for-byte.

## What Landed

### Production code

- **SQL DDL** — `custom_source_connections` table: uuid id, workspace_id FK to
  `workspaces(id) ON DELETE CASCADE`, name, connection_status (varchar(50)),
  `encrypted_credentials BYTEA`, `iv BYTEA`, key_version int default 1,
  last_verified_at, last_failure_reason, full audit + soft-delete cols, partial
  index on workspace_id WHERE deleted = FALSE.
- **`CustomSourceConnectionEntity`** — extends `AuditableSoftDeletableEntity`;
  bytea columns via `columnDefinition = "bytea"`; `@Enumerated(EnumType.STRING)`
  on `ConnectionStatus`; `toModel(host, port, database, user, sslMode)` accepts
  decrypted non-secret params from the service layer and returns a redacted
  `CustomSourceConnectionModel`. Carries explicit `@SQLRestriction("deleted = false")`.
- **`CustomSourceConnectionRepository`** — `JpaRepository<..., UUID>` with
  `findByWorkspaceId` and `findByIdAndWorkspaceId` derived queries.
- **`ConnectionException`** — sealed class (sibling to Phase 1 `AdapterException`)
  extending `RuntimeException`. Four subtypes: `CryptoException`,
  `DataCorruptionException`, `SsrfRejectedException`, `ReadOnlyVerificationException`.
- **`CustomSourceConnectionModel`** — response DTO. Contains id, workspaceId, name,
  host, port, database, user, sslMode, connectionStatus, lastVerifiedAt, createdAt,
  updatedAt. Explicitly omits `encryptedCredentials`, `iv`, `keyVersion`, and any
  password field; `init` block documents the guardrail.

### Tests

- **`CustomSourceConnectionEntityFactory.create(...)`** — populated per plan.
  Signature: `workspaceId, name, connectionStatus, encryptedCredentials (48 bytes),
  iv (12 bytes), keyVersion, lastVerifiedAt, lastFailureReason`. All defaulted.
- **`CustomSourceConnectionEntityTest`** — Testcontainers integration test:
  - `saves and reads entity with bytea credentials preserved` — 64-byte ciphertext
    and 12-byte IV round-trip byte-for-byte via `assertArrayEquals`.
  - `soft-deleted entity is invisible to findById` — uses direct SQL UPDATE to flip
    `deleted=true`, clears persistence context, then verifies
    `findByIdAndWorkspaceId` returns empty while the physical row still exists.
  - `findByWorkspaceId scopes results` — two workspaces, three rows, each query
    returns only its own workspace's rows.

## Verification

- `./gradlew compileKotlin compileTestKotlin` → BUILD SUCCESSFUL
- `./gradlew test --tests "riven.core.entity.customsource.CustomSourceConnectionEntityTest"`
  → 3/3 green against pgvector/pgvector:pg16 Testcontainers
- Entity, repo, exceptions, model files all present at spec'd paths
- `ConnectionException` is a distinct sealed hierarchy (verified import path
  `riven.core.exceptions.customsource`, no inheritance from `AdapterException`)
- Response model has no `password`, `encryptedCredentials`, `iv`, or `keyVersion`
  field (verified by data class property list)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `@SQLRestriction` not inherited from `@MappedSuperclass` in Hibernate 6**

- **Found during:** Task 2 (soft-delete test initially failed — rows remained
  visible to `findByIdAndWorkspaceId` after `deleted=true`).
- **Issue:** `AuditableSoftDeletableEntity` declares `@SQLRestriction("deleted = false")`
  on the mapped superclass, but Hibernate 6 did not append the restriction to
  derived queries on `CustomSourceConnectionEntity`. The physical row was correctly
  flagged deleted in Postgres; the JPQL filter simply wasn't emitted.
- **Fix:** Added explicit `@SQLRestriction("deleted = false")` on the concrete
  entity. This matches the project-wide pattern already present on `WorkspaceEntity`,
  `EntityEntity`, `BlockEntity`, `NotificationEntity`, `UserEntity`, etc. —
  the mapped superclass carries the columns, each concrete entity re-declares
  the restriction.
- **Files modified:** `core/src/main/kotlin/riven/core/entity/customsource/CustomSourceConnectionEntity.kt`
- **Commit:** `3881d0757`

**2. [Rule 3 - Blocking] Integration test persistence-unit scope**

- **Found during:** Task 2 (first test run — `AnnotationException: Association
  'WorkspaceInviteEntity.invitedByUser' targets the type 'UserEntity' which does
  not belong to the same persistence unit`).
- **Issue:** Scanning `riven.core.entity.workspace` pulled in `WorkspaceInviteEntity`,
  which has a `@ManyToOne` to `UserEntity` in a different package; adding the
  user package would cascade further. The plan suggested using a "workspace
  factory in setUp" but the JPA factory requires the full entity graph.
- **Fix:** Scan only `riven.core.entity.customsource`, and hand-create the
  minimal `workspaces(id, name, deleted)` table via `JdbcTemplate` in a
  `@BeforeAll` hook. Test inserts workspace rows directly via SQL. The custom
  source connection entity's FK still references `workspaces(id)` — nothing
  about production wiring changes. This is a test-only scoping choice.
- **Files modified:** `core/src/test/kotlin/riven/core/entity/customsource/CustomSourceConnectionEntityTest.kt`
- **Commit:** `3881d0757`

## Commits

| Task | Commit      | Message                                                                       |
| ---- | ----------- | ----------------------------------------------------------------------------- |
| 1    | `6c2cda43c` | feat(02-01): add CustomSourceConnectionEntity + SQL + exceptions + model      |
| 2    | `3881d0757` | test(02-01): populate factory + CONN-01 Testcontainers round-trip             |

## Requirements Status

- **CONN-01** — SATISFIED. Entity persists + soft-deletes correctly against real
  Postgres. Bytea credentials + IV round-trip byte-for-byte. Workspace-scoped
  queries verified.

## Downstream Contracts

Plans 02-02..04 can assume the following is already in place:

- **Entity import:** `riven.core.entity.customsource.CustomSourceConnectionEntity`
- **Repository import:** `riven.core.repository.customsource.CustomSourceConnectionRepository`
- **Exception imports:** `riven.core.exceptions.customsource.{CryptoException,
  DataCorruptionException, SsrfRejectedException, ReadOnlyVerificationException}` —
  all sealed under `ConnectionException : RuntimeException`.
- **Model import:** `riven.core.models.customsource.CustomSourceConnectionModel` —
  no credential fields; service passes decrypted host/port/db/user/sslMode at
  mapping time.
- **Factory import:** `riven.core.service.util.factory.customsource.CustomSourceConnectionEntityFactory.create(...)`
- **SQL path:** `core/db/schema/01_tables/custom_source_connections.sql`

## Self-Check

- Entity file present: `core/src/main/kotlin/riven/core/entity/customsource/CustomSourceConnectionEntity.kt`
- Repository file present: `core/src/main/kotlin/riven/core/repository/customsource/CustomSourceConnectionRepository.kt`
- Exceptions file present: `core/src/main/kotlin/riven/core/exceptions/customsource/ConnectionExceptions.kt`
- Model file present: `core/src/main/kotlin/riven/core/models/customsource/CustomSourceConnectionModel.kt`
- SQL file present: `core/db/schema/01_tables/custom_source_connections.sql`
- Factory populated: `core/src/test/kotlin/riven/core/service/util/factory/customsource/CustomSourceConnectionEntityFactory.kt`
- Test populated: `core/src/test/kotlin/riven/core/entity/customsource/CustomSourceConnectionEntityTest.kt`
- Commit `6c2cda43c` present in git log
- Commit `3881d0757` present in git log
- Test suite green: 3/3 in CustomSourceConnectionEntityTest

## Self-Check: PASSED
