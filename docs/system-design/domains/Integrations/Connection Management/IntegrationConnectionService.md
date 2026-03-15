---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2025-07-17
Domains:
  - "[[Integrations]]"
---
# IntegrationConnectionService

Part of [[Connection Management]]

## Purpose

Manages the full lifecycle of integration connections between workspaces and external services. Enforces a 10-state connection state machine with validated transitions, coordinates with [[NangoClientWrapper]] for external OAuth connection management, and triggers [[TemplateMaterializationService]] when connections become active.

---

## Responsibilities

- Create new integration connections with workspace-scoped uniqueness enforcement
- Enable connections in a single action (create-and-connect or reconnect from DISCONNECTED)
- Update connection status with state machine validation
- Disconnect connections with graceful Nango cleanup
- Retrieve connections by workspace or by workspace + integration pair
- Trigger template materialization when connections reach CONNECTED state
- Log activity for all mutations (create, update, disconnect)

---

## Dependencies

- `IntegrationConnectionRepository` -- Connection persistence
- `IntegrationDefinitionRepository` -- Validates integration definitions exist, provides Nango provider keys
- [[NangoClientWrapper]] -- External Nango API calls for connection deletion
- [[TemplateMaterializationService]] -- Materializes entity type templates when a connection becomes CONNECTED
- `AuthTokenService` -- JWT user extraction
- `ActivityService` -- Audit logging
- `TransactionTemplate` -- Programmatic transaction management for the disconnect flow

## Used By

- [[IntegrationController]] -- REST endpoints for enable/disable and listing
- Internal services that need to check connection state before performing integration-scoped operations

---

## Key Logic

### Connection state machine

The connection lifecycle is governed by `ConnectionStatus`, an enum with 10 states and explicit transition rules enforced via `canTransitionTo()`. Every call to `updateConnectionStatus` and internal transition helpers validates against this state machine before persisting.

| State | Valid transitions to |
|---|---|
| `PENDING_AUTHORIZATION` | `AUTHORIZING`, `FAILED`, `DISCONNECTED` |
| `AUTHORIZING` | `CONNECTED`, `FAILED` |
| `CONNECTED` | `SYNCING`, `HEALTHY`, `DISCONNECTING`, `FAILED` |
| `SYNCING` | `HEALTHY`, `DEGRADED`, `FAILED` |
| `HEALTHY` | `SYNCING`, `STALE`, `DEGRADED`, `DISCONNECTING`, `FAILED` |
| `DEGRADED` | `HEALTHY`, `STALE`, `FAILED`, `DISCONNECTING` |
| `STALE` | `SYNCING`, `DISCONNECTING`, `FAILED` |
| `DISCONNECTING` | `DISCONNECTED`, `FAILED` |
| `DISCONNECTED` | `PENDING_AUTHORIZATION` |
| `FAILED` | `PENDING_AUTHORIZATION`, `DISCONNECTED` |

Terminal-ish states: `DISCONNECTED` can only re-enter the lifecycle via `PENDING_AUTHORIZATION`. `FAILED` can recover to `PENDING_AUTHORIZATION` or be marked `DISCONNECTED`.

### enableConnection flow

Provides a single-action enable path used by the frontend after the Nango OAuth flow completes:

1. Validate the integration definition exists via `findOrThrow`
2. Check for an existing connection for this workspace + integration pair
3. If existing and `CONNECTED` -- return as-is (idempotent)
4. If existing and `DISCONNECTED` -- reconnect by updating status to `CONNECTED` and replacing the `nangoConnectionId`
5. If existing in any other state -- throw `ConflictException` (connection is in a non-terminal transitional state)
6. If no existing connection -- create a new `IntegrationConnectionEntity` with status `CONNECTED` directly (skipping `PENDING_AUTHORIZATION`/`AUTHORIZING` since Nango has already completed OAuth)
7. Log CREATE activity

Note: `enableConnection` does NOT trigger template materialization. Only `updateConnectionStatus` triggers materialization when transitioning to `CONNECTED`.

### State transition enforcement

`canTransitionTo()` is defined on the `ConnectionStatus` enum itself. Each state declares its set of valid target states via a `when` expression. Invalid transitions cause `InvalidStateTransitionException`, which is mapped to an HTTP error by `@ControllerAdvice`.

