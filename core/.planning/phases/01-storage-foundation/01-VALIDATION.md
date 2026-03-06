---
phase: 1
slug: storage-foundation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-06
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + mockito-kotlin |
| **Config file** | `src/test/resources/application-test.yml` (H2 in Postgres-compat mode) |
| **Quick run command** | `./gradlew test --tests "riven.core.service.storage.*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "riven.core.service.storage.*"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 01-01-01 | 01 | 1 | PROV-01 | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest"` | ❌ W0 | ⬜ pending |
| 01-01-02 | 01 | 1 | PROV-02 | unit | `./gradlew test --tests "riven.core.service.storage.LocalStorageProviderTest"` | ❌ W0 | ⬜ pending |
| 01-01-03 | 01 | 1 | PROV-04 | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest"` | ❌ W0 | ⬜ pending |
| 01-01-04 | 01 | 1 | ADPT-03 | unit | `./gradlew test --tests "riven.core.service.storage.LocalStorageProviderTest"` | ❌ W0 | ⬜ pending |
| 01-02-01 | 02 | 1 | FILE-01 | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest"` | ❌ W0 | ⬜ pending |
| 01-02-02 | 02 | 1 | FILE-02 | unit | `./gradlew test --tests "riven.core.service.storage.ContentValidationServiceTest"` | ❌ W0 | ⬜ pending |
| 01-02-03 | 02 | 1 | FILE-03 | unit | `./gradlew test --tests "riven.core.service.storage.ContentValidationServiceTest"` | ❌ W0 | ⬜ pending |
| 01-02-04 | 02 | 1 | FILE-04 | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest"` | ❌ W0 | ⬜ pending |
| 01-02-05 | 02 | 1 | FILE-05 | unit | `./gradlew test --tests "riven.core.service.storage.SignedUrlServiceTest"` | ❌ W0 | ⬜ pending |
| 01-02-06 | 02 | 1 | FILE-06 | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest"` | ❌ W0 | ⬜ pending |
| 01-02-07 | 02 | 1 | FILE-07 | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest"` | ❌ W0 | ⬜ pending |
| 01-02-08 | 02 | 1 | FILE-08 | unit | `./gradlew test --tests "riven.core.service.storage.ContentValidationServiceTest"` | ❌ W0 | ⬜ pending |
| 01-03-01 | 03 | 2 | DATA-01 | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest"` | ❌ W0 | ⬜ pending |
| 01-03-02 | 03 | 2 | DATA-02 | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest"` | ❌ W0 | ⬜ pending |
| 01-03-03 | 03 | 2 | DATA-03 | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest"` | ❌ W0 | ⬜ pending |
| 01-03-04 | 03 | 2 | DATA-04 | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest"` | ❌ W0 | ⬜ pending |
| 01-04-01 | 04 | 2 | API-01 | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/kotlin/riven/core/service/storage/StorageServiceTest.kt` — stubs for PROV-01, FILE-01, FILE-04, FILE-06, FILE-07, DATA-01, DATA-02, DATA-03, DATA-04
- [ ] `src/test/kotlin/riven/core/service/storage/ContentValidationServiceTest.kt` — stubs for FILE-02, FILE-03, FILE-08
- [ ] `src/test/kotlin/riven/core/service/storage/SignedUrlServiceTest.kt` — stubs for FILE-05
- [ ] `src/test/kotlin/riven/core/service/storage/LocalStorageProviderTest.kt` — stubs for ADPT-03, PROV-02
- [ ] `src/test/kotlin/riven/core/service/util/factory/storage/StorageFactory.kt` — shared test data factory

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| SVG sanitization strips malicious content | FILE-02/FILE-08 | Library-level security verification | Upload SVG with embedded script tags, verify script is removed from stored file |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
