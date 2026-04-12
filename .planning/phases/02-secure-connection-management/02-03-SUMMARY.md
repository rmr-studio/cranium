---
phase: 02-secure-connection-management
plan: 03
subsystem: customsource
tags: [phase-2, wave-2, security, ssrf, read-only-role, testcontainers]
dependency_graph:
  requires:
    - 02-00 (Wave-0 stubs: SsrfValidatorServiceTest, ReadOnlyRoleVerifierServiceTest)
    - 02-01 (ConnectionException hierarchy — SsrfRejectedException, ReadOnlyVerificationException)
  provides:
    - NameResolver (interface) + DefaultNameResolver (@Component)
    - SsrfValidatorService.validateAndResolve(host) → List<InetAddress>
    - ReadOnlyRoleVerifierService.verify(host, resolvedIp, port, database, user, password, sslMode)
    - Generic SSRF rejection copy (SsrfValidatorService.GENERIC_MESSAGE)
  affects:
    - Plan 02-04 can wire both gates into the create-flow @Transactional chain
      (SSRF validator → RO verifier, passing resolved IP between them)
tech_stack:
  added: []
  patterns:
    - "NameResolver seam — abstracts InetAddress.getAllByName so tests can simulate DNS rebinding without a fake DNS server"
    - "Resolve-once contract — SsrfValidator returns the List<InetAddress> it validated; caller must connect by IP literal to defeat rebinding between check and use"
    - "IPv4-mapped IPv6 unwrap + re-check — ::ffff:10.0.0.1 is re-evaluated as 10.0.0.1 so IPv6-wrapped rfc1918 cannot bypass the blocklist"
    - "Short-lived DriverManager (NEVER HikariCP) for unverified roles — a role that fails the probe never enters the pool"
    - "SAVEPOINT CREATE TABLE probe keyed on SQLState 42501 (insufficient_privilege) — a pass is defined by the correct failure, not by any absence of exception"
    - "sanitize() regex stripping (jdbc:)?postgresql://\\S+ before any warn-log or exception propagation — jdbc URLs may contain host/port metadata the caller supplied"
    - "Generic SSRF rejection message — never discloses which CIDR / category matched; protects against reconnaissance via error-message oracle"
key_files:
  created:
    - core/src/main/kotlin/riven/core/service/customsource/SsrfValidatorService.kt
    - core/src/main/kotlin/riven/core/service/customsource/ReadOnlyRoleVerifierService.kt
  modified:
    - core/src/test/kotlin/riven/core/service/customsource/SsrfValidatorServiceTest.kt
    - core/src/test/kotlin/riven/core/service/customsource/ReadOnlyRoleVerifierServiceTest.kt
decisions:
  - "NameResolver is a separate interface (not a lambda on the service constructor) so DefaultNameResolver can be a @Component scanned at config time, while tests inject a mock() without Spring context — keeps the SSRF suite a pure unit test (20 tests, sub-second) with no Testcontainers cost."
  - "@Throws(UnknownHostException::class) on NameResolver.resolve so mockito-kotlin's thenThrow accepts a checked exception — Kotlin's default no-throws signature rejects UnknownHostException stubbing and blocks the DNS-failure test path."
  - "SSRF error copy is a const companion (GENERIC_MESSAGE) so tests can assert exact equality — prevents accidental drift toward leaking category info (e.g. 'rfc1918 blocked') in future edits."
  - "IPv4-mapped IPv6 handled by unwrap-and-re-check rather than a standalone /96 prefix test — this lets JVM-builtin isLoopbackAddress / isSiteLocalAddress / isLinkLocalAddress / isAnyLocalAddress short-circuit the check, covering ::ffff:127.0.0.0/104 etc. without hand-coded ranges."
  - "RO verifier uses DriverManager directly (not HikariCP) and tests verify this reflectively — contract documented in suite so a future refactor to @Autowire DataSource gets caught in CI."
  - "SAVEPOINT probe uses CREATE TABLE (not INSERT) because a clean RO role with CREATE on public still fails the check — we want to reject any schema-mutation capability, and CREATE is the cheapest write operation that doesn't require a pre-existing target table."
  - "Role teardown in @BeforeEach uses PL/pgSQL DO blocks with EXECUTE-wrapped REVOKE/DROP OWNED/DROP ROLE — a direct DROP ROLE fails with 'role cannot be dropped because some objects depend on it' on re-runs, and REASSIGN OWNED requires the role to own objects (which our fixture roles don't). Using DROP OWNED covers the shared-privileges case cleanly."
  - "admExecEach() splits statements at the Kotlin varargs boundary rather than ';' — PL/pgSQL DO blocks contain internal semicolons that a naive split would shred."
