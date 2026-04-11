---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2025-07-17
Updated: 2026-03-18
Domains:
  - "[[riven/docs/system-design/domains/Integrations/Integrations]]"
---
# IntegrationConnectionService

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Connection Management/Connection Management]]

## Purpose

Manages the lifecycle of integration connections between workspaces and external services. Enforces an 8-state connection state machine with validated transitions, coordinates with [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Connection Management/NangoClientWrapper]] for external OAuth connection management, and triggers [[riven/docs/system-design/domains/Integrations/Enablement/TemplateMaterializationService]] when connections become active. Connections are created exclusively by the webhook handler (via `createOrReconnect`) after successful OAuth completion — there is no public connection creation endpoint.

---

## Responsibilities

- Provide workspace-scoped connection queries (by workspace, by workspace + integration pair)
- Update connection status with state machine validation via `canTransitionTo()`
- Disconnect connections with graceful Nango cleanup using programmatic transaction management
- Create or reconnect connections via the internal `createOrReconnect` method (called by webhook handler only)
- Trigger template materialization when connections reach CONNECTED state via `updateConnectionStatus`
- Log activity for all mutations (create, update, disconnect)

---

## Dependencies

- `IntegrationConnectionRepository` -- Connection persistence
- `IntegrationDefinitionRepository` -- Validates integration definitions exist, provides Nango provider keys
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Connection Management/NangoClientWrapper]] -- External Nango API calls for connection deletion
- [[riven/docs/system-design/domains/Integrations/Enablement/TemplateMaterializationService]] -- Materializes entity type templates when a connection becomes CONNECTED
- `AuthTokenService` -- JWT user extraction
- `ActivityService` -- Audit logging
- `TransactionTemplate` -- Programmatic transaction management for the disconnect flow

## Used By

- [[riven/docs/system-design/domains/Integrations/Webhook Authentication/NangoWebhookService]] — Calls `createOrReconnect` for connection creation after OAuth
- [[riven/docs/system-design/domains/Integrations/Enablement/IntegrationEnablementService]] — Calls `getConnection` and `disconnectConnection` during disable flow
- [[riven/docs/system-design/domains/Integrations/Enablement/IntegrationController]] — REST endpoints for status queries

---

## Key Logic

### Connection state machine

The connection lifecycle is governed by `ConnectionStatus`, an enum with 8 states and explicit transition rules enforced via `canTransitionTo()`. Every call to `updateConnectionStatus` and internal transition helpers validates against this state machine before persisting.

| State | Valid transitions to |
|---|---|
| `CONNECTED` | `SYNCING`, `HEALTHY`, `DISCONNECTING`, `FAILED` |
| `SYNCING` | `HEALTHY`, `DEGRADED`, `FAILED` |
| `HEALTHY` | `SYNCING`, `STALE`, `DEGRADED`, `DISCONNECTING`, `FAILED` |
| `DEGRADED` | `HEALTHY`, `STALE`, `FAILED`, `DISCONNECTING` |
| `STALE` | `SYNCING`, `DISCONNECTING`, `FAILED` |
| `DISCONNECTING` | `DISCONNECTED`, `FAILED` |
| `DISCONNECTED` | `CONNECTED` |
| `FAILED` | `CONNECTED`, `DISCONNECTED` |

`PENDING_AUTHORIZATION` and `AUTHORIZING` were removed in Phase 2 — connections are created directly in `CONNECTED` state by the webhook handler since OAuth intermediates are handled entirely by Nango.

Terminal-ish states: `DISCONNECTED` can only re-enter the lifecycle via `CONNECTED` (through the webhook handler). `FAILED` can recover to `CONNECTED` (via webhook) or be marked `DISCONNECTED`.

### createOrReconnect (internal)

Internal method called by [[riven/docs/system-design/domains/Integrations/Webhook Authentication/NangoWebhookService]] after Nango confirms successful OAuth. Handles 4 scenarios:

1. No existing connection → create new with `CONNECTED` status, log CREATE activity
2. Existing with `DISCONNECTED` or `FAILED` status → validate state transition, set to `CONNECTED`, update `nangoConnectionId`, log UPDATE activity
3. Existing with `CONNECTED` status → idempotent: update `nangoConnectionId` if changed, otherwise no-op
4. Existing in any other state → update `nangoConnectionId` only (warning logged, status preserved)

This method is `internal` visibility — it is not exposed as a public API. Connection creation is exclusively webhook-driven.

### State transition enforcement

`canTransitionTo()` is defined on the `ConnectionStatus` enum itself. Each state declares its set of valid target states via a `when` expression. Invalid transitions cause `InvalidStateTransitionException`, which is mapped to an HTTP error by `@ControllerAdvice`.

The service never sets status directly without checking transitions, except in `createOrReconnect` and `reconnectConnection` where the target state (`CONNECTED`) is hardcoded and the source state is verified by the surrounding conditional logic.

### Nango integration

The service interacts with Nango exclusively through [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Connection Management/NangoClientWrapper]]:

- **Disconnect flow**: Calls `nangoClientWrapper.deleteConnection(providerConfigKey, connectionId)` to remove the external OAuth connection
- **Error handling**: All Nango exceptions (`NangoApiException`, `RateLimitException`, `TransientNangoException`, and generic `Exception`) are caught and logged -- Nango failures do NOT prevent the local connection from being marked `DISCONNECTED`
- The `providerConfigKey` is sourced from the `IntegrationDefinitionEntity.nangoProviderKey` field

