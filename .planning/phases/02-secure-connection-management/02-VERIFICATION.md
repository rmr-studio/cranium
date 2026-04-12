---
phase: 02-secure-connection-management
verified: 2026-04-13T00:00:00Z
status: passed
score: 5/5 must-haves verified
re_verification: null
---

# Phase 02: Secure Connection Management — Verification Report

**Phase Goal:** A workspace owner can create, view, update, and soft-delete a Postgres connection with encrypted credentials, SSRF-safe hostnames, and a verified read-only role — with shipping-blocker security gates enforced.

**Verified:** 2026-04-13
**Status:** PASSED
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (Success Criteria)

| # | Truth | Status | Evidence |
|---|---|---|---|
| 1 | POST /api/v1/custom-sources/connections with public host succeeds; credentials AES-256-GCM encrypted | VERIFIED | `CustomSourceConnectionController.kt:50` POST to `/api/v1/custom-sources/connections` -> `CustomSourceConnectionService.create()` runs gate chain, encrypts via `CredentialEncryptionService` (AES/GCM/NoPadding, 12-byte SecureRandom IV, 128-bit tag) then persists via `repository.save`. |
| 2 | Connection to localhost, 127.0.0.0/8, 169.254.169.254, RFC1918, IPv6 loopback rejected (DNS rebinding via resolved-IP check) | VERIFIED | `SsrfValidatorService.kt` uses `isLoopbackAddress`, `isSiteLocalAddress`, `isLinkLocalAddress`, `isAnyLocalAddress`, `isMulticastAddress` + explicit CIDR checks for CGNAT, broadcast, IPv4-mapped IPv6 (unwrap + re-check), IPv6 ULA. `NameResolver` seam allows DNS rebinding defense. Plan 03 summary confirms 20 parameterized unit tests green, including `DNS rebinding - hostname resolving to blocked ip is rejected`. |
| 3 | Role with INSERT/UPDATE/DELETE rejected with clear error | VERIFIED | `ReadOnlyRoleVerifierService.kt:99` checks `rolsuper/rolcreatedb/rolcreaterole`; lines 124-126 sweep `has_table_privilege(current_user, c.oid, 'INSERT'/'UPDATE'/'DELETE')`; SAVEPOINT probe at line 153 requires `sqlState == "42501"`. Throws `ReadOnlyVerificationException` with count-only message. Testcontainers test `rejects rw_user with INSERT` green. |
| 4 | No connection string in logs; CryptoException/DataCorruptionException surface as ConnectionStatus=FAILED with user-safe messages | VERIFIED | `LogRedactionConfiguration.kt` registers global `CredentialRedactionTurboFilter` via `@PostConstruct` on root `LoggerContext` with regex `(jdbc:)?postgresql://[^...]+` and `password=...`. `CustomSourceConnectionService.decryptToModel()` catches `DataCorruptionException` ("Stored credentials are unreadable — please re-enter the password.") and `CryptoException` ("Configuration error — contact support."), transitions to `ConnectionStatus.FAILED`, never propagates. |
| 5 | All connection operations workspace-scoped via @PreAuthorize and soft-delete | VERIFIED | `CustomSourceConnectionService.kt` every public method (create/test/update/softDelete/getById/listByWorkspace) bears `@PreAuthorize("@workspaceSecurity.hasWorkspace(#...)")`. `softDelete()` sets `deleted=true`+`deletedAt` rather than hard-delete. Entity extends `AuditableSoftDeletableEntity` with explicit `@SQLRestriction("deleted = false")`. |

**Score:** 5/5 success criteria verified

### Required Artifacts

Plan 01 (Entity/Repository/Model/Exceptions/SQL):

| Artifact | Status | Details |
|---|---|---|
| `core/src/main/kotlin/riven/core/entity/customsource/CustomSourceConnectionEntity.kt` | VERIFIED | 92 lines; extends `AuditableSoftDeletableEntity`; bytea columns + keyVersion; explicit `@SQLRestriction`; `toModel(host,port,database,user,sslMode)`. |
| `core/src/main/kotlin/riven/core/repository/customsource/CustomSourceConnectionRepository.kt` | VERIFIED | 22 lines; `findByWorkspaceId`, `findByIdAndWorkspaceId`. |
| `core/src/main/kotlin/riven/core/exceptions/customsource/ConnectionExceptions.kt` | VERIFIED | 29 lines; sealed class extends RuntimeException; 4 subtypes (CryptoException, DataCorruptionException, SsrfRejectedException, ReadOnlyVerificationException). |
| `core/src/main/kotlin/riven/core/models/customsource/CustomSourceConnectionModel.kt` | VERIFIED | 36 lines; no password/encryptedCredentials/iv/keyVersion fields. |
| `core/db/schema/01_tables/custom_source_connections.sql` | VERIFIED | 35 lines; uuid id, workspace_id FK, bytea credentials + iv, key_version int, audit + soft-delete cols. |

