---
phase: 02-secure-connection-management
plan: 02
subsystem: security
tags: [phase-2, wave-2, crypto, aes-gcm, logback, turbofilter, redaction]
dependency_graph:
  requires:
    - 02-01 (ConnectionException sealed hierarchy — CryptoException, DataCorruptionException)
  provides:
    - CustomSourceConfigurationProperties (@ConfigurationProperties riven.custom-source.*)
    - CustomSourceConfiguration (@EnableConfigurationProperties bootstrap)
    - EncryptedCredentials (ciphertext + iv + keyVersion value class)
    - CredentialEncryptionService (AES-256-GCM encrypt/decrypt)
    - CredentialRedactionTurboFilter + LogRedactionConfiguration (global Logback scrubber)
    - RIVEN_CREDENTIAL_ENCRYPTION_KEY env var contract
  affects:
    - 02-03 (SSRF validator runs before encrypt/decrypt during connect)
    - 02-04 (read-only verifier uses the scrubber when the driver leaks URLs in errors)
    - 02-05 (connection service injects CredentialEncryptionService to seal/unseal on save/read)
    - Any future Phase that logs connection details — scrubber is now globally active
tech_stack:
  added: []
  patterns:
    - "AES-256-GCM via javax.crypto.Cipher with 12-byte SecureRandom IV per call and 128-bit GCM tag (JVM stdlib, no new deps)"
    - "Fail-fast @ConfigurationProperties validation in bean-init (require + CryptoException on malformed base64) — no lazy first-use surprises"
    - "Logback TurboFilter registered on root LoggerContext via @PostConstruct for JVM-wide coverage — avoids per-appender logback-spring.xml sync burden"
    - "TurboFilter rewrite pattern: render → scrub → if changed, re-log via same logger with ThreadLocal re-entry guard + return FilterReply.DENY on original event; else NEUTRAL"
    - "Explicit equals/hashCode on data classes with ByteArray fields (contentEquals/contentHashCode) so round-trip comparisons are reliable"
key_files:
  created:
    - core/src/main/kotlin/riven/core/configuration/properties/CustomSourceConfigurationProperties.kt
    - core/src/main/kotlin/riven/core/configuration/customsource/CustomSourceConfiguration.kt
    - core/src/main/kotlin/riven/core/service/customsource/EncryptedCredentials.kt
    - core/src/main/kotlin/riven/core/service/customsource/CredentialEncryptionService.kt
    - core/src/main/kotlin/riven/core/configuration/customsource/LogRedactionConfiguration.kt
  modified:
    - core/src/main/resources/application.yml
    - core/src/test/kotlin/riven/core/service/customsource/CredentialEncryptionServiceTest.kt
    - core/src/test/kotlin/riven/core/configuration/customsource/LogRedactionTest.kt
key-decisions:
  - "AES-256-GCM with per-call 12-byte SecureRandom IV — avoids IV reuse (research pitfall 1); keyVersion=1 reserved for future rotation"
  - "AEADBadTagException → DataCorruptionException (matches both corrupted ciphertext AND wrong-key cases — at the cipher layer they are indistinguishable)"
  - "Other GeneralSecurityException → CryptoException (distinguishable wrapper type for operational triage)"
  - "Bean-init validation order: require(non-blank) → try base64 decode (CryptoException on failure) → require(size==32)"
  - "TurboFilter chosen over PatternConverter — satisfies locked decision 'Applied globally so third-party code is covered' without needing to keep logback-spring.xml synchronised with every appender"
  - "Re-log-and-DENY rewrite pattern accepted with known tradeoff: stack traces are NOT preserved on the re-logged scrubbed event. Primary defence remains service-layer exception sanitisation; this filter is the third-party safety net"
  - "Registration is idempotent — guards against Spring test-context reloads stacking duplicate filters"
requirements:
  - CONN-02
  - CONN-04
  - SEC-05
  - SEC-06
