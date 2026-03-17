---
phase: 02-connection-model-nango-client-and-auth-webhook
plan: "03"
subsystem: integration
tags: [kotlin, spring-boot, hmac, webhook, filter, security, materialization]

# Dependency graph
requires:
  - phase: 02-connection-model-nango-client-and-auth-webhook
    plan: 01
    provides: 8-state ConnectionStatus FSM, IntegrationConnectionRepository.findByNangoConnectionId(), IntegrationConnectionService refactoring
  - phase: 02-connection-model-nango-client-and-auth-webhook
    plan: 02
    provides: NangoWebhookPayload DTOs

provides:
  - NangoWebhookHmacFilter: HMAC-SHA256 request signature verification via OncePerRequestFilter
  - NangoWebhookController: POST /api/v1/webhooks/nango endpoint
  - NangoWebhookService: auth event handler (creates connection, installation, triggers materialization) and sync stub
  - SecurityConfig updated: /api/v1/webhooks/nango excluded from JWT auth
affects:
  - Phase 3 Temporal sync workflow (consumes NangoWebhookService sync stub, extends auth handler)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - HMAC webhook security via OncePerRequestFilter with CachedBodyHttpServletRequest inner class
    - Webhook controller always returns 200 — all failures logged and swallowed internally
    - Materialization failure compensation write pattern — catch block sets FAILED status and saves without rethrow
    - FilterRegistrationBean scoped URL pattern for endpoint-specific filter application

key-files:
  created:
    - src/main/kotlin/riven/core/filter/integration/NangoWebhookHmacFilter.kt
    - src/main/kotlin/riven/core/configuration/integration/NangoWebhookFilterConfiguration.kt
    - src/main/kotlin/riven/core/controller/integration/NangoWebhookController.kt
    - src/main/kotlin/riven/core/service/integration/NangoWebhookService.kt
    - src/test/kotlin/riven/core/filter/integration/NangoWebhookHmacFilterTest.kt
    - src/test/kotlin/riven/core/service/integration/NangoWebhookServiceTest.kt
  modified:
    - src/main/kotlin/riven/core/configuration/auth/SecurityConfig.kt

key-decisions:
  - "HMAC filter uses secretKey (not webhookSecret) per established project decision — secretKey is the signing key Nango uses"
  - "Webhook controller has no @PreAuthorize — HMAC filter handles all security for this endpoint"
  - "NangoWebhookService catches all exceptions — controller must always return 200 to Nango"
  - "Materialization failure uses compensation write pattern: catch block sets installation FAILED and saves without rethrowing, preserving CONNECTED connection in same transaction"
  - "NangoWebhookService does not call authTokenService.getUserId() — userId extracted from Nango tags"

patterns-established:
  - "Webhook security via servlet filter, not Spring Security JWT — HMAC is the auth mechanism for webhook endpoints"
  - "Compensation write pattern for partial failure in @Transactional with external calls: catch block does mutation + save, does NOT rethrow"

requirements-completed: [HOOK-01, HOOK-02, HOOK-03, HOOK-05, HOOK-06]

# Metrics
duration: ~25min
completed: 2026-03-17
---

# Phase 02 Plan 03: Nango Webhook Endpoint with HMAC Security

**HMAC-SHA256 verified webhook endpoint that creates IntegrationConnection + WorkspaceIntegrationInstallation on auth events, with materialization failure compensation and Phase 3 sync stub**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-03-17T08:58:24Z
- **Completed:** 2026-03-17T09:25:00Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments
- HMAC-SHA256 filter that reads raw request body, computes signature using NangoConfigurationProperties.secretKey, and compares with constant-time MessageDigest.isEqual — preventing timing attacks
- CachedBodyHttpServletRequest inner class wraps the request so the body remains readable by downstream handlers after the filter consumes the InputStream
- Auth webhook handler parses userId/workspaceId/integrationDefinitionId from Nango tags, creates/reconnects connection (CONNECTED), creates/restores installation (ACTIVE), triggers materialization
- Materialization failure compensation: catch block sets installation to FAILED and saves without rethrowing — connection stays CONNECTED in the same committed transaction
- 17 tests (5 filter + 12 service) covering all behaviors specified in the plan

## Task Commits

Each task was committed atomically:

1. **Task 1: HMAC filter, filter configuration, SecurityConfig update, controller, and webhook service** - `dcb9978d4` (feat)
2. **Task 2: Unit tests for HMAC filter and webhook service** - `04514f23b` (test)

## Files Created/Modified
- `src/main/kotlin/riven/core/filter/integration/NangoWebhookHmacFilter.kt` — OncePerRequestFilter with HMAC-SHA256 verification and CachedBodyHttpServletRequest inner class
- `src/main/kotlin/riven/core/configuration/integration/NangoWebhookFilterConfiguration.kt` — FilterRegistrationBean scoped to /api/v1/webhooks/nango at order 1
- `src/main/kotlin/riven/core/configuration/auth/SecurityConfig.kt` — added permitAll() for /api/v1/webhooks/nango
- `src/main/kotlin/riven/core/controller/integration/NangoWebhookController.kt` — thin POST endpoint, always returns 200
- `src/main/kotlin/riven/core/service/integration/NangoWebhookService.kt` — event routing, auth handler, sync stub
- `src/test/kotlin/riven/core/filter/integration/NangoWebhookHmacFilterTest.kt` — 5 plain JUnit 5 tests, no Spring context
- `src/test/kotlin/riven/core/service/integration/NangoWebhookServiceTest.kt` — 12 tests in Nested classes: AuthEventTests, SyncEventTests, EventRoutingTests

## Decisions Made
- HMAC filter uses `nangoProperties.secretKey` (not `webhookSecret`) — this matches the established project decision from Phase 2 context that secretKey is the signing key
- Controller has no `@PreAuthorize` — webhook path is not JWT-authenticated; HMAC filter is the security mechanism
- `NangoWebhookService` does not use `authTokenService.getUserId()` — userId comes from Nango tags, not a JWT principal
- Materialization failure compensation: catch + save (FAILED) + no-rethrow pattern allows connection and installation to commit atomically even when external materialization fails
- Sync event implemented as Phase 3 stub (logs + returns) — Temporal dispatch is Phase 3 work

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None.

## Next Phase Readiness

- Phase 2 (Wave 2) complete: webhook endpoint secured, auth handler creates all required entities
- Phase 3 (Temporal sync workflow) can proceed: sync event stub is in place, NangoWebhookService.handleSyncEvent() will be extended to dispatch to Temporal
- The connection is always CONNECTED on auth webhook receipt, and installation is always ACTIVE (unless materialization fails)

---
*Phase: 02-connection-model-nango-client-and-auth-webhook*
*Completed: 2026-03-17*