Plan 02 (Encryption + Log Redaction):

| Artifact | Status | Details |
|---|---|---|
| `core/src/main/kotlin/riven/core/configuration/properties/CustomSourceConfigurationProperties.kt` | VERIFIED | @ConfigurationProperties(prefix="riven.custom-source") with `credentialEncryptionKey`. |
| `core/src/main/kotlin/riven/core/service/customsource/CredentialEncryptionService.kt` | VERIFIED | 83 lines; AES/GCM/NoPadding, fresh SecureRandom IV per call, AEADBadTagException -> DataCorruptionException, other GeneralSecurityException -> CryptoException. Fail-fast bean init. |
| `core/src/main/kotlin/riven/core/service/customsource/EncryptedCredentials.kt` | VERIFIED | data class with ByteArray equals/hashCode. |
| `core/src/main/kotlin/riven/core/configuration/customsource/LogRedactionConfiguration.kt` | VERIFIED | 124 lines; `CredentialRedactionTurboFilter` added via `@PostConstruct` on root LoggerContext; idempotent registration. |

Plan 03 (SSRF + RO Verifier):

| Artifact | Status | Details |
|---|---|---|
| `core/src/main/kotlin/riven/core/service/customsource/SsrfValidatorService.kt` | VERIFIED | 148 lines; NameResolver abstraction; validateAndResolve returns `List<InetAddress>`; full IPv4 + IPv6 blocklist with mapped-IPv6 unwrap; `GENERIC_MESSAGE` (no CIDR disclosure). |
| `core/src/main/kotlin/riven/core/service/customsource/ReadOnlyRoleVerifierService.kt` | VERIFIED | 174 lines; `DriverManager.getConnection` (no HikariCP); 3-step probe (superuser attrs, INSERT/UPDATE/DELETE sweep, SAVEPOINT CREATE TABLE 42501); sanitize() strips postgresql URLs. |

Plan 04 (Service + Controller + DTOs):

| Artifact | Status | Details |
|---|---|---|
| `core/src/main/kotlin/riven/core/enums/customsource/SslMode.kt` | VERIFIED | 26 lines; @JsonValue/@JsonCreator for require/verify-ca/verify-full/prefer. |
| `core/src/main/kotlin/riven/core/models/customsource/CredentialPayload.kt` | VERIFIED | 23 lines; toString redacts password. |
| `core/src/main/kotlin/riven/core/service/customsource/CustomSourceConnectionService.kt` | VERIFIED | 335 lines; gate chain (SSRF -> RO -> encrypt -> save) inside @Transactional; PATCH branches on touchesCredentials(); per-row decrypt-failure isolation; activity logging on mutations. |
| `core/src/main/kotlin/riven/core/controller/customsource/CustomSourceConnectionController.kt` | VERIFIED | 115 lines; 6 endpoints (POST /test, POST, GET list, GET /{id}, PATCH /{id}, DELETE /{id}); thin delegation; @Valid on all bodies. |

### Key Link Verification