### Programmatic transaction management

The `disconnectConnection` method uses `TransactionTemplate` instead of `@Transactional` to avoid holding a database transaction open during the external Nango API call. The flow is split into three steps, each with its own transaction boundary:

1. `transitionToDisconnecting()` -- Opens transaction, validates state, sets status to `DISCONNECTING`, commits. Returns Nango provider key and connection ID.
2. `deleteNangoConnection()` -- No transaction. Calls Nango API with graceful error handling.
3. `transitionToDisconnected()` -- Opens transaction, sets status to `DISCONNECTED`, logs activity, commits.

This ensures the `DISCONNECTING` intermediate state is visible to other transactions while the potentially slow Nango call executes.

### Template materialization on connect

When `updateConnectionStatus` transitions a connection to `CONNECTED`, it triggers `templateMaterializationService.materializeIntegrationTemplates()` to create workspace-scoped entity types and relationships from catalog definitions. Materialization failures are caught and logged -- they do not roll back the connection status change.

---

## Public Methods

### `getConnectionsByWorkspace(workspaceId: UUID): List<IntegrationConnectionEntity>`

Returns all connections for a workspace. Requires workspace membership via `@PreAuthorize`.

### `getConnection(workspaceId: UUID, integrationId: UUID): IntegrationConnectionEntity?`

Returns a specific workspace's connection to an integration, or null if none exists. Requires workspace membership.

### `updateConnectionStatus(workspaceId: UUID, connectionId: UUID, newStatus: ConnectionStatus, metadata: Map<String, Any>?): IntegrationConnectionEntity`

Updates a connection's status after validating the transition via `canTransitionTo()`. Optionally merges metadata into the existing `connectionMetadata` map. Triggers template materialization if the new status is `CONNECTED`. Throws `InvalidStateTransitionException` on invalid transitions. Annotated with `@Transactional`.

### `disconnectConnection(workspaceId: UUID, connectionId: UUID): IntegrationConnectionEntity`

Disconnects a connection using a three-phase flow: transition to `DISCONNECTING`, call Nango to delete the external connection, transition to `DISCONNECTED`. Requires ADMIN role. Uses programmatic transactions via `TransactionTemplate` (NOT `@Transactional`). Nango failures are logged but do not prevent local disconnection.

### `createOrReconnect(workspaceId: UUID, integrationId: UUID, nangoConnectionId: String, userId: UUID): IntegrationConnectionEntity` *(internal)*

Creates or reconnects a connection after webhook-confirmed OAuth. Called by [[riven/docs/system-design/domains/Integrations/Webhook Authentication/NangoWebhookService]] only. Not exposed as a public API method — `internal` visibility.

---

## Gotchas

- **State machine strictness**: All 8 states have explicit transition rules. There is no "force" override — invalid transitions always throw `InvalidStateTransitionException`. Code that needs to move a connection through multiple states must do so step by step.
- **Nango external dependency failure modes**: Nango API calls can throw `NangoApiException`, `RateLimitException`, or `TransientNangoException`. The disconnect flow handles all of these gracefully (log and continue), but other flows that may be added in future must account for these failure modes.
- **createOrReconnect bypasses state machine for new connections**: New connections start at `CONNECTED` directly since Nango has already completed the OAuth flow before the webhook arrives.
- **Reconnect overwrites nangoConnectionId**: When reconnecting a `DISCONNECTED` or `FAILED` connection, the stored `nangoConnectionId` is replaced with the new value from the webhook.
- **One connection per integration per workspace**: Uniqueness is enforced at the application level via `findByWorkspaceIdAndIntegrationId`. A race condition could theoretically create duplicates if two webhook events arrive simultaneously.
- **Programmatic transactions in disconnect**: `disconnectConnection` does NOT use `@Transactional` — it uses `TransactionTemplate` explicitly. Adding `@Transactional` to the caller would wrap the entire Nango call in a transaction, defeating the purpose.
- **No public connection creation method**: `createOrReconnect` is `internal` visibility. The only way to create a connection is through the Nango auth webhook.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Connection Management/NangoClientWrapper]] -- External API client for Nango connection management
- [[riven/docs/system-design/domains/Integrations/Enablement/TemplateMaterializationService]] -- Materializes integration templates on connection
- [[riven/docs/system-design/domains/Integrations/Enablement/IntegrationController]] -- REST endpoints consuming this service
- [[riven/docs/system-design/domains/Integrations/Webhook Authentication/NangoWebhookService]] — Creates connections via `createOrReconnect` after webhook-confirmed OAuth
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Connection Management/Connection Management]] -- Parent subdomain
- `ConnectionStatus` — Enum defining the 8-state machine

---

## Changelog

### 2025-07-17

- Initial documentation covering all public methods and key logic
- Documented 10-state connection state machine with full transition table
- Documented `enableConnection` method: single-action enable flow for frontend OAuth integration
- Documented programmatic transaction management in `disconnectConnection`
- Documented Nango integration and failure handling patterns

### 2026-03-18

- Simplified to 8-state machine: removed `PENDING_AUTHORIZATION` and `AUTHORIZING`
- Removed `enableConnection()` and `createConnection()` public methods
- Added `createOrReconnect()` internal method for webhook-driven connection creation
- Connection creation is now exclusively webhook-driven — no public API endpoint
- Updated state machine documentation and transition table