metrics:
  duration: "~9 min"
  completed: "2026-04-12"
  tasks: 2
  files_created: 2
  files_modified: 2
requirements:
  - SEC-01
  - SEC-02
  - SEC-03
---

# Phase 02 Plan 03: SSRF Validator + Read-Only Role Verifier Summary

Ship the two network-facing security gates for custom-source connection
creation: an SSRF validator that resolve-once-checks every candidate host
against the full IPv4 + IPv6 private/loopback/metadata blocklist with a
DNS-rebinding-safe contract, and a read-only role verifier that probes a
real Postgres with short-lived DriverManager to confirm the supplied role
has no superuser attributes, no write privileges anywhere, and no
CREATE-table capability. SEC-01 / SEC-02 / SEC-03 shipping-blockers
satisfied.

## What Landed

### Production code

- **`SsrfValidatorService`** + **`NameResolver` / `DefaultNameResolver`** —
  `validateAndResolve(host): List<InetAddress>` resolves via the seam,
  checks each address against JVM-builtin categories
  (`isLoopbackAddress`, `isSiteLocalAddress`, `isLinkLocalAddress`,
  `isAnyLocalAddress`, `isMulticastAddress`), then layers explicit CIDR
  checks for CGNAT (100.64.0.0/10), IPv4 multicast (224.0.0.0/4),
  broadcast (255.255.255.255), reserved-zero (0.0.0.0/8), IPv6 ULA
  (fc00::/7), and IPv4-mapped IPv6 (unwrap + re-check). Throws
  `SsrfRejectedException` with `GENERIC_MESSAGE` on any hit, on
  `UnknownHostException`, or on empty resolution. Returns the validated
  address list so the caller can JDBC-connect by IP literal.
- **`ReadOnlyRoleVerifierService`** — `verify(host, resolvedIp, port,
  database, user, password, sslMode)` opens a single short-lived
  `DriverManager.getConnection`, disables autocommit, runs three checks,
  and always rolls back:
  1. **Superuser attributes** — `SELECT rolsuper, rolcreatedb,
     rolcreaterole FROM pg_roles WHERE rolname = current_user`.
  2. **Write-privilege sweep** — `COUNT(*)` over `pg_class` × `pg_namespace`
     excluding `information_schema` and `pg_catalog`, filtered by
     `has_schema_privilege(... , 'USAGE')` AND any of INSERT/UPDATE/DELETE
     via `has_table_privilege`. Reports count only, never table names.
  3. **SAVEPOINT probe** — `CREATE TABLE __riven_ro_probe_<uuid>` inside
     a savepoint; pass iff it fails with SQLState `42501`. Rollback is
     best-effort in `finally`.
  JDBC URL built with IPv6-bracketed IP literal, `sslmode=<caller-value>`,
  `sslServerHostname=<original-host>` so TLS SNI and certificate
  verification still target the real hostname. Any `SQLException` is
  re-wrapped in `ReadOnlyVerificationException` with a `sanitize()`-stripped
  message so `jdbc:postgresql://...` never reaches logs.

### Tests

