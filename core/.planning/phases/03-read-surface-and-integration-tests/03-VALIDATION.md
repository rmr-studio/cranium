---
phase: 03
slug: read-surface-and-integration-tests
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-05
---

# Phase 03 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Testcontainers |
| **Config file** | `src/test/resources/application-integration.yml` |
| **Quick run command** | `./gradlew test --tests "*ManifestCatalogServiceTest*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "*catalog*"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 03-01-01 | 01 | 1 | QUERY-01 | unit | `./gradlew test --tests "*ManifestCatalogServiceTest*getAvailableTemplates*"` | No - W0 | pending |
| 03-01-02 | 01 | 1 | QUERY-02 | unit | `./gradlew test --tests "*ManifestCatalogServiceTest*getAvailableModels*"` | No - W0 | pending |
| 03-01-03 | 01 | 1 | QUERY-03 | unit | `./gradlew test --tests "*ManifestCatalogServiceTest*getManifestByKey*"` | No - W0 | pending |
| 03-01-04 | 01 | 1 | QUERY-04 | unit | `./gradlew test --tests "*ManifestCatalogServiceTest*getEntityTypesForManifest*"` | No - W0 | pending |
| 03-02-01 | 02 | 2 | TEST-06 | integration | `./gradlew test --tests "*ManifestLoaderIntegrationTest*fullLoadCycle*"` | No - W0 | pending |
| 03-02-02 | 02 | 2 | TEST-07 | integration | `./gradlew test --tests "*ManifestLoaderIntegrationTest*idempotentReload*"` | No - W0 | pending |
| 03-02-03 | 02 | 2 | TEST-08 | integration | `./gradlew test --tests "*ManifestLoaderIntegrationTest*removalReconciliation*"` | No - W0 | pending |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

- [ ] `ManifestCatalogServiceTest.kt` — unit test stubs for QUERY-01 through QUERY-04
- [ ] `ManifestLoaderIntegrationTest.kt` — integration test stubs for TEST-06, TEST-07, TEST-08

*Existing infrastructure covers test framework and Testcontainers setup.*

---

## Manual-Only Verifications

*All phase behaviors have automated verification.*

---

## Validation Sign-Off

- [ ] All tasks have automated verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
