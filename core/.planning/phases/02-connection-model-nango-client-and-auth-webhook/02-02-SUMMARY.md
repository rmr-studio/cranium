---
phase: 02-connection-model-nango-client-and-auth-webhook
plan: "02"
subsystem: integration
tags: [nango, http-client, dto, records, webhook, sync]
dependency_graph:
  requires: []
  provides:
    - NangoRecordsPage (paginated record response DTO)
    - NangoWebhookPayload (webhook event DTO for Plan 03)
    - NangoClientWrapper.fetchRecords() (paginated record retrieval for Phase 3 sync)
    - NangoClientWrapper.triggerSync() (sync trigger for Phase 3 orchestration)
  affects:
    - Phase 3 Temporal sync workflow (consumes fetchRecords)
    - Plan 03 webhook handler (consumes NangoWebhookPayload, NangoWebhookTags)
tech_stack:
  added: []
  patterns:
    - WebClient fluent chain with withNangoErrorHandling() + withNangoRetry() extension functions
    - @JsonAnySetter for generic payload capture in NangoRecord
    - Paginated cursor-based API design (NangoRecordsPage with nextCursor)
key_files:
  created:
    - src/main/kotlin/riven/core/models/integration/NangoRecordModels.kt
    - src/main/kotlin/riven/core/models/integration/NangoWebhookPayload.kt
    - src/test/kotlin/riven/core/service/integration/NangoClientWrapperTest.kt
  modified:
    - src/main/kotlin/riven/core/service/integration/NangoClientWrapper.kt
decisions:
  - NangoRecord uses @JsonAnySetter to capture arbitrary provider fields into payload map — enables schema-agnostic record handling across different integrations
  - fetchRecords() returns empty NangoRecordsPage on null response (not throws) — empty page is a valid state (unlike getConnection where null indicates API error)
  - NangoWebhookTags pragmatically reuses end_user_email field for integrationDefinitionId — Nango only provides 3 tag fields; this mapping is documented in KDoc and enforced in Plan 03 webhook handler
metrics:
  duration: "~15 minutes"
  completed: "2026-03-17"
  tasks_completed: 3
  files_created: 3
  files_modified: 1
---

# Phase 02 Plan 02: Nango Record DTOs and Client Methods Summary

Extended NangoClientWrapper with fetchRecords() and triggerSync() methods, created NangoRecordsPage/NangoWebhookPayload DTO families, and added 9-test unit coverage for both new methods using Mockito WebClient chain mocking.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Create Nango DTOs for records and webhook payloads | 487e3921c | NangoRecordModels.kt, NangoWebhookPayload.kt |
| 2 | Add fetchRecords() and triggerSync() to NangoClientWrapper | 9d4327b9b | NangoClientWrapper.kt |
| 3 | Unit tests for fetchRecords() and triggerSync() | b3d09708a | NangoClientWrapperTest.kt |

## What Was Built

### NangoRecordModels.kt

Three DTOs for the Nango GET /records response:
- `NangoRecordsPage` — top-level paginated response with `records` list and `nextCursor`
- `NangoRecord` — individual record with `_nango_metadata` plus arbitrary provider fields captured via `@JsonAnySetter` into a `payload: MutableMap<String, Any?>`
- `NangoRecordMetadata` — Nango-managed metadata: `lastAction` (ADDED/UPDATED/DELETED), `cursor`, timestamps

### NangoWebhookPayload.kt

Four DTOs for webhook event handling and sync triggering:
- `NangoWebhookPayload` — covers both `auth` and `sync` event types; fields not relevant to an event type are null
- `NangoWebhookTags` — three custom tag fields with KDoc documenting the Riven mapping convention (`end_user_id`=userId, `organization_id`=workspaceId, `end_user_email`=integrationDefinitionId)
- `NangoSyncResults` — added/updated/deleted counts from sync events
- `NangoTriggerSyncRequest` — request body for POST /sync/trigger

### NangoClientWrapper additions

Two new public methods in a `// ------ Sync Operations ------` section:
- `fetchRecords(providerConfigKey, connectionId, model, cursor?, modifiedAfter?, limit?)` — GET /records with Connection-Id + Provider-Config-Key headers, optional query params for pagination/filtering
- `triggerSync(providerConfigKey, connectionId?, syncs)` — POST /sync/trigger with NangoTriggerSyncRequest body

Both follow the established `withNangoErrorHandling() + withNangoRetry()` pattern.

### NangoClientWrapperTest.kt

9 unit tests using Mockito WebClient chain mocking (doAnswer for URI function capture):

**FetchRecordsTests (5):**
- Verifies Connection-Id and Provider-Config-Key headers are set
- Verifies cursor/modifiedAfter/limit query params are included when provided
- Verifies optional query params are omitted when null
- Verifies null response body returns empty NangoRecordsPage (not exception)
- Verifies records and nextCursor are deserialized correctly

**TriggerSyncTests (4):**
- Verifies POST /sync/trigger endpoint and body contents
- Verifies connectionId is included in body when provided
- Verifies connectionId is absent from body when null
- Verifies no exception on successful 200 response

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Stale compiled class files caused false compilation error**
- **Found during:** Task 1 verification
- **Issue:** `./gradlew compileKotlin` failed with "Unresolved reference: PENDING_AUTHORIZATION" in `IntegrationConnectionService.kt`. The file did not actually contain this reference — stale `.class` files from before the Phase 02-01 enum refactor were cached.
- **Fix:** Ran `./gradlew clean compileKotlin` to purge stale class files. Clean build succeeded.
- **Files modified:** None (build system state only)

## Self-Check: PASSED

All files confirmed on disk. All commits confirmed in git history.

| Item | Status |
|------|--------|
| NangoRecordModels.kt | FOUND |
| NangoWebhookPayload.kt | FOUND |
| NangoClientWrapperTest.kt | FOUND |
| Commit 487e3921c (Task 1) | FOUND |
| Commit 9d4327b9b (Task 2) | FOUND |
| Commit b3d09708a (Task 3) | FOUND |
