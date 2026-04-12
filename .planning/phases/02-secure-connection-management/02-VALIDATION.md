---
phase: 2
slug: secure-connection-management
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-12
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito Kotlin 3.2.0 + Spring Boot Test + Testcontainers (Postgres) |
| **Config file** | None explicit — `@SpringBootTest(classes = [...])` per test class |
| **Quick run command** | `./gradlew test --tests "riven.core.service.customsource.*" --tests "riven.core.entity.customsource.*" --tests "riven.core.controller.customsource.*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | Quick ~30s; Full ~3–5 min (Testcontainers startup dominates) |

---

## Sampling Rate

- **After every task commit:** Run quick run command
- **After every plan wave:** Run full suite command
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds (quick); 300s (full)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 02-00-01 | 00 | 0 | CONN-01..05, SEC-01..06 | test scaffolding | `./gradlew test --tests "riven.core.*.customsource.*"` | ❌ W0 | ⬜ pending |
| 02-XX-XX | XX | 1+ | CONN-01 | integration (Testcontainers) | `./gradlew test --tests "riven.core.entity.customsource.CustomSourceConnectionEntityTest"` | ❌ W0 | ⬜ pending |
| 02-XX-XX | XX | 1+ | CONN-02 | unit | `./gradlew test --tests "riven.core.service.customsource.CredentialEncryptionServiceTest"` | ❌ W0 | ⬜ pending |
| 02-XX-XX | XX | 1+ | CONN-03 | unit | `./gradlew test --tests "riven.core.service.customsource.CustomSourceConnectionServiceTest"` | ❌ W0 | ⬜ pending |
| 02-XX-XX | XX | 1+ | CONN-04 | unit (appender capture) | `./gradlew test --tests "riven.core.configuration.customsource.LogRedactionTest"` | ❌ W0 | ⬜ pending |
| 02-XX-XX | XX | 1+ | CONN-05 | unit (MockMvc) | `./gradlew test --tests "riven.core.controller.customsource.CustomSourceConnectionControllerTest"` | ❌ W0 | ⬜ pending |
| 02-XX-XX | XX | 1+ | SEC-01 | unit | `./gradlew test --tests "riven.core.service.customsource.SsrfValidatorServiceTest"` | ❌ W0 | ⬜ pending |
| 02-XX-XX | XX | 1+ | SEC-02 | unit (DNS rebind) | `./gradlew test --tests "riven.core.service.customsource.SsrfValidatorServiceTest.DNS rebinding*"` | ❌ W0 | ⬜ pending |
| 02-XX-XX | XX | 1+ | SEC-03 | integration (Testcontainers) | `./gradlew test --tests "riven.core.service.customsource.ReadOnlyRoleVerifierServiceTest"` | ❌ W0 | ⬜ pending |
| 02-XX-XX | XX | 1+ | SEC-05 | unit | `./gradlew test --tests "riven.core.service.customsource.CustomSourceConnectionServiceTest.crypto error*"` | ❌ W0 | ⬜ pending |
| 02-XX-XX | XX | 1+ | SEC-06 | unit | `./gradlew test --tests "riven.core.service.customsource.CustomSourceConnectionServiceTest.data corruption*"` | ❌ W0 | ⬜ pending |

*Task IDs finalized by planner. Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/kotlin/riven/core/service/customsource/CredentialEncryptionServiceTest.kt` — covers CONN-02
- [ ] `src/test/kotlin/riven/core/service/customsource/SsrfValidatorServiceTest.kt` — covers SEC-01, SEC-02
- [ ] `src/test/kotlin/riven/core/service/customsource/ReadOnlyRoleVerifierServiceTest.kt` — covers SEC-03 (Testcontainers)
- [ ] `src/test/kotlin/riven/core/service/customsource/CustomSourceConnectionServiceTest.kt` — covers CONN-03, SEC-05, SEC-06
- [ ] `src/test/kotlin/riven/core/entity/customsource/CustomSourceConnectionEntityTest.kt` — covers CONN-01 (Testcontainers)
- [ ] `src/test/kotlin/riven/core/controller/customsource/CustomSourceConnectionControllerTest.kt` — covers CONN-05
- [ ] `src/test/kotlin/riven/core/configuration/customsource/LogRedactionTest.kt` — covers CONN-04
- [ ] `src/test/kotlin/riven/core/service/util/factory/customsource/CustomSourceConnectionEntityFactory.kt` — test factory required by CLAUDE.md

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| End-to-end against real public Postgres host | CONN-01, CONN-05 | Smoke check that TLS SNI + IP pinning works against a real remote server (not Testcontainers loopback) | From UI or curl: POST `/api/v1/custom-sources/connections` with a known public Postgres (e.g., a staging RDS read-replica); confirm 201 and `ConnectionStatus=VERIFIED` |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s quick / 300s full
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