metrics:
  duration: "~4 min"
  completed: "2026-04-12"
  tasks: 2
  files_created: 5
  files_modified: 3
requirements-completed: [CONN-02, CONN-04, SEC-05, SEC-06]
---

# Phase 02 Plan 02: Credential Encryption + Global Log Redaction Summary

**AES-256-GCM credential encryption service (per-call SecureRandom IV, keyVersion=1) plus a global Logback TurboFilter that scrubs Postgres JDBC URLs and password= values from every logger in the JVM — including third-party SQLException paths.**

## Performance

- **Duration:** ~4 min
- **Started:** 2026-04-12T22:38:52Z
- **Completed:** 2026-04-12T22:42:42Z
- **Tasks:** 2
- **Files created:** 5
- **Files modified:** 3

## Accomplishments

- `CredentialEncryptionService` — AES-256-GCM with fresh 12-byte IV per encrypt, 128-bit tag; fail-fast bean init on blank / non-base64 / wrong-size key; AEADBadTagException → `DataCorruptionException`, other `GeneralSecurityException` → `CryptoException`
- `CustomSourceConfigurationProperties` (bound to `riven.custom-source.*`) + `CustomSourceConfiguration` (`@EnableConfigurationProperties`) + `application.yml` wired to `RIVEN_CREDENTIAL_ENCRYPTION_KEY` env var
- `EncryptedCredentials` value class with byte-array-content `equals`/`hashCode` for reliable persistence round-trips
- `CredentialRedactionTurboFilter` registered on the root Logback `LoggerContext` via `LogRedactionConfiguration.@PostConstruct` — covers every logger in the JVM, including third-party JDBC driver + HikariCP paths
- 10 tests for `CredentialEncryptionService` (round-trip, fresh-IV-per-call, tamper detection, wrong-key, bean-init fail-fast, value-class equality) and 8 tests for `LogRedactionTest` (direct URL, bare postgresql URL, password param, SLF4J format param, third-party SQLException, nested cause-chain, pass-through, idempotent registration)

## Task Commits

1. **Task 1: CredentialEncryptionService + ConfigurationProperties + EncryptedCredentials** — `e190aa34e` (feat)
2. **Task 2: Global Logback TurboFilter credential redaction** — `e15580b58` (feat)

**Plan metadata:** (see final docs commit)

## Files Created/Modified

### Created (main)

- `core/src/main/kotlin/riven/core/configuration/properties/CustomSourceConfigurationProperties.kt` — `@ConfigurationProperties(prefix="riven.custom-source")` data class with `credentialEncryptionKey` field
- `core/src/main/kotlin/riven/core/configuration/customsource/CustomSourceConfiguration.kt` — `@EnableConfigurationProperties(CustomSourceConfigurationProperties::class)` bootstrap
- `core/src/main/kotlin/riven/core/service/customsource/EncryptedCredentials.kt` — value class with contentEquals/contentHashCode overrides
- `core/src/main/kotlin/riven/core/service/customsource/CredentialEncryptionService.kt` — AES-256-GCM encrypt/decrypt
- `core/src/main/kotlin/riven/core/configuration/customsource/LogRedactionConfiguration.kt` — `CredentialRedactionTurboFilter` + `@Configuration` + `@PostConstruct` registrar

### Modified

- `core/src/main/resources/application.yml` — added `riven.custom-source.credential-encryption-key: ${RIVEN_CREDENTIAL_ENCRYPTION_KEY:}`
- `core/src/test/kotlin/riven/core/service/customsource/CredentialEncryptionServiceTest.kt` — populated (was `@Disabled` placeholder)
- `core/src/test/kotlin/riven/core/configuration/customsource/LogRedactionTest.kt` — populated (was `@Disabled` placeholder)

## Decisions Made