| From | To | Via | Status | Details |
|---|---|---|---|---|
| CustomSourceConnectionController | CustomSourceConnectionService | constructor + @PostMapping delegation | WIRED | Controller line 37 constructor-injects service; all 6 endpoints call `service.create/test/update/softDelete/getById/listByWorkspace`. |
| CustomSourceConnectionService.create | SsrfValidatorService + ReadOnlyRoleVerifierService + CredentialEncryptionService | constructor injection + sequential gate chain | WIRED | Lines 49-57 constructor; `runGateChain()` calls `ssrfValidator.validateAndResolve(host)` -> `roVerifier.verify(...resolved.first()...)`; `encryptPayload()` -> `repository.save`. |
| CustomSourceConnectionService.create | CustomSourceConnectionRepository.save | persist only after all gates pass | WIRED | Line 84: save occurs only after gateChain + encryptPayload succeed; @Transactional provides rollback. |
| ExceptionHandler | SsrfRejectedException + ReadOnlyVerificationException | @ExceptionHandler methods returning 400 | WIRED | ExceptionHandler.kt:251 + 264 both have @ExceptionHandler returning ResponseEntity<ErrorResponse>. |
| CredentialEncryptionService | CustomSourceConfigurationProperties | constructor injection | WIRED | Confirmed via import + constructor signature in summaries. |
| LogRedactionConfiguration | LoggerContext | @PostConstruct loggerContext.addTurboFilter() | WIRED | Line 121: `loggerContext.addTurboFilter(CredentialRedactionTurboFilter())`. |
| CustomSourceConnectionEntity | AuditableSoftDeletableEntity | extends | WIRED | Verified in summary + `@SQLRestriction` pattern. |
| CustomSourceConnectionEntity.toModel | CustomSourceConnectionModel | method; omits encryptedCredentials/iv/keyVersion/password | WIRED | Model data class lacks those fields. |
| SsrfValidatorService | SsrfRejectedException | throws on blocklist/unknown host | WIRED | `throw SsrfRejectedException(GENERIC_MESSAGE)` at lines 64/68/80. |
| ReadOnlyRoleVerifierService | DriverManager.getConnection | short-lived, NOT HikariCP | WIRED | Line 65; plan-03 summary confirms reflective test enforces no-Hikari contract. |
| ReadOnlyRoleVerifierService | ReadOnlyVerificationException | throws on superuser/write/CREATE | WIRED | Lines 107/133/149/159. |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| CONN-01 | 02-00, 02-01 | Entity extends AuditableEntity, implements SoftDeletable | SATISFIED | Entity extends AuditableSoftDeletableEntity; SQL DDL + 3-test Testcontainers round-trip. |
| CONN-02 | 02-00, 02-02 | AES-256-GCM encrypted credentials | SATISFIED | CredentialEncryptionService + 10 tests (round-trip, fresh-IV, tamper, wrong-key, init fail-fast). |
| CONN-03 | 02-00, 02-04 | Service CRUD with @PreAuthorize workspace scoping | SATISFIED | All 6 public methods bear @PreAuthorize on #workspaceId or #request.workspaceId. |
| CONN-04 | 02-00, 02-02 | Connection string never logged | SATISFIED | Global Logback TurboFilter with JDBC_URL + password= regex scrubs; 8 tests including third-party SQLException path. |
| CONN-05 | 02-00, 02-04 | REST endpoints for create/view/update/soft-delete | SATISFIED | 6 endpoints at /api/v1/custom-sources/connections with MockMvc controller tests. |
| SEC-01 | 02-00, 02-03 | SSRF blocklist | SATISFIED | 20 parameterized tests covering all listed CIDRs. |
| SEC-02 | 02-00, 02-03 | DNS rebinding defense | SATISFIED | NameResolver seam; resolve-once-check-then-JDBC-by-IP contract; tests with mocked resolver returning blocked IPs. |
| SEC-03 | 02-00, 02-03 | Read-only role enforcement | SATISFIED | Superuser attr check + privilege sweep + SAVEPOINT probe; 6 Testcontainers tests. |
| SEC-05 | 02-00, 02-02, 02-04 | CryptoException -> ConnectionStatus=FAILED, "Config error" | SATISFIED | decryptToModel() catches CryptoException -> transitionToFailed + buildFailedModel with "Configuration error — contact support." |
| SEC-06 | 02-00, 02-02, 02-04 | DataCorruptionException -> prompt re-enter | SATISFIED | decryptToModel() catches DataCorruptionException -> "Stored credentials are unreadable — please re-enter the password." |

All 10 Phase 2 requirements (CONN-01..05, SEC-01..03, SEC-05..06) are claimed across plans 02-00..02-04 with no orphans. REQUIREMENTS.md mapping table concurs that all 10 are Phase 2 and Complete.

### Anti-Patterns Found

None.

- No TODO/FIXME/XXX/HACK comments in any customsource production code.
- No @Disabled tests remain (all Wave 0 stubs were populated).
- No catch-and-swallow of credential exceptions; CryptoException / DataCorruptionException are intentionally caught at `decryptToModel` only for status transition, matching locked Phase-2 decision.
- No plaintext credential logging found.
- No `!!` ID assertions; `requireNotNull(id) { ... }` used throughout.
- No HikariCP usage in ReadOnlyRoleVerifierService (plan-03 reflective contract test confirms).

### Human Verification Required

The following items are recommended for manual verification but do NOT block phase passing, as the automated suite covers the primary contract. Called out in 02-VALIDATION.md as "Manual-Only Verifications":

1. **Real TLS SNI with sslServerHostname against real Postgres (e.g. RDS)**
   - Test: Create a connection to a real RDS Postgres instance with `sslmode=verify-full`.
   - Expected: TLS cert verification succeeds when the original hostname CN matches; fails if the resolved IP is presented without `sslServerHostname`.
   - Why human: Testcontainers default image runs without TLS so `sslmode=disable` is exercised in CI; real SNI behaviour requires a TLS-enabled target.

2. **End-to-end controller smoke against a production-shaped environment**
   - Test: POST /api/v1/custom-sources/connections with a real read-only role on a reachable public Postgres.
   - Expected: 200 OK with redacted DTO; credentials visible only via GET /{id} (decrypted); DELETE soft-deletes; subsequent GET 404.
   - Why human: MockMvc tests mock the service; controller<->service<->DB round-trip through @Transactional gate chain benefits from manual confirmation before shipping.

### Gaps Summary

None. All 5 success criteria are verified, all 10 requirements satisfied, all artifacts substantive and wired, all 4 plans' must_haves.truths accounted for. Phase 2 goal — "A workspace owner can create, view, update, and soft-delete a Postgres connection with encrypted credentials, SSRF-safe hostnames, and a verified read-only role — with shipping-blocker security gates enforced." — is ACHIEVED.

---

_Verified: 2026-04-13_
_Verifier: Claude (gsd-verifier)_
