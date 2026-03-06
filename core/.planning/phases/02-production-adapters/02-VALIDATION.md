---
phase: 2
slug: production-adapters
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-06
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + mockito-kotlin |
| **Config file** | `src/test/resources/application-test.yml` (H2 compat mode) |
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
| 02-01-XX | 01 | 1 | ADPT-01 | unit (mocked SupabaseClient) | `./gradlew test --tests "riven.core.service.storage.SupabaseStorageProviderTest"` | W0 | pending |
| 02-02-XX | 02 | 1 | ADPT-02 | unit (mocked S3Client) | `./gradlew test --tests "riven.core.service.storage.S3StorageProviderTest"` | W0 | pending |
| 02-XX-XX | 01/02 | 2 | ADPT-01/02 | unit (mocked StorageProvider) | `./gradlew test --tests "riven.core.service.storage.StorageServiceTest"` | Exists (needs new methods) | pending |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

- [ ] `src/test/kotlin/riven/core/service/storage/SupabaseStorageProviderTest.kt` — stubs for ADPT-01
- [ ] `src/test/kotlin/riven/core/service/storage/S3StorageProviderTest.kt` — stubs for ADPT-02
- [ ] New test methods in existing `StorageServiceTest.kt` for signed URL fallback behavior

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Supabase Storage end-to-end | ADPT-01 | Requires live Supabase project | Set `STORAGE_PROVIDER=supabase`, provide Supabase credentials, upload/download/delete a file |
| S3/MinIO end-to-end | ADPT-02 | Requires running MinIO or AWS credentials | Set `STORAGE_PROVIDER=s3`, provide MinIO/AWS credentials, upload/download/delete a file |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