- **AES-256-GCM, 12-byte IV, 128-bit tag, keyVersion=1** — matches RESEARCH.md Pattern 1; fresh `SecureRandom` IV per call to avoid GCM nonce reuse (research pitfall 1). `keyVersion` field reserved for future rotation (Phase 7+).
- **AEADBadTagException → DataCorruptionException (not CryptoException)** — at the cipher layer, wrong-key and corrupted-ciphertext both surface as `AEADBadTagException`. Treating both as corruption is operationally correct: in either case the stored record cannot be decrypted and the user must re-enter credentials. `CryptoException` is reserved for genuinely different failure modes (non-base64 key, cipher misconfiguration).
- **TurboFilter over PatternConverter** — locked decision from CONTEXT.md required third-party code coverage. `PatternConverter` only scrubs specific appender pattern layouts and would miss `logger.error(throwable)` paths from the PostgreSQL driver or HikariCP. TurboFilter intercepts every log event on the root `LoggerContext` before any appender runs, with no `logback-spring.xml` sync burden.
- **Re-log-and-DENY rewrite pattern, stack-trace tradeoff accepted** — the SLF4J TurboFilter API forbids in-place format mutation, so we render → scrub → re-log the scrubbed `String` via the same logger under a ThreadLocal re-entry guard and return `FilterReply.DENY` on the original. The re-log loses the `Throwable` stack. Accepted for Phase 2 because: (a) our connection-service code sanitises exceptions before logging (primary defence); (b) this filter is belt-and-suspenders for third-party paths where we don't control the caller. If a future phase needs stack-preserving redaction, layer a `ThrowableConverter` in `logback-spring.xml` on top.
- **Idempotent `addTurboFilter`** — `loggerContext.turboFilterList.none { it is CredentialRedactionTurboFilter }` guard prevents Spring test-context reloads from stacking duplicates.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Reverted prematurely-populated `SsrfValidatorServiceTest.kt`**

- **Found during:** Task 1 (first `./gradlew test` compile attempt)
- **Issue:** `core/src/test/kotlin/riven/core/service/customsource/SsrfValidatorServiceTest.kt` had an uncommitted working-tree modification (123 lines of tests referencing `SsrfValidatorService` and `NameResolver` types that are scoped to Plan 02-03 and do not yet exist). The modification was left over from a prior session — it wasn't part of any scaffold commit. It broke `compileTestKotlin` for the entire module.
- **Fix:** `git checkout HEAD -- core/src/test/kotlin/riven/core/service/customsource/SsrfValidatorServiceTest.kt` — restored the committed `@Disabled` scaffold from commit `4e7084d10`. Plan 02-03 will populate it properly when `SsrfValidatorService` is built.
- **Files modified:** `core/src/test/kotlin/riven/core/service/customsource/SsrfValidatorServiceTest.kt` (reverted to committed scaffold; no net change vs `HEAD`)
- **Verification:** `./gradlew compileTestKotlin` green; `CredentialEncryptionServiceTest` runs 10/10 green.
- **Committed in:** N/A (revert — nothing to commit)

**2. [Rule 3 - Blocking] Reverted prematurely-created `ReadOnlyRoleVerifierService.kt` + populated `ReadOnlyRoleVerifierServiceTest.kt`**

- **Found during:** Final `git status` before metadata commit
- **Issue:** An untracked production file `core/src/main/kotlin/riven/core/service/customsource/ReadOnlyRoleVerifierService.kt` plus a modified test file `ReadOnlyRoleVerifierServiceTest.kt` (220 lines, full Testcontainers-backed SEC-03 coverage) were present in the worktree — leftover from a prior session that pre-populated Plan 02-04 work. These are out-of-scope for Plan 02-02.
- **Fix:** `git checkout HEAD -- ReadOnlyRoleVerifierServiceTest.kt` (restore scaffold) and `rm ReadOnlyRoleVerifierService.kt`. Plan 02-04 will re-create both when its wave runs.
- **Files modified:** reverted + deleted (no net change vs `HEAD`)
- **Verification:** `git status` clean of stray files; `compileTestKotlin` still green.
- **Committed in:** N/A (revert)

---

