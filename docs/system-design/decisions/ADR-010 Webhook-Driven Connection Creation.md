---
tags:
  - adr/proposed
  - architecture/decision
Created: 2026-03-16
---
# ADR-010: Webhook-Driven Connection Creation

---

## Context

The current integration connection lifecycle has 10 states in the `ConnectionStatus` enum, including `PENDING_AUTHORIZATION` and `AUTHORIZING`. The intended flow is: backend pre-creates a connection in `PENDING_AUTHORIZATION` when the user enables an integration, frontend opens Nango's Connect UI for OAuth, OAuth completes, and the backend transitions the connection through `AUTHORIZING` to `CONNECTED`.

In practice, `PENDING_AUTHORIZATION` and `AUTHORIZING` are dead code. The OAuth flow is handled entirely by Nango's frontend Connect UI — the backend has no role during the authorization phase and cannot influence whether the user completes or abandons the OAuth consent screen. These states add complexity to the state machine without providing value. Connections are pre-created for OAuth flows that may never complete (user closes the browser, denies consent, or encounters an error), leaving orphaned connection records in `PENDING_AUTHORIZATION`.

Additionally, the `enableIntegration()` endpoint currently creates both the installation record and the connection entity in a single operation, coupling installation enablement to connection creation. This means the system cannot distinguish between "integration enabled, waiting for OAuth" and "integration enabled, OAuth completed" — both states result in a connection record existing.

The `IntegrationInstallation` entity has an `InstallationStatus` enum (`PENDING_CONNECTION`, `ACTIVE`, `FAILED`) that was designed to track this lifecycle but is underutilized because the connection is created eagerly.

---

## Decision

Remove `PENDING_AUTHORIZATION` and `AUTHORIZING` from the `ConnectionStatus` enum. Connections are created in `CONNECTED` state directly in response to Nango's auth webhook, which fires only after successful OAuth completion. The `enableIntegration()` endpoint creates only an installation record in `PENDING_CONNECTION` status; connection creation and template materialization are triggered by the auth webhook handler when Nango confirms successful authorization.

---

## Rationale

- **`PENDING_AUTHORIZATION` and `AUTHORIZING` have no observable effect** — the backend cannot influence the OAuth flow and these states exist only as bookkeeping that the frontend ignores; no business logic branches on these states
- **Webhook-driven creation is more reliable** — the connection is only created after OAuth actually succeeds, eliminating orphaned connection records for abandoned or failed OAuth flows
- **Decoupling installation from connection creation** enables the frontend to show a clear lifecycle: `PENDING_CONNECTION` (waiting for user to complete OAuth) to `ACTIVE` (OAuth succeeded, connection established), with `FAILED` for timeout or error cases
- **`InstallationStatus` surfaces auth failures** that were previously invisible — if the webhook never arrives (OAuth abandoned or Nango error), the installation stays in `PENDING_CONNECTION` and can be timed out to `FAILED`, giving the user actionable feedback
- **Simplifies the state machine** from 10 states to 8, reducing the number of valid transitions and the cognitive load for developers working with connection lifecycle code

---

## Alternatives Considered

### Option 1: Keep Existing 10-State Model

Retain `PENDING_AUTHORIZATION` and `AUTHORIZING` in the enum and continue pre-creating connections on integration enablement.

- **Pros:** No breaking change. Existing code continues to work without modification.
- **Cons:** `PENDING_AUTHORIZATION` and `AUTHORIZING` are dead code that adds cognitive load to the state machine. Pre-created connections for abandoned OAuth flows accumulate in the database. Cannot distinguish "waiting for OAuth" from "OAuth completed" at the installation level. The state machine has transitions that never fire in production.
- **Why rejected:** Maintaining dead states in the enum makes the connection lifecycle harder to reason about and leaves orphaned records for incomplete OAuth flows.

### Option 2: Remove States but Keep Pre-Creation

Remove `PENDING_AUTHORIZATION` and `AUTHORIZING` but continue creating the connection in `CONNECTED` state immediately on enablement, before OAuth completes.