The service never sets status directly without checking transitions, except in `enableConnection` and `reconnectConnection` where the target state (`CONNECTED`) is hardcoded and the source state is verified by the surrounding conditional logic.

### Nango integration

The service interacts with Nango exclusively through [[NangoClientWrapper]]:

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

### `createConnection(workspaceId: UUID, integrationId: UUID, nangoConnectionId: String): IntegrationConnectionEntity`

Creates a new connection with `PENDING_AUTHORIZATION` status. Requires ADMIN role. Throws `ConflictException` if a connection already exists for the workspace + integration pair. Validates the integration definition exists. Annotated with `@Transactional`.

### `updateConnectionStatus(workspaceId: UUID, connectionId: UUID, newStatus: ConnectionStatus, metadata: Map<String, Any>?): IntegrationConnectionEntity`

Updates a connection's status after validating the transition via `canTransitionTo()`. Optionally merges metadata into the existing `connectionMetadata` map. Triggers template materialization if the new status is `CONNECTED`. Throws `InvalidStateTransitionException` on invalid transitions. Annotated with `@Transactional`.

### `disconnectConnection(workspaceId: UUID, connectionId: UUID): IntegrationConnectionEntity`

Disconnects a connection using a three-phase flow: transition to `DISCONNECTING`, call Nango to delete the external connection, transition to `DISCONNECTED`. Requires ADMIN role. Uses programmatic transactions via `TransactionTemplate` (NOT `@Transactional`). Nango failures are logged but do not prevent local disconnection.

### `enableConnection(workspaceId: UUID, integrationId: UUID, nangoConnectionId: String): IntegrationConnectionEntity`

Single-action enable for the frontend OAuth flow. Creates a new connection with `CONNECTED` status, reconnects a `DISCONNECTED` connection, or returns an existing `CONNECTED` connection idempotently. Throws `ConflictException` if a connection exists in a non-terminal transitional state. Requires ADMIN role. Annotated with `@Transactional`.

---

## Gotchas

- **State machine strictness**: All 10 states have explicit transition rules. There is no "force" override -- invalid transitions always throw `InvalidStateTransitionException`. Code that needs to move a connection through multiple states must do so step by step.
- **Nango external dependency failure modes**: Nango API calls can throw `NangoApiException`, `RateLimitException`, or `TransientNangoException`. The disconnect flow handles all of these gracefully (log and continue), but other flows that may be added in future must account for these failure modes.
- **enableConnection bypasses intermediate states**: Unlike `createConnection` (which starts at `PENDING_AUTHORIZATION`), `enableConnection` sets `CONNECTED` directly because Nango has already completed the OAuth flow before this method is called. These two creation paths produce connections at different initial states.
- **enableConnection does not trigger materialization**: Only `updateConnectionStatus` triggers `triggerMaterialization()` when reaching `CONNECTED`. Connections created via `enableConnection` skip this path. If materialization is needed, it must be triggered separately.
- **Reconnect overwrites nangoConnectionId**: When `enableConnection` reconnects a `DISCONNECTED` connection, it replaces the stored `nangoConnectionId` with the new value from the frontend flow.
- **One connection per integration per workspace**: Both `createConnection` and `enableConnection` enforce this uniqueness constraint at the application level (not DB-level unique constraint). A race condition could theoretically create duplicates if two requests arrive simultaneously.
- **Programmatic transactions in disconnect**: `disconnectConnection` does NOT use `@Transactional` -- it uses `TransactionTemplate` explicitly. Adding `@Transactional` to the caller would wrap the entire Nango call in a transaction, defeating the purpose.

---

## Related

- [[NangoClientWrapper]] -- External API client for Nango connection management
- [[TemplateMaterializationService]] -- Materializes integration templates on connection
- [[IntegrationController]] -- REST endpoints consuming this service
- [[IntegrationEnablementService]] -- Orchestrates enable/disable lifecycle using this service
- [[Connection Management]] -- Parent subdomain
- `ConnectionStatus` -- Enum defining the 10-state machine

---

## Changelog

### 2025-07-17

- Initial documentation covering all public methods and key logic
- Documented 10-state connection state machine with full transition table
- Documented `enableConnection` method: single-action enable flow for frontend OAuth integration
- Documented programmatic transaction management in `disconnectConnection`
- Documented Nango integration and failure handling patterns