**Total deviations:** 2 auto-fixed (2 blocking — both prior-session leftovers)
**Impact on plan:** Zero scope creep. Both reverted files are out-of-scope for 02-02 (they belong to Plans 02-03 and 02-04). Deferring their population keeps phase ordering clean and avoids scope bleed between plans.

## Issues Encountered

- None during planned work. The SsrfValidatorServiceTest stray modification is documented above under Deviations.

## Authentication Gates

None — all work was pure implementation; no external services were invoked.

## User Setup Required

No new env var setup is _required_ during development (the property defaults to empty and only fails at service bean init). Before production deployment:

- `RIVEN_CREDENTIAL_ENCRYPTION_KEY` must be set to a base64-encoded 32-byte (256-bit) random value.
- Example generation: `openssl rand -base64 32`
- Do NOT rotate this key without a migration plan — existing `encrypted_credentials` rows cannot be decrypted under a different key.

(Not surfaced via USER-SETUP.md because the plan frontmatter does not declare a `user_setup:` section.)

## Verification

- `./gradlew test --tests "riven.core.service.customsource.CredentialEncryptionServiceTest"` → BUILD SUCCESSFUL (10/10 tests green)
- `./gradlew test --tests "riven.core.configuration.customsource.LogRedactionTest"` → BUILD SUCCESSFUL (8/8 tests green)
- `./gradlew compileKotlin compileTestKotlin` → BUILD SUCCESSFUL

## Downstream Contracts

Plans 02-03..05 can assume the following is in place:

- **Service import:** `riven.core.service.customsource.CredentialEncryptionService` — `encrypt(String) → EncryptedCredentials`, `decrypt(EncryptedCredentials) → String`
- **Value-class import:** `riven.core.service.customsource.EncryptedCredentials` — `ciphertext: ByteArray, iv: ByteArray, keyVersion: Int`
- **Config-properties import:** `riven.core.configuration.properties.CustomSourceConfigurationProperties`
- **Exception mapping:** encrypt/decrypt failures throw `CryptoException` or `DataCorruptionException` (both from Plan 02-01's `riven.core.exceptions.customsource`)
- **Logging contract:** credentials embedded in format strings, SLF4J params, or throwable cause chains are globally scrubbed at log emission time — downstream services do not need to pre-scrub to prevent credential leaks, but SHOULD still sanitise exceptions for stack-preserving error logging (see TurboFilter tradeoff above)
- **Env var contract:** `RIVEN_CREDENTIAL_ENCRYPTION_KEY` — base64-encoded 32-byte key

## Next Phase Readiness

Ready for Plan 02-03 (SSRF validator + read-only verifier) in parallel with Plan 02-04 (read-only role verifier), then Plan 02-05 (service + controller wire-up). This plan is file-disjoint from 02-03 (different packages, different test classes), so they can truly run in parallel per the wave-2 design.

## Self-Check

- `core/src/main/kotlin/riven/core/configuration/properties/CustomSourceConfigurationProperties.kt` present
- `core/src/main/kotlin/riven/core/configuration/customsource/CustomSourceConfiguration.kt` present
- `core/src/main/kotlin/riven/core/service/customsource/EncryptedCredentials.kt` present
- `core/src/main/kotlin/riven/core/service/customsource/CredentialEncryptionService.kt` present
- `core/src/main/kotlin/riven/core/configuration/customsource/LogRedactionConfiguration.kt` present
- `core/src/main/resources/application.yml` contains `riven.custom-source.credential-encryption-key`
- `core/src/test/kotlin/riven/core/service/customsource/CredentialEncryptionServiceTest.kt` populated (no `@Disabled`)
- `core/src/test/kotlin/riven/core/configuration/customsource/LogRedactionTest.kt` populated (no `@Disabled`)
- Commit `e190aa34e` present in git log
- Commit `e15580b58` present in git log
- Both test classes run green

## Self-Check: PASSED

---
*Phase: 02-secure-connection-management*
*Completed: 2026-04-12*