- **`SsrfValidatorServiceTest`** — pure unit (mocked `NameResolver` + mocked
  `KLogger`, no Spring, no Testcontainers). 20 tests:
  - Parameterised IPv4: `127.0.0.1`, `10.0.0.1`, `172.16.0.1`,
    `192.168.1.1`, `169.254.169.254`, `100.64.0.1`, `224.0.0.1`,
    `255.255.255.255`, `0.0.0.0`, `0.0.0.1` — all rejected with
    `GENERIC_MESSAGE`.
  - Parameterised IPv6: `::1`, `fe80::1`, `fc00::1`, `::ffff:127.0.0.1`,
    `::ffff:10.0.0.1` — all rejected.
  - `accepts public 8.8.8.8 and returns the resolved addresses` — confirms
    the `List<InetAddress>` round-trip.
  - `DNS rebinding defense - hostname resolving to blocked ip is rejected`
    — resolver stub returns `[10.0.0.1]` for `"evil.example.com"`.
  - `DNS rebinding defense - any blocked ip in multi-ip response is
    rejected` — `[8.8.8.8, 169.254.169.254]` → rejected.
  - `UnknownHostException is translated to SsrfRejectedException with
    generic message`.
  - `error message does not leak specific CIDR that matched` — asserts
    `message == GENERIC_MESSAGE` and does-not-contain probes.
