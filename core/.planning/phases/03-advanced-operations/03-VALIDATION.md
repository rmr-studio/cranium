---
phase: 3
slug: advanced-operations
status: draft
nyquist_compliant: true
wave_0_complete: true
created: 2026-03-06
---

# Phase 3 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + mockito-kotlin 3.2.0 |
| **Config file** | `build.gradle.kts` (JUnit Platform) |
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
| 03-01-01 | 01 | 1 | FILE-09 | unit | `./gradlew test --tests "riven.core.service.storage.S3StorageProviderTest"` | Needs new tests | ⬜ pending |
| 03-01-02 | 01 | 1 | FILE-09 | unit | `./gradlew test --tests "riven.core.service.storage.SupabaseStorageProviderTest"` | Needs new tests | ⬜ pending |
| 03-01-03 | 01 | 1 | FILE-09 | unit | `./gradlew test --tests "riven.core.service.storage.LocalStorageProviderTest"` | Needs new tests | ⬜ pending |
| 03-01-04 | 01 | 1 | FILE-10 | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest"` | Needs new tests | ⬜ pending |
| 03-01-05 | 01 | 1 | FILE-10 | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest"` | Needs new tests | ⬜ pending |
| 03-01-06 | 01 | 1 | DATA-05 | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest"` | Needs new tests | ⬜ pending |
| 03-01-07 | 01 | 1 | DATA-05 | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest"` | Needs new tests | ⬜ pending |
| 03-01-08 | 01 | 1 | DATA-05 | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest"` | Needs new tests | ⬜ pending |
| 03-02-01 | 02 | 1 | FILE-11 | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest"` | Needs new tests | ⬜ pending |
| 03-02-02 | 02 | 1 | FILE-11 | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest"` | Needs new tests | ⬜ pending |
| 03-02-03 | 02 | 1 | FILE-12 | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest"` | Needs new tests | ⬜ pending |
| 03-02-04 | 02 | 1 | FILE-12 | unit | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest"` | Needs new tests | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements. New test cases will be added to existing test classes:
- `StorageServiceTest` — presigned upload confirmation, metadata CRUD, batch operations
- `S3StorageProviderTest` — `generateUploadUrl` presigned PUT
- `SupabaseStorageProviderTest` — `generateUploadUrl` signed upload URL
- `LocalStorageProviderTest` — `generateUploadUrl` throws `UnsupportedOperationException`

*No new test infrastructure or Wave 0 setup needed.*

---

## Manual-Only Verifications

*All phase behaviors have automated verification.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