- **Pros:** Simpler enum. Connection exists immediately for any queries that need it.
- **Cons:** Connection exists before the user has authorized, creating a misleading state — the system reports a connection as `CONNECTED` when no valid OAuth token exists. If OAuth fails or is abandoned, the connection must be cleaned up. Nango may reject API calls for a connection that has not completed auth.
- **Why rejected:** Creating a `CONNECTED` connection before OAuth completion is semantically incorrect and introduces cleanup complexity for failed flows.

### Option 3: Polling-Based Connection Detection

Instead of relying on Nango's auth webhook, have the frontend poll the Nango API or a backend endpoint to detect when the OAuth flow completes.

- **Pros:** No dependency on webhook delivery. Frontend controls the timing.
- **Cons:** Adds latency (polling interval). Unnecessary API calls to Nango. No server-side event to trigger materialization — must either poll server-side as well or wait for the next frontend request. More complex frontend logic.
- **Why rejected:** Webhook-driven is more efficient and provides a clear server-side trigger for downstream processing (connection creation, template materialization, initial sync).

---

## Consequences

### Positive

- Connection records only exist after successful OAuth — no orphaned connections for abandoned or failed flows
- The `InstallationStatus` lifecycle (`PENDING_CONNECTION` to `ACTIVE` to `FAILED`) provides clear, user-visible feedback on integration enablement progress
- Simpler state machine with 8 states instead of 10, reducing the number of valid transitions and edge cases
- Template materialization and initial sync are triggered by the same webhook event, creating a clean causal chain: auth success to connection creation to materialization to first sync

### Negative

- Breaking change to the `ConnectionStatus` enum — any code referencing `PENDING_AUTHORIZATION` or `AUTHORIZING` must be updated or removed
- The Integration Connection Lifecycle documentation in the architecture vault needs updating to reflect the simplified state machine
- If Nango's auth webhook fails to deliver (rare but possible), the installation remains in `PENDING_CONNECTION` indefinitely without a manual recovery mechanism (mitigated by adding a timeout-to-`FAILED` mechanism)

### Neutral

- Frontend must handle the `PENDING_CONNECTION` to `ACTIVE` installation lifecycle, either by polling the installation status or receiving a WebSocket/push notification when the webhook fires
- The webhook handler takes on additional responsibility (connection creation + materialization trigger) compared to the current model where it only updates an existing connection

---

## Implementation Notes

- **`ConnectionStatus` enum:** Remove `PENDING_AUTHORIZATION` and `AUTHORIZING` values; update `canTransitionTo()` to reflect the simplified state machine
- **Simplified state machine transitions:**
  ```
  CONNECTED -> SYNCING -> HEALTHY | DEGRADED | FAILED
  HEALTHY -> SYNCING | STALE | DEGRADED | DISCONNECTING | FAILED
  DEGRADED -> HEALTHY | STALE | FAILED | DISCONNECTING
  STALE -> SYNCING | DISCONNECTING | FAILED
  DISCONNECTING -> DISCONNECTED | FAILED
  DISCONNECTED -> CONNECTED
  FAILED -> CONNECTED | DISCONNECTED
  ```
- **`IntegrationEnablementService.enableIntegration()`:** Create installation in `PENDING_CONNECTION` status only; do not create a connection or trigger materialization
- **`IntegrationConnectionService`:** Extract create-or-reconnect logic into a private method called by the webhook handler
- **`NangoWebhookService` auth handler:** Create connection in `CONNECTED` state, trigger template materialization, update installation status to `ACTIVE`
- **Cleanup consideration:** Add a scheduled job or manual endpoint to transition stale `PENDING_CONNECTION` installations to `FAILED` after a configurable timeout (e.g., 30 minutes)

---

## Related

- [[Integration Data Sync Pipeline]] — Feature design for the sync pipeline triggered after connection creation
- [[Integration Connection Lifecycle]] — Existing documentation that must be updated to reflect the simplified state machine
- [[Entity Integration Sync]] — Sub-domain plan for entity sync processing
- [[ADR-001 Nango as Integration Infrastructure]] — Decision to use Nango, whose webhook delivery model is central to this design