- **`ReadOnlyRoleVerifierServiceTest`** — Testcontainers pgvector/pg16.
  Role fixtures rebuilt in `@BeforeEach` (PL/pgSQL `DO` blocks + DROP
  OWNED to escape Postgres' "role cannot be dropped" error on re-runs).
  6 tests: accepts RO role, SAVEPOINT leaves no residue, rejects
  superuser (Testcontainers' `test` user), rejects rw_user (INSERT on
  `public.sample`) with count-only message, sanitises JDBC URLs from
  auth-failure SQLException messages, reflective no-HikariCP/no-DataSource
  contract check on the service class.

## Verification

- `./gradlew test --tests "riven.core.service.customsource.SsrfValidatorServiceTest"` → 20/20 green
- `./gradlew test --tests "riven.core.service.customsource.ReadOnlyRoleVerifierServiceTest"` → 6/6 green
- `./gradlew build` → BUILD SUCCESSFUL (full suite + checks)
- `grep -r "Hikari\|DataSource" core/src/main/kotlin/riven/core/service/customsource/ReadOnlyRoleVerifierService.kt` → no matches
- SsrfRejectedException messages asserted equal to `GENERIC_MESSAGE` exactly — CIDR non-disclosure verified
- ReadOnlyVerificationException.message for write-role case asserts `contains("write privileges")` and `doesNotContain("sample")` — count-without-names confirmed

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Checked-exception stubbing on NameResolver.resolve**

- **Found during:** Task 1 first test run — `MockitoException` at the
  `UnknownHostException` stub call.
- **Issue:** Kotlin interface methods have no default `throws` declaration;
  Mockito refuses to stub a checked exception that isn't in the method
  signature. `thenThrow(UnknownHostException(...))` fails with
  "Checked exception is invalid for this method".
- **Fix:** Added `@Throws(UnknownHostException::class)` to
  `NameResolver.resolve`. Kotlin-side call sites are unaffected (Kotlin
  ignores `@Throws`), but the Java-facing signature now includes the
  exception, and Mockito accepts the stub.
- **Files modified:** `core/src/main/kotlin/riven/core/service/customsource/SsrfValidatorService.kt`
- **Commit:** `13c5561e8`

**2. [Rule 3 - Blocking] Role teardown fails on re-runs**

- **Found during:** Task 2 first test run — every test failed with
  `PSQLException: role "ro_user" cannot be dropped because some objects
  depend on it. Detail: privileges for schema public / privileges for
  database riven_test`.
- **Issue:** The plan snippet used `DROP ROLE IF EXISTS ro_user` directly.
  That works on the first container start, but on subsequent
  `@BeforeEach` calls Postgres refuses the drop because the role still
  owns the GRANTs from the previous iteration. The right primitive is
  `DROP OWNED BY <role>` followed by `DROP ROLE` — `DROP OWNED BY`
  cascades through privilege grants in every database/schema the role
  has touched.
- **Fix:** Replaced the naive drop-role sequence with PL/pgSQL `DO`
  blocks that check `pg_roles` existence and call `DROP OWNED BY` +
  `DROP ROLE` in order, with explicit `REVOKE` on database/schema/table
  privileges as a belt-and-suspenders pre-drop. Split all statements into
  Kotlin varargs (`adminExecEach(vararg statements: String)`) because
  `;`-splitting mangles the internal semicolons inside `DO` blocks.
- **Files modified:** `core/src/test/kotlin/riven/core/service/customsource/ReadOnlyRoleVerifierServiceTest.kt`
- **Commit:** `c2c4b12d4`

### Notes

- No architectural changes (Rule 4). No authentication gates. No
  checkpoints.

## Commits

| Task | Commit      | Message                                                                               |
| ---- | ----------- | ------------------------------------------------------------------------------------- |
| 1    | `13c5561e8` | feat(02-03): add SsrfValidatorService with blocklist + DNS rebinding defense          |
| 2    | `c2c4b12d4` | feat(02-03): add ReadOnlyRoleVerifierService with Testcontainers SEC-03 suite         |

## Requirements Status

- **SEC-01 (SSRF blocklist)** — SATISFIED. Exhaustive IPv4 + IPv6 coverage,
  parameterised tests for every listed CIDR. Error copy does not disclose
  category.
- **SEC-02 (DNS-rebinding-safe resolved-IP check)** — SATISFIED.
  `validateAndResolve` returns the List<InetAddress> the caller must
  connect to; the RO verifier's `buildJdbcUrl` consumes the resolved IP
  directly (not the hostname) and brackets IPv6.
- **SEC-03 (read-only role enforcement)** — SATISFIED. Three-step probe
  backed by real Postgres; superuser/createdb/createrole attribute check,
  INSERT/UPDATE/DELETE sweep, CREATE-table SAVEPOINT expecting 42501.
  Short-lived DriverManager, never HikariCP.

## Downstream Contracts (for Plan 02-04)

Plan 04 can compose the two gates as:

```kotlin
val resolved = ssrfValidator.validateAndResolve(request.host)
val primaryIp = resolved.first()
readOnlyVerifier.verify(
    host = request.host,
    resolvedIp = primaryIp,
    port = request.port,
    database = request.database,
    user = request.user,
    password = decryptedPassword,
    sslMode = request.sslMode,
)
```

- Both services are `@Service`-annotated, constructor-inject `KLogger`.
- `SsrfValidatorService` additionally needs `NameResolver` — bound by
  default to `DefaultNameResolver` (also a `@Component`), so no extra
  wiring is required.
- Both services throw subtypes of `ConnectionException` (Plan 01 sealed
  hierarchy), so `@ControllerAdvice` / Temporal no-retry configuration
  can remain untouched.
- Generic SSRF copy available as `SsrfValidatorService.GENERIC_MESSAGE`
  for user-facing error messages in the controller layer.

## Manual-Verify Caveat

Testcontainers runs Postgres without TLS, so `sslmode=disable` in the
RO verifier tests means the `sslServerHostname` URL parameter is
currently exercised only structurally (appears in the built JDBC URL)
rather than behaviourally (verifying SNI / cert CN against the original
hostname). Real TLS SNI behaviour is covered by 02-VALIDATION.md's
"Manual-Only Verifications" row and will be exercised end-to-end in
Plan 02-04's controller smoke test against a real RDS instance.

## Self-Check

- SsrfValidatorService.kt present: FOUND
- ReadOnlyRoleVerifierService.kt present: FOUND
- SsrfValidatorServiceTest.kt populated: FOUND (125 lines, 20 tests)
- ReadOnlyRoleVerifierServiceTest.kt populated: FOUND (210 lines, 6 tests)
- Commit `13c5561e8` present in git log: FOUND
- Commit `c2c4b12d4` present in git log: FOUND
- `./gradlew build` green: CONFIRMED

## Self-Check: PASSED
